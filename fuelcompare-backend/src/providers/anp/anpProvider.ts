import { env } from '../../config/env.js';
import {
  FUEL_CODES,
  FUEL_INFO,
  type RegionalPrice,
  type SourceAttribution,
  type StationPrice,
} from '../../types.js';
import { fetchBuffer, fetchResponse } from '../../utils/http.js';
import { normalizeText, normalizeState } from '../../utils/text.js';
import { logger } from '../../utils/logger.js';
import type {
  AnpDataProvider,
  AnpStationPriceInput,
  AnpStatus,
} from '../contracts.js';
import {
  ANP_SOURCE_NAME,
  parseResumoWorkbook,
  parseRevendasWorkbook,
  type AnpPeriod,
  type AnpStationEntry,
} from './anpParser.js';
import { buildAnpIndex, matchAnpStation, type AnpIndex } from './anpMatcher.js';

interface AnpDataset {
  index: AnpIndex;
  regionalByCity: Map<string, RegionalPrice[]>;
  period?: AnpPeriod;
  sources: { revendas?: string; resumo?: string };
  warnings: string[];
  loadedAt: number;
}

interface ResolvedUrls {
  revendas?: string;
  resumo?: string;
}

const RETRY_AFTER_FAILURE_MS = 5 * 60 * 1000;
const DISCOVERY_TTL_MS = 6 * 60 * 60 * 1000;
const ANP_ASSET_PREFIX = 'https://www.gov.br/';

function extractFileDates(url: string): number {
  let latest = 0;
  for (const match of url.matchAll(/(\d{4})[_-](\d{2})[_-](\d{2})/g)) {
    const [, year, month, day] = match;
    const time = Date.UTC(Number(year), Number(month) - 1, Number(day));
    if (Number.isFinite(time)) latest = Math.max(latest, time);
  }
  return latest;
}

export class AnpPriceProvider implements AnpDataProvider {
  readonly attribution: SourceAttribution = {
    id: 'anp',
    name: 'ANP - Levantamento de Preços de Combustíveis',
    url: 'https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos/levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas',
    description:
      'Preços individuais coletados pela ANP e médias municipais oficiais. Os preços não são em tempo real.',
  };

  private dataset: AnpDataset | null = null;
  private loadPromise: Promise<void> | null = null;
  private failedAt = 0;
  private lastError: string | undefined;
  private lastUrls: ResolvedUrls = {};
  private lastDiscoveryAt = 0;

  constructor(
    private readonly options: {
      pageUrl?: string;
      revendasUrl?: string;
      resumoUrl?: string;
      ttlMs?: number;
    } = {},
  ) {
    this.lastUrls = {
      ...(options.revendasUrl ?? env.ANP_REVENDAS_XLSX_URL
        ? { revendas: options.revendasUrl ?? env.ANP_REVENDAS_XLSX_URL }
        : {}),
      ...(options.resumoUrl ?? env.ANP_RESUMO_XLSX_URL
        ? { resumo: options.resumoUrl ?? env.ANP_RESUMO_XLSX_URL }
        : {}),
    };
  }

  async initialize(): Promise<void> {
    try {
      await this.ensureLoaded();
    } catch {
      // O status e o log já registram a falha; a API continua funcionando sem preços.
    }
  }

  async findStationPrices(input: AnpStationPriceInput): Promise<StationPrice[]> {
    const dataset = await this.ensureLoaded();
    if (!dataset) return [];
    const match = matchAnpStation(dataset.index, input);
    if (!match) return [];

    const prices: StationPrice[] = [];
    for (const [fuel, product] of match.entry.products.entries()) {
      const referenceDate = product.collectedAt ?? dataset.period?.end;
      if (!referenceDate) continue;
      prices.push({
        fuel,
        amount: product.amount,
        currency: 'BRL',
        unit: FUEL_INFO[fuel].unit,
        priceType: 'individual',
        source: {
          name: ANP_SOURCE_NAME,
          ...(dataset.sources.revendas ? { url: dataset.sources.revendas } : {}),
          official: true,
        },
        referenceDate,
        collectedAt: `${referenceDate}T12:00:00.000Z`,
        validationStatus: 'official_survey',
        matchMethod: match.method,
      });
    }

    return prices.sort(
      (a, b) => FUEL_CODES.indexOf(a.fuel) - FUEL_CODES.indexOf(b.fuel),
    );
  }

