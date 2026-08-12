import { createHash, timingSafeEqual } from 'node:crypto';

export const TEAM_KEY_HEADER = 'x-team-key';

/**
 * Compara a credencial recebida com a esperada em tempo constante.
 *
 * As duas passam por SHA-256 antes: `timingSafeEqual` exige buffers do mesmo
 * tamanho e lanca se diferirem, o que por si so ja vazaria o tamanho da chave.
 *
 * Vale tanto para a chave legada de ambiente quanto para o token de admin — sao
 * segredos configurados, comparados diretamente. As chaves emitidas no banco nao
 * passam por aqui: elas sao localizadas por hash indexado.
 */
export function isValidTeamKey(received: string | undefined, expected: string): boolean {
  if (!received) {
    return false;
  }
  const receivedDigest = createHash('sha256').update(received).digest();
  const expectedDigest = createHash('sha256').update(expected).digest();
  return timingSafeEqual(receivedDigest, expectedDigest);
}
