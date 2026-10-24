import { describe, expect, it, vi } from 'vitest';
import { StationService } from '../../src/services/stationService.js';
import type { GeocodingService } from '../../src/services/geocodingService.js';
import type {
  AnpDataProvider,
  AnpStationPriceInput,
  StationCatalogProvider,
} from '../../src/providers/contracts.js';
import type { SourceAttribution, StationPrice } from '../../src/types.js';
import {
  anpAttribution,
  loadedAnpStatus,
  makeOsmStation,
  makePrice,
  makeRegionalPrice,
  osmAttribution,
} from '../helpers/fixtures.js';

const nominatimAttribution: SourceAttribution = {
  id: 'nominatim',
  name: 'Nominatim / OpenStreetMap',
  url: 'https://nominatim.org',
  description: 'Geocodificação baseada no OpenStreetMap.',
};

const QUERY = { latitude: -23.55, longitude: -46.63, radiusKm: 5, limit: 10 };

function stationA() {
  return makeOsmStation({
    id: 'node_1',
    name: 'Posto A',
    address: { street: 'Avenida Paulista', number: '1000', city: undefined, state: undefined },
    latitude: -23.55,
    longitude: -46.63,
    tags: { amenity: 'fuel', 'ref:vatin': '11.222.333/0001-81' },
  });
}

function stationB() {
  return makeOsmStation({
    id: 'node_2',
    name: 'Posto B',
    address: { street: 'Rua Augusta', number: '200', city: 'São Paulo', state: 'SP' },
    latitude: -23.555,
    longitude: -46.63,
  });
}

function stationFar() {
  return makeOsmStation({
    id: 'node_3',
    name: 'Posto Distante',
    address: { street: 'Estrada Longe', number: '3000', city: 'São Paulo', state: 'SP' },
    latitude: -23.7,
    longitude: -46.63,
  });
}

function setup(
  options: {
    nearbyTtlMs?: number;
    stationTtlMs?: number;
    anpLoaded?: boolean;
    resolveRegion?: boolean;
  } = {},
) {
  const priceByStation: Record<string, StationPrice[]> = {
    'Posto A': [
      makePrice('gasoline', 5.99, { referenceDate: '2026-10-12' }),
      makePrice('ethanol', 4.09, { referenceDate: '2026-10-12' }),
    ],
    'Posto B': [],
  };

  const catalog = {
    findNearby: vi.fn(async () => [stationA(), stationB(), stationFar()]),
    findById: vi.fn(async (id: string) => {
      if (id === 'node_1') return stationA();
      if (id === 'node_2') return stationB();
      return null;
    }),
    attribution: osmAttribution,
  };

  const anp = {
    initialize: vi.fn(async () => undefined),
    findStationPrices: vi.fn(async (input: AnpStationPriceInput) =>
      input.name && priceByStation[input.name] ? priceByStation[input.name] : [],
    ),
    getRegionalPrices: vi.fn(async () => [
      makeRegionalPrice('gasoline', 6.1),
      makeRegionalPrice('ethanol', 4.2),
    ]),
    getStatus: vi.fn(() => ({
      ...loadedAnpStatus,
      loaded: options.anpLoaded ?? true,
    })),
    attribution: anpAttribution,
  };

  const geocoding = {
    search: vi.fn(async () => []),
    reverse: vi.fn(async () =>
      options.resolveRegion === false
        ? null
        : {
            municipality: 'São Paulo',
            state: 'SP',
            latitude: QUERY.latitude,
            longitude: QUERY.longitude,
          },
    ),
    attribution: () => nominatimAttribution,
    cacheStats: () => ({
      search: { hits: 0, misses: 0, size: 0 },
      reverse: { hits: 0, misses: 0, size: 0 },
    }),
  };

  const service = new StationService(
    catalog as unknown as StationCatalogProvider,
    anp as unknown as AnpDataProvider,
    geocoding as unknown as GeocodingService,
    {
      nearbyTtlMs: options.nearbyTtlMs ?? 60_000,
      stationTtlMs: options.stationTtlMs ?? 60_000,
    },
  );

  return { service, catalog, anp, geocoding };
}

