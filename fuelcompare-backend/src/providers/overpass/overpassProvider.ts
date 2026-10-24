import { env } from '../../config/env.js';
import type { SourceAttribution } from '../../types.js';
import { fetchJson } from '../../utils/http.js';
import { notFound, upstreamUnavailable } from '../../utils/errors.js';
import type { NearbyParams, OsmStation, StationCatalogProvider } from '../contracts.js';
import {
  mapOverpassElements,
  type OverpassElement,
  type OverpassResponse,
} from './overpassMapper.js';

const MAX_RADIUS_KM = 10;
const MAX_LIMIT = 100;

export class OverpassProvider implements StationCatalogProvider {
  readonly attribution: SourceAttribution = {
    id: 'openstreetmap',
    name: 'OpenStreetMap',
    url: 'https://www.openstreetmap.org/copyright',
    description:
      'Localização, nome e endereço dos postos. Dados © colaboradores do OpenStreetMap, licença ODbL.',
  };

  constructor(private readonly endpoint: string = env.OVERPASS_URL) {}

  async findNearby(params: NearbyParams): Promise<OsmStation[]> {
    const radiusMeters = Math.round(Math.min(params.radiusKm, MAX_RADIUS_KM) * 1000);
    const limit = Math.min(Math.max(params.limit, 1), MAX_LIMIT);
    const query = [
      '[out:json][timeout:25];',
      `nwr["amenity"="fuel"](around:${radiusMeters},${params.latitude.toFixed(6)},${params.longitude.toFixed(6)});`,
      `out center tags ${limit};`,
    ].join('\n');

    const data = await this.execute(query);
    return mapOverpassElements(data.elements ?? []).slice(0, limit);
  }

  async findById(id: string): Promise<OsmStation | null> {
    const match = /^(node|way|relation)_(\d{1,20})$/.exec(id);
    if (!match) {
      throw notFound('Posto não encontrado.');
    }
    const [, type, numericId] = match;
    const query = [
      '[out:json][timeout:20];',
      `${type}(${numericId});`,
      'out center tags 1;',
    ].join('\n');

    const data = await this.execute(query);
    const elements = (data.elements ?? []) as OverpassElement[];
    const station = mapOverpassElements(elements)[0];
    return station ?? null;
  }

  private async execute(query: string): Promise<OverpassResponse> {
    try {
      return await fetchJson<OverpassResponse>(this.endpoint, {
        method: 'POST',
        timeoutMs: 35_000,
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ data: query }).toString(),
        retries: 1,
        maxBytes: 5 * 1024 * 1024,
      });
    } catch (error) {
      throw upstreamUnavailable(
        'O serviço de mapas está indisponível no momento. Tente novamente.',
        error,
      );
    }
  }
}
