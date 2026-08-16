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

Todas as variáveis estão documentadas em [`.env.example`](.env.example).

| Variável | Obrigatória | Default | Nota |
|---|---|---|---|
| `TEAM_ADMIN_TOKEN` | ver nota | — | Mínimo 32 caracteres. Presente, monta `/api/admin/*` e vale como credencial de leitura de qualquer conta. |
| `TEAM_KEY_SECRET` | com admin | — | Mínimo 32 caracteres. Cifra as chaves emitidas em repouso. |
| `TEAM_API_KEY` | ver nota | — | Mínimo 32 caracteres. Chave única **legada**. |
| `TEAM_LEGACY_KEY_MODE` | não | `open` | `open` mantém a chave legada lendo tudo; `off` a rejeita. |
| `DATA_DIR` | não | `./data` | `/data` no container. |
| `PORT` | não | `3000` | |
| `TEAM_RETENTION_DAYS` | não | `45` | |
| `TEAM_MAX_TURNS_PER_REQUEST` | não | `5000` | |
| `TRUST_PROXY_HOPS` | não | `0` | `1` atrás do Traefik do Dokploy. |
| `LOG_LEVEL` | não | `info` | |

**O boot falha se `TEAM_ADMIN_TOKEN` e `TEAM_API_KEY` estiverem ambos ausentes** — sem nenhum dos dois o servidor não teria como autenticar cliente nem como emitir a primeira chave.

