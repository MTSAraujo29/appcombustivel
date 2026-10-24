import type { Meta } from '../types.js';
import { TtlCache } from '../utils/cache.js';
import { normalizeText } from '../utils/text.js';
import { roundCoordinate } from '../utils/geo.js';
import type { GeocoderProvider, ReverseGeocodeResult } from '../providers/contracts.js';

export class GeocodingService {
  private readonly searchCache = new TtlCache<Awaited<ReturnType<GeocoderProvider['search']>>>(
    24 * 60 * 60 * 1000,
    Date.now,
    500,
  );
  private readonly reverseCache = new TtlCache<ReverseGeocodeResult | null>(
    7 * 24 * 60 * 60 * 1000,
    Date.now,
    1000,
  );

  constructor(private readonly provider: GeocoderProvider) {}

  async search(
    query: string,
    limit: number,
  ): Promise<{ results: Awaited<ReturnType<GeocoderProvider['search']>>; meta: Meta }> {
    const key = `${normalizeText(query)}|${limit}`;
    try {
      const { value, cached } = await this.searchCache.getOrLoad(key, () =>
        this.provider.search(query, limit),
      );
      return {
        results: value,
        meta: { cached, generatedAt: new Date().toISOString(), warnings: [] },
      };
    } catch (error) {
      const stale = this.searchCache.peek(key);
      if (stale) {
        return {
          results: stale,
          meta: {
            cached: true,
            generatedAt: new Date().toISOString(),
            warnings: ['A busca está indisponível; exibindo resultados armazenados em cache.'],
          },
        };
      }
      throw error;
    }
  }

  async reverse(latitude: number, longitude: number): Promise<ReverseGeocodeResult | null> {
    const key = `${roundCoordinate(latitude, 4)},${roundCoordinate(longitude, 4)}`;
    try {
      const { value } = await this.reverseCache.getOrLoad(key, () =>
        this.provider.reverse(latitude, longitude),
      );
      return value;
    } catch (error) {
      const stale = this.reverseCache.peek(key);
      if (stale) return stale;
      throw error;
    }
  }

  attribution() {
    return this.provider.attribution;
  }

  cacheStats() {
    return { search: this.searchCache.stats(), reverse: this.reverseCache.stats() };
  }
}
