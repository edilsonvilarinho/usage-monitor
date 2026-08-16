import { createHash, randomBytes, randomUUID } from 'node:crypto';
import type { KeyCipher } from '../crypto/keyCipher.js';
import type { Db } from '../db/openDatabase.js';

/** Chave de time como o modal do admin a ve: crua, com os vinculos ao lado. */
export interface TeamKeyRecord {
  id: string;
  label: string;
  key: string;
  keyPrefix: string;
  maxAccounts: number;
  accounts: string[];
  createdAt: number;
  revokedAt: number | null;
  lastUsedAt: number | null;
}

/** O minimo que a autorizacao precisa saber. Nunca traz a chave crua. */
export interface ResolvedTeamKey {
  id: string;
  maxAccounts: number;
  accounts: string[];
}

export interface CreateTeamKeyInput {
  label: string;
  maxAccounts: number;
}

export interface UpdateTeamKeyInput {
  label?: string;
  maxAccounts?: number;
}

/** Numero de caracteres do prefixo exibido. So identifica; nao autentica. */
const KEY_PREFIX_LENGTH = 8;

/** 32 bytes aleatorios: mesma forca da chave que o README manda gerar a mao. */
const KEY_BYTES = 32;

const INSERT_KEY_SQL = `
INSERT INTO team_keys (
  id, key_hash, key_prefix, key_cipher, key_iv, key_tag,
  label, max_accounts, created_at, revoked_at, last_used_at
) VALUES (
  @id, @keyHash, @keyPrefix, @keyCipher, @keyIv, @keyTag,
  @label, @maxAccounts, @createdAt, NULL, NULL
)
`;

/**
 * Resolucao por hash, ignorando revogada.
 *
 * `revoked_at IS NULL` no `WHERE` e o que faz a revogacao valer na requisicao
 * seguinte, sem cache a invalidar em lugar nenhum.
 */
const SELECT_BY_HASH_SQL = `
SELECT id, max_accounts AS maxAccounts
FROM team_keys
WHERE key_hash = @keyHash AND revoked_at IS NULL
`;

const SELECT_KEYS_SQL = `
SELECT id, key_prefix AS keyPrefix, key_cipher AS keyCipher, key_iv AS keyIv,
       key_tag AS keyTag, label, max_accounts AS maxAccounts,
       created_at AS createdAt, revoked_at AS revokedAt, last_used_at AS lastUsedAt
FROM team_keys
ORDER BY label COLLATE NOCASE ASC, created_at ASC
`;

const SELECT_KEY_BY_ID_SQL = `
SELECT id, key_prefix AS keyPrefix, key_cipher AS keyCipher, key_iv AS keyIv,
       key_tag AS keyTag, label, max_accounts AS maxAccounts,
       created_at AS createdAt, revoked_at AS revokedAt, last_used_at AS lastUsedAt
FROM team_keys
WHERE id = @id
`;

const SELECT_ACCOUNTS_SQL = `
SELECT account_key AS accountKey FROM team_key_accounts
WHERE key_id = @keyId
ORDER BY claimed_at ASC
`;

const SELECT_ALL_ACCOUNTS_SQL = `
SELECT key_id AS keyId, account_key AS accountKey FROM team_key_accounts
ORDER BY claimed_at ASC
`;

const SELECT_ACCOUNT_OWNER_SQL = `
SELECT key_id AS keyId FROM team_key_accounts WHERE account_key = @accountKey
`;

/**
 * Rotulo de cada conta, para a visao global do admin.
 *
 * `LEFT JOIN` porque conta reivindicada no modo legado nao tem chave nenhuma:
 * ela existe nos dados e precisa aparecer na lista, ainda que so pelo UUID.
 */
const SELECT_ACCOUNT_LABELS_SQL = `
SELECT a.account_key AS accountKey, k.label AS label
FROM team_key_accounts a
LEFT JOIN team_keys k ON k.id = a.key_id
`;

const INSERT_ACCOUNT_SQL = `
INSERT INTO team_key_accounts (key_id, account_key, claimed_at)
VALUES (@keyId, @accountKey, @claimedAt)
`;

const DELETE_ACCOUNT_SQL = `
DELETE FROM team_key_accounts WHERE key_id = @keyId AND account_key = @accountKey
`;

/**
 * Mesmo delete, sem saber de que chave a conta e.
 *
 * Quem apaga uma conta inteira nao tem o `key_id` em maos — a visao global
 * mostra contas, nao chaves — e exigi-lo obrigaria a varrer a lista de chaves
 * atras da dona antes de cada remocao. O indice unico
 * `idx_team_key_accounts_account` garante que isso apague no maximo uma linha.
 */
const DELETE_ACCOUNT_ANYWHERE_SQL = `
DELETE FROM team_key_accounts WHERE account_key = @accountKey
`;

const UPDATE_KEY_SQL = `
UPDATE team_keys SET label = @label, max_accounts = @maxAccounts WHERE id = @id
`;