  async getRegionalPrices(
    city: string | undefined,
    state: string | undefined,
  ): Promise<RegionalPrice[]> {
    if (!city || !state) return [];
    const dataset = await this.ensureLoaded();
    if (!dataset) return [];
    const key = `${normalizeText(city)}|${normalizeState(state)}`;
    return (dataset.regionalByCity.get(key) ?? []).map((price) => ({
      ...price,
      source: { ...price.source },
    }));
  }

  getStatus(): AnpStatus {
    const dataset = this.dataset;
    return {
      loaded: dataset !== null,
      stale: dataset !== null && Date.now() - dataset.loadedAt > this.ttlMs,
      ...(dataset?.period ? { datasetPeriod: dataset.period } : {}),
      ...(dataset ? { loadedAt: new Date(dataset.loadedAt).toISOString() } : {}),
      sources: dataset?.sources ?? this.lastUrls,
      warnings: dataset?.warnings ?? [],
      ...(this.lastError ? { lastError: this.lastError } : {}),
    };
  }

  private get ttlMs(): number {
    return this.options.ttlMs ?? env.ANP_CACHE_TTL_SECONDS * 1000;
  }

  private async ensureLoaded(): Promise<AnpDataset | null> {
    if (this.dataset && Date.now() - this.dataset.loadedAt < this.ttlMs) {
      return this.dataset;
    }
    if (this.loadPromise) {
      await this.loadPromise;
      return this.dataset;
    }
    if (this.dataset && this.failedAt && Date.now() - this.failedAt < RETRY_AFTER_FAILURE_MS) {
      return this.dataset;
    }

    this.loadPromise = this.refresh().finally(() => {
      this.loadPromise = null;
    });
    await this.loadPromise;
    return this.dataset;
  }

  private async refresh(): Promise<void> {
    try {
      const urls = await this.resolveUrls();
      this.lastUrls = urls;
      if (!urls.revendas && !urls.resumo) {
        throw new Error('Nenhum arquivo do levantamento da ANP foi localizado.');
      }

      const [revendasResult, resumoResult] = await Promise.allSettled([
        urls.revendas
          ? fetchBuffer(urls.revendas, {
              timeoutMs: 90_000,
              retries: 1,
              maxBytes: 25 * 1024 * 1024,
              headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
            })
          : Promise.reject(new Error('URL de revendas ausente.')),
        urls.resumo
          ? fetchBuffer(urls.resumo, {
              timeoutMs: 90_000,
              retries: 1,
              maxBytes: 15 * 1024 * 1024,
              headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
            })
          : Promise.reject(new Error('URL de resumo ausente.')),
      ]);

      const warnings: string[] = [];
      let stations: AnpStationEntry[] = [];
      let regionalPrices: RegionalPrice[] = [];
      let period: AnpPeriod | undefined;

      if (revendasResult.status === 'fulfilled') {
        const parsed = await parseRevendasWorkbook(
          revendasResult.value.buffer,
          revendasResult.value.finalUrl,
        );
        stations = parsed.stations;
        period = parsed.period ?? period;
        warnings.push(...parsed.warnings);
      } else {
        warnings.push('Os preços individuais da ANP não puderam ser atualizados.');
        logger.warn(
          { reason: revendasResult.reason instanceof Error ? revendasResult.reason.message : 'unknown' },
          'Falha ao carregar preços individuais da ANP',
        );
      }

      if (resumoResult.status === 'fulfilled') {
        const parsed = await parseResumoWorkbook(
          resumoResult.value.buffer,
          resumoResult.value.finalUrl,
        );
        regionalPrices = parsed.prices;
        period = period ?? parsed.period;
        warnings.push(...parsed.warnings);
      } else {
        warnings.push('As médias municipais da ANP não puderam ser atualizadas.');
        logger.warn(
          { reason: resumoResult.reason instanceof Error ? resumoResult.reason.message : 'unknown' },
          'Falha ao carregar resumo de preços da ANP',
        );
      }

      if (stations.length === 0 && regionalPrices.length === 0) {
        throw new Error('Os arquivos da ANP foram baixados, mas não produziram dados utilizáveis.');
      }

      const regionalByCity = new Map<string, RegionalPrice[]>();
      for (const price of regionalPrices) {
        const key = `${normalizeText(price.municipality)}|${normalizeState(price.state)}`;
        const current = regionalByCity.get(key) ?? [];
        current.push(price);
        regionalByCity.set(key, current);
      }

      this.dataset = {
        index: buildAnpIndex(stations),
        regionalByCity,
        ...(period ? { period } : {}),
        sources: urls,
        warnings,
        loadedAt: Date.now(),
      };
      this.failedAt = 0;
      this.lastError = undefined;

      logger.info(
        {
          stations: stations.length,
          regionalPrices: regionalPrices.length,
          period: period ?? null,
        },
        'Levantamento de preços da ANP atualizado',
      );
    } catch (error) {
      this.failedAt = Date.now();
      this.lastError = 'Não foi possível atualizar os dados oficiais da ANP.';
      logger.warn(
        { reason: error instanceof Error ? error.message : 'unknown' },
        'Falha ao atualizar levantamento da ANP',
      );
      if (!this.dataset) throw error;
    }
  }

