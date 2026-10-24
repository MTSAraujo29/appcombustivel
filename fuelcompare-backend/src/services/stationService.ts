import {
  type DetailsData,
  type FuelCode,
  type Meta,
  type NearbyFeed,
  type PriceComparison,
  type RegionalPrice,
  type Region,
  type SourceAttribution,
  type Station,
  type StationPrice,
} from '../types.js';
import type {
  AnpDataProvider,
  NearbyParams,
  OsmStation,
  StationCatalogProvider,
} from '../providers/contracts.js';
import { TtlCache } from '../utils/cache.js';
import { haversineKm, roundCoordinate } from '../utils/geo.js';
import { normalizeState, normalizeText } from '../utils/text.js';
import { logger } from '../utils/logger.js';
import { badRequest, notFound } from '../utils/errors.js';
import { buildComparison } from './comparisonService.js';
import type { GeocodingService } from './geocodingService.js';

export interface NearbyQuery extends NearbyParams {
  city?: string;
  state?: string;
}

export interface StationServiceOptions {
  nearbyTtlMs: number;
  stationTtlMs: number;
}

interface LoadedDetails {
  data: DetailsData;
  warnings: string[];
}

const CNPJ_TAGS = ['ref:vatin', 'ref:BR:CNPJ', 'ref:cnpj', 'cnpj', 'ref:CNPJ'];

function extractCnpj(tags: Record<string, string>): string | undefined {
  for (const key of CNPJ_TAGS) {
    const value = tags[key];
    if (value) return value;
  }
  return undefined;
}

function isoFromDateOnly(date: string | undefined): string | undefined {
  if (!date) return undefined;
  return `${date}T12:00:00.000Z`;
}

function latestDate(dates: (string | undefined)[]): string | undefined {
  const valid = dates.filter((date): date is string => Boolean(date)).sort();
  return valid.at(-1);
}

function sortPrices(prices: StationPrice[]): StationPrice[] {
  const order: FuelCode[] = [
    'gasoline',
    'gasoline_additivated',
    'ethanol',
    'diesel_s10',
    'diesel',
    'gnv',
  ];
  return [...prices].sort((a, b) => order.indexOf(a.fuel) - order.indexOf(b.fuel));
}

export class StationService {
  private readonly nearbyCache: TtlCache<NearbyFeed>;
  private readonly detailsCache: TtlCache<DetailsData>;
  private readonly comparisonCache: TtlCache<PriceComparison>;

  constructor(
    private readonly catalog: StationCatalogProvider,
    private readonly anp: AnpDataProvider,
    private readonly geocoding: GeocodingService,
    private readonly options: StationServiceOptions,
  ) {
    this.nearbyCache = new TtlCache<NearbyFeed>(options.nearbyTtlMs, Date.now, 200);
    this.detailsCache = new TtlCache<DetailsData>(options.stationTtlMs, Date.now, 500);
    this.comparisonCache = new TtlCache<PriceComparison>(
      Math.min(options.stationTtlMs, options.nearbyTtlMs),
      Date.now,
      200,
    );
  }

  async nearby(query: NearbyQuery): Promise<{ data: NearbyFeed; meta: Meta }> {
    const key = this.nearbyKey(query);
    const cached = this.nearbyCache.get(key);
    if (cached) {
      return {
        data: cached,
        meta: { cached: true, generatedAt: new Date().toISOString(), warnings: [] },
      };
    }

    try {
      const { feed, warnings } = await this.buildNearby(query);
      this.nearbyCache.set(key, feed);
      return {
        data: feed,
        meta: { cached: false, generatedAt: new Date().toISOString(), warnings },
      };
    } catch (error) {
      const stale = this.nearbyCache.peek(key);
      if (stale) {
        return {
          data: stale,
          meta: {
            cached: true,
            generatedAt: new Date().toISOString(),
            warnings: ['O provedor está indisponível; exibindo a última resposta armazenada.'],
          },
        };
      }
      throw error;
    }
  }

  async details(id: string): Promise<{ data: DetailsData; meta: Meta }> {
    const cached = this.detailsCache.get(id);
    if (cached) {
      return {
        data: cached,
        meta: { cached: true, generatedAt: new Date().toISOString(), warnings: [] },
      };
    }

    try {
      const { data, warnings } = await this.loadDetails(id);
      this.detailsCache.set(id, data);
      return {
        data,
        meta: { cached: false, generatedAt: new Date().toISOString(), warnings },
      };
    } catch (error) {
      const stale = this.detailsCache.peek(id);
      if (stale) {
        return {
          data: stale,
          meta: {
            cached: true,
            generatedAt: new Date().toISOString(),
            warnings: ['O provedor está indisponível; exibindo a última resposta armazenada.'],
          },
        };
      }
      throw error;
    }
  }

