# Servidor de time: deploy no Dokploy e certificado expirado (#489)

## Objetivo

A issue junta dois sintomas do servidor de time: o deploy no Dokploy falha no `npm install` e o app não lê
presença nem sessões do time (`certificate_expired`, `PKIX path validation failed`). São duas causas
independentes; só a primeira é código deste repositório.

## Fatos confirmados

### Build do Dokploy

- Log da issue: `RUN npm install --no-audit --no-fund` no `Dockerfile.dokploy:33` aborta com
  `npm error Cannot read properties of null (reading 'edgesOut')`, npm 10.8.2 (o do `node:20-bookworm-slim`).
- Reproduzido fora do Docker com `server/package.json` sozinho (o Dockerfile não copia o lockfile):
  - Node 20.20.2 + npm 10.8.2: falha idêntica, com e sem `--os=linux --cpu=x64 --libc=glibc`. Stack em
    `@npmcli/arborist` `build-ideal-tree.js` `#loadPeerSet`, logo depois de colocar `vitest@5.0.5`, `vite@8.3.5`
    e `rolldown@1.2.15`.
  - Node 22 + npm 10.8.2: `npm install` (também simulando Linux x64 glibc), `npm run build`,
    `npm prune --omit=dev` e o `import("better-sqlite3")` passam.
- `vitest@5.0.5` declara `engines.node` `^22.12.0 || ^24.0.0 || >=26.0.0`; `@vitest/istanbul-lib-*` `>=22`.
  Entraram com o bump do vitest 3 → 5 (#348). O pacote declarava `>=20`.
- O CI não pegou: o job `server` usa `npm ci` com o lockfile, e nada construía a imagem do Dokploy.

### Certificado (fora do repositório)

- `lab.informata.com.br:5175` é um nginx do host (`/etc/nginx/conf.d/cad-estrutura-armazem-5175.conf`) que faz
  proxy para o contêiner do servidor; cert `/etc/letsencrypt/live/lab.informata.com.br/` (`cert2.pem`),
  `notAfter=Oct 27 15:21:43 2026 GMT`. O mesmo cert serve a 5173.
- Não há cron nem timer de renovação; `renewal.conf` usa `authenticator = standalone`.
- A porta 80 pública não responde (timeout em três nós externos); a 5175 responde. HTTP-01 não funciona.
- `_acme-challenge.lab.informata.com.br` é CNAME para `accceecb-….auth.acme-dns.io`: o cert de julho saiu por
  DNS-01 via acme-dns, fora deste servidor. As credenciais acme-dns não estão no servidor.
- O cliente está correto ao recusar o cert vencido; nada a mudar no app. Renovação fica com o responsável pela
  infraestrutura (DNS da Locaweb ou credencial acme-dns); passo a passo na issue.

## Decisões

- **Node 22 na imagem, no CI e no `engines`** (`>=22.12.0`), em vez de fixar o vitest numa versão que aceite
  Node 20: Node 20 está fora de suporte desde abril de 2026, e o devDependency só expôs a defasagem.
- **O job `server` constrói a imagem do Dokploy** (`docker build --file Dockerfile.dokploy .`): é o único
  caminho que instala sem lockfile e com o Node da imagem, que é exatamente o que quebrou. O filtro de path
  passa a tratar `Dockerfile.dokploy` e `.dockerignore` como mudança do servidor.
- **Lockfile continua fora da imagem** (npm/cli#4828); não muda.

## Atividades

| ID | Atividade | Commit |
|---|---|---|
| A01 | Node 22 no Dockerfile, CI e `engines`; build da imagem no CI; plano | `fix(server): build the Dokploy image on Node 22 (#489)` |

## Pontos de situação

| ID | Data | Modelo | Comando | Resultado |
|---|---|---|---|---|
| A01 | 2026-10-27 | Claude Opus 5.5 | `npx -p node@20 -p npm@10.8.2 -c 'npm install --no-audit --no-fund'` (só `package.json`) | falha `edgesOut` — reproduz a issue |
| A01 | 2026-10-27 | Claude Opus 5.5 | `npx -p node@22 -p npm@10.8.2 -c 'npm install … && npm run build && npm prune --omit=dev'` | passa; `better-sqlite3` carrega |
| A01 | 2026-10-27 | Claude Opus 5.5 | `cd server && npm ci && npm run typecheck && npm test` (Node 22.22.1) | typecheck limpo; 24 arquivos, 313 testes passam |
| A01 | 2026-10-27 | Claude Opus 5.5 | CI do PR #491, run `40615671222` | `ci-ok` verde; job `server`: `Build Dokploy image` (Node 22, sem lockfile, Linux) passa |
