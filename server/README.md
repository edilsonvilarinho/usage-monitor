# Servidor de time — Usage Monitor

Recebe os turnos do Claude Code indexados por cada máquina e devolve a visão agregada por conta Anthropic. É o que alimenta o modal **Sessões do time** do app desktop.

Self-hosted: a empresa que usa a integração opera este servidor. Não há serviço gerenciado.

> **Build file:** [`Dockerfile.dokploy`](../Dockerfile.dokploy), na **raiz** do repositório, com contexto na raiz. Compose em [`docker/docker-compose.yml`](../docker/docker-compose.yml). Ver [Deploy no Dokploy](#deploy-no-dokploy).

## O que trafega

Apenas metadados de uso — **nenhum conteúdo de prompt ou resposta**:

`sessionId`, `messageId`, `ts`, `model`, contagens de token, `cwd`, `gitBranch`, `hostName`, `alias`, `accountUuid`.

O `cwd` e o `gitBranch` revelam nomes de projeto e de branch. É o que permite o detalhamento por sessão dentro do modal.

## Stack

Node 20 · Express 4 · TypeScript ESM · SQLite (`better-sqlite3`) · zod · pino · vitest + supertest.

Um arquivo SQLite em `${DATA_DIR}/team-usage.sqlite`. Sem serviço de banco separado.

## Desenvolvimento

```bash
cd server
npm install
cp .env.example .env    # preencha TEAM_API_KEY
npm run dev             # http://localhost:3000
```

```bash
npm test          # vitest
npm run typecheck # tsc --noEmit
npm run build     # dist/
```

Verificação rápida:

```bash
curl http://localhost:3000/api/health
# {"status":"ok"}
```

## Configuração

Todas as variáveis estão documentadas em [`.env.example`](.env.example). As duas que importam:

| Variável | Obrigatória | Default | Nota |
|---|---|---|---|
| `TEAM_API_KEY` | sim | — | Mínimo 32 caracteres. Boot falha abaixo disso. |
| `DATA_DIR` | não | `./data` | `/data` no container. |
| `PORT` | não | `3000` | |
| `TEAM_RETENTION_DAYS` | não | `45` | |
| `TEAM_MAX_TURNS_PER_REQUEST` | não | `5000` | |
| `TRUST_PROXY_HOPS` | não | `0` | `1` atrás do Traefik do Dokploy. |
| `LOG_LEVEL` | não | `info` | |

Gerar a chave:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
```

## API

Base `/api`. Todas as rotas exigem o header `x-team-key`, exceto o healthcheck.

A comparação da chave é em tempo constante (SHA-256 + `timingSafeEqual`).

### `GET /api/health`

Sem autenticação. Executa um `SELECT 1` no banco — um volume desmontado derruba o healthcheck em vez de responder `ok` com o servidor inútil.

```json
{ "status": "ok" }
```

### `POST /api/v1/ingest`

Idempotente. A chave primária `(account_key, session_id, message_id)` com `INSERT OR IGNORE` faz o reenvio do mesmo lote ser inofensivo — é a mesma dedup que o índice local do desktop usa.

```jsonc
{
  "accountKey": "<accountUuid da conta Anthropic>",
  "member": {
    "deviceId": "<uuid estável por instalação>",
    "alias": "edilson",
    "hostName": "DESKTOP-A1",
    "organizationUuid": null,
    "organizationName": null
  },
  "sessions": [
    {
      "sessionId": "a3f9c1e2-...",
      "cwd": "/home/dev/api-gateway",
      "gitBranch": "main",
      "firstTs": 1786000000000,
      "lastTs": 1786003600000,
      "liveContextTokens": 120000,
      "liveContextModel": "claude-opus-4-20250514"
    }
  ],
  "turns": [
    {
      "sessionId": "a3f9c1e2-...",
      "messageId": "msg_01ABC",
      "ts": 1786003600000,
      "model": "claude-opus-4-20250514",
      "isSidechain": false,
      "inputTokens": 100,
      "outputTokens": 200,
      "cacheReadTokens": 300,
      "cacheWrite5mTokens": 400,
      "cacheWrite1hTokens": 0
    }
  ]
}
```

Resposta:

```json
{ "acceptedTurns": 1, "ignoredTurns": 0, "acceptedSessions": 1 }
```

**Invariante do lote:** todo `turn.sessionId` tem de estar em `sessions` no mesmo corpo. A leitura faz `JOIN team_sessions`; um turno órfão seria gravado e nunca apareceria. O servidor rejeita o lote com `400` em vez de aceitar dado invisível.

Timestamps em **epoch millis**. Os tokens são o **delta do turno**, não o acumulado da sessão.

### `GET /api/v1/team`

| Query | Obrigatório | Nota |
|---|---|---|
| `accountKey` | sim | Escopo da resposta. Uma resposta nunca mistura contas. |
| `since` | não | Epoch millis. Ausente = tudo o que sobreviveu à retenção. |

```jsonc
{
  "members": [
    { "deviceId": "device-1", "alias": "edilson", "hostName": "DESKTOP-A1",
      "organizationUuid": null, "organizationName": null, "lastSeenAt": 1786003600000 }
  ],
  "rows": [
    { "deviceId": "device-1", "sessionId": "a3f9c1e2-...", "cwd": "...", "gitBranch": "main",
      "liveContextTokens": 120000, "liveContextModel": "claude-opus-4-20250514",
      "model": "claude-opus-4-20250514", "turnCount": 12,
      "firstTs": 1786000000000, "lastTs": 1786003600000,
      "inputTokens": 0, "outputTokens": 0, "cacheReadTokens": 0,
      "cacheWrite5mTokens": 0, "cacheWrite1hTokens": 0 }
  ]
}
```

Duas propriedades desenhadas de propósito:

- **O recorte incide sobre os turnos, não sobre as sessões.** Uma sessão de dez dias atrás com um turno nas últimas 5h aparece com os tokens desse turno, não com o total histórico. É a mesma semântica do filtro local.
- **Uma linha por `(deviceId, sessionId, model)`.** Uma sessão que trocou de modelo no meio precisa ser precificada com a tarifa de cada trecho.

**O servidor não calcula custo.** Devolve tokens por modelo; o cliente aplica a própria tabela de preços (`ModelPricingTable`). Assim a tabela não é duplicada aqui e o custo do modal de time acompanha as atualizações do app.

`members` traz todos os membros da conta, inclusive quem não teve atividade na janela — quem não consumiu é informação, não ruído.

### Erros

`{ "error": "<mensagem>", "code": "<código>" }` com `400` (`validation_error`), `401` (`unauthorized`), `404` (`not_found`), `503` (`service_unavailable`) ou `500` (`internal_error`).

## Retenção

`TEAM_RETENTION_DAYS` (default 45). Roda no boot e a cada 6h: apaga turnos fora do horizonte, depois sessões sem turno, depois membros antigos sem sessão. Uma falha na limpeza é logada e **não** derruba o servidor.

## Modelo de segurança

Chave **compartilhada** por todo o time, validada em tempo constante. O escopo de leitura é o `accountKey` que o cliente declara.

**Limite aceito e conhecido:** quem tem a chave pode consultar qualquer `accountUuid` que conheça. Não há prova de posse do token OAuth da conta. É adequado a um servidor interno; não exponha este serviço na internet aberta sem uma camada de rede na frente.

Trocar a chave invalida todos os clientes de uma vez — não há revogação individual por dispositivo.

## Docker

O build file é **`Dockerfile.dokploy`, na raiz do repositório** — mesmo padrão do `montador-pacote`. O contexto é a raiz, e o `.dockerignore` de lá restringe tudo a `server/`: sem isso o `build/` do Gradle, com o runtime Java empacotado, entraria no contexto.

```bash
# a partir da raiz do repositório
docker build -f Dockerfile.dokploy -t usage-monitor-team .
docker run -d -p 3000:3000 -v team-data:/data -e TEAM_API_KEY=... usage-monitor-team
```

Ou via compose:

```bash
cd docker
cp ../server/.env.example .env    # preencha TEAM_API_KEY
docker compose up --build
docker inspect --format '{{.State.Health.Status}}' <container>   # healthy
```

O `HEALTHCHECK` está no Dockerfile e no compose. O processo roda como `node` (uid 1000), não como root. Com **volume nomeado** as permissões vêm da imagem; com **bind mount**, o diretório do host precisa pertencer ao uid 1000.

## Deploy no Dokploy

1. **Create Service → Application.**
2. **Provider:** o repositório Git deste projeto, branch `main`.
3. **Build Type:** `Dockerfile`.
   - **Dockerfile Path:** `Dockerfile.dokploy`
   - **Docker Context Path:** `.` (raiz — deixe em branco se o Dokploy já usar a raiz)

   O Dockerfile faz `COPY server/package.json ./`, relativo à raiz. O `.dockerignore` da raiz nega tudo e reinclui só `server/`, então o contexto enviado ao daemon fica pequeno mesmo com o repositório inteiro em volta.
4. **Environment:** cole o conteúdo de `server/.env.example` preenchido. Obrigatória: `TEAM_API_KEY`. Recomendadas em produção: `TRUST_PROXY_HOPS=1`, `DATA_DIR=/data`.
5. **Volumes:** monte um volume em `/data`.

   | Tipo | Host / Nome | Container |
   |---|---|---|
   | Volume Mount | `team-data` | `/data` |

   Sem isso o SQLite vive na camada gravável do container e some a cada redeploy.
6. **Ports / Domains:** porta interna `3000`. Publique um domínio e deixe o Traefik do Dokploy terminar o TLS.
7. **Deploy.** Confira `GET https://<dominio>/api/health`.
8. **Auto Deploy:** ative o webhook na branch `main` se quiser redeploy a cada push.

Rodando em **Docker Swarm**, mantenha **1 réplica**: o SQLite é um arquivo local e duas réplicas em nós diferentes veriam bancos distintos. Se o cluster tiver mais de um nó, fixe uma constraint de nó para o volume seguir o serviço.

### Configurar os clientes

Em cada máquina, no app desktop: **Configurações → Integração com time** → ligar, informar a URL (`https://<dominio>`), colar a `TEAM_API_KEY`, definir o alias e marcar as contas Anthropic que participam.

## Inspecionar os dados

```bash
sqlite3 data/team-usage.sqlite "SELECT alias, host_name, last_seen_at FROM team_members;"
sqlite3 data/team-usage.sqlite "SELECT COUNT(*) FROM team_turns;"
```
