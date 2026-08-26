import { describe, expect, it } from 'vitest';
import { MIN_TEAM_API_KEY_LENGTH, loadConfigFromEnv } from '../../src/config.js';

const VALID_KEY = 'x'.repeat(MIN_TEAM_API_KEY_LENGTH);

describe('loadConfigFromEnv', () => {
  it('aplica os defaults com apenas a chave definida', () => {
    const config = loadConfigFromEnv({ TEAM_API_KEY: VALID_KEY } as NodeJS.ProcessEnv);

    expect(config.port).toBe(3000);
    expect(config.dataDir).toBe('./data');
    expect(config.retentionDays).toBe(45);
    expect(config.trustProxyHops).toBe(0);
  });

  it('falha sem nenhum segredo', () => {
    expect(() => loadConfigFromEnv({} as NodeJS.ProcessEnv)).toThrow(/TEAM_ADMIN_TOKEN/);
  });

  it('sobe so com administracao, sem a chave legada', () => {
    const config = loadConfigFromEnv({
      TEAM_ADMIN_TOKEN: VALID_KEY,
      TEAM_KEY_SECRET: VALID_KEY,
    } as NodeJS.ProcessEnv);

    expect(config.teamApiKey).toBeNull();
    expect(config.adminToken).toBe(VALID_KEY);
    // Sem escolha explicita o modo legado nasce aberto: um deploy que so
    // acrescenta administracao nao pode cortar os clientes que ja existiam.
    expect(config.legacyKeyMode).toBe('open');
  });

  it('falha com TEAM_ADMIN_TOKEN curto', () => {
    expect(() =>
      loadConfigFromEnv({
        TEAM_ADMIN_TOKEN: 'curto',
        TEAM_KEY_SECRET: VALID_KEY,
      } as NodeJS.ProcessEnv),
    ).toThrow(/TEAM_ADMIN_TOKEN/);
  });

  it('falha com administracao sem TEAM_KEY_SECRET', () => {
    expect(() =>
      loadConfigFromEnv({ TEAM_ADMIN_TOKEN: VALID_KEY } as NodeJS.ProcessEnv),
    ).toThrow(/TEAM_KEY_SECRET/);
  });

  it('le TEAM_REPORT_TOKEN quando presente', () => {
    const config = loadConfigFromEnv({
      TEAM_API_KEY: VALID_KEY,
      TEAM_REPORT_TOKEN: VALID_KEY,
    } as NodeJS.ProcessEnv);

    expect(config.reportToken).toBe(VALID_KEY);
  });

  it('nasce sem token de relatorio', () => {
    const config = loadConfigFromEnv({ TEAM_API_KEY: VALID_KEY } as NodeJS.ProcessEnv);

    expect(config.reportToken).toBeNull();
  });

  // Um servidor que so publica relatorio e nao aceita cliente nenhum nao tem o
  // que relatar: o token de relatorio NAO conta como o segredo que falta.
  it('nao aceita TEAM_REPORT_TOKEN como unico segredo', () => {
    expect(() =>
      loadConfigFromEnv({ TEAM_REPORT_TOKEN: VALID_KEY } as NodeJS.ProcessEnv),
    ).toThrow(/TEAM_ADMIN_TOKEN/);
  });

  it('falha com TEAM_REPORT_TOKEN curto', () => {
    expect(() =>
      loadConfigFromEnv({
        TEAM_API_KEY: VALID_KEY,
        TEAM_REPORT_TOKEN: 'curto',
      } as NodeJS.ProcessEnv),
    ).toThrow(/TEAM_REPORT_TOKEN/);
  });

  it('falha com TEAM_LEGACY_KEY_MODE invalido', () => {
    expect(() =>
      loadConfigFromEnv({
        TEAM_API_KEY: VALID_KEY,
        TEAM_LEGACY_KEY_MODE: 'talvez',
      } as NodeJS.ProcessEnv),
    ).toThrow(/TEAM_LEGACY_KEY_MODE/);
  });

  it('aceita TEAM_LEGACY_KEY_MODE=off', () => {
    const config = loadConfigFromEnv({
      TEAM_API_KEY: VALID_KEY,
      TEAM_LEGACY_KEY_MODE: 'off',
    } as NodeJS.ProcessEnv);

    expect(config.legacyKeyMode).toBe('off');
  });

  it('falha com TEAM_API_KEY curta', () => {
    expect(() => loadConfigFromEnv({ TEAM_API_KEY: 'curta' } as NodeJS.ProcessEnv)).toThrow(
      /TEAM_API_KEY/,
    );
  });

  it('falha com PORT nao numerica', () => {
    expect(() =>
      loadConfigFromEnv({ TEAM_API_KEY: VALID_KEY, PORT: '3000abc' } as NodeJS.ProcessEnv),
    ).toThrow(/PORT/);
  });

  it('falha com TEAM_RETENTION_DAYS zero', () => {
    expect(() =>
      loadConfigFromEnv({ TEAM_API_KEY: VALID_KEY, TEAM_RETENTION_DAYS: '0' } as NodeJS.ProcessEnv),
    ).toThrow(/TEAM_RETENTION_DAYS/);
  });

  it('falha com TRUST_PROXY_HOPS negativo', () => {
    expect(() =>
      loadConfigFromEnv({ TEAM_API_KEY: VALID_KEY, TRUST_PROXY_HOPS: '-1' } as NodeJS.ProcessEnv),
    ).toThrow(/TRUST_PROXY_HOPS/);
  });
});
