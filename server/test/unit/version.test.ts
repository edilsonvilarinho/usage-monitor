import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { SERVER_VERSION } from '../../src/version.js';

/**
 * `SERVER_VERSION` e um **segundo dono** do numero da versao.
 *
 * O primeiro e o `package.json`; a constante existe porque o `Dockerfile.dokploy`
 * copia so `server/tsconfig.json` e `server/src`, e ler o arquivo em runtime
 * obrigaria o `dist/` a resolver um caminho para fora dele. O preco da duplicacao
 * e este teste — mesmo desenho da guarda de paridade da tabela de precos, que
 * tambem tem dois donos por uma razao de empacotamento.
 */
describe('versao do servidor', () => {
  it('bate com o package.json', () => {
    const packageJsonPath = fileURLToPath(new URL('../../package.json', import.meta.url));
    const pkg = JSON.parse(readFileSync(packageJsonPath, 'utf8')) as { version: string };

    expect(SERVER_VERSION).toBe(pkg.version);
  });

  it('e semver de tres partes', () => {
    expect(SERVER_VERSION).toMatch(/^\d+\.\d+\.\d+$/);
  });
});
