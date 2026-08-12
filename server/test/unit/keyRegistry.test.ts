import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { KeyCipher, loadOrCreateCipherSalt } from '../../src/crypto/keyCipher.js';
import { openDatabase, type Db } from '../../src/db/openDatabase.js';
import { TeamKeyRepository, hashKey } from '../../src/repositories/teamKeyRepository.js';

const SECRET = 'segredo-de-teste-0123456789abcdefg';
const NOW = Date.UTC(2026, 7, 11, 12, 0, 0);

describe('TeamKeyRepository', () => {
  let dataDir: string;
  let db: Db;
  let repository: TeamKeyRepository;

  beforeEach(() => {
    dataDir = mkdtempSync(join(tmpdir(), 'usage-monitor-keys-'));
    db = openDatabase(dataDir);
    repository = new TeamKeyRepository(db, new KeyCipher(SECRET, loadOrCreateCipherSalt(db)));
  });

  afterEach(() => {
    db.close();
    rmSync(dataDir, { recursive: true, force: true });
  });

  it('cria a chave sem nenhuma conta vinculada', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);

    // O vinculo so nasce no primeiro ingest: e o que permite emitir a chave sem
    // ninguem descobrir o accountUuid da pessoa antes.
    expect(created.accounts).toEqual([]);
    expect(created.key.length).toBeGreaterThan(32);
    expect(created.keyPrefix).toBe(created.key.slice(0, 8));
  });

  it('resolve pela chave crua e devolve a chave decifrada na listagem', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);

    expect(repository.resolve(hashKey(created.key))?.id).toBe(created.id);
    expect(repository.list()[0]?.key).toBe(created.key);
  });

  it('vincula a conta no primeiro uso e mantem o vinculo', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);

    expect(repository.claimAccount(created.id, 'conta-a', NOW)).toBe(true);
    expect(repository.resolve(hashKey(created.key))?.accounts).toEqual(['conta-a']);
    expect(repository.ownerOf('conta-a')).toBe(created.id);
  });

  it('recusa conta que ja pertence a outra chave', () => {
    const primeira = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);
    const segunda = repository.create({ label: 'sicrano@empresa.com', maxAccounts: 1 }, NOW);

    repository.claimAccount(primeira.id, 'conta-a', NOW);

    // Invariante do isolamento: uma conta tem no maximo uma dona. Quem garante e
    // o indice unico, nao uma checagem previa que uma corrida derrubaria.
    expect(repository.claimAccount(segunda.id, 'conta-a', NOW)).toBe(false);
  });

  it('libera a conta depois de desfazer o vinculo', () => {
    const primeira = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);
    const segunda = repository.create({ label: 'sicrano@empresa.com', maxAccounts: 1 }, NOW);
    repository.claimAccount(primeira.id, 'conta-a', NOW);

    expect(repository.unclaimAccount(primeira.id, 'conta-a')).toBe(true);
    expect(repository.ownerOf('conta-a')).toBeNull();
    expect(repository.claimAccount(segunda.id, 'conta-a', NOW)).toBe(true);
  });

  it('nao resolve chave revogada', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);

    repository.revoke(created.id, NOW);

    expect(repository.resolve(hashKey(created.key))).toBeNull();
  });

  it('regerar troca a chave crua, mantem os vinculos e reativa a revogada', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);
    repository.claimAccount(created.id, 'conta-a', NOW);
    repository.revoke(created.id, NOW);

    const regenerated = repository.regenerate(created.id);

    expect(regenerated?.key).not.toBe(created.key);
    expect(regenerated?.accounts).toEqual(['conta-a']);
    expect(regenerated?.revokedAt).toBeNull();
    // A chave antiga para de valer na requisicao seguinte — e o que torna isto
    // um conserto util para chave vazada.
    expect(repository.resolve(hashKey(created.key))).toBeNull();
    expect(repository.resolve(hashKey(regenerated?.key ?? ''))?.id).toBe(created.id);
  });

  it('rotula as contas pelo label da chave dona', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 2 }, NOW);
    repository.claimAccount(created.id, 'conta-a', NOW);

    const labels = repository.accountLabels();

    expect(labels.get('conta-a')).toBe('fulano@empresa.com');
    expect(labels.has('conta-sem-chave')).toBe(false);
  });

  it('marca o ultimo uso', () => {
    const created = repository.create({ label: 'fulano@empresa.com', maxAccounts: 1 }, NOW);

    repository.touch(created.id, NOW + 5_000);

    expect(repository.findById(created.id)?.lastUsedAt).toBe(NOW + 5_000);
  });
});
