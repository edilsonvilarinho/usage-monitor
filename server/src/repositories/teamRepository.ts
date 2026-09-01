import type { Db } from '../db/openDatabase.js';
import { normalizeAccountEmail } from '../domain/accountEmail.js';

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
  accountEmail?: string | null;
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

/**
 * Um dia de consumo de uma maquina, ja agrupado por modelo.
 *
 * O bucket e o dia **UTC**: o servidor nao conhece o fuso de quem consulta, e
 * agrupar num fuso arbitrario daria um grafico deslocado para metade do time. O
 * cliente traduz, do mesmo jeito que faz com a grade de atividade local.
 */
export interface TeamTrendRow {
  deviceId: string;
  dayStartMillis: number;
  model: string | null;
  turnCount: number;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWrite5mTokens: number;
  cacheWrite1hTokens: number;
}

export interface TeamTrend {
  members: TeamMemberRow[];
  rows: TeamTrendRow[];
}

/**
 * Tempo de trabalho de uma sessao dentro da janela.
 *
 * Lista separada, e nunca uma coluna em [TeamUsageRow]: aquela linha e
 * `(maquina, sessao, modelo)` e uma sessao que trocou de modelo no meio aparece
 * em varias delas — o cliente somaria a hora uma vez por modelo.
 */
export interface TeamSessionActivityRow {
  deviceId: string;
  sessionId: string;
  activeMillis: number;
}

export interface TeamSnapshot {
  members: TeamMemberRow[];
  rows: TeamUsageRow[];
  activity: TeamSessionActivityRow[];
}

/** Uma conta dentro da visao global do admin. */
export interface TeamAccountSnapshot extends TeamSnapshot {
  accountKey: string;
  /** Rotulo da chave dona da conta, ou `null` para conta sem chave emitida. */
  label: string | null;
  /** E-mail efetivo usado somente para agrupamento visual. */
  accountEmail: string | null;
  /** Origem do e-mail efetivo; `label` e sempre um fallback provisório. */
  emailSource: 'reported' | 'label' | null;
}

/**
 * Uma pagina de relatorio, com o cursor da proxima.
 *
 * `nextCursor` nulo significa fim do conjunto — nao "sem resultado". A ultima
 * pagina cheia vem sem cursor porque a consulta pede um item a mais do que o
 * limite: sem isso, a ultima pagina exata devolveria um cursor que abriria uma
 * pagina vazia.
 */
export interface ReportPage<T> {
  rows: T[];
  nextCursor: ReportCursor | null;
}

/**
 * Posicao no conjunto ordenado, que e a **propria chave de agrupamento**.
 *
 * Nao e `MAX(t.ts) DESC` como no overview: recencia nao da ordem total sem
 * desempate, e quem pagina um periodo fechado quer completude, nao o mais
 * recente primeiro. O `model` fica vazio quando e nulo — NULL nao compara em
 * row-value.
 */
export interface ReportCursor {
  accountKey: string;
  deviceId: string;
  sessionId: string;
  model: string;
}

export interface ReportUsageRow extends TeamUsageRow {
  accountKey: string;
}

export interface ReportActivityRow extends TeamSessionActivityRow {
  accountKey: string;
}

