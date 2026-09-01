import { isAccountAllowedByLabel } from '../domain/teamKeyLabel.js';
import type { Db } from './openDatabase.js';

/** Vinculo que o portao do rotulo recusaria na proxima requisicao. */
export interface KeyLabelMismatch {
  keyId: string;
  label: string;
  accountKey: string;
  accountEmail: string | null;
}

/**
 * Chave revogada fica de fora: ela ja nao autentica nada, e apontar um vinculo
 * dela seria ruido sobre um problema que nao existe.
 */
const SELECT_LINKS_SQL = `
SELECT k.id AS keyId, k.label AS label, a.account_key AS accountKey,
       e.account_email AS accountEmail
FROM team_key_accounts a
JOIN team_keys k ON k.id = a.key_id
LEFT JOIN team_accounts e ON e.account_key = a.account_key
WHERE k.revoked_at IS NULL
ORDER BY k.label COLLATE NOCASE ASC, a.account_key ASC
`;

/**
 * Vinculos ja existentes que o portao do rotulo recusa.
 *
 * O portao vale retroativamente — e essa e a decisao que o torna util, porque a
 * conta intrusa ja estava vinculada quando ele foi escrito. O preco e que subir
 * o servidor pode cortar quem sincronizava ontem, e sem esta varredura o
 * operador so descobriria quem foi cortado pela reclamacao da pessoa.
 *
 * Roda uma vez, no arranque, e nao decide nada: quem recusa e o
 * [assertAllowedByLabel] de cada requisicao. Duplicar a regra aqui daria duas
 * respostas para a mesma pergunta, entao a avaliacao e a mesma funcao pura.
 */
export function auditKeyLabels(db: Db): KeyLabelMismatch[] {
  const rows = db.prepare(SELECT_LINKS_SQL).all() as KeyLabelMismatch[];
  return rows.filter((row) => !isAccountAllowedByLabel(row.label, row.accountEmail));
}