Gerar qualquer um dos segredos:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
```

## Chaves por time

Disponível a partir da versão **0.3.0**.

Cada pessoa recebe uma chave própria, emitida pelo app desktop de quem administra. A chave nasce **sem conta** e se amarra à primeira conta Anthropic que ela usar num ingest. É o que permite emitir sem antes descobrir o `accountUuid` de ninguém — o app só expõe o e-mail da conta.

Fluxo:

1. No app do administrador: **Configurações → Integração com time** → ligar → informar o **servidor** → **Eu sou admin do servidor** → colar o `TEAM_ADMIN_TOKEN` → **Validar**. Não é preciso chave de time, apelido nem conta marcada: quem administra não participa necessariamente de nenhum time.
2. **Configurar chaves das contas** → emitir uma chave por pessoa. O **rótulo** é texto livre (use o e-mail) e o servidor **não o verifica** — quem prova o vínculo é o `accountUuid` que aparece ao lado depois que a chave é usada.
3. Entregar a chave à pessoa por canal fechado. Ela cola em **Chave do time**, marca a conta e clica em **Testar conexão** — é esse clique que cria o vínculo, por `POST /v1/claim`. Contra um servidor 0.3.0 o app cai no `GET /v1/verify`, que só informa, e o vínculo volta a depender do próximo envio de turnos.
4. Máquina logada em duas contas da empresa: subir `maxAccounts` daquela chave para `2`. A segunda conta se reivindica sozinha na passada seguinte.
5. Depois que todos migrarem, definir `TEAM_LEGACY_KEY_MODE=off`. **É este passo que efetiva o isolamento.**

**Uma conta pertence a no máximo uma chave**, garantido por índice único. Se a chave errada reivindicar a conta, use `DELETE /api/admin/v1/keys/:id/accounts/:accountKey` (botão **Desvincular** no painel) para liberá-la.

**Regerar** troca a chave crua mantendo os vínculos — serve para chave perdida ou vazada, e a antiga para de valer na requisição seguinte. **Revogar** tira o acesso e **não apaga** nada, e **Desvincular** também não: os dois mexem em quem pode ler, não no que já foi enviado. Apagar histórico é `DELETE /api/v1/member` (uma máquina) ou `DELETE /api/admin/v1/accounts/:accountKey` (a conta inteira).

O `label` é PII quando você digita um e-mail nele. Ele é gravado no banco por decisão de quem administra — **nenhum e-mail vem do cliente**.

## API

Base `/api`. Todas as rotas exigem credencial, exceto o healthcheck.

| Credencial | Header | Alcance |
|---|---|---|
| Chave de time | `x-team-key` | As contas daquela chave. Única aceita no ingest e na presença. |
| Chave legada | `x-team-key` | Todas as contas, **enquanto** `TEAM_LEGACY_KEY_MODE=open`. |
| Token de admin | `x-admin-token` | Todas as contas, **só leitura** — recusado no ingest e na presença. |

O `x-admin-token` ser aceito nas rotas `/v1/*` é o que evita uma família `/admin/v1/team`, `/admin/v1/session` e `/admin/v1/member` paralela: é a mesma leitura, com outra credencial.

A comparação da chave legada e do token de admin é em tempo constante (SHA-256 + `timingSafeEqual`). As chaves emitidas são localizadas por hash SHA-256 indexado — são 32 bytes aleatórios, sem prefixo a descobrir incrementalmente.

Credencial válida para a conta errada devolve **`403` `forbidden_account`**, e não `401`: são consertos diferentes — conferir se copiou a chave certa, ou pedir ao administrador o vínculo certo.

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

### `POST /api/v1/presence`

Disponível a partir da versão **0.4.0**. Diz "o Usage Monitor está aberto nesta máquina agora". É o que alimenta o modal **Conectados agora** do app desktop.

```jsonc
{
  "accountKey": "<accountUuid da conta Anthropic>",
  "member": {
    "deviceId": "<uuid estável por instalação>",
    "alias": "edilson",
    "hostName": "DESKTOP-A1",
    "organizationUuid": null,
    "organizationName": null
  }
}
```

```json
{ "lastSeenAt": 1786003600000 }
```

O app bate a cada **30 segundos**, por conta participante, enquanto estiver aberto — inclusive minimizado, e inclusive sem nenhum turno novo a enviar.

Três propriedades desenhadas de propósito:

- **Grava só `team_members`.** Nunca sessão nem turno. É o mesmo upsert do ingest (`MAX` no `last_seen_at`, `COALESCE` no `host_name` e na organização), num statement só, sem transação.
- **O `lastSeenAt` é o relógio do servidor**, e vem na resposta justamente para o cliente medir o próprio desvio. Sem isso o app compararia um carimbo do servidor com o relógio local, e um desvio de minutos deixaria o time inteiro "online" para sempre. O corpo **não** aceita timestamp: aceitar o do cliente permitiria a uma máquina se declarar eternamente presente.
- **Idempotente e barata.** A mesma linha é reescrita a cada batida; ~200 bytes por requisição.

**Não há coluna nova nem migração**: a rota escreve na `team_members.last_seen_at`, que existe desde a 0.1.0.

O `x-admin-token` é recusado com **401** — presença é declaração de identidade em nome de um `deviceId`, e um admin capaz de marcá-la por terceiros criaria membro fantasma. Como no ingest, a primeira batida **vincula** a conta à chave apresentada.

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

### `GET /api/v1/session`

Turnos crus de **uma** sessão. É o que alimenta o painel de detalhe dentro do modal **Sessões do time** — o transcript é de outra máquina e não está no disco de quem consulta, mas os turnos estão aqui desde o primeiro ingest.

Disponível a partir da versão **0.2.0** do servidor.

| Query | Obrigatório | Nota |
|---|---|---|
| `accountKey` | sim | Escopo da resposta. Uma resposta nunca mistura contas. |
| `sessionId` | sim | |
| `deviceId` | sim | O escopo é sempre `(conta, máquina)`, como no resto da API. |

```jsonc
{
  "session": {
    "deviceId": "device-1",
    "sessionId": "a3f9c1e2-...",
    "hostName": "DESKTOP-A1",
    "cwd": "/home/dev/api-gateway",
    "gitBranch": "main",
    "firstTs": 1786000000000,
    "lastTs": 1786003600000,
    "liveContextTokens": 120000,
    "liveContextModel": "claude-opus-4-20250514"
  },
  "turns": [
    { "messageId": "msg_01ABC", "ts": 1786003600000, "model": "claude-opus-4-20250514",
      "isSidechain": false, "inputTokens": 100, "outputTokens": 200,
      "cacheReadTokens": 300, "cacheWrite5mTokens": 400, "cacheWrite1hTokens": 0 }
  ]
}
```

Três propriedades desenhadas de propósito:

- **Sem recorte temporal.** O detalhe é sempre a sessão inteira, ao contrário de `/v1/team`. Recortá-lo pela janela de quota daria gráficos que começam no meio da conversa.
- **Turnos ordenados por `(ts, messageId)`.** Não há coluna de sequência: o cliente sintetiza a ordem na leitura. O desempate pelo `messageId` deixa a série estável entre leituras.
- **O servidor continua sem precificar.** Devolve tokens e modelo por turno; o cliente aplica `ModelPricingTable`.

Sessão inexistente, de outra conta ou de outra máquina devolve `404` — a mesma resposta nos três casos, porque confirmar que uma sessão existe em outra conta já seria vazamento.

**Nada de conteúdo de prompt ou resposta.** Um turno aqui é contagem de token e nome de modelo, exatamente o que o `POST /v1/ingest` recebeu.

### `DELETE /api/v1/member`

Remove um integrante e tudo o que ele enviou: turnos, sessões e a linha em `team_members`, numa transação.

| Query | Obrigatório | Nota |
|---|---|---|
| `accountKey` | sim | Conta a que o integrante pertence. |
| `deviceId` | sim | Integrante a remover. |

```jsonc
{ "deletedTurns": 1240, "deletedSessions": 8, "deletedMembers": 1 }
```

Idempotente: um `deviceId` desconhecido devolve `200` com zeros.

**Destrutivo e irreversível.** A máquina daquele `deviceId` já marcou os turnos como enviados no próprio marcador local e não os reenvia — o histórico dela não volta. A rota existe para o caso de duplicata: uma instalação que perdeu o `~/.usage-monitor/team.json` volta com outro `deviceId` e o antigo fica na lista, sem atividade, até a retenção recolhê-lo.

### `GET /api/v1/verify`

Disponível a partir da versão **0.3.0**. Responde se a chave apresentada cobre — ou pode cobrir — uma conta. **Não cria vínculo**: o vínculo continua nascendo só no ingest, porque uma rota de leitura que amarrasse conta permitiria adotar contas alheias varrendo `accountUuid`, sem nunca provar uso.

| Query | Obrigatório |
|---|---|
| `accountKey` | sim |

```jsonc
{ "authorized": true, "claimed": false, "label": "fulano@empresa.com",
  "maxAccounts": 1, "claimedAccounts": 0 }
```

`claimed: false` com `authorized: true` é o estado normal de quem acabou de colar a chave e ainda não sincronizou. Conta de outra chave, ou limite atingido, devolve `403`.

### `POST /api/v1/claim`

Disponível a partir da versão **0.3.1**. Amarra a conta à chave apresentada e devolve o mesmo corpo do `verify`. Idempotente: repetir com a conta já vinculada responde `200`.

```jsonc
{ "accountKey": "<accountUuid da conta Anthropic>" }
```

É o que o botão **Testar conexão** do app chama. Existe porque o vínculo, até a 0.3.0, só nascia dentro de um `POST /v1/ingest` — e numa máquina que já tinha enviado todo o histórico, trocar a chave não gerava requisição nenhuma: a conta ficava sem dona e a leitura era recusada indefinidamente.

**Não enfraquece o isolamento.** O `accountKey` do ingest também é auto-declarado no corpo, então reivindicar por um `POST` explícito tem exatamente a mesma força. O que continua valendo é que **nenhum `GET` reivindica**: uma leitura que amarrasse conta permitiria adotar contas alheias varrendo `accountUuid`.

Token de admin e chave legada em modo `open` respondem `200` **sem criar vínculo** — nenhum dos dois representa uma conta.

### Rotas administrativas

Disponíveis a partir da versão **0.3.0**, e **só quando `TEAM_ADMIN_TOKEN` está definida** — sem ela caem no `404` de rota desconhecida. Header `x-admin-token`.

| Rota | Efeito |
|---|---|
| `GET /api/admin/v1/ping` | `{"status":"ok"}`. É o que o botão **Validar** do app chama. |
| `GET /api/admin/v1/overview?since=` | Todas as contas: `{ accounts: [{ accountKey, label, members[], rows[] }] }`. Mesmo formato de `/v1/team`, uma entrada por conta. |
| `POST /api/admin/v1/keys` | `{ label, maxAccounts? }` → `201` com a chave crua. |
| `GET /api/admin/v1/keys` | Lista **com a chave crua**, mais `keyPrefix`, `maxAccounts`, `accounts[]` e as datas. |
| `PATCH /api/admin/v1/keys/:id` | `{ label?, maxAccounts? }`. Teto abaixo do já reivindicado → `400`. |
| `POST /api/admin/v1/keys/:id/regenerate` | Nova chave crua, vínculos mantidos, antiga invalidada na hora. |
| `DELETE /api/admin/v1/keys/:id` | Revoga. **Não apaga dados.** |
| `DELETE /api/admin/v1/keys/:id/accounts/:accountKey` | Desfaz um vínculo errado. **Não apaga dados.** |
| `DELETE /api/admin/v1/accounts/:accountKey` | **0.5.0+.** Apaga a conta inteira: integrantes, sessões, turnos e o vínculo. Irreversível. |

Conta que entrou pela chave legada aparece no `overview` com `label: null` — existe nos dados e não tem chave dona.

**Desvincular não faz a conta sumir.** O `overview` é derivado de `team_members` e `team_turns`, nunca de `team_key_accounts`: uma conta desvinculada continua na lista, agora sem rótulo. Quem a tira de lá é `DELETE /api/admin/v1/accounts/:accountKey` — é o conserto da conta que a empresa deixou de usar, e o único caminho que não exige uma chamada por máquina.

```jsonc
{ "deletedTurns": 1240, "deletedSessions": 8, "deletedMembers": 3, "unlinkedKeys": 1 }
```

Idempotente: conta desconhecida responde `200` com zeros. **Apagar não impede a conta de voltar**: ingest e presença reivindicam sozinhos, então uma máquina que ainda participe dela a recria na batida seguinte. As travas são do lado do cliente — desmarcar a conta nas Configurações daquela máquina — ou baixar o `maxAccounts` da chave até o slot ficar cheio, o que faz a reivindicação receber `403`.

A chave crua vem no corpo do `GET` de propósito: o painel é a lista de "quem tem qual chave", e mostrá-la só na criação obrigaria o administrador a guardá-la fora do sistema ou a regerar a cada consulta. O preço está em [Modelo de segurança](#modelo-de-segurança).

### Erros

`{ "error": "<mensagem>", "code": "<código>" }` com `400` (`validation_error`), `401` (`unauthorized`), `403` (`forbidden_account`), `404` (`not_found`), `503` (`service_unavailable`) ou `500` (`internal_error`).

## Retenção

`TEAM_RETENTION_DAYS` (default 45). Roda no boot e a cada 6h: apaga turnos fora do horizonte, depois sessões sem turno, depois membros antigos sem sessão. Uma falha na limpeza é logada e **não** derruba o servidor.

Com a presença da 0.4.0 o efeito prático muda para membros: quem mantém o app aberto tem `last_seen_at` sempre fresco e **nunca é recolhido**, mesmo sem consumo nenhum. É o comportamento desejado — a lista de conectados precisa da linha do membro — mas significa que a retenção só recolhe quem parou de abrir o app.

## Modelo de segurança

O isolamento entre times vem das **chaves por conta**: cada chave só lê e escreve as contas que ela reivindicou, uma conta pertence a no máximo uma chave, e a autorização é conferida em toda rota.

**Enquanto `TEAM_LEGACY_KEY_MODE=open` esse isolamento não existe.** A chave legada de ambiente continua lendo qualquer `accountUuid` que se conheça, exatamente como antes. Emita as chaves, atualize os clientes e então mude para `off`.

Limites aceitos e conhecidos:

- **`TEAM_ADMIN_TOKEN` lê todas as contas.** É o objetivo — quem administra não participa dos times que administra — mas vazá-lo entrega o servidor inteiro. Não há rotação nem rate limit.
- **`TEAM_KEY_SECRET` + banco entregam todas as chaves em claro.** É o preço de o painel poder re-exibir a chave depois de criada. Vazamento só do banco, sem o segredo, não expõe chave nenhuma.
- **Janela de reivindicação.** Uma chave interceptada antes do primeiro uso pode ser amarrada à conta de quem a interceptou. É detectável — o time legítimo passa a receber `403` — e reversível pelo `DELETE .../accounts/:accountKey`. Confira no painel que o `accountUuid` reivindicado é o esperado.
- **Não há prova de posse do token OAuth da conta.** A reivindicação prova uso, não propriedade.

Adequado a um servidor interno; não exponha este serviço na internet aberta sem uma camada de rede na frente.

Revogação é por chave, e portanto por pessoa. A chave legada continua sendo tudo ou nada: trocá-la invalida todos os clientes que ainda dependem dela.

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

**Atualizar o servidor junto com o app.** O detalhe de sessão do modal de time depende do `GET /api/v1/session`, que só existe a partir da **0.2.0**; as chaves por conta, a validação de vínculo e a visão global dependem da **0.3.0**; a presença em tempo real depende do `POST /api/v1/presence`, que só existe a partir da **0.4.0**; apagar uma conta inteira depende do `DELETE /api/admin/v1/accounts/:accountKey`, da **0.5.0** — contra um servidor anterior a rota responde `404` e o app avisa que é preciso atualizar, porque aqui não há fallback: nenhuma outra rota faz o mesmo. Contra um servidor mais antigo o app não quebra: a rota responde `404`, o painel cai no detalhe agregado — sem os gráficos por turno — e avisa o usuário. No caso da presença o app cai sozinho num ingest só-membro, que carimba o mesmo `last_seen_at`: a tela funciona igual, apenas sem a correção de relógio. Um redeploy resolve.

Não há migração de banco a rodar: as tabelas novas da 0.3.0 (`team_keys`, `team_key_accounts`, `server_meta`) são criadas no boot e as antigas não mudam; a 0.4.0 não acrescenta tabela nem coluna — a presença escreve numa coluna que já existia — e a 0.5.0 tampouco: apagar conta só executa `DELETE` nas tabelas existentes. Um servidor 0.3.0 com `TEAM_LEGACY_KEY_MODE=open` e só `TEAM_API_KEY` definida se comporta exatamente como a 0.2.x.

Rodando em **Docker Swarm**, mantenha **1 réplica**: o SQLite é um arquivo local e duas réplicas em nós diferentes veriam bancos distintos. Se o cluster tiver mais de um nó, fixe uma constraint de nó para o volume seguir o serviço.

### Configurar os clientes

Em cada máquina, no app desktop: **Configurações → Integração com time** → ligar, informar a URL (`https://<dominio>`), colar a chave que o administrador emitiu, definir o alias e marcar as contas Anthropic que participam. O app confere o vínculo na hora e mostra a mensagem do servidor se a chave não cobrir a conta marcada.

Quem administra acrescenta **Eu sou admin do servidor** e o `TEAM_ADMIN_TOKEN`. A partir daí ganha o botão de **todas as contas** na barra inferior do dashboard, que abre a tela de Sessões do time com as contas agrupadas.

Nessa visão o recorte de **5h é deslizante**, e não ancorado no reset de quota: cada conta reseta numa hora diferente e ancorar numa delas daria um número que não corresponde a nenhuma. A tela avisa isso quando o filtro está em 5h.

## Inspecionar os dados

```bash
sqlite3 data/team-usage.sqlite "SELECT alias, host_name, last_seen_at FROM team_members;"
sqlite3 data/team-usage.sqlite "SELECT COUNT(*) FROM team_turns;"
```
