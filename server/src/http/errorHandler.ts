import type { ErrorRequestHandler, NextFunction, Request, RequestHandler, Response } from 'express';
import {
  NotFoundError,
  ServiceUnavailableError,
  UnauthorizedError,
  ValidationError,
} from '../domain/errors.js';
import { logger } from '../logger.js';

/**
 * Envolve handlers async para que uma rejection chegue no `errorHandler`.
 *
 * Necessario no Express 4: ele nao propaga promise rejection sozinho.
 */
export function wrap(handler: RequestHandler): RequestHandler {
  return (req: Request, res: Response, next: NextFunction) => {
    Promise.resolve(handler(req, res, next)).catch(next);
  };
}

/** Mapeia a classe do erro de dominio para o status. Ultimo middleware da app. */
export const errorHandler: ErrorRequestHandler = (error, _req, res, _next) => {
  if (error instanceof ValidationError) {
    res.status(400).json({ error: error.message, code: error.code });
    return;
  }
  if (error instanceof UnauthorizedError) {
    res.status(401).json({ error: error.message, code: error.code });
    return;
  }
  if (error instanceof NotFoundError) {
    res.status(404).json({ error: error.message, code: error.code });
    return;
  }
  if (error instanceof ServiceUnavailableError) {
    res.status(503).json({ error: error.message, code: error.code });
    return;
  }

  logger.error({ err: error }, 'erro nao tratado');
  res.status(500).json({ error: 'Erro interno do servidor.', code: 'internal_error' });
};
