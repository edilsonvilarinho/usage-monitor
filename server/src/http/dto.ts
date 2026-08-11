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
 * `since` fica opcional em vez de anulavel com default: `z.coerce` roda antes de
 * qualquer default e transformaria a ausencia do parametro em `NaN`.
 */
export const teamQuerySchema = z.object({
  accountKey: z.string().min(1).max(TEXT_MAX),
  since: z.coerce.number().int().nonnegative().optional(),
});
