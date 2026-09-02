/**
 * Versao do servidor, publicada em `usage_monitor_build_info`.
 *
 * Constante em codigo, e nao leitura do `package.json` em runtime: o
 * `Dockerfile.dokploy` copia so `server/tsconfig.json` e `server/src`, e o
 * `dist/` teria de resolver um caminho relativo para fora dele para achar o
 * arquivo. O preco e um **segundo dono do numero**, e por isso
 * `test/unit/version.test.ts` compara os dois e reprova divergencia — mesmo
 * desenho da guarda de paridade da tabela de precos.
 *
 * Sobe junto com o `package.json` a cada mudanca de contrato da API.
 */
export const SERVER_VERSION = '0.12.0';
