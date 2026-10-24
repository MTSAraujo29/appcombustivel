import { describe, expect, it } from 'vitest';
import {
  compareQuerySchema,
  nearbyQuerySchema,
  parseOrThrow,
  reverseQuerySchema,
  stationIdParamSchema,
} from '../../src/schemas/apiSchemas.js';
import { AppError } from '../../src/utils/errors.js';

describe('nearbyQuerySchema', () => {
  it('converte parâmetros numéricos e aplica valores padrão', () => {
    const parsed = parseOrThrow(
      nearbyQuerySchema,
      { lat: '-23.5505', lon: '-46.6333' },
      'inválido',
    );
    expect(parsed).toEqual({ lat: -23.5505, lon: -46.6333, radiusKm: 5, limit: 50 });
  });

  it('aceita município e UF e remove espaços', () => {
    const parsed = parseOrThrow(
      nearbyQuerySchema,
      { lat: -23.55, lon: -46.63, city: ' São Paulo ', state: 'SP', radiusKm: '2', limit: 10 },
      'inválido',
    );
    expect(parsed.city).toBe('São Paulo');
    expect(parsed.radiusKm).toBe(2);
    expect(parsed.limit).toBe(10);
  });

  it('rejeita coordenadas fora dos limites com detalhes do campo', () => {
    try {
      parseOrThrow(nearbyQuerySchema, { lat: '91', lon: '0' }, 'Parâmetros inválidos.');
      throw new Error('deveria ter falhado');
    } catch (error) {
      expect(error).toBeInstanceOf(AppError);
      const appError = error as AppError;
      expect(appError.status).toBe(400);
      expect(appError.code).toBe('INVALID_PARAMETERS');
      expect(appError.details).toEqual(
        expect.arrayContaining([expect.objectContaining({ field: 'lat' })]),
      );
    }
  });

  it('rejeita raio e limite fora do permitido', () => {
    expect(() =>
      parseOrThrow(nearbyQuerySchema, { lat: 0, lon: 0, radiusKm: '11' }, 'inválido'),
    ).toThrow(AppError);
    expect(() =>
      parseOrThrow(nearbyQuerySchema, { lat: 0, lon: 0, limit: '101' }, 'inválido'),
    ).toThrow(AppError);
    expect(() =>
      parseOrThrow(nearbyQuerySchema, { lat: 0, lon: 0, limit: '2.5' }, 'inválido'),
    ).toThrow(AppError);
  });

  it('rejeita parâmetros desconhecidos e valores com marcação', () => {
    expect(() =>
      parseOrThrow(nearbyQuerySchema, { lat: 0, lon: 0, extra: '1' }, 'inválido'),
    ).toThrow(AppError);
    expect(() =>
      parseOrThrow(nearbyQuerySchema, { lat: 0, lon: 0, city: '<script>' }, 'inválido'),
    ).toThrow(AppError);
  });
});

describe('stationIdParamSchema', () => {
  it('aceita identificadores do OpenStreetMap', () => {
    expect(parseOrThrow(stationIdParamSchema, { id: 'way_123456' }, 'inválido')).toEqual({
      id: 'way_123456',
    });
  });

  it('rejeita identificadores arbitrários e muito longos', () => {
    expect(() => parseOrThrow(stationIdParamSchema, { id: '123' }, 'inválido')).toThrow(AppError);
    expect(() =>
      parseOrThrow(stationIdParamSchema, { id: `node_${'9'.repeat(21)}` }, 'inválido'),
    ).toThrow(AppError);
    expect(() =>
      parseOrThrow(stationIdParamSchema, { id: 'node_1/path' }, 'inválido'),
    ).toThrow(AppError);
  });
});

describe('compareQuerySchema', () => {
  it('exige dois identificadores distintos', () => {
    expect(() =>
      parseOrThrow(
        compareQuerySchema,
        { stationA: 'node_1', stationB: 'node_1' },
        'inválido',
      ),
    ).toThrow(AppError);
    expect(
      parseOrThrow(compareQuerySchema, { stationA: 'node_1', stationB: 'way_2' }, 'inválido'),
    ).toEqual({ stationA: 'node_1', stationB: 'way_2' });
  });
});

describe('reverseQuerySchema', () => {
  it('valida coordenadas de geocodificação reversa', () => {
    expect(parseOrThrow(reverseQuerySchema, { lat: -23.55, lon: -46.63 }, 'inválido')).toEqual({
      lat: -23.55,
      lon: -46.63,
    });
    expect(() => parseOrThrow(reverseQuerySchema, { lat: 0, lon: 181 }, 'inválido')).toThrow(
      AppError,
    );
  });
});
