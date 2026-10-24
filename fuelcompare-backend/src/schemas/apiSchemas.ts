import { z } from 'zod';
import { badRequest } from '../utils/errors.js';

const numeric = (schema: z.ZodNumber) =>
  z
    .union([z.string(), z.number()])
    .transform((value) => (typeof value === 'number' ? value : Number(value.trim())))
    .pipe(schema);

const latitude = numeric(z.number().finite().min(-90).max(90));
const longitude = numeric(z.number().finite().min(-180).max(180));

const cityName = z
  .string()
  .trim()
  .min(2, 'Informe um município válido.')
  .max(100, 'O município deve ter no máximo 100 caracteres.')
  .refine((value) => !/[\u0000-\u001f<>]/.test(value), 'Município inválido.');

const stateName = z
  .string()
  .trim()
  .min(2, 'Informe um estado válido.')
  .max(50, 'O estado deve ter no máximo 50 caracteres.')
  .refine((value) => !/[\u0000-\u001f<>]/.test(value), 'Estado inválido.');

export const nearbyQuerySchema = z
  .object({
    lat: latitude,
    lon: longitude,
    radiusKm: numeric(z.number().min(1).max(10)).default(5),
    limit: numeric(z.number().int().min(1).max(100)).default(50),
    city: cityName.optional(),
    state: stateName.optional(),
  })
  .strict();

export const stationIdParamSchema = z.object({
  id: z
    .string()
    .regex(/^(node|way|relation)_\d{1,20}$/, 'Identificador de posto inválido.'),
});

export const compareQuerySchema = z
  .object({
    stationA: z
      .string()
      .regex(/^(node|way|relation)_\d{1,20}$/, 'Identificador do posto A inválido.'),
    stationB: z
      .string()
      .regex(/^(node|way|relation)_\d{1,20}$/, 'Identificador do posto B inválido.'),
  })
  .strict()
  .refine((value) => value.stationA !== value.stationB, {
    message: 'Selecione dois postos diferentes para comparar.',
    path: ['stationB'],
  });

export const geocodeSearchQuerySchema = z
  .object({
    q: z
      .string()
      .trim()
      .min(2, 'Digite ao menos 2 caracteres.')
      .max(160, 'A busca deve ter no máximo 160 caracteres.')
      .refine((value) => !/[\u0000-\u001f<>]/.test(value), 'Busca inválida.'),
    limit: numeric(z.number().int().min(1).max(8)).default(5),
  })
  .strict();

export const reverseQuerySchema = z
  .object({
    lat: latitude,
    lon: longitude,
  })
  .strict();

export type NearbyQueryInput = z.infer<typeof nearbyQuerySchema>;
export type CompareQueryInput = z.infer<typeof compareQuerySchema>;
export type GeocodeSearchQueryInput = z.infer<typeof geocodeSearchQuerySchema>;

export function parseOrThrow<S extends z.ZodTypeAny>(
  schema: S,
  input: unknown,
  message: string,
): z.output<S> {
  const result = schema.safeParse(input);
  if (!result.success) {
    const details = result.error.issues.map((issue) => ({
      field: issue.path.join('.') || 'query',
      message: issue.message,
    }));
    throw badRequest(message, details);
  }
  return result.data as z.output<S>;
}