describe('StationService.nearby', () => {
  it('filtra por raio, ordena por distância e separa preços individuais de médias municipais', async () => {
    const { service, catalog, anp } = setup();
    const { data, meta } = await service.nearby(QUERY);

    expect(data.stations.map((station) => station.id)).toEqual(['node_1', 'node_2']);
    expect(data.stations[0]?.distanceKm).toBe(0);
    expect(data.stations[0]?.prices.map((price) => price.fuel)).toEqual(['gasoline', 'ethanol']);
    expect(data.stations[0]?.prices.every((price) => price.priceType === 'individual')).toBe(true);
    expect(data.stations[1]?.prices).toEqual([]);
    expect(data.regionalPrices).toHaveLength(2);
    expect(data.regionalPrices.every((price) => price.priceType === 'municipal_average')).toBe(
      true,
    );
    expect(data.region).toMatchObject({
      municipality: 'São Paulo',
      state: 'SP',
      resolvedFrom: 'geocoder',
    });
    expect(data.lastUpdated).toBe('2026-10-17T12:00:00.000Z');
    expect(data.sources.map((source) => source.id).sort()).toEqual([
      'anp',
      'nominatim',
      'openstreetmap',
    ]);
    expect(meta).toMatchObject({ cached: false, warnings: [] });
    expect(anp.findStationPrices).toHaveBeenCalledWith(
      expect.objectContaining({
        cnpj: '11.222.333/0001-81',
        city: 'São Paulo',
        state: 'SP',
      }),
    );
    expect(catalog.findNearby).toHaveBeenCalledWith(QUERY);
  });

  it('reaproveita a resposta em cache para a mesma região arredondada', async () => {
    const { service, catalog } = setup();
    await service.nearby(QUERY);
    const second = await service.nearby({ ...QUERY, latitude: -23.5501 });

    expect(second.meta.cached).toBe(true);
    expect(catalog.findNearby).toHaveBeenCalledTimes(1);
    expect(second.data.stations).toHaveLength(2);
  });

  it('usa a última resposta válida quando o provedor falha após o cache expirar', async () => {
    const { service, catalog } = setup({ nearbyTtlMs: 1 });
    await service.nearby(QUERY);
    await new Promise((resolve) => setTimeout(resolve, 15));
    catalog.findNearby.mockRejectedValueOnce(new Error('Overpass indisponível'));

    const result = await service.nearby(QUERY);
    expect(result.meta.cached).toBe(true);
    expect(result.meta.warnings[0]).toContain('última resposta armazenada');
    expect(result.data.stations).toHaveLength(2);
  });

  it('não inventa preços e avisa quando a ANP está indisponível', async () => {
    const { service, anp } = setup({ anpLoaded: false });
    anp.findStationPrices.mockResolvedValue([]);
    anp.getRegionalPrices.mockResolvedValue([]);

    const { data, meta } = await service.nearby(QUERY);
    expect(data.stations).toHaveLength(2);
    expect(data.stations.every((station) => station.prices.length === 0)).toBe(true);
    expect(data.regionalPrices).toEqual([]);
    expect(meta.warnings).toContain(
      'Os dados oficiais de preços da ANP estão indisponíveis no momento.',
    );
  });

  it('resolve o município a partir do primeiro posto quando a geocodificação falha', async () => {
    const { service } = setup({ resolveRegion: false });

    const { data, meta } = await service.nearby(QUERY);
    expect(data.region).toMatchObject({
      municipality: 'São Paulo',
      state: 'SP',
      resolvedFrom: 'station',
    });
    expect(meta.warnings).toContain('Não foi possível identificar o município da busca.');
  });
});

describe('StationService.details', () => {
  it('retorna dados do posto e médias municipais separadas', async () => {
    const { service } = setup();
    const { data, meta } = await service.details('node_1');
    expect(data.station.id).toBe('node_1');
    expect(data.station.prices).toHaveLength(2);
    expect(data.regionalPrices).toHaveLength(2);
    expect(data.lastUpdated).toBe('2026-10-17T12:00:00.000Z');
    expect(meta.cached).toBe(false);
  });

  it('reaproveita o cache de detalhes', async () => {
    const { service, catalog } = setup();
    await service.details('node_1');
    const second = await service.details('node_1');
    expect(second.meta.cached).toBe(true);
    expect(catalog.findById).toHaveBeenCalledTimes(1);
  });

  it('retorna 404 para posto inexistente', async () => {
    const { service } = setup();
    await expect(service.details('node_999')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    });
  });

  it('expõe apenas dados de preços no endpoint de preços', async () => {
    const { service } = setup();
    const { data, meta } = await service.pricesForStation('node_1');
    expect(Object.keys(data).sort()).toEqual(['lastUpdated', 'regionalPrices', 'sources', 'station']);
    expect(data.station.id).toBe('node_1');
    expect(meta.cached).toBe(false);
  });
});

describe('StationService.compare', () => {
  it('compara dois postos e trata combustíveis ausentes', async () => {
    const { service, anp } = setup();
    anp.findStationPrices.mockImplementation(async (input: AnpStationPriceInput) => {
      if (input.name === 'Posto A') {
        return [makePrice('gasoline', 5.99), makePrice('ethanol', 4.09)];
      }
      if (input.name === 'Posto B') {
        return [makePrice('gasoline', 6.19), makePrice('diesel', 6.49)];
      }
      return [];
    });

    const { data } = await service.compare('node_1', 'node_2');
    expect(data.stationA).toEqual({ id: 'node_1', name: 'Posto A' });
    expect(data.stationB).toEqual({ id: 'node_2', name: 'Posto B' });
    expect(data.rows.map((row) => [row.fuel, row.note])).toEqual([
      ['gasoline', 'comparable'],
      ['ethanol', 'only_a'],
      ['diesel', 'only_b'],
    ]);
    expect(data.rows[0]).toMatchObject({ difference: -0.2, cheaper: 'A' });
  });

  it('rejeita comparação do mesmo posto', async () => {
    const { service } = setup();
    await expect(service.compare('node_1', 'node_1')).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_PARAMETERS',
    });
  });
});
