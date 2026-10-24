import { describe, expect, it, vi } from 'vitest';
import request from 'supertest';
import { createApp, type AppConfig, type AppDependencies } from '../../src/app.js';
import type { StationService } from '../../src/services/stationService.js';
import type { GeocodingService } from '../../src/services/geocodingService.js';
import type { AnpDataProvider } from '../../src/providers/contracts.js';
import type { DetailsData, Meta, NearbyFeed } from '../../src/types.js';
import {
  anpAttribution,
  loadedAnpStatus,
  makePrice,
  makeRegionalPrice,
  makeStation,
  osmAttribution,
} from '../helpers/fixtures.js';
import { notFound } from '../../src/utils/errors.js';

const meta: Meta = {
  cached: false,
  generatedAt: '2026-10-24T12:00:00.000Z',
  warnings: [],
};

const feed: NearbyFeed = {
  stations: [makeStation({ prices: [makePrice('gasoline', 5.89)] })],
  regionalPrices: [makeRegionalPrice('gasoline', 6.1)],
  region: {
    municipality: 'São Paulo',
    state: 'SP',
    latitude: -23.55,
    longitude: -46.63,
    resolvedFrom: 'geocoder',
  },
  lastUpdated: '2026-10-17T12:00:00.000Z',
  sources: [osmAttribution, anpAttribution],
};

const details: DetailsData = {
  station: makeStation({ prices: [makePrice('gasoline', 5.89)] }),
  regionalPrices: [makeRegionalPrice('gasoline', 6.1)],
  lastUpdated: '2026-10-17T12:00:00.000Z',
  sources: [osmAttribution, anpAttribution],
};

function setup(config: AppConfig = {}) {
  const stationService = {
    nearby: vi.fn(async () => ({ data: feed, meta })),
    details: vi.fn(async () => ({ data: details, meta })),
    pricesForStation: vi.fn(async () => ({
      data: {
        station: details.station,
        regionalPrices: details.regionalPrices,
        lastUpdated: details.lastUpdated,
        sources: details.sources,
      },
      meta,
    })),
    compare: vi.fn(async () => ({
      data: {
        stationA: { id: 'node_1', name: 'Posto A' },
        stationB: { id: 'node_2', name: 'Posto B' },
        rows: [],
      },
      meta,
    })),
    cacheStats: () => ({
      nearby: { hits: 0, misses: 0, size: 0 },
      details: { hits: 0, misses: 0, size: 0 },
      comparison: { hits: 0, misses: 0, size: 0 },
      geocoding: {
        search: { hits: 0, misses: 0, size: 0 },
        reverse: { hits: 0, misses: 0, size: 0 },
      },
    }),
  };

  const geocodingService = {
    search: vi.fn(async () => ({
      results: [
        {
          id: 'relation_1',
          displayName: 'São Paulo, Brasil',
          municipality: 'São Paulo',
          state: 'SP',
          latitude: -23.55,
          longitude: -46.63,
        },
      ],
      meta,
    })),
    reverse: vi.fn(async () => ({
      municipality: 'São Paulo',
      state: 'SP',
      latitude: -23.55,
      longitude: -46.63,
    })),
    attribution: () => ({
      id: 'nominatim' as const,
      name: 'Nominatim',
      url: 'https://nominatim.org',
      description: 'Geocodificação.',
    }),
    cacheStats: () => ({
      search: { hits: 0, misses: 0, size: 0 },
      reverse: { hits: 0, misses: 0, size: 0 },
    }),
  };

  const anpProvider = {
    getStatus: vi.fn(() => loadedAnpStatus),
    initialize: vi.fn(async () => undefined),
    findStationPrices: vi.fn(async () => []),
    getRegionalPrices: vi.fn(async () => []),
    attribution: anpAttribution,
  };

  const deps: AppDependencies = {
    stationService: stationService as unknown as StationService,
    geocodingService: geocodingService as unknown as GeocodingService,
    anpProvider: anpProvider as unknown as AnpDataProvider,
    sources: [osmAttribution, anpAttribution],
    version: '1.0.0',
    startedAt: Date.now() - 5_000,
  };

  const app = createApp(deps, { enableHttpLogging: false, ...config });
  return { app, stationService, geocodingService, anpProvider };
}