/** Conta e integrantes, sem consumo: o "quem e quem" do relatorio. */
export interface ReportAccountMembers {
  accountKey: string;
  label: string | null;
  accountEmail: string | null;
  emailSource: 'reported' | 'label' | null;
  members: TeamMemberRow[];
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

export interface DeleteSessionReport {
  deletedTurns: number;
  deletedSessions: number;
}

/**
 * Mesmo recibo da remocao de integrante, agora para a conta inteira.
 *
 * Um tipo proprio, e nao um alias de [DeleteMemberReport]: a rota que o devolve
 * acrescenta `unlinkedKeys`, que nao vem deste repositorio.
 */
export interface DeleteAccountReport {
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
/**
 * Consumo por maquina, dia (UTC) e modelo.
 *
 * O dia sai da divisao inteira do `ts` por 86.400.000 — a mesma tecnica da grade
 * local, e pela mesma razao: quem traduz para o fuso de apresentacao e o
 * cliente. O indice `idx_team_turns_window` cobre o recorte.
 *
 * A maquina vem de `team_sessions`: `team_turns` nao guarda `device_id`, e o
 * vinculo turno -> maquina passa sempre pela sessao.
 */
const MILLIS_PER_DAY = 24 * 60 * 60 * 1_000;

const SELECT_TREND_SQL = `
SELECT s.device_id AS deviceId,
       (t.ts / 86400000) AS dayBucket,
       t.model AS model,
       COUNT(*) AS turnCount,
       SUM(t.input_tokens) AS inputTokens,
       SUM(t.output_tokens) AS outputTokens,
       SUM(t.cache_read_tokens) AS cacheReadTokens,
       SUM(t.cache_write_5m_tokens) AS cacheWrite5mTokens,
       SUM(t.cache_write_1h_tokens) AS cacheWrite1hTokens
FROM team_turns t
JOIN team_sessions s
  ON s.account_key = t.account_key AND s.session_id = t.session_id
WHERE t.account_key = @accountKey
  AND t.ts >= @since
  AND (@until IS NULL OR t.ts < @until)
GROUP BY s.device_id, dayBucket, t.model
ORDER BY dayBucket ASC, deviceId ASC
`;

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
  AND (@until IS NULL OR t.ts < @until)
GROUP BY s.device_id, t.session_id, t.model
ORDER BY MAX(t.ts) DESC
`;

/**
 * Tempo de trabalho por sessao: soma dos intervalos entre turnos consecutivos
 * da thread principal menores que o corte.
 *
 * E a mesma definicao que o indice local aplica em `activeTimeMillisOf`, e o
 * corte chega **do cliente** em `@gapCutoffMs` justamente para o servidor nao
 * virar um segundo dono da constante — pelo mesmo motivo que ele nao precifica
 * turno nenhum.
 *
 * `is_sidechain = 0` porque o subagente roda em paralelo com a conversa
 * principal: somar os intervalos dele contaria o mesmo tempo duas vezes.
 *
 * O recorte entra **dentro** da subconsulta, antes do `LAG`. O intervalo que
 * cruza a fronteira nao e contado em nenhuma das duas janelas: conta-lo nas duas
 * duplicaria o mesmo tempo, e atribui-lo a uma delas exigiria ler um turno que
 * esta fora dela. E o que `since` sempre fez na borda esquerda — `until` so
 * passa a fazer o mesmo na direita.
 *
 * A ordem e `(ts, message_id)` e nao `seq`: o servidor nao guarda a sequencia
 * do transcript, e esse par ja e a ordem canonica que `/v1/session` devolve.
 */
const SELECT_SESSION_ACTIVITY_SQL = `
SELECT deviceId, sessionId, SUM(gap) AS activeMillis
FROM (
  SELECT s.device_id AS deviceId,
         t.session_id AS sessionId,
         t.ts - LAG(t.ts) OVER (
           PARTITION BY t.account_key, t.session_id ORDER BY t.ts, t.message_id
         ) AS gap
  FROM team_turns t
  JOIN team_sessions s
    ON s.account_key = t.account_key AND s.session_id = t.session_id
  WHERE t.account_key = @accountKey
    AND t.is_sidechain = 0
    AND (@since IS NULL OR t.ts >= @since)
    AND (@until IS NULL OR t.ts < @until)
)
WHERE gap > 0 AND gap < @gapCutoffMs
GROUP BY deviceId, sessionId
`;

const SELECT_ALL_SESSION_ACTIVITY_SQL = `
SELECT accountKey, deviceId, sessionId, SUM(gap) AS activeMillis
FROM (
  SELECT t.account_key AS accountKey,
         s.device_id AS deviceId,
         t.session_id AS sessionId,
         t.ts - LAG(t.ts) OVER (
           PARTITION BY t.account_key, t.session_id ORDER BY t.ts, t.message_id
         ) AS gap
  FROM team_turns t
  JOIN team_sessions s
    ON s.account_key = t.account_key AND s.session_id = t.session_id
  WHERE t.is_sidechain = 0
    AND (@since IS NULL OR t.ts >= @since)
    AND (@until IS NULL OR t.ts < @until)
)
WHERE gap > 0 AND gap < @gapCutoffMs
GROUP BY accountKey, deviceId, sessionId
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
  AND (@until IS NULL OR t.ts < @until)
GROUP BY t.account_key, s.device_id, t.session_id, t.model
ORDER BY MAX(t.ts) DESC
`;

/**
 * Linhas planas para o relatorio, ordenadas pela chave de agrupamento.
 *
 * Irma de [SELECT_ALL_USAGE_SQL], com duas diferencas: a ordem e
 * `(conta, maquina, sessao, modelo)` em vez de recencia, e o predicado do cursor
 * cabe no `WHERE` — as quatro colunas sao do `GROUP BY`, entao o filtro age
 * antes de agrupar e nao vira `HAVING` sobre agregado.
 *
 * `IFNULL(t.model, '')` porque `model` e anulavel e NULL nao compara em
 * row-value: sem isso a pagina pararia na primeira linha sem modelo.
 *
 * O `LIMIT` pede um item a mais do que o cliente pediu; quem descarta o extra e
 * quem monta o cursor.
 */
const SELECT_REPORT_USAGE_SQL = `
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
  AND (@until IS NULL OR t.ts < @until)
  AND (@cursorAccountKey IS NULL
       OR (t.account_key, s.device_id, t.session_id, IFNULL(t.model, '')) >
          (@cursorAccountKey, @cursorDeviceId, @cursorSessionId, @cursorModel))
GROUP BY t.account_key, s.device_id, t.session_id, t.model
ORDER BY t.account_key ASC, s.device_id ASC, t.session_id ASC, IFNULL(t.model, '') ASC
LIMIT @limit
`;

/**
 * Tempo de trabalho por sessao, paginado.
 *
 * Mesma definicao de [SELECT_ALL_SESSION_ACTIVITY_SQL]. O cursor filtra
 * `(conta, maquina, sessao)` **inteiras**, nunca turnos dentro de uma sessao:
 * cortar turnos ali mudaria o `LAG` e a soma da sessao dependeria da pagina em
 * que ela caiu.
 */
const SELECT_REPORT_ACTIVITY_SQL = `
SELECT accountKey, deviceId, sessionId, SUM(gap) AS activeMillis
FROM (
  SELECT t.account_key AS accountKey,
         s.device_id AS deviceId,
         t.session_id AS sessionId,
         t.ts - LAG(t.ts) OVER (
           PARTITION BY t.account_key, t.session_id ORDER BY t.ts, t.message_id
         ) AS gap
  FROM team_turns t
  JOIN team_sessions s
    ON s.account_key = t.account_key AND s.session_id = t.session_id
  WHERE t.is_sidechain = 0
    AND (@since IS NULL OR t.ts >= @since)
    AND (@until IS NULL OR t.ts < @until)
    AND (@cursorAccountKey IS NULL
         OR (t.account_key, s.device_id, t.session_id) >
            (@cursorAccountKey, @cursorDeviceId, @cursorSessionId))
)
WHERE gap > 0 AND gap < @gapCutoffMs
GROUP BY accountKey, deviceId, sessionId
ORDER BY accountKey ASC, deviceId ASC, sessionId ASC
LIMIT @limit
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

const UPSERT_ACCOUNT_EMAIL_SQL = `
INSERT INTO team_accounts (account_key, account_email, email_updated_at)
VALUES (@accountKey, @accountEmail, @emailUpdatedAt)
ON CONFLICT(account_key) DO UPDATE SET
  account_email = excluded.account_email,
  email_updated_at = MAX(excluded.email_updated_at, team_accounts.email_updated_at)
`;

const SELECT_ACCOUNT_EMAILS_SQL = `
SELECT account_key AS accountKey, account_email AS accountEmail
FROM team_accounts
`;

/**
 * O e-mail de **uma** conta, para a autorizacao de cada requisicao.
 *
 * Irma de [SELECT_ACCOUNT_EMAILS_SQL], que le a tabela inteira porque monta uma
 * tela. Aqui a pergunta e sobre a conta do pedido, e varrer todas para responder
 * sobre uma so cresceria com o time a cada chamada.
 */
const SELECT_ACCOUNT_EMAIL_SQL = `
SELECT account_email AS accountEmail FROM team_accounts WHERE account_key = @accountKey
`;

/**
 * Apaga somente os turnos da sessao que ainda pertence ao device informado.
 *
 * O subselect impede um identificador de maquina obsoleto de apagar uma sessao
 * que foi associada a outra maquina pelo ingest mais recente.
 */
const DELETE_SESSION_TURNS_SQL = `
DELETE FROM team_turns
 WHERE account_key = @accountKey
   AND session_id IN (
     SELECT session_id FROM team_sessions
      WHERE account_key = @accountKey
        AND device_id = @deviceId
        AND session_id = @sessionId
   )
`;

const DELETE_SESSION_SQL = `
DELETE FROM team_sessions
 WHERE account_key = @accountKey
   AND device_id = @deviceId
   AND session_id = @sessionId
`;

/**
 * Os mesmos tres deletes, sem o recorte por maquina.
 *
 * Aqui os turnos nao precisam do subselect sobre `team_sessions`: sem filtro de
 * `device_id`, `account_key` sozinho ja identifica tudo o que sai. A ordem
 * continua importando pelo mesmo motivo — apagar as sessoes antes deixaria os
 * turnos sem de onde ser deduzidos se o filtro voltasse a depender delas.
 */
const DELETE_ACCOUNT_TURNS_SQL = `
DELETE FROM team_turns WHERE account_key = @accountKey
`;

const DELETE_ACCOUNT_SESSIONS_SQL = `
DELETE FROM team_sessions WHERE account_key = @accountKey
`;

const DELETE_ACCOUNT_MEMBERS_SQL = `
DELETE FROM team_members WHERE account_key = @accountKey
`;

const DELETE_ACCOUNT_METADATA_SQL = `
DELETE FROM team_accounts WHERE account_key = @accountKey
`;

export class TeamRepository {
  private readonly db: Db;

