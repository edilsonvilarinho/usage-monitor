import type { Db } from '../db/openDatabase.js';

export interface IngestMember {
  deviceId: string;
  alias: string;
  hostName: string | null;
  organizationUuid: string | null;
  organizationName: string | null;
}

export interface IngestSession {
  sessionId: string;
  cwd: string | null;
  gitBranch: string | null;
  firstTs: number;
  lastTs: number;
  liveContextTokens: number;
  liveContextModel: string | null;
}

export interface IngestTurn {
  sessionId: string;
  messageId: string;
  ts: number;
  model: string | null;
  isSidechain: boolean;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWrite5mTokens: number;
  cacheWrite1hTokens: number;
}

export interface IngestPayload {
  accountKey: string;
  member: IngestMember;
  sessions: IngestSession[];
  turns: IngestTurn[];
}

export interface IngestReceipt {
  acceptedTurns: number;
  ignoredTurns: number;
  acceptedSessions: number;
}

export interface TeamMemberRow {
  deviceId: string;
  alias: string;
  hostName: string | null;
  organizationUuid: string | null;
  organizationName: string | null;
  lastSeenAt: number;
}

export interface TeamUsageRow {
  deviceId: string;
  sessionId: string;
  cwd: string | null;
  gitBranch: string | null;
  liveContextTokens: number;
  liveContextModel: string | null;
  model: string | null;
  turnCount: number;
  firstTs: number;
  lastTs: number;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWrite5mTokens: number;
  cacheWrite1hTokens: number;
}

export interface TeamSnapshot {
  members: TeamMemberRow[];
  rows: TeamUsageRow[];
}

const UPSERT_MEMBER_SQL = `
INSERT INTO team_members (
  account_key, device_id, alias, host_name, organization_uuid, organization_name, last_seen_at
) VALUES (
  @accountKey, @deviceId, @alias, @hostName, @organizationUuid, @organizationName, @lastSeenAt
)
ON CONFLICT(account_key, device_id) DO UPDATE SET
  alias = excluded.alias,
  host_name = COALESCE(excluded.host_name, team_members.host_name),
  organization_uuid = COALESCE(excluded.organization_uuid, team_members.organization_uuid),
  organization_name = COALESCE(excluded.organization_name, team_members.organization_name),
  last_seen_at = MAX(excluded.last_seen_at, team_members.last_seen_at)
`;

/**
 * O upsert de sessao alarga a janela em vez de sobrescreve-la: dois pushes
 * parciais da mesma sessao nao podem encurtar `first_ts`/`last_ts`.
 *
 * `live_context_*` e a excecao — e o estado atual da sessao, entao so o envio
 * mais recente vale. No SQLite todas as expressoes do `DO UPDATE SET` sao
 * avaliadas contra a linha antiga, entao o `CASE` compara com o `last_ts` de
 * antes da atualizacao, e nao com o valor recem-calculado.
 */
const UPSERT_SESSION_SQL = `
INSERT INTO team_sessions (
  account_key, session_id, device_id, cwd, git_branch,
  first_ts, last_ts, live_context_tokens, live_context_model
) VALUES (
  @accountKey, @sessionId, @deviceId, @cwd, @gitBranch,
  @firstTs, @lastTs, @liveContextTokens, @liveContextModel
)
ON CONFLICT(account_key, session_id) DO UPDATE SET
  device_id = excluded.device_id,
  cwd = COALESCE(excluded.cwd, team_sessions.cwd),
  git_branch = COALESCE(excluded.git_branch, team_sessions.git_branch),
  first_ts = MIN(excluded.first_ts, team_sessions.first_ts),
  last_ts = MAX(excluded.last_ts, team_sessions.last_ts),
  live_context_tokens = CASE
    WHEN excluded.last_ts >= team_sessions.last_ts THEN excluded.live_context_tokens
    ELSE team_sessions.live_context_tokens
  END,
  live_context_model = CASE
    WHEN excluded.last_ts >= team_sessions.last_ts THEN excluded.live_context_model
    ELSE team_sessions.live_context_model
  END
`;

/** `OR IGNORE` + PK composta: reenviar o mesmo lote nao duplica nem falha. */
const INSERT_TURN_SQL = `
INSERT OR IGNORE INTO team_turns (
  account_key, session_id, message_id, ts, model, is_sidechain,
  input_tokens, output_tokens, cache_read_tokens,
  cache_write_5m_tokens, cache_write_1h_tokens
) VALUES (
  @accountKey, @sessionId, @messageId, @ts, @model, @isSidechain,
  @inputTokens, @outputTokens, @cacheReadTokens,
  @cacheWrite5mTokens, @cacheWrite1hTokens
)
`;

