import { Router } from 'express';
import type { Db } from '../../db/openDatabase.js';
import { ServiceUnavailableError } from '../../domain/errors.js';
import { wrap } from '../errorHandler.js';

/**
 * Healthcheck que realmente toca o banco.
 *
 * Um `200 {status:'ok'}` estatico passaria com o volume desmontado, e o Dokploy
 * manteria no ar um container que nao consegue gravar nada.
 */
export function createHealthRouter(db: Db): Router {
  const router = Router();

  router.get(
    '/health',
    wrap((_req, res) => {
      try {
        db.prepare('SELECT 1').get();
      } catch (error) {
        throw new ServiceUnavailableError(
          `Banco indisponivel: ${error instanceof Error ? error.message : 'erro desconhecido'}`,
        );
      }
      res.json({ status: 'ok' });
    }),
  );

  return router;
}