describe('GET /api/v1/health', () => {
  it('retorna 200 quando os dados da ANP estão carregados, sem dados sensíveis', async () => {
    const { app } = setup();
    const response = await request(app).get('/api/v1/health').expect(200);

    expect(response.body).toMatchObject({
      status: 'ok',
      version: '1.0.0',
      dependencies: {
        anp: { status: 'loaded', datasetPeriod: { start: '2026-10-11', end: '2026-10-17' } },
        openstreetmap: { status: 'configured' },
        nominatim: { status: 'configured' },
      },
      cache: { nearby: { size: 0 } },
    });
    expect(response.body.uptimeSeconds).toBeGreaterThanOrEqual(5);
    expect(JSON.stringify(response.body)).not.toContain('exemplo.xlsx');
  });

  it('retorna 503 degradado quando a ANP ainda não carregou', async () => {
    const { app, anpProvider } = setup();
    anpProvider.getStatus.mockReturnValue({
      ...loadedAnpStatus,
      loaded: false,
      warnings: ['carregando'],
    });
    const response = await request(app).get('/api/v1/health').expect(503);
    expect(response.body).toMatchObject({
      status: 'degraded',
      dependencies: { anp: { status: 'unavailable', warnings: ['carregando'] } },
    });
  });
});

describe('GET /api/v1/stations/nearby', () => {
  it('valida e repassa os parâmetros, incluindo valores padrão', async () => {
    const { app, stationService } = setup();
    const response = await request(app)
      .get('/api/v1/stations/nearby')
      .query({ lat: -23.55, lon: -46.63, city: 'São Paulo', state: 'SP' })
      .expect(200);

    expect(response.body.data.stations).toHaveLength(1);
    expect(response.body.meta.cached).toBe(false);
    expect(stationService.nearby).toHaveBeenCalledWith({
      latitude: -23.55,
      longitude: -46.63,
      radiusKm: 5,
      limit: 50,
      city: 'São Paulo',
      state: 'SP',
    });
  });

  it('rejeita coordenadas inválidas com mensagem pública e detalhes de campo', async () => {
    const { app, stationService } = setup();
    const response = await request(app)
      .get('/api/v1/stations/nearby')
      .query({ lat: 123, lon: -46.63 })
      .expect(400);

    expect(response.body.error).toMatchObject({
      code: 'INVALID_PARAMETERS',
      message: 'Parâmetros de busca inválidos.',
    });
    expect(response.body.error.details).toEqual(
      expect.arrayContaining([expect.objectContaining({ field: 'lat' })]),
    );
    expect(response.body.error.requestId).toBeTruthy();
    expect(stationService.nearby).not.toHaveBeenCalled();
  });

  it('rejeita raio excessivo e parâmetros desconhecidos', async () => {
    const { app } = setup();
    await request(app)
      .get('/api/v1/stations/nearby')
      .query({ lat: -23.55, lon: -46.63, radiusKm: 50 })
      .expect(400);
    await request(app)
      .get('/api/v1/stations/nearby')
      .query({ lat: -23.55, lon: -46.63, foo: 'bar' })
      .expect(400);
  });
});