  async pricesForStation(
    id: string,
  ): Promise<{ data: Pick<DetailsData, 'station' | 'regionalPrices' | 'lastUpdated' | 'sources'>; meta: Meta }> {
    const result = await this.details(id);
    return {
      data: {
        station: result.data.station,
        regionalPrices: result.data.regionalPrices,
        lastUpdated: result.data.lastUpdated,
        sources: result.data.sources,
      },
      meta: result.meta,
    };
  }

  async compare(stationAId: string, stationBId: string): Promise<{ data: PriceComparison; meta: Meta }> {
    if (stationAId === stationBId) {
      throw badRequest('Selecione dois postos diferentes para comparar.');
    }

    const key = `${stationAId}|${stationBId}`;
    const cached = this.comparisonCache.get(key);
    if (cached) {
      return {
        data: cached,
        meta: { cached: true, generatedAt: new Date().toISOString(), warnings: [] },
      };
    }

    const [a, b] = await Promise.all([this.loadDetails(stationAId), this.loadDetails(stationBId)]);
    this.detailsCache.set(stationAId, a.data);
    this.detailsCache.set(stationBId, b.data);

    const comparison = buildComparison(a.data.station, b.data.station);
    this.comparisonCache.set(key, comparison);
    return {
      data: comparison,
      meta: {
        cached: false,
        generatedAt: new Date().toISOString(),
        warnings: [...new Set([...a.warnings, ...b.warnings])],
      },
    };
  }

  cacheStats() {
    return {
      nearby: this.nearbyCache.stats(),
      details: this.detailsCache.stats(),
      comparison: this.comparisonCache.stats(),
      geocoding: this.geocoding.cacheStats(),
    };
  }

  private nearbyKey(query: NearbyQuery): string {
    return [
      roundCoordinate(query.latitude, 3),
      roundCoordinate(query.longitude, 3),
      query.radiusKm,
      query.limit,
      normalizeText(query.city),
      normalizeState(query.state),
    ].join('|');
  }

  private async buildNearby(
    query: NearbyQuery,
  ): Promise<{ feed: NearbyFeed; warnings: string[] }> {
    const warnings: string[] = [];
    const sources: SourceAttribution[] = [this.catalog.attribution];

    let region = await this.resolveRegion(query, warnings);
    if (!region) {
      sources.push(this.geocoding.attribution());
    }

    const stations = await this.catalog.findNearby(query);
    const normalizedStations = stations
      .map((station) => ({
        station,
        distanceKm: haversineKm(
          { latitude: query.latitude, longitude: query.longitude },
          { latitude: station.latitude, longitude: station.longitude },
        ),
      }))
      .filter(({ distanceKm }) => distanceKm <= query.radiusKm + 0.05)
      .sort((a, b) => a.distanceKm - b.distanceKm)
      .slice(0, query.limit);

    if (!region) {
      const fallback = normalizedStations.find(
        ({ station }) =>
          station.address.city && normalizeState(station.address.state) !== '',
      )?.station;
      if (fallback?.address.city && fallback.address.state) {
        region = {
          municipality: fallback.address.city,
          state: normalizeState(fallback.address.state),
          latitude: query.latitude,
          longitude: query.longitude,
          resolvedFrom: 'station',
        };
      }
    }

    const anpStatus = this.anp.getStatus();
    if (!anpStatus.loaded) {
      warnings.push('Os dados oficiais de preços da ANP estão indisponíveis no momento.');
    } else if (anpStatus.stale || anpStatus.lastError) {
      warnings.push('Os dados oficiais da ANP podem estar desatualizados.');
    }
    if (!region) {
      warnings.push(
        'Município não identificado; os preços oficiais da ANP não puderam ser associados.',
      );
    }

    const priceResults = await Promise.allSettled(
      normalizedStations.map(async ({ station, distanceKm }) => {
        const city = station.address.city ?? region?.municipality;
        const state = normalizeState(station.address.state) || region?.state;
        const prices = await this.anp.findStationPrices({
          cnpj: extractCnpj(station.tags),
          name: station.name,
          brand: station.brand,
          street: station.address.street,
          number: station.address.number,
          city,
          state,
        });
        return {
          station: this.toStation(station, prices, distanceKm),
        } satisfies { station: Station };
      }),
    );

    const resultStations: Station[] = [];
    for (const result of priceResults) {
      if (result.status === 'fulfilled') {
        resultStations.push(result.value.station);
      } else {
        logger.warn({ reason: result.reason }, 'Falha ao associar preços a um posto');
      }
    }

    let regionalPrices: RegionalPrice[] = [];
    if (region) {
      try {
        regionalPrices = await this.anp.getRegionalPrices(
          region.municipality,
          region.state,
        );
        if (regionalPrices.length === 0) {
          warnings.push('Não há médias municipais da ANP disponíveis para este município.');
        }
      } catch (error) {
        logger.warn({ reason: error }, 'Falha ao obter médias municipais da ANP');
        warnings.push('As médias municipais da ANP não estão disponíveis neste momento.');
      }
    }

    const lastUpdated = latestDate([
      ...resultStations.flatMap((station) =>
        station.prices.map((price) => isoFromDateOnly(price.referenceDate)),
      ),
      ...regionalPrices.map((price) => isoFromDateOnly(price.referenceEnd)),
    ]);

    if (anpStatus.loaded || regionalPrices.length > 0) {
      sources.push(this.anp.attribution);
    }
    if (region?.resolvedFrom === 'geocoder') {
      sources.push(this.geocoding.attribution());
    }

    const feed: NearbyFeed = {
      stations: resultStations,
      regionalPrices,
      ...(region ? { region } : {}),
      ...(lastUpdated ? { lastUpdated } : {}),
      sources: dedupeSources(sources),
    };
    return { feed, warnings: [...new Set(warnings)] };
  }

