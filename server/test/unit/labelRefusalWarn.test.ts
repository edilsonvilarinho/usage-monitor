import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Config } from '../../src/config.js';
import { ForbiddenError } from '../../src/domain/errors.js';
import { assertAllowedByLabel, type AccessDeps } from '../../src/http/access.js';
import { logger } from '../../src/logger.js';
import type { ResolvedTeamKey, TeamKeyRepository } from '../../src/repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../src/repositories/teamRepository.js';

const T0 = Date.UTC(2026, 10, 12, 12, 0, 0);
const HOUR = 60 * 60 * 1000;
const LABEL_MESSAGE = 'conta fora da relacao declarada no rotulo da chave';

describe('assertAllowedByLabel: aviso deduplicado por chave e conta', () => {
  let now = T0;

  afterEach(() => {
    vi.restoreAllMocks();
    now = T0;
  });

  function deps(): AccessDeps {
    return {
      config: { keyLabelMatch: 'strict' } as Config,
      keyRepository: {} as TeamKeyRepository,
      repository: { accountEmailOf: () => null } as unknown as TeamRepository,
      now: () => now,
    };
  }

  /** Recusa a conta pelo rotulo e confere que o 403 sai em toda chamada. */
  function refuse(keyId: string, accountKey: string): void {
    const resolved = { id: keyId, label: 'helio@empresa.com' } as ResolvedTeamKey;
    expect(() => assertAllowedByLabel(deps(), resolved, accountKey, 'pessoal@gmail.com')).toThrow(ForbiddenError);
  }

  function labelWarnings(spy: ReturnType<typeof vi.spyOn>): unknown[][] {
    return spy.mock.calls.filter((call) => call[1] === LABEL_MESSAGE);
  }

  it('loga uma vez e as repeticoes dentro de 1 h seguem 403 em silencio', () => {
    const spy = vi.spyOn(logger, 'warn');

    refuse('key-dedup-1', 'conta-1');
    refuse('key-dedup-1', 'conta-1');
    refuse('key-dedup-1', 'conta-1');

    expect(labelWarnings(spy)).toHaveLength(1);
  });

  it('a primeira recusa apos a janela sai com a contagem das suprimidas', () => {
    const spy = vi.spyOn(logger, 'warn');

    refuse('key-dedup-2', 'conta-1');
    refuse('key-dedup-2', 'conta-1');
    refuse('key-dedup-2', 'conta-1');
    now = T0 + HOUR;
    refuse('key-dedup-2', 'conta-1');

    const warnings = labelWarnings(spy);
    expect(warnings).toHaveLength(2);
    expect(warnings[1][0]).toMatchObject({ keyId: 'key-dedup-2', accountKey: 'conta-1', suppressed: 2 });
  });

  it('chave ou conta diferente tem aviso proprio', () => {
    const spy = vi.spyOn(logger, 'warn');

    refuse('key-dedup-3', 'conta-1');
    refuse('key-dedup-4', 'conta-1');
    refuse('key-dedup-4', 'conta-2');

    expect(labelWarnings(spy)).toHaveLength(3);
  });
});
