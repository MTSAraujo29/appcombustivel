import crypto from 'node:crypto';
import express, { type Express, type NextFunction, type Request, type Response } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit, { type Options as RateLimitOptions } from 'express-rate-limit';
import { pinoHttp } from 'pino-http';
import { env } from './config/env.js';
import { StationController } from './controllers/stationController.js';
import { GeocodeController } from './controllers/geocodeController.js';
import { errorHandler, notFoundHandler } from './middlewares/errorHandler.js';
import type { AnpDataProvider } from './providers/contracts.js';
import type { GeocodingService } from './services/geocodingService.js';
import type { StationService } from './services/stationService.js';
import type { SourceAttribution } from './types.js';
import { AppError } from './utils/errors.js';
import { logger } from './utils/logger.js';

export interface AppDependencies {
  stationService: StationService;
  geocodingService: GeocodingService;
  anpProvider: AnpDataProvider;
  sources: SourceAttribution[];
  version: string;
  startedAt?: number;
}

export interface AppConfig {
  enableHttpLogging?: boolean;
  rateLimitWindowMs?: number;
  rateLimitMax?: number;
  geocodeRateLimitMax?: number;
}

function createLimiter(options: {
  windowMs: number;
  max: number;
  code?: string;
  message: string;
}): ReturnType<typeof rateLimit> {
  const limiterOptions: Partial<RateLimitOptions> = {
    windowMs: options.windowMs,
    limit: options.max,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    handler: (req, res) => {
      const id = (req as Request & { id?: string | number }).id;
      res.status(429).json({
        error: {
          code: 'RATE_LIMITED',
          message: options.message,
          requestId: id ? String(id) : 'unknown',
        },
      });
    },
  };
  return rateLimit(limiterOptions);
}

export function createApp(deps: AppDependencies, config: AppConfig = {}): Express {
  const app = express();
  const startedAt = deps.startedAt ?? Date.now();
  const stationController = new StationController(deps.stationService);
  const geocodeController = new GeocodeController(deps.geocodingService);

  app.disable('x-powered-by');
  if (env.TRUST_PROXY) app.set('trust proxy', 1);

  app.use(
    helmet({
      strictTransportSecurity:
        env.NODE_ENV === 'production'
          ? { maxAge: 15_552_000, includeSubDomains: true }
          : false,
      referrerPolicy: { policy: 'no-referrer' },
      crossOriginResourcePolicy: { policy: 'same-site' },
    }),
  );

  const corsOrigins = (env.CORS_ORIGINS ?? '')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  if (corsOrigins.length > 0) {
    app.use(
      cors({
        origin: (origin, callback) => {
          if (!origin || corsOrigins.includes(origin)) {
            callback(null, true);
            return;
          }
          callback(
            new AppError({
              status: 403,
              code: 'CORS_NOT_ALLOWED',
              publicMessage: 'Origem não autorizada.',
            }),
          );
        },
        methods: ['GET', 'OPTIONS'],
        maxAge: 86_400,
      }),
    );
  }

  if (config.enableHttpLogging !== false) {
    app.use(
      pinoHttp({
        logger,
        genReqId: (req, res) => {
          const id = crypto.randomUUID();
          res.setHeader('x-request-id', id);
          return id;
        },
        customLogLevel: (_req, res, error) => {
          if (error || res.statusCode >= 500) return 'error';
          if (res.statusCode >= 400) return 'warn';
          return 'info';
        },
        serializers: {
          req: (req) => ({ method: req.method, url: req.url?.split('?')[0] }),
          res: (res) => ({ statusCode: res.statusCode }),
        },
        redact: {
          paths: ['req.headers.authorization', 'req.headers.cookie'],
          censor: '[redacted]',
        },
      }),
    );
  } else {
    app.use((req, res, next) => {
      const id = crypto.randomUUID();
      (req as Request & { id: string }).id = id;
      res.setHeader('x-request-id', id);
      next();
    });
  }

  app.use((req, _res, next) => {
    if (env.ENFORCE_HTTPS && env.NODE_ENV === 'production' && !req.secure) {
      next(
        new AppError({
          status: 403,
          code: 'HTTPS_REQUIRED',
          publicMessage: 'Esta API só aceita conexões HTTPS.',
        }),
      );
      return;
    }
    next();
  });

  app.use(express.json({ limit: '10kb' }));

  app.get('/api/v1/health', (_req: Request, res: Response) => {
    const anp = deps.anpProvider.getStatus();
    const healthy = anp.loaded;
    res.status(healthy ? 200 : 503).json({
      status: healthy ? 'ok' : 'degraded',
      version: deps.version,
      timestamp: new Date().toISOString(),
      uptimeSeconds: Math.round((Date.now() - startedAt) / 1000),
      dependencies: {
        openstreetmap: { status: 'configured' },
        anp: {
          status: anp.loaded ? (anp.stale ? 'stale' : 'loaded') : 'unavailable',
          ...(anp.datasetPeriod ? { datasetPeriod: anp.datasetPeriod } : {}),
          ...(anp.loadedAt ? { loadedAt: anp.loadedAt } : {}),
          warnings: anp.warnings,
        },
        nominatim: { status: 'configured' },
      },
      cache: deps.stationService.cacheStats(),
    });
  });

  const nearbyLimiter = createLimiter({
    windowMs: config.rateLimitWindowMs ?? env.RATE_LIMIT_WINDOW_MS,
    max: config.rateLimitMax ?? env.RATE_LIMIT_MAX,
    message: 'Muitas requisições. Tente novamente em alguns minutos.',
  });

  app.use('/api/v1', nearbyLimiter);

  app.get('/api/v1/stations/nearby', stationController.nearby);
  app.get('/api/v1/stations/:id', stationController.details);
  app.get('/api/v1/stations/:id/prices', stationController.prices);
  app.get('/api/v1/prices/compare', stationController.compare);

  const geocodeLimiter = createLimiter({
    windowMs: config.rateLimitWindowMs ?? env.RATE_LIMIT_WINDOW_MS,
    max: config.geocodeRateLimitMax ?? env.GEOCODE_RATE_LIMIT_MAX,
    message: 'Muitas buscas de localização. Aguarde um instante.',
  });
  app.use('/api/v1/geocode', geocodeLimiter);
  app.get('/api/v1/geocode/search', geocodeController.search);
  app.get('/api/v1/geocode/reverse', geocodeController.reverse);

  app.get('/api/v1/sources', (_req, res) => {
    res.json({
      data: { sources: deps.sources },
      meta: { cached: false, generatedAt: new Date().toISOString(), warnings: [] },
    });
  });

  app.use(notFoundHandler);
  app.use((error: unknown, req: Request, res: Response, next: NextFunction) => {
    errorHandler(error, req, res, next);
  });

  return app;
}
