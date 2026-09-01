import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { KeyCipher, loadOrCreateCipherSalt } from '../../src/crypto/keyCipher.js';
import { auditKeyLabels } from '../../src/db/keyLabelAudit.js';
import { openDatabase, type Db } from '../../src/db/openDatabase.js';
import { TeamKeyRepository } from '../../src/repositories/teamKeyRepository.js';
import { TeamRepository } from '../../src/repositories/teamRepository.js';

const SECRET = 'segredo-de-teste-0123456789abcdefg';
const NOW = Date.UTC(2026, 8, 1, 12, 0, 0);

describe('auditKeyLabels', () => {
  let dataDir: string;
  let db: Db;
  let keys: TeamKeyRepository;
  let repository: TeamRepository;

  beforeEach(() => {
    dataDir = mkdtempSync(join(tmpdir(), 'usage-monitor-audit-'));
    db = openDatabase(dataDir);
    keys = new TeamKeyRepository(db, new KeyCipher(SECRET, loadOrCreateCipherSalt(db)));
    repository = new TeamRepository(db);
  });

  afterEach(() => {
    db.close();
    rmSync(dataDir, { recursive: true, force: true });
  });

  function link(label: string, accountKey: string, accountEmail: string | null): string {
    const key = keys.create({ label, maxAccounts: 10 }, NOW);
    keys.claimAccount(key.id, accountKey, NOW);
    if (accountEmail !== null) {
      repository.touchMember(
        accountKey,
        { deviceId: 'device-1', alias: 'quem', hostName: null, organizationUuid: null, organizationName: null },
        NOW,
        accountEmail,
      );
    }
    return key.id;
  }

  it('nao aponta nada num servidor coerente', () => {
    link('helio@empresa.com', 'conta-a', 'helio@empresa.com');
    link('Chave do setor', 'conta-b', 'qualquer@gmail.com');
    link('sem-envio@empresa.com', 'conta-c', null);

    expect(auditKeyLabels(db)).toEqual([]);
  });

  it('aponta o vinculo divergente com as duas pontas', () => {
    const keyId = link('helio@empresa.com', 'conta-a', 'pessoal@gmail.com');

    expect(auditKeyLabels(db)).toEqual([
      {
        keyId,
        label: 'helio@empresa.com',
        accountKey: 'conta-a',
        accountEmail: 'pessoal@gmail.com',
      },
    ]);
  });

  // Chave revogada ja nao autentica nada: apontar um vinculo dela seria ruido
  // sobre um problema que nao existe.
  it('ignora vinculo de chave revogada', () => {
    const keyId = link('helio@empresa.com', 'conta-a', 'pessoal@gmail.com');
    keys.revoke(keyId, NOW);

    expect(auditKeyLabels(db)).toEqual([]);
  });
});
