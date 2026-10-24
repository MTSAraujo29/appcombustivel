import type { Request, Response } from 'express';
import type { GeocodingService } from '../services/geocodingService.js';
import {
  geocodeSearchQuerySchema,
  parseOrThrow,
  reverseQuerySchema,
} from '../schemas/apiSchemas.js';

export class GeocodeController {
  constructor(private readonly service: GeocodingService) {}

  search = async (req: Request, res: Response): Promise<void> => {
    const { q, limit } = parseOrThrow(
      geocodeSearchQuerySchema,
      req.query,
      'Parâmetros de busca inválidos.',
    );
    const result = await this.service.search(q, limit);
    res.json({ data: { results: result.results }, meta: result.meta });
  };

  reverse = async (req: Request, res: Response): Promise<void> => {
    const { lat, lon } = parseOrThrow(
      reverseQuerySchema,
      req.query,
      'Coordenadas inválidas.',
    );
    const region = await this.service.reverse(lat, lon);
    res.json({
      data: { region },
      meta: { cached: false, generatedAt: new Date().toISOString(), warnings: [] },
    });
  };
}
