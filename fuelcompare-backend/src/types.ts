export const FUEL_CODES = [
  'gasoline',
  'gasoline_additivated',
  'ethanol',
  'diesel_s10',
  'diesel',
  'gnv',
] as const;

export type FuelCode = (typeof FUEL_CODES)[number];

export type PriceUnit = 'liter' | 'm3';
export type PriceType = 'individual' | 'municipal_average';

export interface SourceRef {
  name: string;
  url?: string;
  official: boolean;
}

export interface StationPrice {
  fuel: FuelCode;
  amount: number;
  currency: 'BRL';
  unit: PriceUnit;
  priceType: 'individual';
  source: SourceRef;
  /** Data de coleta/divulgação do preço, no formato YYYY-MM-DD. */
  referenceDate: string;
  collectedAt?: string;
  validationStatus: 'official_survey';
  /** Como o posto do OpenStreetMap foi associado ao levantamento da ANP. */
  matchMethod: 'cnpj' | 'address' | 'name';
}

export interface RegionalPrice {
  fuel: FuelCode;
  amount: number;
  minAmount: number;
  maxAmount: number;
  currency: 'BRL';
  unit: PriceUnit;
  priceType: 'municipal_average';
  municipality: string;
  state: string;
  referenceStart: string;
  referenceEnd: string;
  surveyedStations: number;
  source: SourceRef;
}

export interface StationAddress {
  street?: string;
  number?: string;
  neighborhood?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  label?: string;
}

export interface Station {
  id: string;
  name: string;
  brand?: string;
  address: StationAddress;
  latitude: number;
  longitude: number;
  distanceKm?: number;
  prices: StationPrice[];
  updatedAt?: string;
}

export interface Region {
  municipality: string;
  state: string;
  latitude: number;
  longitude: number;
  resolvedFrom: 'request' | 'geocoder' | 'station';
}

export interface SourceAttribution {
  id: 'openstreetmap' | 'anp' | 'nominatim';
  name: string;
  url: string;
  description: string;
}

export interface NearbyFeed {
  stations: Station[];
  regionalPrices: RegionalPrice[];
  region?: Region;
  lastUpdated?: string;
  sources: SourceAttribution[];
}

export interface DetailsData {
  station: Station;
  regionalPrices: RegionalPrice[];
  lastUpdated?: string;
  sources: SourceAttribution[];
}

export interface PriceComparisonSide {
  stationId: string;
  stationName: string;
  price?: StationPrice;
}

export interface PriceComparisonRow {
  fuel: FuelCode;
  comparable: boolean;
  stationA: PriceComparisonSide;
  stationB: PriceComparisonSide;
  difference?: number;
  cheaper?: 'A' | 'B' | 'tie';
  note:
    | 'comparable'
    | 'tie'
    | 'only_a'
    | 'only_b'
    | 'missing_both';
}

export interface PriceComparison {
  stationA: { id: string; name: string };
  stationB: { id: string; name: string };
  rows: PriceComparisonRow[];
}

export interface Meta {
  cached: boolean;
  generatedAt: string;
  warnings: string[];
}

export interface ApiResponse<T> {
  data: T;
  meta: Meta;
}

export interface LocationSuggestion {
  id: string;
  displayName: string;
  municipality: string;
  state: string;
  latitude: number;
  longitude: number;
  postalCode?: string;
}

export const FUEL_INFO: Record<FuelCode, { unit: PriceUnit; anpName: string; label: string }> = {
  gasoline: { unit: 'liter', anpName: 'GASOLINA COMUM', label: 'Gasolina comum' },
  gasoline_additivated: {
    unit: 'liter',
    anpName: 'GASOLINA ADITIVADA',
    label: 'Gasolina aditivada',
  },
  ethanol: { unit: 'liter', anpName: 'ETANOL', label: 'Etanol' },
  diesel_s10: { unit: 'liter', anpName: 'OLEO DIESEL S10', label: 'Diesel S10' },
  diesel: { unit: 'liter', anpName: 'OLEO DIESEL', label: 'Diesel comum' },
  gnv: { unit: 'm3', anpName: 'GNV', label: 'GNV' },
};

export function isFuelCode(value: string): value is FuelCode {
  return (FUEL_CODES as readonly string[]).includes(value);
}