const REGENERATE_KEY_SQL = `
UPDATE team_keys
   SET key_hash = @keyHash, key_prefix = @keyPrefix, key_cipher = @keyCipher,
       key_iv = @keyIv, key_tag = @keyTag, revoked_at = NULL
 WHERE id = @id
`;

const REVOKE_KEY_SQL = 'UPDATE team_keys SET revoked_at = @revokedAt WHERE id = @id';

const TOUCH_KEY_SQL = 'UPDATE team_keys SET last_used_at = @lastUsedAt WHERE id = @id';

/** Gera o material de uma chave nova. `base64url` para caber numa URL e num header. */
export function generateRawKey(): string {
  return randomBytes(KEY_BYTES).toString('base64url');
}

export function hashKey(rawKey: string): string {
  return createHash('sha256').update(rawKey).digest('hex');
}

interface KeyRow {
  id: string;
  keyPrefix: string;
  keyCipher: string;
  keyIv: string;
  keyTag: string;
  label: string;
  maxAccounts: number;
  createdAt: number;
  revokedAt: number | null;
  lastUsedAt: number | null;
}

/**
 * Chaves de time e os vinculos com as contas Anthropic.
 *
 * A chave nasce sem conta e se amarra a primeira que ela usar num ingest. E o
 * unico jeito de o admin emitir uma chave sem antes descobrir o `accountUuid` da
 * pessoa — o app so expoe o e-mail da conta, nunca o UUID.
 */
export class TeamKeyRepository {
  private readonly db: Db;
  private readonly cipher: KeyCipher;

  constructor(db: Db, cipher: KeyCipher) {
    this.db = db;
    this.cipher = cipher;
  }

  /** Devolve `null` para chave desconhecida ou revogada — os dois viram 401. */
  resolve(keyHash: string): ResolvedTeamKey | null {
    const row = this.db.prepare(SELECT_BY_HASH_SQL).get({ keyHash }) as
      | { id: string; maxAccounts: number }
      | undefined;

    if (row === undefined) {
      return null;
    }

    return { id: row.id, maxAccounts: row.maxAccounts, accounts: this.accountsOf(row.id) };
  }

  /**
   * Amarra uma conta a chave. `false` quando a conta ja e de outra.
   *
   * A decisao vem do indice unico em `team_key_accounts(account_key)`, e nao de
   * um `SELECT` antes do `INSERT`: com dois ingests simultaneos da mesma conta,
   * a checagem previa aprovaria os dois e o segundo gravaria um vinculo
   * duplicado. Aqui o banco recusa e a resposta e um 403 honesto.
   */
  claimAccount(keyId: string, accountKey: string, now: number): boolean {
    try {
      this.db.prepare(INSERT_ACCOUNT_SQL).run({ keyId, accountKey, claimedAt: now });
      return true;
    } catch (error) {
      if (isConstraintViolation(error)) {
        return false;
      }
      throw error;
    }
  }

  /**
   * Id da chave dona de uma conta, ou `null` se ninguem a reivindicou.
   *
   * O indice unico garante no maximo uma dona. Serve a `/v1/verify`, que precisa
   * responder "esta conta e de outra chave" **antes** do ingest — dizer que esta
   * autorizada e falhar depois seria pior que recusar agora.
   */
  ownerOf(accountKey: string): string | null {
    const row = this.db.prepare(SELECT_ACCOUNT_OWNER_SQL).get({ accountKey }) as
      | { keyId: string }
      | undefined;
    return row?.keyId ?? null;
  }

  unclaimAccount(keyId: string, accountKey: string): boolean {
    const result = this.db.prepare(DELETE_ACCOUNT_SQL).run({ keyId, accountKey });
    return result.changes > 0;
  }

  /** Solta a conta de qualquer chave. `false` quando ninguem a tinha. */
  unclaimAccountAnywhere(accountKey: string): boolean {
    const result = this.db.prepare(DELETE_ACCOUNT_ANYWHERE_SQL).run({ accountKey });
    return result.changes > 0;
  }

  create(input: CreateTeamKeyInput, now: number): TeamKeyRecord {
    const rawKey = generateRawKey();
    const sealed = this.cipher.seal(rawKey);
    const id = randomUUID();

    this.db.prepare(INSERT_KEY_SQL).run({
      id,
      keyHash: hashKey(rawKey),
      keyPrefix: rawKey.slice(0, KEY_PREFIX_LENGTH),
      keyCipher: sealed.cipher,
      keyIv: sealed.iv,
      keyTag: sealed.tag,
      label: input.label,
      maxAccounts: input.maxAccounts,
      createdAt: now,
    });

    return {
      id,
      label: input.label,
      key: rawKey,
      keyPrefix: rawKey.slice(0, KEY_PREFIX_LENGTH),
      maxAccounts: input.maxAccounts,
      accounts: [],
      createdAt: now,
      revokedAt: null,
      lastUsedAt: null,
    };
  }