const SELECT_MEMBERS_SQL = `
SELECT device_id AS deviceId, alias, host_name AS hostName,
       organization_uuid AS organizationUuid, organization_name AS organizationName,
       last_seen_at AS lastSeenAt
FROM team_members
WHERE account_key = @accountKey
ORDER BY alias COLLATE NOCASE ASC
`;

/**
 * Agregados da janela, agrupados por modelo.
 *
 * O `GROUP BY ... t.model` e obrigatorio: uma sessao que trocou de modelo no
 * meio precisa ser precificada com a tarifa de cada trecho. Quem dobra as linhas
 * por sessao e aplica a tabela de precos e o cliente, para que o custo do modal
 * de time acompanhe a tabela do app sem duplica-la aqui.
 */
const SELECT_USAGE_SQL = `
SELECT s.device_id AS deviceId,
       t.session_id AS sessionId,
       s.cwd AS cwd,
       s.git_branch AS gitBranch,
       s.live_context_tokens AS liveContextTokens,
       s.live_context_model AS liveContextModel,
       t.model AS model,
       COUNT(*) AS turnCount,
       MIN(t.ts) AS firstTs,
       MAX(t.ts) AS lastTs,
       SUM(t.input_tokens) AS inputTokens,
       SUM(t.output_tokens) AS outputTokens,
       SUM(t.cache_read_tokens) AS cacheReadTokens,
       SUM(t.cache_write_5m_tokens) AS cacheWrite5mTokens,
       SUM(t.cache_write_1h_tokens) AS cacheWrite1hTokens
FROM team_turns t
JOIN team_sessions s
  ON s.account_key = t.account_key AND s.session_id = t.session_id
WHERE t.account_key = @accountKey
  AND (@since IS NULL OR t.ts >= @since)
GROUP BY s.device_id, t.session_id, t.model
ORDER BY MAX(t.ts) DESC
`;

export class TeamRepository {
  private readonly db: Db;

  constructor(db: Db) {
    this.db = db;
  }

  /** Grava membro, sessoes e turnos numa transacao unica. Idempotente. */
  ingest(payload: IngestPayload, now: number): IngestReceipt {
    const upsertMember = this.db.prepare(UPSERT_MEMBER_SQL);
    const upsertSession = this.db.prepare(UPSERT_SESSION_SQL);
    const insertTurn = this.db.prepare(INSERT_TURN_SQL);

    const run = this.db.transaction((): IngestReceipt => {
      upsertMember.run({
        accountKey: payload.accountKey,
        deviceId: payload.member.deviceId,
        alias: payload.member.alias,
        hostName: payload.member.hostName,
        organizationUuid: payload.member.organizationUuid,
        organizationName: payload.member.organizationName,
        lastSeenAt: now,
      });

      for (const session of payload.sessions) {
        upsertSession.run({
          accountKey: payload.accountKey,
          sessionId: session.sessionId,
          deviceId: payload.member.deviceId,
          cwd: session.cwd,
          gitBranch: session.gitBranch,
          firstTs: session.firstTs,
          lastTs: session.lastTs,
          liveContextTokens: session.liveContextTokens,
          liveContextModel: session.liveContextModel,
        });
      }

      let acceptedTurns = 0;
      for (const turn of payload.turns) {
        const result = insertTurn.run({
          accountKey: payload.accountKey,
          sessionId: turn.sessionId,
          messageId: turn.messageId,
          ts: turn.ts,
          model: turn.model,
          isSidechain: turn.isSidechain ? 1 : 0,
          inputTokens: turn.inputTokens,
          outputTokens: turn.outputTokens,
          cacheReadTokens: turn.cacheReadTokens,
          cacheWrite5mTokens: turn.cacheWrite5mTokens,
          cacheWrite1hTokens: turn.cacheWrite1hTokens,
        });
        acceptedTurns += result.changes;
      }

      return {
        acceptedTurns,
        ignoredTurns: payload.turns.length - acceptedTurns,
        acceptedSessions: payload.sessions.length,
      };
    });

    return run();
  }

  /** Snapshot de uma conta. `since` nulo devolve tudo o que sobreviveu a retencao. */
  readTeam(accountKey: string, since: number | null): TeamSnapshot {
    const members = this.db.prepare(SELECT_MEMBERS_SQL).all({ accountKey }) as TeamMemberRow[];
    const rows = this.db.prepare(SELECT_USAGE_SQL).all({ accountKey, since }) as TeamUsageRow[];
    return { members, rows };
  }
}
