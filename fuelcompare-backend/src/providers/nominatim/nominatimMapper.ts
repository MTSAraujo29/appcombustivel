import type { LocationSuggestion } from '../../types.js';
import { normalizeState, toTitleCase } from '../../utils/text.js';

export interface NominatimAddress {
  city?: string;
  town?: string;
  village?: string;
  municipality?: string;
  county?: string;
  state?: string;
  postcode?: string;
  'ISO3166-2-lvl4'?: string;
}

export interface NominatimResult {
  osm_type?: string;
  osm_id?: number;
  lat?: string;
  lon?: string;
  name?: string;
  display_name?: string;
  address?: NominatimAddress;
}

function resolveMunicipality(result: NominatimResult): string | undefined {
  const address = result.address ?? {};
  return address.city ?? address.town ?? address.village ?? address.municipality;
}

function resolveState(result: NominatimResult): string {
  const iso = result.address?.['ISO3166-2-lvl4'];
  if (iso?.includes('-')) {
    return normalizeState(iso.split('-').pop());
  }
  return normalizeState(result.address?.state);
}

export function mapSearchResults(results: NominatimResult[], limit: number): LocationSuggestion[] {
  const suggestions: LocationSuggestion[] = [];
  const seen = new Set<string>();

  for (const result of results) {
    const latitude = Number(result.lat);
    const longitude = Number(result.lon);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) continue;
    if (!result.display_name) continue;

    const municipality = resolveMunicipality(result);
    if (!municipality) continue;
    const state = resolveState(result);
    if (!state) continue;

    const key = `${latitude.toFixed(4)},${longitude.toFixed(4)}`;
    if (seen.has(key)) continue;
    seen.add(key);

    suggestions.push({
      id: `${result.osm_type ?? 'place'}_${result.osm_id ?? key}`,
      displayName: result.display_name,
      municipality: toTitleCase(municipality),
      state,
      latitude,
      longitude,
      ...(result.address?.postcode ? { postalCode: result.address.postcode } : {}),
    });

    if (suggestions.length >= limit) break;
  }

  return suggestions;
}

export function mapReverseResult(
  result: NominatimResult | null,
): { municipality: string; state: string; latitude: number; longitude: number } | null {
  if (!result) return null;
  const latitude = Number(result.lat);
  const longitude = Number(result.lon);
  const municipality = resolveMunicipality(result);
  const state = resolveState(result);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || !municipality || !state) {
    return null;
  }
  return {
    municipality: toTitleCase(municipality),
    state,
    latitude,
    longitude,
  };
}
