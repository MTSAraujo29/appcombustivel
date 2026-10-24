import { env } from './config/env.js';
import { createApp, type AppConfig } from './app.js';
import { OverpassProvider } from './providers/overpass/overpassProvider.js';
import { NominatimProvider } from './providers/nominatim/nominatimProvider.js';
import { AnpPriceProvider } from './providers/anp/anpProvider.js';
import { GeocodingService } from './services/geocodingService.js';
import { StationService } from './services/stationService.js';

export const APP_VERSION = '1.0.0';

export function buildApplication(config: AppConfig = {}) {
  const catalog = new OverpassProvider();
  const geocoder = new NominatimProvider();
  const anp = new AnpPriceProvider();
  const geocodingService = new GeocodingService(geocoder);
  const stationService = new StationService(catalog, anp, geocodingService, {
    nearbyTtlMs: env.NEARBY_CACHE_TTL_SECONDS * 1000,
    stationTtlMs: env.STATION_CACHE_TTL_SECONDS * 1000,
  });

  const app = createApp(
    {
      stationService,
      geocodingService,
      anpProvider: anp,
      sources: [catalog.attribution, anp.attribution, geocoder.attribution],
      version: APP_VERSION,
      startedAt: Date.now(),
    },
    config,
  );

  return { app, anp, stationService, geocodingService };
}
