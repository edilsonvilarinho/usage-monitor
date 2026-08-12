# Plano — Integração com time (servidor Node.js + visão agregada por conta)

> **Status:** implementado em 2026-08-11 sobre `846f1f0`.
> **Escopo:** novo recurso opcional. Não altera o comportamento de quem não o liga.

## Contexto

O app só enxergava as sessões do Claude Code da **máquina local**: `LocalCliSessionDataSource`
indexa `<configDir>/projects/**/*.jsonl` num SQLite local e serve os agregados de lá.

Numa empresa, a mesma conta Anthropic é usada por vários desenvolvedores em máquinas
diferentes, e não havia visão do consumo agregado do time.

## Decisões travadas antes da execução

| Decisão | Escolha | Motivo |
|---|---|---|
| Granularidade do sync | **Turnos crus** | Só assim o servidor reproduz o janelamento por turno com a mesma precisão do modal local. Agregados pré-janelados desalinhariam as janelas entre máquinas. |
| Banco do servidor | **SQLite + better-sqlite3** | Mesma stack do `montador-pacote`. Um arquivo em volume, zero serviço extra para a empresa operar. |
| Modal | Integrantes agrupados, **expansível** para as sessões | Dá o número de gestão e a evidência que o gerou. |
| Auth | **API key compartilhada** (`x-team-key`) | Servidor interno. Sem prova de posse do token OAuth. |

## Chave de agrupamento

`accountKey = UsageAccountKey.providerAccountId` — o **`accountUuid`** da conta, lido de
`~/.claude.json` por `AnthropicProfileRegistry.inspect`, sem rede.

**Sem o `organizationUuid`.** Ele é nulo em parte das instalações; usá-lo na chave faria
o agrupamento quebrar entre máquinas da mesma conta. Ele viaja como metadado do membro.

Consequência esperada: duas contas diferentes na mesma organização geram dois cards e
dois modais separados — que é o pedido ("botão no card da conta").

---

## Parte 1 — Servidor (`server/`)

Node 20 · Express 4 · TypeScript ESM · `better-sqlite3` · `zod` · `pino` · `vitest` + `supertest`.

Espelha `montador-pacote/backend` com duas correções deliberadas sobre ele: **Routers por
domínio** em vez de um `createApp.ts` monolítico, e **pino efetivamente usado** (lá é
dependência morta). O Dockerfile também corrige as duas lacunas de lá: `HEALTHCHECK` no
próprio Dockerfile e `USER node` em vez de root.

`buildApp(config, overrides?)` devolve `{ app, db, repository }` e nunca instancia por
side-effect de import — é o que torna o teste com `supertest` trivial.

### Schema (`${DATA_DIR}/team-usage.sqlite`)

`team_members` · `team_sessions` · `team_turns`, todas com `account_key` na frente da
chave. `PRIMARY KEY (account_key, session_id, message_id)` + `INSERT OR IGNORE` reproduz
exatamente a dedup que o índice local já faz em `cli_turns`: reenviar um lote é inofensivo.

### Endpoints

| Rota | Nota |
|---|---|
| `GET /api/health` | Toca o banco (`SELECT 1`). Sem autenticação. |
| `POST /api/v1/ingest` | Idempotente. Rejeita turno cuja sessão não veio no lote. |
| `GET /api/v1/team` | `accountKey` + `since`. Uma linha por `(deviceId, sessionId, model)`. |
| `GET /api/v1/session` | `accountKey` + `deviceId` + `sessionId`. Turnos crus da sessão inteira, sem recorte. Servidor 0.2.0+. |

**O servidor não calcula custo.** Devolve tokens por modelo; o cliente aplica a própria
`ModelPricingTable`. Evita duplicar a tabela de preços em TypeScript e mantém o custo do
modal de time coerente com o do modal local quando a tabela for atualizada.

Retenção por `TEAM_RETENTION_DAYS` (default 45), no boot e a cada 6h.

Build file em **`Dockerfile.dokploy` na raiz**, com contexto na raiz — mesmo padrão do
`montador-pacote`, que é como o Dokploy da empresa já está configurado. O `.dockerignore`
da raiz nega tudo e reinclui só `server/`: sem isso o `build/` do Gradle, com o runtime
Java empacotado, entraria no contexto. Compose em `docker/docker-compose.yml`
(`context: ..`). Passo a passo em `server/README.md`.