describe('detalhes, preços e comparação', () => {
  it('retorna detalhes de um posto válido', async () => {
    const { app, stationService } = setup();
    const response = await request(app).get('/api/v1/stations/node_1').expect(200);
    expect(response.body.data.station.id).toBe('node_1');
    expect(stationService.details).toHaveBeenCalledWith('node_1');
  });

  it('rejeita identificador de posto inválido antes de chamar o serviço', async () => {
    const { app, stationService } = setup();
    const response = await request(app).get('/api/v1/stations/abc').expect(400);
    expect(response.body.error.code).toBe('INVALID_PARAMETERS');
    expect(stationService.details).not.toHaveBeenCalled();
  });

  it('retorna 404 controlado para posto inexistente', async () => {
    const { app, stationService } = setup();
    stationService.details.mockRejectedValueOnce(notFound('Posto não encontrado.'));
    const response = await request(app).get('/api/v1/stations/node_999').expect(404);
    expect(response.body.error).toMatchObject({
      code: 'NOT_FOUND',
      message: 'Posto não encontrado.',
    });
  });

  it('retorna preços do posto sem vazar dados internos', async () => {
    const { app } = setup();
    const response = await request(app).get('/api/v1/stations/node_1/prices').expect(200);
    expect(response.body.data.station.id).toBe('node_1');
    expect(response.body.data).toHaveProperty('regionalPrices');
  });

  it('valida os dois identificadores da comparação', async () => {
    const { app, stationService } = setup();
    await request(app)
      .get('/api/v1/prices/compare')
      .query({ stationA: 'node_1', stationB: 'node_1' })
      .expect(400);
    expect(stationService.compare).not.toHaveBeenCalled();

    await request(app)
      .get('/api/v1/prices/compare')
      .query({ stationA: 'node_1', stationB: 'way_2' })
      .expect(200);
    expect(stationService.compare).toHaveBeenCalledWith('node_1', 'way_2');
  });
});

describe('geocodificação', () => {
  it('retorna sugestões de localização', async () => {
    const { app } = setup();
    const response = await request(app)
      .get('/api/v1/geocode/search')
      .query({ q: 'São Paulo' })
      .expect(200);
    expect(response.body.data.results[0]).toMatchObject({ municipality: 'São Paulo', state: 'SP' });
  });

  it('rejeita busca curta e coordenadas inválidas', async () => {
    const { app } = setup();
    await request(app).get('/api/v1/geocode/search').query({ q: 'a' }).expect(400);
    await request(app).get('/api/v1/geocode/reverse').query({ lat: -23.55, lon: 200 }).expect(400);
  });
});

describe('erros e cabeçalhos de segurança', () => {
  it('não expõe stack trace, caminhos ou mensagens internas em erro 500', async () => {
    const { app, stationService } = setup();
    stationService.details.mockRejectedValueOnce(
      new Error('SQLITE_ERROR: unable to open C:\\dados\\segredo.sqlite'),
    );
    const response = await request(app).get('/api/v1/stations/node_1').expect(500);
    expect(response.body.error).toMatchObject({
      code: 'INTERNAL_ERROR',
      message: 'Erro interno inesperado. Tente novamente.',
    });
    expect(response.body.error.requestId).toBeTruthy();
    expect(JSON.stringify(response.body)).not.toContain('segredo');
    expect(JSON.stringify(response.body)).not.toContain('SQLITE');
    expect(response.body.error.stack).toBeUndefined();
  });

  it('aplica cabeçalhos de segurança e remove x-powered-by', async () => {
    const { app } = setup();
    const response = await request(app).get('/api/v1/health').expect(200);
    expect(response.headers['x-powered-by']).toBeUndefined();
    expect(response.headers['x-content-type-options']).toBe('nosniff');
    expect(response.headers['referrer-policy']).toBe('no-referrer');
    expect(response.headers['content-security-policy']).toBeTruthy();
    expect(response.headers['x-request-id']).toBeTruthy();
  });

  it('retorna 404 em JSON consistente para rota inexistente', async () => {
    const { app } = setup();
    const response = await request(app).get('/api/v1/nao-existe').expect(404);
    expect(response.body.error).toMatchObject({
      code: 'NOT_FOUND',
      message: 'Recurso não encontrado.',
    });
    expect(response.body.error.requestId).toBeTruthy();
  });

  it('limita requisições e responde 429 em JSON', async () => {
    const { app } = setup({ rateLimitMax: 1 });
    await request(app).get('/api/v1/sources').expect(200);
    const response = await request(app).get('/api/v1/sources').expect(429);
    expect(response.body.error).toMatchObject({
      code: 'RATE_LIMITED',
      message: 'Muitas requisições. Tente novamente em alguns minutos.',
    });
  });

  it('lista fontes e atribuições', async () => {
    const { app } = setup();
    const response = await request(app).get('/api/v1/sources').expect(200);
    expect(response.body.data.sources).toHaveLength(2);
    expect(response.body.data.sources[0]).toMatchObject({ id: 'openstreetmap' });
  });
});
