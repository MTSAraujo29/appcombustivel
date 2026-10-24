import { describe, expect, it } from 'vitest';
import {
  digitsOnly,
  normalizeCnpj,
  normalizeState,
  normalizeStationName,
  normalizeStreet,
  normalizeStreetNumber,
  normalizeText,
  toTitleCase,
} from '../../src/utils/text.js';
import { haversineKm, isValidCoordinate, roundCoordinate } from '../../src/utils/geo.js';

describe('normalização de texto', () => {
  it('remove acentos, caixa e pontuação', () => {
    expect(normalizeText('  São Paulo - SP! ')).toBe('sao paulo sp');
    expect(normalizeText(undefined)).toBe('');
  });

  it('converte nomes de estados para UF', () => {
    expect(normalizeState('São Paulo')).toBe('SP');
    expect(normalizeState('MATO GROSSO DO SUL')).toBe('MS');
    expect(normalizeState('sp')).toBe('SP');
    expect(normalizeState('')).toBe('');
  });

  it('expande abreviações de logradouro', () => {
    expect(normalizeStreet('Av. Paulista')).toBe('avenida paulista');
    expect(normalizeStreet('R. da Consolação')).toBe('rua da consolacao');
    expect(normalizeStreet('Al. Lorena')).toBe('alameda lorena');
    expect(normalizeStreet('Praça da Sé')).toBe('praca da se');
  });

  it('normaliza números de endereço e trata sem número', () => {
    expect(normalizeStreetNumber('Nº 1000')).toBe('1000');
    expect(normalizeStreetNumber(1000)).toBe('1000');
    expect(normalizeStreetNumber('s/n')).toBe('');
    expect(normalizeStreetNumber('')).toBe('');
    expect(normalizeStreetNumber(undefined)).toBe('');
  });

  it('remove termos genéricos de nomes de postos', () => {
    expect(normalizeStationName('Auto Posto Estrela LTDA')).toBe('estrela');
    expect(normalizeStationName('Posto Ipiranga')).toBe('ipiranga');
    expect(normalizeStationName('Shell')).toBe('shell');
  });

  it('formata nomes próprios preservando conectivos', () => {
    expect(toTitleCase('SAO PAULO')).toBe('Sao Paulo');
    expect(toTitleCase('JARDIM DA GLORIA')).toBe('Jardim da Gloria');
  });

  it('normaliza CNPJ para 14 dígitos', () => {
    expect(normalizeCnpj('11.222.333/0001-81')).toBe('11222333000181');
    expect(normalizeCnpj(11222333000181)).toBe('11222333000181');
    expect(normalizeCnpj('')).toBe('');
    expect(digitsOnly('(11) 99999-0000')).toBe('11999990000');
  });
});

describe('geolocalização', () => {
  it('calcula distância aproximada em quilômetros', () => {
    const distance = haversineKm(
      { latitude: -23.5505, longitude: -46.6333 },
      { latitude: -22.9068, longitude: -43.1729 },
    );
    expect(distance).toBeGreaterThan(350);
    expect(distance).toBeLessThan(370);
    expect(haversineKm({ latitude: 0, longitude: 0 }, { latitude: 0, longitude: 0 })).toBe(0);
  });

  it('arredonda coordenadas e valida limites', () => {
    expect(roundCoordinate(-23.550512, 3)).toBe(-23.551);
    expect(isValidCoordinate(-23.55, -46.63)).toBe(true);
    expect(isValidCoordinate(91, 0)).toBe(false);
    expect(isValidCoordinate(0, 181)).toBe(false);
    expect(isValidCoordinate(Number.NaN, 0)).toBe(false);
  });
});