  constructor(db: Db) {
    this.db = db;
  }

  /**
   * Carimba `last_seen_at` do integrante com o relogio do servidor.
   *
   * Mesma instrucao do ingest ([UPSERT_MEMBER_SQL]): o heartbeat nao pode ter
   * uma segunda regra de upsert que divirja dela — o `COALESCE` do host_name e
   * o `MAX` do `last_seen_at` valem aqui pelos mesmos motivos.
   *
   * O e-mail reportado e o membro sao gravados na mesma transacao. Passar por
   * [ingest] com arrays vazios prepararia `UPSERT_SESSION` e `INSERT_TURN` para
   * nada e devolveria um recibo que mente.
   */
  touchMember(
    accountKey: string,
    member: IngestMember,
    now: number,
    accountEmail: string | null = null,
  ): void {
    const run = this.db.transaction(() => {
      this.upsertAccountEmail(accountKey, accountEmail, now);
      this.db.prepare(UPSERT_MEMBER_SQL).run({
        accountKey,
        deviceId: member.deviceId,
        alias: member.alias,
        hostName: member.hostName,
        organizationUuid: member.organizationUuid,
        organizationName: member.organizationName,
        lastSeenAt: now,
      });
    });

    run();
  }

  /**
   * Ultimo e-mail que a conta reportou, ou `null` se ela nunca reportou nenhum.
   *
   * Confiavel como memoria porque [upsertAccountEmail] nunca sobrescreve com
   * nulo: uma vez reportado, o valor so muda para outro e-mail valido. E o que
   * permite a autorizacao das **leituras** — que nao carregam e-mail no pedido —
   * conferirem a mesma coisa que o ingest confere.
   */
  accountEmailOf(accountKey: string): string | null {
    const row = this.db.prepare(SELECT_ACCOUNT_EMAIL_SQL).get({ accountKey }) as
      | { accountEmail: string }
      | undefined;
    return row?.accountEmail ?? null;
  }

