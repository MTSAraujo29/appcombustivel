import type { OsmStation } from '../contracts.js';
import type { StationAddress } from '../../types.js';

export interface OverpassElement {
  type: 'node' | 'way' | 'relation';
  id: number;
  lat?: number;
  lon?: number;
  center?: { lat: number; lon: number };
  tags?: Record<string, string>;
}

export interface OverpassResponse {
  elements?: OverpassElement[];
  remark?: string;
}

function clean(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function buildAddress(tags: Record<string, string>): StationAddress {
  const street = clean(tags['addr:street']);
  const number = clean(tags['addr:housenumber']);
  const neighborhood = clean(tags['addr:suburb']) ?? clean(tags['addr:neighbourhood']);
  const city = clean(tags['addr:city']);
  const state = clean(tags['addr:state']);
  const postalCode = clean(tags['addr:postcode']);

  const labelParts = [
    [street, number].filter(Boolean).join(', '),
    neighborhood,
    [city, state].filter(Boolean).join(' - '),
  ].filter(Boolean);

  return {
    ...(street ? { street } : {}),
    ...(number ? { number } : {}),
    ...(neighborhood ? { neighborhood } : {}),
    ...(city ? { city } : {}),
    ...(state ? { state } : {}),
    ...(postalCode ? { postalCode } : {}),
    ...(labelParts.length > 0 ? { label: labelParts.join(' · ') } : {}),
  };
}

export function mapOverpassElements(elements: OverpassElement[]): OsmStation[] {
  const stations: OsmStation[] = [];

  for (const element of elements) {
    if (!element.tags || element.tags.amenity !== 'fuel') continue;
    const latitude = element.lat ?? element.center?.lat;
    const longitude = element.lon ?? element.center?.lon;
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) continue;

    const tags = element.tags;
    const brand = clean(tags.brand) ?? clean(tags.operator);
    const name = clean(tags.name) ?? brand ?? 'Posto de combustível';
    const address = buildAddress(tags);

    stations.push({
      id: `${element.type}_${element.id}`,
      name,
      ...(brand ? { brand } : {}),
      address,
      latitude: latitude as number,
      longitude: longitude as number,
      tags,
    });
  }

  return stations;
}
