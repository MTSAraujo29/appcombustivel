export class AppError extends Error {
  readonly status: number;
  readonly code: string;
  readonly publicMessage: string;
  readonly details?: unknown;

  constructor(options: {
    status: number;
    code: string;
    publicMessage: string;
    details?: unknown;
    cause?: unknown;
  }) {
    super(options.publicMessage, { cause: options.cause });
    this.name = 'AppError';
    this.status = options.status;
    this.code = options.code;
    this.publicMessage = options.publicMessage;
    this.details = options.details;
  }
}

export function badRequest(publicMessage: string, details?: unknown): AppError {
  return new AppError({
    status: 400,
    code: 'INVALID_PARAMETERS',
    publicMessage,
    details,
  });
}

export function notFound(publicMessage: string): AppError {
  return new AppError({ status: 404, code: 'NOT_FOUND', publicMessage });
}

export function upstreamUnavailable(publicMessage: string, cause?: unknown): AppError {
  return new AppError({
    status: 502,
    code: 'UPSTREAM_UNAVAILABLE',
    publicMessage,
    cause,
  });
}

export function timeoutError(publicMessage: string, cause?: unknown): AppError {
  return new AppError({ status: 504, code: 'UPSTREAM_TIMEOUT', publicMessage, cause });
}