  private async resolveUrls(): Promise<ResolvedUrls> {
    const configured: ResolvedUrls = {
      ...(this.options.revendasUrl ?? env.ANP_REVENDAS_XLSX_URL
        ? { revendas: this.options.revendasUrl ?? env.ANP_REVENDAS_XLSX_URL }
        : {}),
      ...(this.options.resumoUrl ?? env.ANP_RESUMO_XLSX_URL
        ? { resumo: this.options.resumoUrl ?? env.ANP_RESUMO_XLSX_URL }
        : {}),
    };

    if (configured.revendas && configured.resumo) return configured;
    if (this.lastDiscoveryAt && Date.now() - this.lastDiscoveryAt < DISCOVERY_TTL_MS) {
      return { ...this.lastUrls, ...configured };
    }

    const pageUrl = this.options.pageUrl ?? env.ANP_PRICE_PAGE_URL;
    try {
      const response = await fetchResponse(pageUrl, {
        timeoutMs: 30_000,
        retries: 1,
        headers: { Accept: 'text/html' },
      });
      if (!response.ok) throw new Error(`Página da ANP respondeu ${response.status}.`);
      const html = await response.text();
      const discovered = this.discoverUrlsFromHtml(html, pageUrl);
      this.lastDiscoveryAt = Date.now();
      return { ...discovered, ...configured };
    } catch (error) {
      logger.warn(
        { reason: error instanceof Error ? error.message : 'unknown' },
        'Falha ao descobrir arquivos da ANP; usando URLs conhecidas',
      );
      return { ...this.lastUrls, ...configured };
    }
  }

  private discoverUrlsFromHtml(html: string, pageUrl: string): ResolvedUrls {
    const candidates: { url: string; revendas: boolean }[] = [];
    for (const match of html.matchAll(/href=["']([^"']+\.xlsx[^"']*)["']/gi)) {
      const raw = match[1]?.replace(/&amp;/g, '&');
      if (!raw) continue;
      let url: URL;
      try {
        url = new URL(raw, pageUrl);
      } catch {
        continue;
      }
      if (!url.toString().startsWith(ANP_ASSET_PREFIX)) continue;
      const lower = url.pathname.toLowerCase();
      if (!lower.includes('arquivos-lpc')) continue;
      if (lower.includes('revendas_lpc')) {
        candidates.push({ url: url.toString(), revendas: true });
      } else if (lower.includes('resumo_semanal_lpc')) {
        candidates.push({ url: url.toString(), revendas: false });
      }
    }

    const latest = (revendas: boolean) =>
      candidates
        .filter((candidate) => candidate.revendas === revendas)
        .sort((a, b) => extractFileDates(b.url) - extractFileDates(a.url))[0]?.url;

    return {
      ...(latest(true) ? { revendas: latest(true) } : {}),
      ...(latest(false) ? { resumo: latest(false) } : {}),
    };
  }
}
