import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { KeyCipher, loadOrCreateCipherSalt } from '../../src/crypto/keyCipher.js';
import { openDatabase, type Db } from '../../src/db/openDatabase.js';

const SECRET = 'segredo-de-teste-0123456789abcdefg';

describe('KeyCipher', () => {
  let dataDir: string;
  let db: Db;

  beforeEach(() => {
    dataDir = mkdtempSync(join(tmpdir(), 'usage-monitor-cipher-'));
    db = openDatabase(dataDir);
  });

  afterEach(() => {
    db.close();
    rmSync(dataDir, { recursive: true, force: true });
  });

  it('decifra o que cifrou', () => {
    const cipher = new KeyCipher(SECRET, loadOrCreateCipherSalt(db));

    const sealed = cipher.seal('chave-crua-do-time');

    expect(sealed.cipher).not.toContain('chave-crua-do-time');
    expect(cipher.open(sealed)).toBe('chave-crua-do-time');
  });

  it('produz envelopes diferentes para o mesmo texto', () => {
    const cipher = new KeyCipher(SECRET, loadOrCreateCipherSalt(db));

    // IV aleatorio por chamada: duas chaves iguais nao podem gerar o mesmo
    // ciphertext, senao a coluna denunciaria a repeticao.
    expect(cipher.seal('mesmo-valor').cipher).not.toBe(cipher.seal('mesmo-valor').cipher);
  });

  it('recusa envelope adulterado', () => {
    const cipher = new KeyCipher(SECRET, loadOrCreateCipherSalt(db));
    const sealed = cipher.seal('chave-crua-do-time');

    const forged = { ...sealed, tag: Buffer.alloc(16).toString('base64') };

    expect(() => cipher.open(forged)).toThrow();
  });

  it('nao decifra com outro segredo', () => {
    const sealed = new KeyCipher(SECRET, loadOrCreateCipherSalt(db)).seal('chave-crua-do-time');
    const outro = new KeyCipher('outro-segredo-0123456789abcdefghi', loadOrCreateCipherSalt(db));

    expect(() => outro.open(sealed)).toThrow();
  });

  it('reusa o salt gravado entre aberturas', () => {
    const first = loadOrCreateCipherSalt(db);
    const sealed = new KeyCipher(SECRET, first).seal('chave-crua-do-time');
    db.close();

    // Reabrir o banco tem de devolver o mesmo salt: se ele fosse regerado, toda
    // chave ja emitida viraria lixo ilegivel apos um restart.
    db = openDatabase(dataDir);
    const second = loadOrCreateCipherSalt(db);

    expect(second.equals(first)).toBe(true);
    expect(new KeyCipher(SECRET, second).open(sealed)).toBe('chave-crua-do-time');
  });
});