---

## Parte 2 — Cliente

### Refactor de base

`WindowedSessionAccumulator` foi **extraído** de `LocalCliSessionDataSource` (onde era
`private class` acoplada a `ResultSet`) para `domain/entity/`. As duas fontes — as linhas
do índice SQLite local e as linhas que o servidor devolve — chegam no mesmo formato
`(sessão, modelo) → tokens`, e agora passam pelo mesmo cálculo de custo por trecho.
Duas implementações divergiriam com o tempo.

Componentes de UI que eram `private` em `CliSessionsScreen.kt` viraram `internal` para o
modal de time reaproveitar: `CliSessionRow`, `MetricText`, `MeterBar`, `LiveBadge`,
`CenteredMessage`, `NoticeText`, `healthColor` e as cores `INPUT_COLOR`/`CACHE_READ_COLOR`.

A issue #34 estendeu a mesma promoção ao painel de detalhe inteiro —
`CliSessionDetailSections`, `SessionHealthBanner`, `SessionMetadataCard`,
`SessionSummaryRow`, `SessionAdvancedSections`, `AdvancedDisclosure`, `DetailSection`,
`MetricCard`, `GlossaryPanel`, `HelpDot`, `CostDistributionBar`,
`CostDistributionLegend` e o resto da paleta. Duplicar o painel para o time faria os dois
divergirem no primeiro ajuste de layout.

### Arquivos novos

| Camada | Arquivos |
|---|---|
| domain/entity | `TeamIntegrationSettings`, `TeamUsageModels`, `WindowedSessionAccumulator` |
| domain/repository | `TeamUsageRepository` |
| domain/usecase | `GetTeamUsageUseCase`, `PushTeamUsageUseCase` |
| data | `dto/TeamDto`, `mapper/TeamUsageMapper`, `datasource/RemoteTeamDataSource`, `repository/TeamUsageRepositoryImpl` |
| desktopMain | `LocalTeamSettingsDataSource`, `LocalTeamSyncStateDataSource`, `TeamSyncService`, `TeamUsageWindowPreferences` |
| presentation | `TeamUsageUiState`, `TeamUsageViewModel`, `TeamUsageScreen`, `TeamUsageFormatting`, `components/TeamIntegrationSection` |

`TeamIngestPayload` reusa `CliSessionSummary` e `CliSessionTurn` em vez de criar entidades
paralelas: é exatamente o que o índice local já guarda.

### Três decisões que exigem atenção ao mexer

1. **Conexão compartilhada.** `LocalTeamSyncStateDataSource` recebe o
   `SqliteConnectionManager` do `LocalCliSessionDataSource` (exposto como
   `sharedConnectionManager`), não abre uma segunda conexão para o mesmo arquivo.
   `useConnection` é `synchronized`, então o envio espera a indexação terminar em vez de
   disputar a escrita e receber `SQLITE_BUSY` — pedágio que o repo já pagou uma vez
   (ver `plano-filtros-sessoes-cli.md`, §"Desvios aplicados").

2. **Reset do marcador.** `team_sync_state` guarda o maior `seq` enviado por sessão. Se um
   transcript é truncado ou recriado no mesmo caminho, o índice reconstrói a sessão do zero
   e os `seq` recomeçam — os turnos novos ficariam abaixo do marcador antigo e nunca
   sairiam. `resetStaleWatermarks` apaga todo marcador maior que o maior `seq` que a sessão
   ainda tem. Coberto por teste.

3. **Segredo fora das preferências.** A configuração vive em `~/.usage-monitor/team.json`
   com escrita atômica + `restrictToOwnerReadWrite`. `PreferencesSettings` grava em claro
   no registro `HKCU` e hoje o app não guarda nenhum segredo lá.

### Anti-flicker

`TeamUsageViewModel` espelha as três restrições que fazem o tempo real do modal local não
piscar, e que são fáceis de quebrar sem perceber:

