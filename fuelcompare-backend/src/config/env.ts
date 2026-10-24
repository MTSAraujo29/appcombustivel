import 'dotenv/config';
import { z } from 'zod';

const booleanFromString = z
  .union([z.boolean(), z.string()])
  .transform((value) => {
    if (typeof value === 'boolean') return value;
    return ['1', 'true', 'yes', 'sim'].includes(value.toLowerCase());
  });

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  HOST: z.string().min(1).default('0.0.0.0'),
  PORT: z.coerce.number().int().min(1).max(65535).default(3333),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']).default('info'),

  HTTP_USER_AGENT: z
    .string()
    .min(10)
    .max(300)
    .default('FuelCompare/1.0 (projeto educacional; contato: dev@exemplo.com)'),
  OVERPASS_URL: z.string().url().default('https://overpass-api.de/api/interpreter'),
  NOMINATIM_URL: z.string().url().default('https://nominatim.openstreetmap.org'),

  NEARBY_CACHE_TTL_SECONDS: z.coerce.number().int().min(10).max(86_400).default(600),
  STATION_CACHE_TTL_SECONDS: z.coerce.number().int().min(10).max(86_400).default(1800),
  ANP_CACHE_TTL_SECONDS: z.coerce.number().int().min(600).max(86_400).default(21_600),

  RATE_LIMIT_WINDOW_MS: z.coerce.number().int().min(1000).default(300_000),
  RATE_LIMIT_MAX: z.coerce.number().int().min(1).default(120),
  GEOCODE_RATE_LIMIT_MAX: z.coerce.number().int().min(1).default(30),

  TRUST_PROXY: booleanFromString.default(false),
  ENFORCE_HTTPS: booleanFromString.default(false),
  CORS_ORIGINS: z.string().optional(),

  ANP_PRICE_PAGE_URL: z
    .string()
    .url()
    .default(
      'https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos/levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas',
    ),
  ANP_REVENDAS_XLSX_URL: z.string().url().optional(),
  ANP_RESUMO_XLSX_URL: z.string().url().optional(),
});

const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  // Não expõe segredos: apenas os nomes dos campos inválidos.
  const fields = parsed.error.issues.map((issue) => issue.path.join('.')).join(', ');
  throw new Error(`Configuração inválida. Revise as variáveis: ${fields}`);
}

export const env = parsed.data;

export type Env = typeof env;
