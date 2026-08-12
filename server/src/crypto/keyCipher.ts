import { createDecipheriv, createCipheriv, randomBytes, scryptSync } from 'node:crypto';
import type { Db } from '../db/openDatabase.js';

/** Envelope de um valor cifrado, como ele fica gravado em `team_keys`. */
export interface SealedValue {
  cipher: string;
  iv: string;
  tag: string;
}

const ALGORITHM = 'aes-256-gcm';
const KEY_LENGTH = 32;
const IV_LENGTH = 12;
const SALT_LENGTH = 16;
const SALT_META_KEY = 'key_cipher_salt';

const SELECT_META_SQL = 'SELECT value FROM server_meta WHERE key = ?';
const INSERT_META_SQL = 'INSERT OR IGNORE INTO server_meta (key, value) VALUES (?, ?)';

/**
 * Cifra as chaves de time para que o admin possa rele-las depois de criadas.
 *
 * O par que autentica continua sendo o `key_hash`: nenhuma requisicao passa por
 * aqui. Isto serve so a listagem administrativa, e a consequencia importa —
 * perder o `TEAM_KEY_SECRET` **nao derruba nenhum cliente**, apenas torna a
 * chave ilegivel no modal, e o conserto e regerar a chave.
 *
 * AES-256-GCM em vez de AES-CBC: sem o `authTag` um banco adulterado devolveria
 * lixo decifrado sem nenhum erro, e o admin copiaria uma chave invalida.
 */
export class KeyCipher {
  private readonly derivedKey: Buffer;

  constructor(secret: string, salt: Buffer) {
    this.derivedKey = scryptSync(secret, salt, KEY_LENGTH);
  }

  seal(plainText: string): SealedValue {
    const iv = randomBytes(IV_LENGTH);
    const cipher = createCipheriv(ALGORITHM, this.derivedKey, iv);
    const sealed = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);

    return {
      cipher: sealed.toString('base64'),
      iv: iv.toString('base64'),
      tag: cipher.getAuthTag().toString('base64'),
    };
  }

  open(value: SealedValue): string {
    const decipher = createDecipheriv(ALGORITHM, this.derivedKey, Buffer.from(value.iv, 'base64'));
    decipher.setAuthTag(Buffer.from(value.tag, 'base64'));

    const opened = Buffer.concat([
      decipher.update(Buffer.from(value.cipher, 'base64')),
      decipher.final(),
    ]);

    return opened.toString('utf8');
  }
}

/**
 * Le o salt de derivacao do banco, criando-o na primeira vez.
 *
 * O salt fica no banco, e nao em variavel de ambiente, porque ele acompanha os
 * dados que protege: um `DATA_DIR` restaurado de backup volta com o salt certo
 * sem ninguem precisar lembrar de copiar mais um valor junto.
 *
 * O `INSERT OR IGNORE` seguido de nova leitura cobre a corrida de dois processos
 * subindo ao mesmo tempo: quem perde a corrida le o salt de quem ganhou, em vez
 * de sobrescrever e tornar ilegivel o que ja foi cifrado.
 */
export function loadOrCreateCipherSalt(db: Db): Buffer {
  const existing = db.prepare(SELECT_META_SQL).get(SALT_META_KEY) as { value: string } | undefined;
  if (existing !== undefined) {
    return Buffer.from(existing.value, 'base64');
  }

  const created = randomBytes(SALT_LENGTH).toString('base64');
  db.prepare(INSERT_META_SQL).run(SALT_META_KEY, created);

  const stored = db.prepare(SELECT_META_SQL).get(SALT_META_KEY) as { value: string };
  return Buffer.from(stored.value, 'base64');
}
