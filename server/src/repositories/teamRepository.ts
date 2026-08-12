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

/** Uma conta dentro da visao global do admin. */
export interface TeamAccountSnapshot extends TeamSnapshot {
  accountKey: string;
  /** Rotulo da chave dona da conta, ou `null` para conta sem chave emitida. */
  label: string | null;
}

/** Identificacao da sessao pedida, com a maquina que a reportou. */
export interface TeamSessionRow {
  deviceId: string;
  sessionId: string;
  hostName: string | null;
  cwd: string | null;
  gitBranch: string | null;
  firstTs: number;
  lastTs: number;
  liveContextTokens: number;
  liveContextModel: string | null;
}

/**
 * Um turno cru, como o cliente o enviou.
 *
 * Nao ha `seq` gravado: a ordem e `(ts, message_id)` e o cliente a sintetiza na
 * leitura. Sao contagens de token e o modelo — **nenhum conteudo de prompt ou
 * resposta**, igual ao resto da API.
 */
export interface TeamSessionTurnRow {
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

export interface TeamSessionDetail {
  session: TeamSessionRow;
  turns: TeamSessionTurnRow[];
}

export interface DeleteMemberReport {
  deletedTurns: number;
  deletedSessions: number;
  deletedMembers: number;
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

/**
 * Mesmas duas consultas acima, sem o recorte por conta.
 *
 * Existem para a visao global do admin. Nao da para reaproveitar as de cima com
 * um `@accountKey` nulo: o `WHERE account_key = @accountKey` deixaria de casar
 * com tudo e passaria a casar com nada. O `account_key` sobe para o `SELECT` e
 * para o `GROUP BY`, e quem separa as contas e o agrupamento em memoria.
 */
const SELECT_ALL_MEMBERS_SQL = `
SELECT account_key AS accountKey, device_id AS deviceId, alias, host_name AS hostName,
       organization_uuid AS organizationUuid, organization_name AS organizationName,
       last_seen_at AS lastSeenAt
FROM team_members
ORDER BY alias COLLATE NOCASE ASC
`;

const SELECT_ALL_USAGE_SQL = `
SELECT t.account_key AS accountKey,
       s.device_id AS deviceId,
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
WHERE (@since IS NULL OR t.ts >= @since)
GROUP BY t.account_key, s.device_id, t.session_id, t.model
ORDER BY MAX(t.ts) DESC
`;

/**
 * Sessao unica, escopada por `(conta, maquina)`.
 *
 * O `host_name` vem de `team_members` por `LEFT JOIN` para a resposta se bastar:
 * o card de metadados do cliente mostra a maquina, e ela nao vive em
 * `team_sessions`. `LEFT` porque a sessao sobrevive a remocao do membro ate a
 * retencao passar, e uma sessao sem maquina conhecida ainda tem detalhe.
 */
const SELECT_SESSION_SQL = `
SELECT s.device_id AS deviceId,
       s.session_id AS sessionId,
       m.host_name AS hostName,
       s.cwd AS cwd,
       s.git_branch AS gitBranch,
       s.first_ts AS firstTs,
       s.last_ts AS lastTs,
       s.live_context_tokens AS liveContextTokens,
       s.live_context_model AS liveContextModel
FROM team_sessions s
LEFT JOIN team_members m
  ON m.account_key = s.account_key AND m.device_id = s.device_id
WHERE s.account_key = @accountKey
  AND s.session_id = @sessionId
  AND s.device_id = @deviceId
`;

/**
 * Turnos crus da sessao, na ordem em que aconteceram.
 *
 * Sem filtro de `device_id`: `team_turns` nao guarda a maquina, e nao precisa —
 * `(account_key, session_id)` ja identifica a sessao, cujo dono foi conferido
 * por [SELECT_SESSION_SQL] antes desta consulta rodar.
 *
 * O `WHERE` e prefixo da chave primaria `(account_key, session_id, message_id)`,
 * entao a busca usa o indice da PK — nenhum indice novo e necessario.
 *
 * O desempate por `message_id` deixa a ordem estavel: dois turnos com o mesmo
 * `ts` sairiam em ordem arbitraria e a serie por turno mudaria a cada leitura.
 */
const SELECT_SESSION_TURNS_SQL = `
SELECT message_id AS messageId,
       ts AS ts,
       model AS model,
       is_sidechain AS isSidechain,
       input_tokens AS inputTokens,
       output_tokens AS outputTokens,
       cache_read_tokens AS cacheReadTokens,
       cache_write_5m_tokens AS cacheWrite5mTokens,
       cache_write_1h_tokens AS cacheWrite1hTokens
FROM team_turns
WHERE account_key = @accountKey AND session_id = @sessionId
ORDER BY ts ASC, message_id ASC
`;

/**
 * Turnos das sessoes daquele device.
 *
 * `team_turns` nao guarda `device_id` — o dono do turno e a sessao. Por isso o
 * `IN` sobre `team_sessions`, e por isso esta consulta tem de rodar **antes** de
 * apagar as sessoes, ou nao sobraria de onde deduzir quais turnos remover.
 */
const DELETE_MEMBER_TURNS_SQL = `
DELETE FROM team_turns
 WHERE account_key = @accountKey
   AND session_id IN (
     SELECT session_id FROM team_sessions
      WHERE account_key = @accountKey AND device_id = @deviceId
   )
`;

const DELETE_MEMBER_SESSIONS_SQL = `
DELETE FROM team_sessions WHERE account_key = @accountKey AND device_id = @deviceId
`;

const DELETE_MEMBER_SQL = `
DELETE FROM team_members WHERE account_key = @accountKey AND device_id = @deviceId
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

  /**
   * Remove um integrante e tudo o que veio dele, numa transacao.
   *
   * Serve para o caso em que a mesma maquina aparece duas vezes: um `deviceId`
   * novo (arquivo de configuracao perdido, reinstalacao) deixa o antigo como um
   * integrante fantasma que a retencao so recolhe depois de 45 dias.
   *
   * Idempotente: apagar quem nao existe devolve zeros, nao erro. **Destrutivo e
   * irreversivel** — o cliente daquele device ja marcou os turnos como enviados
   * e nao os reenvia.
   */
  deleteMember(accountKey: string, deviceId: string): DeleteMemberReport {
    const params = { accountKey, deviceId };

    const run = this.db.transaction((): DeleteMemberReport => {
      const deletedTurns = this.db.prepare(DELETE_MEMBER_TURNS_SQL).run(params).changes;
      const deletedSessions = this.db.prepare(DELETE_MEMBER_SESSIONS_SQL).run(params).changes;
      const deletedMembers = this.db.prepare(DELETE_MEMBER_SQL).run(params).changes;
      return { deletedTurns, deletedSessions, deletedMembers };
    });

    return run();
  }

  /** Snapshot de uma conta. `since` nulo devolve tudo o que sobreviveu a retencao. */
  readTeam(accountKey: string, since: number | null): TeamSnapshot {
    const members = this.db.prepare(SELECT_MEMBERS_SQL).all({ accountKey }) as TeamMemberRow[];
    const rows = this.db.prepare(SELECT_USAGE_SQL).all({ accountKey, since }) as TeamUsageRow[];
    return { members, rows };
  }

  /**
   * Todas as contas de uma vez, para a visao global do admin.
   *
   * Duas consultas e o agrupamento em memoria, em vez de uma consulta por conta:
   * o numero de contas de um servidor interno e pequeno, mas o laco viraria N+1
   * e cresceria justamente no cenario que a tela existe para atender.
   *
   * Uma conta que so tem membro e nenhum turno na janela continua aparecendo,
   * com `rows` vazia — e a mesma promessa de `readTeam`, onde quem nao consumiu
   * e informacao e nao ruido.
   */
  readOverview(since: number | null, labels: Map<string, string>): TeamAccountSnapshot[] {
    const memberRows = this.db.prepare(SELECT_ALL_MEMBERS_SQL).all() as Array<
      TeamMemberRow & { accountKey: string }
    >;
    const usageRows = this.db.prepare(SELECT_ALL_USAGE_SQL).all({ since }) as Array<
      TeamUsageRow & { accountKey: string }
    >;

    const byAccount = new Map<string, TeamAccountSnapshot>();

    const ensure = (accountKey: string): TeamAccountSnapshot => {
      const existing = byAccount.get(accountKey);
      if (existing !== undefined) {
        return existing;
      }
      const created: TeamAccountSnapshot = {
        accountKey,
        label: labels.get(accountKey) ?? null,
        members: [],
        rows: [],
      };
      byAccount.set(accountKey, created);
      return created;
    };

    for (const row of memberRows) {
      const { accountKey, ...member } = row;
      ensure(accountKey).members.push(member);
    }

    for (const row of usageRows) {
      const { accountKey, ...usage } = row;
      ensure(accountKey).rows.push(usage);
    }

    return [...byAccount.values()];
  }

  /**
   * Detalhe de uma sessao: os metadados dela e os turnos crus, em ordem.
   *
   * Sem recorte temporal, ao contrario de [readTeam]: o detalhe e sempre a
   * sessao inteira, como no modal da propria maquina. Recorta-lo pela janela de
   * quota daria graficos que comecam no meio da conversa.
   *
   * Devolve `null` quando a sessao nao existe **naquela conta e naquela
   * maquina** — e a mesma resposta para conta errada e para sessao inexistente,
   * de proposito: confirmar a existencia de uma sessao de outra conta ja seria
   * vazamento.
   */
  readSession(accountKey: string, deviceId: string, sessionId: string): TeamSessionDetail | null {
    const session = this.db
      .prepare(SELECT_SESSION_SQL)
      .get({ accountKey, deviceId, sessionId }) as TeamSessionRow | undefined;

    if (session === undefined) {
      return null;
    }

    const rows = this.db.prepare(SELECT_SESSION_TURNS_SQL).all({ accountKey, sessionId }) as Array<
      Omit<TeamSessionTurnRow, 'isSidechain'> & { isSidechain: number }
    >;

    // `is_sidechain` e INTEGER no SQLite; o contrato HTTP e booleano.
    const turns = rows.map((row) => ({ ...row, isSidechain: row.isSidechain !== 0 }));

    return { session, turns };
  }
}
