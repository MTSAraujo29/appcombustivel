import type { NextFunction, Request, Response } from 'express';
import { AppError } from '../utils/errors.js';
import { logger } from '../utils/logger.js';

function requestId(req: Request): string {
  const id = (req as Request & { id?: string | number }).id;
  return id ? String(id) : 'unknown';
}

export function notFoundHandler(req: Request, res: Response): void {
  res.status(404).json({
    error: {
      code: 'NOT_FOUND',
      message: 'Recurso não encontrado.',
      requestId: requestId(req),
    },
  });
}

export function errorHandler(
  error: unknown,
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  if (res.headersSent) {
    next(error);
    return;
  }

  const id = requestId(req);
  let appError: AppError;

  if (error instanceof AppError) {
    appError = error;
  } else if (
    error instanceof SyntaxError &&
    'status' in error &&
    (error as SyntaxError & { status?: number }).status === 400
  ) {
    appError = new AppError({
      status: 400,
      code: 'INVALID_JSON',
      publicMessage: 'Corpo da requisição inválido.',
    });
  } else {
    appError = new AppError({
      status: 500,
      code: 'INTERNAL_ERROR',
      publicMessage: 'Erro interno inesperado. Tente novamente.',
      cause: error,
    });
  }

  if (appError.status >= 500) {
    logger.error({ err: error, requestId: id }, 'Erro ao processar requisição');
  } else {
    logger.info(
      { code: appError.code, requestId: id, status: appError.status },
      'Requisição rejeitada',
    );
  }

  res.status(appError.status).json({
    error: {
      code: appError.code,
      message: appError.publicMessage,
      ...(appError.details !== undefined ? { details: appError.details } : {}),
      requestId: id,
    },
  });
}
