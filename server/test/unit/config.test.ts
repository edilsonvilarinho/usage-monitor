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

  it('falha sem TEAM_API_KEY', () => {
    expect(() => loadConfigFromEnv({} as NodeJS.ProcessEnv)).toThrow(/TEAM_API_KEY/);
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
