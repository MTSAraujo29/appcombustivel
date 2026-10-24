import type { Request, Response } from 'express';
import type { StationService } from '../services/stationService.js';
import {
  compareQuerySchema,
  nearbyQuerySchema,
  parseOrThrow,
  stationIdParamSchema,
} from '../schemas/apiSchemas.js';

export class StationController {
  constructor(private readonly service: StationService) {}

  nearby = async (req: Request, res: Response): Promise<void> => {
    const query = parseOrThrow(nearbyQuerySchema, req.query, 'Parâmetros de busca inválidos.');
    const result = await this.service.nearby({
      latitude: query.lat,
      longitude: query.lon,
      radiusKm: query.radiusKm,
      limit: query.limit,
      ...(query.city ? { city: query.city } : {}),
      ...(query.state ? { state: query.state } : {}),
    });
    res.json({ data: result.data, meta: result.meta });
  };

  details = async (req: Request, res: Response): Promise<void> => {
    const { id } = parseOrThrow(
      stationIdParamSchema,
      req.params,
      'Identificador de posto inválido.',
    );
    const result = await this.service.details(id);
    res.json({ data: result.data, meta: result.meta });
  };

  prices = async (req: Request, res: Response): Promise<void> => {
    const { id } = parseOrThrow(
      stationIdParamSchema,
      req.params,
      'Identificador de posto inválido.',
    );
    const result = await this.service.pricesForStation(id);
    res.json({ data: result.data, meta: result.meta });
  };

  compare = async (req: Request, res: Response): Promise<void> => {
    const { stationA, stationB } = parseOrThrow(
      compareQuerySchema,
      req.query,
      'Parâmetros de comparação inválidos.',
    );
    const result = await this.service.compare(stationA, stationB);
    res.json({ data: result.data, meta: result.meta });
  };
}
