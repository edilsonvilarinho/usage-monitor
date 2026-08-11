import type { Db } from './openDatabase.js';

export interface PurgeReport {
  deletedTurns: number;
  deletedSessions: number;
  deletedMembers: number;
}

const MILLIS_PER_DAY = 24 * 60 * 60 * 1_000;

/**
 * Apaga o que ja saiu do horizonte de consulta.
 *
 * O filtro mais largo da UI e 30 dias, entao o default de 45 deixa folga para
 * quem abre "30d" com o relogio da maquina adiantado. A ordem importa: turnos
 * primeiro, depois as sessoes que ficaram sem nenhum turno, depois os membros
 * que ficaram sem nenhuma sessao.
 */
export function purgeExpiredData(db: Db, retentionDays: number, now: number): PurgeReport {
  const cutoff = now - retentionDays * MILLIS_PER_DAY;

  const purge = db.transaction((): PurgeReport => {
    const deletedTurns = db.prepare('DELETE FROM team_turns WHERE ts < ?').run(cutoff).changes;

    const deletedSessions = db
      .prepare(
        `DELETE FROM team_sessions
         WHERE NOT EXISTS (
           SELECT 1 FROM team_turns t
           WHERE t.account_key = team_sessions.account_key
             AND t.session_id = team_sessions.session_id
         )`,
      )
      .run().changes;

    const deletedMembers = db
      .prepare(
        `DELETE FROM team_members
         WHERE last_seen_at < ?
           AND NOT EXISTS (
             SELECT 1 FROM team_sessions s
             WHERE s.account_key = team_members.account_key
               AND s.device_id = team_members.device_id
           )`,
      )
      .run(cutoff).changes;

    return { deletedTurns, deletedSessions, deletedMembers };
  });

  return purge();
}