  /** Grava membro, sessoes e turnos numa transacao unica. Idempotente. */
  ingest(payload: IngestPayload, now: number): IngestReceipt {
    const upsertMember = this.db.prepare(UPSERT_MEMBER_SQL);
    const upsertSession = this.db.prepare(UPSERT_SESSION_SQL);
    const insertTurn = this.db.prepare(INSERT_TURN_SQL);

    const run = this.db.transaction((): IngestReceipt => {
      this.upsertAccountEmail(payload.accountKey, payload.accountEmail ?? null, now);
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

  /**
   * Remove uma sessao e os turnos dela, preservando o integrante e as demais
   * sessoes. Idempotente e irreversivel: o cliente de origem ja marcou os
   * turnos antigos como enviados e so atividade futura pode recriar a sessao.
   */
  deleteSession(accountKey: string, deviceId: string, sessionId: string): DeleteSessionReport {
    const params = { accountKey, deviceId, sessionId };

    const run = this.db.transaction((): DeleteSessionReport => {
      const deletedTurns = this.db.prepare(DELETE_SESSION_TURNS_SQL).run(params).changes;
      const deletedSessions = this.db.prepare(DELETE_SESSION_SQL).run(params).changes;
      return { deletedTurns, deletedSessions };
    });

    return run();
  }

  /**
   * Remove uma conta inteira: todos os integrantes, sessoes e turnos dela.
   *
   * Existe para a conta que a empresa deixou de usar — alguem trocou de conta
   * Anthropic e a antiga ficou na visao global com os integrantes de antes.
   * Desvincular a chave nao resolvia: a agregacao parte de `team_members` e
   * `team_turns`, entao a conta continuava na lista, so que sem rotulo.
   *
   * Nao toca em `team_key_accounts` — o vinculo e da camada de chaves, e quem
   * compoe as duas e a rota. Idempotente, e **destrutivo e irreversivel** pela
   * mesma razao de [deleteMember]: as maquinas ja marcaram os turnos como
   * enviados no marcador local e nao os reenviam.
   */
  deleteAccount(accountKey: string): DeleteAccountReport {
    const params = { accountKey };

    const run = this.db.transaction((): DeleteAccountReport => {
      const deletedTurns = this.db.prepare(DELETE_ACCOUNT_TURNS_SQL).run(params).changes;
      const deletedSessions = this.db.prepare(DELETE_ACCOUNT_SESSIONS_SQL).run(params).changes;
      const deletedMembers = this.db.prepare(DELETE_ACCOUNT_MEMBERS_SQL).run(params).changes;
      this.db.prepare(DELETE_ACCOUNT_METADATA_SQL).run(params);
      return { deletedTurns, deletedSessions, deletedMembers };
    });

    return run();
  }

  /**
   * Snapshot de uma conta no recorte `[since, until)`.
   *
   * Os dois sao opcionais: nulos devolvem tudo o que sobreviveu a retencao. O
   * intervalo e semiaberto porque duas janelas adjacentes nao podem contar duas
   * vezes o turno que cai exatamente na fronteira.
   */
  readTeam(
    accountKey: string,
    since: number | null,
    until: number | null,
    gapCutoffMs: number,
  ): TeamSnapshot {
    const members = this.db.prepare(SELECT_MEMBERS_SQL).all({ accountKey }) as TeamMemberRow[];
    const rows = this.db
      .prepare(SELECT_USAGE_SQL)
      .all({ accountKey, since, until }) as TeamUsageRow[];
    const activity = this.db
      .prepare(SELECT_SESSION_ACTIVITY_SQL)
      .all({ accountKey, since, until, gapCutoffMs }) as TeamSessionActivityRow[];
    return { members, rows, activity };
  }

  /**
   * Serie diaria de uma conta a partir de `since`.
   *
   * Os integrantes vem junto para a tela nomear as linhas sem uma segunda
   * chamada — e para uma maquina que existe mas nao consumiu no periodo
   * aparecer com serie vazia, em vez de sumir.
   */
  readTrend(accountKey: string, since: number, until: number | null): TeamTrend {
    const members = this.db.prepare(SELECT_MEMBERS_SQL).all({ accountKey }) as TeamMemberRow[];
    const raw = this.db.prepare(SELECT_TREND_SQL).all({ accountKey, since, until }) as Array<
      Omit<TeamTrendRow, 'dayStartMillis'> & { dayBucket: number }
    >;

    const rows = raw.map(({ dayBucket, ...rest }) => ({
      ...rest,
      dayStartMillis: dayBucket * MILLIS_PER_DAY,
    }));

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
  readOverview(
    since: number | null,
    until: number | null,
    labels: Map<string, string>,
    gapCutoffMs: number,
  ): TeamAccountSnapshot[] {
    const memberRows = this.db.prepare(SELECT_ALL_MEMBERS_SQL).all() as Array<
      TeamMemberRow & { accountKey: string }
    >;
    const usageRows = this.db.prepare(SELECT_ALL_USAGE_SQL).all({ since, until }) as Array<
      TeamUsageRow & { accountKey: string }
    >;
    const activityRows = this.db.prepare(SELECT_ALL_SESSION_ACTIVITY_SQL).all({
      since,
      until,
      gapCutoffMs,
    }) as Array<TeamSessionActivityRow & { accountKey: string }>;
    const reportedEmails = new Map(
      (
        this.db.prepare(SELECT_ACCOUNT_EMAILS_SQL).all() as Array<{
          accountKey: string;
          accountEmail: string;
        }>
      ).map((row) => [row.accountKey, row.accountEmail]),
    );

    const byAccount = new Map<string, TeamAccountSnapshot>();

    const ensure = (accountKey: string): TeamAccountSnapshot => {
      const existing = byAccount.get(accountKey);
      if (existing !== undefined) {
        return existing;
      }
      const label = labels.get(accountKey) ?? null;
      const reportedEmail = reportedEmails.get(accountKey) ?? null;
      const provisionalEmail = normalizeAccountEmail(label);
      const created: TeamAccountSnapshot = {
        accountKey,
        label,
        accountEmail: reportedEmail ?? provisionalEmail,
        emailSource: reportedEmail !== null ? 'reported' : provisionalEmail !== null ? 'label' : null,
        members: [],
        rows: [],
        activity: [],
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

    for (const row of activityRows) {
      const { accountKey, ...activity } = row;
      ensure(accountKey).activity.push(activity);
    }

    return [...byAccount.values()];
  }

  /**
   * Linhas planas de consumo para o consumidor externo, uma pagina por vez.
   *
   * Rota nova e plana em vez de paginar `/admin/v1/overview`: aquele monta a tela
   * do app, cujo `flattenAccounts`/`toUsageBreakdown` assume resposta completa —
   * uma pagina parcial subestimaria os totais sem erro nenhum.
   */
  readReportUsage(options: {
    since: number | null;
    until: number | null;
    limit: number;
    cursor: ReportCursor | null;
  }): ReportPage<ReportUsageRow> {
    const rows = this.db.prepare(SELECT_REPORT_USAGE_SQL).all({
      since: options.since,
      until: options.until,
      limit: options.limit + 1,
      ...cursorParams(options.cursor),
    }) as ReportUsageRow[];

    return toPage(rows, options.limit, (row) => ({
      accountKey: row.accountKey,
      deviceId: row.deviceId,
      sessionId: row.sessionId,
      model: row.model ?? '',
    }));
  }

  /**
   * Tempo de trabalho por sessao, uma pagina por vez.
   *
   * Sessao sem intervalo medido nao aparece — e a mesma promessa de
   * [readTeam]: `null` seria "nao medido" e zero seria "medido e sem intervalo",
   * e uma linha inventada com zero afirmaria a segunda coisa.
   */
  readReportActivity(options: {
    since: number | null;
    until: number | null;
    gapCutoffMs: number;
    limit: number;
    cursor: ReportCursor | null;
  }): ReportPage<ReportActivityRow> {
    const rows = this.db.prepare(SELECT_REPORT_ACTIVITY_SQL).all({
      since: options.since,
      until: options.until,
      gapCutoffMs: options.gapCutoffMs,
      limit: options.limit + 1,
      ...cursorParams(options.cursor),
    }) as ReportActivityRow[];

    return toPage(rows, options.limit, (row) => ({
      accountKey: row.accountKey,
      deviceId: row.deviceId,
      sessionId: row.sessionId,
      // A ordem desta consulta nao tem modelo; o campo viaja vazio para o cursor
      // ter uma forma so.
      model: '',
    }));
  }

  /**
   * Contas e integrantes, sem consumo e sem paginacao.
   *
   * O tamanho da resposta e o tamanho do time, nao o do historico: paginar aqui
   * seria cursor para uma lista que cabe numa tela.
   */
  readReportMembers(labels: Map<string, string>): ReportAccountMembers[] {
    const memberRows = this.db.prepare(SELECT_ALL_MEMBERS_SQL).all() as Array<
      TeamMemberRow & { accountKey: string }
    >;
    const reportedEmails = new Map(
      (
        this.db.prepare(SELECT_ACCOUNT_EMAILS_SQL).all() as Array<{
          accountKey: string;
          accountEmail: string;
        }>
      ).map((row) => [row.accountKey, row.accountEmail]),
    );

    const byAccount = new Map<string, ReportAccountMembers>();
    const ensure = (accountKey: string): ReportAccountMembers => {
      const existing = byAccount.get(accountKey);
      if (existing !== undefined) {
        return existing;
      }
      const label = labels.get(accountKey) ?? null;
      const reportedEmail = reportedEmails.get(accountKey) ?? null;
      const provisionalEmail = normalizeAccountEmail(label);
      const created: ReportAccountMembers = {
        accountKey,
        label,
        accountEmail: reportedEmail ?? provisionalEmail,
        emailSource: reportedEmail !== null ? 'reported' : provisionalEmail !== null ? 'label' : null,
        members: [],
      };
      byAccount.set(accountKey, created);
      return created;
    };

    // Conta que so aparece em `team_key_accounts` — chave emitida, maquina ainda
    // nao enviou nada — entra na lista sem integrante, em vez de sumir.
    for (const accountKey of labels.keys()) {
      ensure(accountKey);
    }

    for (const row of memberRows) {
      const { accountKey, ...member } = row;
      ensure(accountKey).members.push(member);
    }

    return [...byAccount.values()].sort((left, right) =>
      left.accountKey.localeCompare(right.accountKey),
    );
  }

  private upsertAccountEmail(accountKey: string, accountEmail: string | null, now: number): void {
    const normalized = normalizeAccountEmail(accountEmail);
    if (normalized === null) {
      return;
    }

    this.db.prepare(UPSERT_ACCOUNT_EMAIL_SQL).run({
      accountKey,
      accountEmail: normalized,
      emailUpdatedAt: now,
    });
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

/**
 * Parametros do cursor, com `null` significando "primeira pagina".
 *
 * Os quatro viajam sempre juntos: o SQL testa apenas `@cursorAccountKey IS NULL`
 * e usaria os outros tres em row-value, que nao tolera mistura de nulos.
 */
function cursorParams(cursor: ReportCursor | null): {
  cursorAccountKey: string | null;
  cursorDeviceId: string | null;
  cursorSessionId: string | null;
  cursorModel: string | null;
} {
  if (cursor === null) {
    return {
      cursorAccountKey: null,
      cursorDeviceId: null,
      cursorSessionId: null,
      cursorModel: null,
    };
  }
  return {
    cursorAccountKey: cursor.accountKey,
    cursorDeviceId: cursor.deviceId,
    cursorSessionId: cursor.sessionId,
    cursorModel: cursor.model,
  };
}

/**
 * Corta o item extra e monta o cursor a partir da ultima linha **mantida**.
 *
 * A consulta pede `limit + 1`: e a presenca desse extra, e nao a pagina estar
 * cheia, que prova haver proxima pagina. Sem ele, a ultima pagina exata
 * devolveria um cursor que abriria uma pagina vazia.
 */
function toPage<T>(rows: T[], limit: number, toCursor: (row: T) => ReportCursor): ReportPage<T> {
  if (rows.length <= limit) {
    return { rows, nextCursor: null };
  }
  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  return { rows: page, nextCursor: last === undefined ? null : toCursor(last) };
}
