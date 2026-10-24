import { env } from '../../config/env.js';
import type { LocationSuggestion, SourceAttribution } from '../../types.js';
import { fetchJson } from '../../utils/http.js';
import { upstreamUnavailable } from '../../utils/errors.js';
import { logger } from '../../utils/logger.js';
import type { GeocoderProvider, ReverseGeocodeResult } from '../contracts.js';
import {
  mapReverseResult,
  mapSearchResults,
  type NominatimResult,
} from './nominatimMapper.js';

const MIN_INTERVAL_MS = 1_100;

/**
 * Cliente da API pública do Nominatim. Respeita o uso justo:
 * uma requisição por vez, intervalo mínimo e identificação por User-Agent.
 * Os resultados são cacheados no serviço de geocodificação.
 */
export class NominatimProvider implements GeocoderProvider {
  readonly attribution: SourceAttribution = {
    id: 'nominatim',
    name: 'Nominatim / OpenStreetMap',
    url: 'https://nominatim.org/release-docs/latest/api/Overview/',
    description:
      'Busca manual por cidade, CEP e endereço. Resultados © colaboradores do OpenStreetMap, ODbL.',
  };

  private queue: Promise<void> = Promise.resolve();
  private lastRequestAt = 0;

  constructor(private readonly baseUrl: string = env.NOMINATIM_URL) {}

  async search(query: string, limit: number): Promise<LocationSuggestion[]> {
    const params = new URLSearchParams({
      q: query,
      format: 'jsonv2',
      addressdetails: '1',
      countrycodes: 'br',
      'accept-language': 'pt-BR',
      limit: String(Math.min(Math.max(limit, 1), 10)),
    });
    const results = await this.request<NominatimResult[]>(`/search?${params.toString()}`);
    return mapSearchResults(Array.isArray(results) ? results : [], limit);
  }

  async reverse(latitude: number, longitude: number): Promise<ReverseGeocodeResult | null> {
    const params = new URLSearchParams({
      lat: latitude.toFixed(6),
      lon: longitude.toFixed(6),
      format: 'jsonv2',
      addressdetails: '1',
      zoom: '10',
      'accept-language': 'pt-BR',
    });
    const result = await this.request<NominatimResult>(`/reverse?${params.toString()}`);
    return mapReverseResult(result);
  }

  private async request<T>(path: string): Promise<T> {
    await this.throttle();
    try {
      return await fetchJson<T>(`${this.baseUrl}${path}`, {
        timeoutMs: 20_000,
        retries: 1,
        headers: { Accept: 'application/json' },
      });
    } catch (error) {
      throw upstreamUnavailable(
        'A busca de localização está temporariamente indisponível. Tente novamente.',
        error,
      );
    }
  }

  private throttle(): Promise<void> {
    this.queue = this.queue.then(async () => {
      const wait = MIN_INTERVAL_MS - (Date.now() - this.lastRequestAt);
      if (wait > 0) {
        await new Promise((resolve) => setTimeout(resolve, wait));
      }
      this.lastRequestAt = Date.now();
    });
    return this.queue.catch((error) => {
      logger.warn({ err: error }, 'Falha no controle de taxa do Nominatim');
    });
  }
}
