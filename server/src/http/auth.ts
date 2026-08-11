import { createHash, timingSafeEqual } from 'node:crypto';
import type { NextFunction, Request, RequestHandler, Response } from 'express';
import { UnauthorizedError } from '../domain/errors.js';

export const TEAM_KEY_HEADER = 'x-team-key';

/**
 * Compara a chave recebida com a configurada em tempo constante.
 *
 * As duas passam por SHA-256 antes: `timingSafeEqual` exige buffers do mesmo
 * tamanho e lanca se diferirem, o que por si so ja vazaria o tamanho da chave.
 */
export function isValidTeamKey(received: string | undefined, expected: string): boolean {
  if (!received) {
    return false;
  }
  const receivedDigest = createHash('sha256').update(received).digest();
  const expectedDigest = createHash('sha256').update(expected).digest();
  return timingSafeEqual(receivedDigest, expectedDigest);
}

/**
 * Envolve um handler exigindo a chave de time.
 *
 * HOF em vez de middleware encadeado para que a rota declare a propria protecao
 * no ponto de uso — nao da para esquecer de proteger uma rota que voce escreveu.
 */
export function requireTeamKey(expectedKey: string, handler: RequestHandler): RequestHandler {
  return (req: Request, res: Response, next: NextFunction) => {
    const received = req.header(TEAM_KEY_HEADER);
    if (!isValidTeamKey(received ?? undefined, expectedKey)) {
      next(new UnauthorizedError());
      return;
    }
    handler(req, res, next);
  };
}
