import type {
  RegionalPrice,
  SourceAttribution,
  SourceRef,
  Station,
  StationPrice,
} from '../../src/types.js';
import type { AnpStatus, OsmStation } from '../../src/providers/contracts.js';

export const osmAttribution: SourceAttribution = {
  id: 'openstreetmap',
  name: 'OpenStreetMap',
  url: 'https://www.openstreetmap.org/copyright',
  description: 'Dados geográficos colaborativos, disponibilizados sob ODbL.',
};

export const anpSource: SourceRef = {
  name: 'ANP - Levantamento de Preços de Combustíveis',
  url: 'https://www.gov.br/anp/exemplo.xlsx',
  official: true,
};

export const anpAttribution: SourceAttribution = {
  id: 'anp',
  name: 'ANP',
  url: 'https://www.gov.br/anp',
  description: 'Preços oficiais coletados pela ANP.',
};

export const loadedAnpStatus: AnpStatus = {
  loaded: true,
  stale: false,
  datasetPeriod: { start: '2026-10-11', end: '2026-10-17' },
  loadedAt: '2026-10-20T12:00:00.000Z',
  sources: { revendas: 'https://www.gov.br/anp/revendas.xlsx' },
  warnings: [],
};

export function makePrice(
  fuel: StationPrice['fuel'],
  amount: number,
  overrides: Partial<StationPrice> = {},
): StationPrice {
  return {
    fuel,
    amount,
    currency: 'BRL',
    unit: fuel === 'gnv' ? 'm3' : 'liter',
    priceType: 'individual',
    source: anpSource,
    referenceDate: '2026-10-12',
    validationStatus: 'official_survey',
    matchMethod: 'address',
    ...overrides,
  };
}

export function makeRegionalPrice(
  fuel: RegionalPrice['fuel'],
  amount: number,
  overrides: Partial<RegionalPrice> = {},
): RegionalPrice {
  return {
    fuel,
    amount,
    minAmount: amount - 0.5,
    maxAmount: amount + 0.5,
    currency: 'BRL',
    unit: fuel === 'gnv' ? 'm3' : 'liter',
    priceType: 'municipal_average',
    municipality: 'São Paulo',
    state: 'SP',
    referenceStart: '2026-10-11',
    referenceEnd: '2026-10-17',
    surveyedStations: 120,
    source: anpSource,
    ...overrides,
  };
}

export function makeStation(overrides: Partial<Station> = {}): Station {
  return {
    id: 'node_1',
    name: 'Posto Central',
    brand: 'Shell',
    address: { street: 'Avenida Paulista', number: '1000', city: 'São Paulo', state: 'SP' },
    latitude: -23.55,
    longitude: -46.63,
    prices: [],
    ...overrides,
  };
}

export function makeOsmStation(overrides: Partial<OsmStation> = {}): OsmStation {
  return {
    id: 'node_1',
    name: 'Posto Central',
    brand: 'Shell',
    address: { street: 'Avenida Paulista', number: '1000', city: 'São Paulo', state: 'SP' },
    latitude: -23.55,
    longitude: -46.63,
    tags: { amenity: 'fuel' },
    ...overrides,
  };
}
