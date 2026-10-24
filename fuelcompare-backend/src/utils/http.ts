import { env } from '../config/env.js';
import { timeoutError, upstreamUnavailable } from './errors.js';
import { logger } from './logger.js';

const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_RESPONSE_BYTES = 30 * 1024 * 1024;

export interface FetchOptions {
  timeoutMs?: number;
  headers?: Record<string, string>;
  method?: 'GET' | 'POST';
  body?: string;
  retries?: number;
  maxBytes?: number;
}

async function singleFetch(url: string, options: FetchOptions): Promise<Response> {
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await fetch(url, {
      method: options.method ?? 'GET',
      headers: {
        'User-Agent': env.HTTP_USER_AGENT,
        Accept: 'application/json, text/html;q=0.8, */*;q=0.5',
        ...(options.headers ?? {}),
      },
      body: options.body,
      redirect: 'follow',
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Executa a requisição externa com timeout, limite de tamanho e retentativas
 * para falhas transitórias. Não registra URLs com dados sensíveis.
 */
export async function fetchResponse(url: string, options: FetchOptions = {}): Promise<Response> {
  const retries = options.retries ?? 1;
  let lastError: unknown;

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      const response = await singleFetch(url, options);
      if ([429, 502, 503, 504].includes(response.status) && attempt < retries) {
        const retryAfter = Number(response.headers.get('retry-after'));
        const delay = Number.isFinite(retryAfter)
          ? Math.min(retryAfter * 1000, 5_000)
          : 700 * (attempt + 1);
        logger.warn({ status: response.status, attempt }, 'Provedor externo instável; tentando novamente');
        await new Promise((resolve) => setTimeout(resolve, delay));
        continue;
      }
      return response;
    } catch (error) {
      lastError = error;
      if (error instanceof Error && error.name === 'AbortError') {
        if (attempt === retries) {
          throw timeoutError('O provedor externo demorou demais para responder.', error);
        }
      } else if (attempt === retries) {
        throw upstreamUnavailable('Não foi possível consultar o provedor externo.', error);
      }
      await new Promise((resolve) => setTimeout(resolve, 700 * (attempt + 1)));
    }
  }

  throw upstreamUnavailable('Não foi possível consultar o provedor externo.', lastError);
}

export async function fetchJson<T>(url: string, options: FetchOptions = {}): Promise<T> {
  const response = await fetchResponse(url, options);
  if (!response.ok) {
    throw upstreamUnavailable(`O provedor externo respondeu com HTTP ${response.status}.`);
  }
  const text = await response.text();
  if (text.length > (options.maxBytes ?? MAX_RESPONSE_BYTES)) {
    throw upstreamUnavailable('A resposta do provedor externo excedeu o limite permitido.');
  }
  try {
    return JSON.parse(text) as T;
  } catch (error) {
    throw upstreamUnavailable('O provedor externo retornou um formato inesperado.', error);
  }
}

export async function fetchBuffer(
  url: string,
  options: FetchOptions = {},
): Promise<{ buffer: Buffer; contentType: string; finalUrl: string }> {
  const response = await fetchResponse(url, options);
  if (!response.ok) {
    throw upstreamUnavailable(`O provedor externo respondeu com HTTP ${response.status}.`);
  }
  const maxBytes = options.maxBytes ?? MAX_RESPONSE_BYTES;
  const declaredLength = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredLength) && declaredLength > maxBytes) {
    throw upstreamUnavailable('A resposta do provedor externo excedeu o limite permitido.');
  }
  const arrayBuffer = await response.arrayBuffer();
  if (arrayBuffer.byteLength > maxBytes) {
    throw upstreamUnavailable('A resposta do provedor externo excedeu o limite permitido.');
  }
  return {
    buffer: Buffer.from(arrayBuffer),
    contentType: response.headers.get('content-type') ?? 'application/octet-stream',
    finalUrl: response.url || url,
  };
}