- o laço ao vivo nunca passa por `Loading`;
- `lastChangedAt` só avança quando o conteúdo muda de fato;
- `expandedDeviceIds` é carregado do estado anterior a cada tique, senão os grupos abertos
  se fechariam sozinhos a cada 5s.

Além disso, falha de rede **depois** de a lista já ter carregado vira estado silencioso: o
usuário está lendo a tela, e uma intermitência não pode arrancar o conteúdo dela.

---

## Testes

| Suíte | Testes |
|---|---|
| `server/test` (vitest + supertest) | 30 |
| `TeamUsageMapperTest` (commonTest) | 10 |
| `TeamUsageViewModelTest` (commonTest) | 15 |
| `TeamSyncServiceTest` (desktopTest, fixtures `.jsonl` reais) | 7 |
| `TeamUsageScreenTest` (desktopTest, Compose) | 11 |

```bash
cd server && npm test && npm run typecheck
```

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.data.TeamUsageMapperTest" --tests "com.usagemonitor.presentation.TeamUsageViewModelTest" --tests "com.usagemonitor.ui.TeamUsageScreenTest" --tests "com.usagemonitor.TeamSyncServiceTest"
gradlew.bat allTests
```

**Nota sobre `allTests`:** a suíte agregada é flaky de forma preexistente — coroutines de
background (`AnthropicProfileRegistry.updateLabel`, com debounce de 300ms) vazam entre
testes e a falha muda de arquivo a cada execução. Medido em `846f1f0` sem nenhuma das
mudanças deste plano: 3 falhas em `HistoryViewModelTest`. Não é regressão desta entrega.

---

## Verificação manual

1. Servidor local rodando; Configurações → ligar integração, URL, chave, apelido, marcar a
   conta → **Testar conexão** retorna OK.
2. Aguardar ~30s → `sqlite3 server/data/team-usage.sqlite "SELECT COUNT(*) FROM team_turns;"` > 0.
3. Card da conta marcada exibe o ícone de time; conta não marcada e cards não-Anthropic não
   exibem. Desligar a integração faz o ícone sumir de todos.
4. Abrir o modal → filtro **5h** pré-selecionado, o próprio usuário na lista.
5. Alternar 5h → 7d → 30d → Total: os totais crescem **monotonicamente**.
6. Expandir um integrante, esperar 3 tiques (15s) → não colapsa nem reexpande sozinho.
7. Segunda máquina com apelido diferente na mesma conta → aparece em até ~35s.
8. Total em **Total** bate com o header do modal de Sessões CLI local (tolerância de poucos
   micros no custo).
9. Derrubar o servidor com o modal aberto → a lista permanece; subir de novo → recupera no
   próximo tique.
10. Requisição sem `x-team-key` → 401; `accountKey` de outra conta → resposta vazia.

---

## Fora de escopo

- Autenticação por usuário, papéis, UI web de administração no servidor.
- Prova de posse do token OAuth (o cliente declara o `accountUuid`; risco aceito e
  documentado em `server/README.md`).
- Sessões de outros CLIs (Codex, OpenCode, Kilo) na visão de time.
- ~~Detalhe completo de sessão (analytics, gráficos por turno) dentro do modal de time — o
  transcript é de outra máquina e não está disponível localmente.~~ **Entregue (issue #34).**
  A premissa estava errada: verdadeira para o cliente, falsa para o servidor. `team_turns`
  guarda o turno cru desde o primeiro ingest — é a decisão *"Granularidade do sync: turnos
  crus"* travada no topo deste documento. Só faltava expor a leitura: `GET /api/v1/session`
  devolve os turnos ordenados por `(ts, messageId)` e o cliente sintetiza o `seq`, reagrega
  pelo `WindowedSessionAccumulator` e reusa as mesmas seções de `CliSessionsScreen`.
  Nenhuma migração de schema foi necessária. Contra servidor anterior à 0.2.0 a rota
  responde `404`, o painel cai no agregado e avisa — atualizar app e servidor no mesmo dia
  não é garantido.
- Exportação, alertas de orçamento, ranking histórico.
- CI de testes no GitHub Actions (não existe hoje para o desktop; não foi criado para o
  servidor).