  list(): TeamKeyRecord[] {
    const rows = this.db.prepare(SELECT_KEYS_SQL).all() as KeyRow[];
    const links = this.db.prepare(SELECT_ALL_ACCOUNTS_SQL).all() as Array<{
      keyId: string;
      accountKey: string;
    }>;

    // Um SELECT para todos os vinculos, e nao um por chave: a lista do admin e
    // pequena, mas a consulta em laco vira N+1 sem nenhum ganho de clareza.
    const accountsByKey = new Map<string, string[]>();
    for (const link of links) {
      const current = accountsByKey.get(link.keyId);
      if (current === undefined) {
        accountsByKey.set(link.keyId, [link.accountKey]);
      } else {
        current.push(link.accountKey);
      }
    }

    return rows.map((row) => this.toRecord(row, accountsByKey.get(row.id) ?? []));
  }

  findById(id: string): TeamKeyRecord | null {
    const row = this.db.prepare(SELECT_KEY_BY_ID_SQL).get({ id }) as KeyRow | undefined;
    if (row === undefined) {
      return null;
    }
    return this.toRecord(row, this.accountsOf(id));
  }

  /** Devolve `null` quando o id nao existe; quem chama traduz para 404. */
  update(id: string, patch: UpdateTeamKeyInput): TeamKeyRecord | null {
    const current = this.findById(id);
    if (current === null) {
      return null;
    }

    this.db.prepare(UPDATE_KEY_SQL).run({
      id,
      label: patch.label ?? current.label,
      maxAccounts: patch.maxAccounts ?? current.maxAccounts,
    });

    return this.findById(id);
  }

  /**
   * Troca a chave crua mantendo os vinculos.
   *
   * Serve a dois casos: o admin perdeu a chave e precisa de uma nova para a
   * mesma pessoa, ou a chave vazou. Nos dois o time ja formado nao pode se
   * desfazer, entao `team_key_accounts` fica intacto e a chave antiga para de
   * valer na requisicao seguinte, porque o `key_hash` mudou.
   *
   * Tambem limpa `revoked_at`: regerar uma chave revogada e o caminho natural
   * para reativar um integrante sem recriar o vinculo dele na mao.
   */
  regenerate(id: string): TeamKeyRecord | null {
    const current = this.findById(id);
    if (current === null) {
      return null;
    }

    const rawKey = generateRawKey();
    const sealed = this.cipher.seal(rawKey);

    this.db.prepare(REGENERATE_KEY_SQL).run({
      id,
      keyHash: hashKey(rawKey),
      keyPrefix: rawKey.slice(0, KEY_PREFIX_LENGTH),
      keyCipher: sealed.cipher,
      keyIv: sealed.iv,
      keyTag: sealed.tag,
    });

    return this.findById(id);
  }

  revoke(id: string, now: number): TeamKeyRecord | null {
    const current = this.findById(id);
    if (current === null) {
      return null;
    }
    this.db.prepare(REVOKE_KEY_SQL).run({ id, revokedAt: now });
    return this.findById(id);
  }

  /**
   * Marca uso. Chamado so no caminho de ingest, que ja e escrita.
   *
   * Marcar tambem na leitura pareceria mais completo e custaria uma escrita em
   * WAL a cada 5s por janela de time aberta, sem informacao nova: quem le nao e
   * necessariamente quem a chave representa.
   */
  touch(id: string, now: number): void {
    this.db.prepare(TOUCH_KEY_SQL).run({ id, lastUsedAt: now });
  }

  /** `accountKey -> label`, para a visao global rotular cada conta. */
  accountLabels(): Map<string, string> {
    const rows = this.db.prepare(SELECT_ACCOUNT_LABELS_SQL).all() as Array<{
      accountKey: string;
      label: string | null;
    }>;

    const labels = new Map<string, string>();
    for (const row of rows) {
      if (row.label !== null) {
        labels.set(row.accountKey, row.label);
      }
    }
    return labels;
  }

  private accountsOf(keyId: string): string[] {
    const rows = this.db.prepare(SELECT_ACCOUNTS_SQL).all({ keyId }) as Array<{
      accountKey: string;
    }>;
    return rows.map((row) => row.accountKey);
  }

  private toRecord(row: KeyRow, accounts: string[]): TeamKeyRecord {
    return {
      id: row.id,
      label: row.label,
      key: this.cipher.open({ cipher: row.keyCipher, iv: row.keyIv, tag: row.keyTag }),
      keyPrefix: row.keyPrefix,
      maxAccounts: row.maxAccounts,
      accounts,
      createdAt: row.createdAt,
      revokedAt: row.revokedAt,
      lastUsedAt: row.lastUsedAt,
    };
  }
}

/** `better-sqlite3` sinaliza violacao de unicidade pelo prefixo do codigo. */
function isConstraintViolation(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    typeof (error as { code: unknown }).code === 'string' &&
    (error as { code: string }).code.startsWith('SQLITE_CONSTRAINT')
  );
}