  private async resolveRegion(
    query: NearbyQuery,
    warnings: string[],
  ): Promise<Region | undefined> {
    if (query.city && query.state) {
      return {
        municipality: query.city,
        state: normalizeState(query.state),
        latitude: query.latitude,
        longitude: query.longitude,
        resolvedFrom: 'request',
      };
    }

    try {
      const reversed = await this.geocoding.reverse(query.latitude, query.longitude);
      if (reversed) {
        return {
          ...reversed,
          latitude: query.latitude,
          longitude: query.longitude,
          resolvedFrom: 'geocoder',
        };
      }
      warnings.push('Não foi possível identificar o município da busca.');
    } catch (error) {
      logger.warn({ reason: error }, 'Falha na geocodificação reversa');
      warnings.push('Não foi possível identificar o município da busca.');
    }
    return undefined;
  }

  private async loadDetails(id: string): Promise<LoadedDetails> {
    const warnings: string[] = [];
    const station = await this.catalog.findById(id);
    if (!station) {
      throw notFound('Posto não encontrado.');
    }

    let city = station.address.city;
    let state = normalizeState(station.address.state);
    let usedGeocoder = false;

    if (!city || !state) {
      usedGeocoder = true;
      try {
        const reversed = await this.geocoding.reverse(station.latitude, station.longitude);
        if (reversed) {
          city = city ?? reversed.municipality;
          state = state || reversed.state;
        }
      } catch (error) {
        logger.warn({ reason: error }, 'Falha na geocodificação reversa do posto');
        warnings.push('Não foi possível identificar o município deste posto.');
      }
    }

    const prices = await this.anp.findStationPrices({
      cnpj: extractCnpj(station.tags),
      name: station.name,
      brand: station.brand,
      street: station.address.street,
      number: station.address.number,
      city,
      state,
    });
    const regionalPrices = await this.anp.getRegionalPrices(city, state);

    if (!this.anp.getStatus().loaded) {
      warnings.push('Os dados oficiais de preços da ANP estão indisponíveis no momento.');
    }

    const stationData = this.toStation(station, prices);
    const lastUpdated = latestDate([
      ...prices.map((price) => isoFromDateOnly(price.referenceDate)),
      ...regionalPrices.map((price) => isoFromDateOnly(price.referenceEnd)),
    ]);

    const sources: SourceAttribution[] = [this.catalog.attribution];
    if (this.anp.getStatus().loaded || regionalPrices.length > 0) sources.push(this.anp.attribution);
    if (usedGeocoder) sources.push(this.geocoding.attribution());

    return {
      data: {
        station: stationData,
        regionalPrices,
        ...(lastUpdated ? { lastUpdated } : {}),
        sources: dedupeSources(sources),
      },
      warnings,
    };
  }

  private toStation(
    station: OsmStation,
    prices: StationPrice[],
    distanceKm?: number,
  ): Station {
    return {
      id: station.id,
      name: station.name,
      ...(station.brand ? { brand: station.brand } : {}),
      address: station.address,
      latitude: station.latitude,
      longitude: station.longitude,
      ...(distanceKm !== undefined
        ? { distanceKm: Math.round(distanceKm * 1000) / 1000 }
        : {}),
      prices: sortPrices(prices),
    };
  }
}

function dedupeSources(sources: SourceAttribution[]): SourceAttribution[] {
  const seen = new Set<string>();
  return sources.filter((source) => {
    if (seen.has(source.id)) return false;
    seen.add(source.id);
    return true;
  });
}
