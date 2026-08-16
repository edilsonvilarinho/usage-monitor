import { z } from 'zod';

const TEXT_MAX = 400;

/** Timestamps chegam em epoch millis, iguais aos que o indice local grava. */
const epochMillis = z.number().int().nonnegative();

const tokenCount = z.number().int().nonnegative().default(0);

const nullableText = z.string().max(TEXT_MAX).nullable().default(null);

export const memberSchema = z.object({
  deviceId: z.string().min(1).max(TEXT_MAX),
  alias: z.string().trim().min(1).max(60),
  hostName: nullableText,
  organizationUuid: nullableText,
  organizationName: nullableText,
});

export const sessionSchema = z.object({
  sessionId: z.string().min(1).max(TEXT_MAX),
  cwd: nullableText,
  gitBranch: nullableText,
  firstTs: epochMillis,
  lastTs: epochMillis,
  liveContextTokens: tokenCount,
  liveContextModel: nullableText,
});

export const turnSchema = z.object({
  sessionId: z.string().min(1).max(TEXT_MAX),
  messageId: z.string().min(1).max(TEXT_MAX),
  ts: epochMillis,
  model: nullableText,
  isSidechain: z.boolean().default(false),
  inputTokens: tokenCount,
  outputTokens: tokenCount,
  cacheReadTokens: tokenCount,
  cacheWrite5mTokens: tokenCount,
  cacheWrite1hTokens: tokenCount,
});

/**
 * O limite de turnos por requisicao e configuravel porque ele define o tamanho
 * do lote de backfill do cliente: o app envia em lotes e o servidor precisa
 * aceitar pelo menos um lote inteiro.
 */
export function createIngestSchema(maxTurnsPerRequest: number) {
  return z.object({
    accountKey: z.string().min(1).max(TEXT_MAX),
    member: memberSchema,
    sessions: z.array(sessionSchema).max(maxTurnsPerRequest).default([]),
    turns: z.array(turnSchema).max(maxTurnsPerRequest).default([]),
  });
}

export type IngestRequestBody = z.infer<ReturnType<typeof createIngestSchema>>;

/**
 * Batida de presenca: "esta maquina esta com o app aberto agora".
 *
 * Reusa [memberSchema] em vez de declarar os campos de novo — e exatamente o
 * membro do ingest, sem sessoes nem turnos, e um schema paralelo divergiria
 * dele no primeiro campo novo de identidade.
 *
 * Sem nenhum campo de timestamp, de proposito: o `last_seen_at` sai do relogio
 * do servidor. Aceitar o do cliente deixaria uma maquina se declarar
 * eternamente online.
 */
export const presenceBodySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
  member: memberSchema,
});

export type PresenceRequestBody = z.infer<typeof presenceBodySchema>;

/**
 * `since` fica opcional em vez de anulavel com default: `z.coerce` roda antes de
 * qualquer default e transformaria a ausencia do parametro em `NaN`.
 */
export const teamQuerySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
  since: z.coerce.number().int().nonnegative().optional(),
});

/**
 * Parametros na query, e nao no corpo: `DELETE` com body e mal suportado por
 * proxy reverso, e o deploy padrao daqui fica atras de um.
 */
export const deleteMemberQuerySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
  deviceId: z.string().min(1).max(TEXT_MAX),
});

/** A conta e o unico parametro: a credencial vem no header. */
export const verifyQuerySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
});

/** Mesmo escopo do verify, mas no corpo: `POST /v1/claim` cria vinculo. */
export const claimBodySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
});

/** Recorte opcional da visao global. Mesma semantica do `since` de `/v1/team`. */
export const overviewQuerySchema = z.object({
  since: z.coerce.number().int().nonnegative().optional(),
});

/**
 * Teto de contas por chave.
 *
 * O default `1` implementa "uma chave, um time" sem obrigar o admin a pensar
 * nisso; subir para 2 e o caminho da maquina logada em duas contas da empresa,
 * que usa a mesma chave para as duas.
 */
const maxAccounts = z.number().int().min(1).max(50);

export const createKeyBodySchema = z.object({
  label: z.string().trim().min(1).max(TEXT_MAX),
  maxAccounts: maxAccounts.default(1),
});

/**
 * `refine` em vez de campos opcionais soltos: um `PATCH` sem nenhum campo
 * passaria na validacao e responderia 200 sem ter mudado nada.
 */
export const updateKeyBodySchema = z
  .object({
    label: z.string().trim().min(1).max(TEXT_MAX).optional(),
    maxAccounts: maxAccounts.optional(),
  })
  .refine(
    (value) => value.label !== undefined || value.maxAccounts !== undefined,
    'informe label ou maxAccounts',
  );

/**
 * Detalhe de uma sessao.
 *
 * O `deviceId` e obrigatorio junto do `sessionId`: o escopo de uma leitura e
 * sempre `(conta, maquina)`, como no resto da API. Sem ele a rota responderia a
 * sessao de outra maquina da mesma conta a partir do id sozinho.
 */
/**
 * Janela da serie diaria, em dias.
 *
 * O teto existe porque a resposta cresce com dias x maquinas x modelos, e um
 * `days=100000` viraria uma varredura da tabela inteira a pedido de qualquer
 * portador de chave.
 */
export const trendQuerySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
  days: z.coerce.number().int().positive().max(365).optional(),
});

export const sessionQuerySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
  sessionId: z.string().min(1).max(TEXT_MAX),
  deviceId: z.string().min(1).max(TEXT_MAX),
});
