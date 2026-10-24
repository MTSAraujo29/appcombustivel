import type {
  FuelCode,
  LocationSuggestion,
  RegionalPrice,
  SourceAttribution,
  StationAddress,
  StationPrice,
} from '../types.js';

export interface NearbyParams {
  latitude: number;
  longitude: number;
  radiusKm: number;
  limit: number;
}

export interface OsmStation {
  id: string;
  name: string;
  brand?: string;
  address: StationAddress;
  latitude: number;
  longitude: number;
  tags: Record<string, string>;
}

export interface StationCatalogProvider {
  findNearby(params: NearbyParams): Promise<OsmStation[]>;
  findById(id: string): Promise<OsmStation | null>;
  attribution: SourceAttribution;
}

export interface ReverseGeocodeResult {
  municipality: string;
  state: string;
  latitude: number;
  longitude: number;
}

export interface GeocoderProvider {
  search(query: string, limit: number): Promise<LocationSuggestion[]>;
  reverse(latitude: number, longitude: number): Promise<ReverseGeocodeResult | null>;
  attribution: SourceAttribution;
}

export interface AnpStationPriceInput {
  cnpj?: string;
  name?: string;
  brand?: string;
  street?: string;
  number?: string;
  city?: string;
  state?: string;
}

export interface AnpStatus {
  loaded: boolean;
  stale: boolean;
  datasetPeriod?: { start: string; end: string };
  loadedAt?: string;
  sources: { revendas?: string; resumo?: string };
  warnings: string[];
  lastError?: string;
}

export interface AnpDataProvider {
  initialize(): Promise<void>;
  findStationPrices(input: AnpStationPriceInput): Promise<StationPrice[]>;
  getRegionalPrices(city: string | undefined, state: string | undefined): Promise<RegionalPrice[]>;
  getStatus(): AnpStatus;
  attribution: SourceAttribution;
}

export interface PriceComparisonInput {
  fuel: FuelCode;
  priceA?: StationPrice;
  priceB?: StationPrice;
}
