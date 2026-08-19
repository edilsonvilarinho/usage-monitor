# AGENTS.md

## Build commands (Windows)

Todos os comandos usam `gradlew.bat` ou `.\gradlew.bat` no PowerShell:
```bat
gradlew.bat run                               % roda a app desktop
gradlew.bat desktopJar                        % gera o JAR executavel
gradlew.bat build                             % compila + roda checks
gradlew.bat allTests                          % suite agregada de testes
gradlew.bat desktopTest                       % testes JVM/Desktop
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"        % foco em domain/common tests
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"  % foco em ViewModel/common tests
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"            % testes Compose Desktop
gradlew.bat createDistributable               % gera distribuicao desktop
gradlew.bat packageInstaller                  % gera instalador NSIS se o NSIS estiver instalado
gradlew.bat clean                             % limpa artefatos
```

**Importante:** a task raiz `test` nao existe neste projeto KMP. Use `allTests`, `desktopTest` ou `build`.

**Env var obrigatoria para MiniMax:**
```bat
set MINIMAX_API_KEY=your_key_here
```

Se a MiniMax estiver habilitada e a env var nao existir, o repositorio falha explicitamente.

## Architecture

Arquitetura em tres camadas com dependencia unidirecional: `presentation -> domain <- data`

| Source set | Platform | Contents |
|---|---|---|
| `commonMain` | shared | domain + data (DTOs, mappers, repositories) + presentation/UI |
| `desktopMain` | JVM only | data sources locais com `java.io.File`, `AutoStartManager`, `Main.kt` e bootstrap desktop |
| `commonTest` | shared | testes unitarios de domain, mappers e ViewModel |
| `desktopTest` | JVM only | testes de componentes Compose Desktop |
| `installer` | Windows only | scripts e assets do NSIS installer |

## Domain layer constraints

- **Zero imports** de Ktor, Compose ou bibliotecas de infra. Apenas Kotlin puro + `kotlinx.datetime`.
- Entidades e contratos do domain nao conhecem HTTP, JSON, ficheiros locais ou UI.
- `LocalCredentialDataSource` e `LocalCodexAuthDataSource` ficam em `desktopMain`, nao em `commonMain`, porque leem ficheiros do utilizador.

## DI and lifecycle

`Main.kt` monta o grafo manualmente:

`HttpClient(OkHttp)` -> `LocalCredentialDataSource` + `LocalCodexAuthDataSource` + `RemoteApiDataSource` -> repositories -> use cases -> `DashboardViewModel` -> `DashboardScreen`

Regras importantes:

- `DashboardViewModel` faz polling a cada 10 minutos via `while(true) + delay`.
- O scope usa `SupervisorJob`, para que falha de uma API nao cancele as outras.
- **Tem de chamar `viewModel.onDestroy()` ao fechar a janela.**
- `Main.kt` tambem fecha o `HttpClient` no fechamento da janela e no shutdown hook.

## External API calls

### Anthropic

- Endpoint atual: `GET https://api.anthropic.com/api/oauth/usage`
- Headers obrigatorios:
  - `Authorization: Bearer <accessToken>`
  - `anthropic-beta: oauth-2025-04-20`
  - `User-Agent: claude-code/1.0.0`
- O token vem de `~/.claude/.credentials.json` -> `claudeAiOauth.accessToken`
- Se o token estiver perto de expirar, `LocalCredentialDataSource` tenta refresh em `https://platform.claude.com/v1/oauth/token`
  - O corpo precisa de `client_id` e `scope` alem de `grant_type`/`refresh_token`. Sem `client_id` o endpoint responde `400 Invalid request format` com qualquer refresh token
  - A regravacao do ficheiro e patch de JSON: ele tem nos que o app nao declara (`mcpOAuth`, `refreshTokenExpiresAt`) e serializar o DTO de volta os apagava
- O payload retornado traz janelas de uso `five_hour` e `seven_day`

### MiniMax

- Endpoint: `GET https://www.minimax.io/v1/token_plan/remains`
- Autenticacao: `MINIMAX_API_KEY` via env var
- **Nunca hardcode** a chave
- A app hoje filtra quotas do modelo `MiniMax-M*`

### Codex

- Endpoint: `GET https://chatgpt.com/backend-api/codex/usage`
- Autenticacao:
  - bearer token de `~/.codex/auth.json` -> `tokens.access_token`
  - cookie `cap_sid` lido de `~/.codex/cap_sid`
- O endpoint legado hoje deve ser tratado como fonte da quota 5h
- A quota semanal do Codex depende de uma segunda fonte ainda nao descoberta/validada

## Persistence and preferences

- Preferencias locais usam `PreferencesSettings(Preferences.userRoot().node("com.usagemonitor"))`
- Chaves persistidas hoje:
  - `enabledApis`
  - `isDark`
  - `language`
  - `autoStart`
  - `windowOpacityPercent`
  - `uiScalePercent` (escala global da interface; troca a densidade em `AppTheme`, nao a tipografia)
  - `teamUsageWindow*` (geometria da janela de Sessoes do time)
  - `teamPresenceWindow*` (geometria da janela de Conectados agora)
- `AutoStartManager` escreve em `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
- **A configuracao de time NAO vai para as preferencias.** Vive em
  `~/.usage-monitor/team.json` (`LocalTeamSettingsDataSource`), com escrita atomica
  e `restrictToOwnerReadWrite`: a chave do servidor e um segredo e as preferencias
  sao gravadas em claro no registro do Windows.

## Team integration (server/)

Recurso opcional, desligado por default. Um servidor Node.js self-hosted pela
empresa recebe os turnos indexados de cada maquina e devolve a visao agregada por
conta Anthropic.

- Codigo do servidor em `server/` — Express 4 + TypeScript ESM + SQLite
  (`better-sqlite3`). `cd server && npm test`. Detalhes de contrato e deploy
  Dokploy em `server/README.md`.
- Build file: **`Dockerfile.dokploy` na raiz**, contexto na raiz (mesmo padrao do
  `montador-pacote`). O `.dockerignore` da raiz nega tudo e reinclui so `server/` —
  sem isso o `build/` do Gradle, com o runtime Java, entraria no contexto. Compose
  em `docker/docker-compose.yml` (`context: ..`).
- **Chave de agrupamento:** o `accountUuid` da conta (`UsageAccountKey.providerAccountId`),
  sem o `organizationUuid` — este e nulo em parte das instalacoes e usa-lo na chave
  quebraria o agrupamento entre maquinas da mesma conta.
- **Envio:** `TeamSyncService` (desktopMain), laco de 30s, independente da janela.
  Marcador de progresso em `team_sync_state` (mesmo `usage-history.db`), pela
  conexao **compartilhada** do `LocalCliSessionDataSource`.
- **Cada passada indexa antes de enviar** (`ensureIndexFresh`). O servico so ve
  turno que ja esta no indice: sem indexar na propria passada, a latencia do time
  seria a do laco de background (10min) e nao a dos 30s. Falha ao indexar entra no
  relatorio mas nao cancela o envio do que ja estava indexado. Abrir o modal
  chama `requestImmediateSync()`, que antecipa uma passada sem reiniciar o laco.
- **Leitura:** `TeamUsageViewModel`, laco ao vivo de 5s, so com a janela aberta.
- **Presenca (servidor 0.4.0+):** `POST /api/v1/presence` carimba `team_members.last_seen_at`
  a cada passada de 30s, mesmo sem turno novo — e o que separa "app aberto" de
  "houve consumo". Rota propria porque e escrita, com a conta no corpo e
  `x-admin-token` recusado; **nenhuma coluna nova**. Contra servidor anterior o 404
  cai num ingest so-membro, lembrado por URL. A tela `TeamPresenceViewModel`
  classifica *online* (90s de janela) e *trabalhando agora*
  (`ACTIVE_SESSION_WINDOW_MILLIS`), corrigindo o relogio pelo desvio que a propria
  resposta do heartbeat mede.
- **Precificacao:** o servidor devolve tokens por `(deviceId, sessionId, model)` e
  **nao** calcula custo. Quem aplica `ModelPricingTable` e o cliente, via
  `WindowedSessionAccumulator` — a mesma classe que o indice local usa.
- **Nao trafega conteudo de prompt ou resposta**, so metadados de uso
  (`sessionId`, `messageId`, `ts`, `model`, tokens, `cwd`, `gitBranch`, `hostName`).
- O botao no card so aparece com a integracao ligada **e** a conta marcada em
  Configuracoes -> Integracao com time.

## UI and presentation

- Nomes em ingles, comentarios em portugues.
- Evitar nested scope functions (`let`, `apply`, `run`). Preferir fluxo explicito.
- Componentes de UI devem ser **stateless**: dados por params, eventos por lambdas.
- `DashboardScreen` e o ponto stateful principal da UI.
- `UiState` aceita sucesso parcial: se pelo menos uma API responder, a tela mostra os dados e lista erros das APIs que falharam.

## Sistema visual

A app passou por uma refatoracao visual integral em agosto de 2026 (linguagem do OpenCode). O
detalhamento esta em `CLAUDE.md`, secao "Sistema visual"; o plano com as decisoes em
`docs/planos/refatoracao-visual-opencode-execucao.md` e a especificacao de aparencia em
`docs/planos/prototipo-visual-opencode.html`.

O essencial para nao quebrar nada:

- Tokens em `presentation/ui/theme/AppTheme.kt`: superficies neutras, raio maximo 10dp, elevacao 0
  para superficie de dados, espacamento em `AppSpacing`, motion 120/180/240.
- Tipografia IBM Plex: mono em rotulo/numero/titulo, sans em texto corrido. Os TTFs vivem em
  `desktopMain/resources/fonts/` e sao carregados por `appFontFamilies` (expect/actual).
- Primitivas em `presentation/ui/components/App{Structure,Controls,States}.kt`. Componente novo de
  UI deve sair delas.
- Cor nunca informa sozinha: `AppStatusIndicator` traz ponto e palavra, e o tom sai de `AppTone`.
- `weight` nao funciona dentro de `FlowRow`; acao com icone precisa de `contentDescription` na
  semantica; placeholder de campo precisa de `clearAndSetSemantics`.
- Tela mais alta exige subir a altura da **cena** do teste de componente, nao a do `Box` interno.

## Testing

- `commonTest` cobre domain, mappers e `DashboardViewModel`
- `desktopTest` cobre componentes Compose Desktop
- A suite agregada atual passa com `gradlew.bat allTests`

## Installer (NSIS) - Debugging Lessons

**Problema**: installer travava em 99% e/ou na tela de finish

**Causas raiz**:
1. **LZMA compression** -> thread deadlock (NSIS Bug #248)
2. **ExecWait** -> bloqueia installer esperando a app fechar

**Solucoes**:
1. `SetCompressor zlib` no topo do `.nsi`
2. Evitar launch bloqueante no fluxo de sucesso do instalador

**Sintomas**:
- Freeze em 99% = forte sinal de problema de compressao LZMA
- Freeze na tela final = forte sinal de processo bloqueante

**Diagnostico correto**:
- Analisar imagens e logs com atencao; o ponto exato do freeze importa
- Pesquisar antes de chutar solucao
- Perguntar "onde exatamente trava?" antes de propor fix

## Commit convention

Antes de commitar:
```bash
git config user.name "codex"
git config user.email "codex@openai.com"
```

Depois de commitar, restaurar:
```bash
git config user.name "edilsonvilarinho"
git config user.email "edilson.vilarinho.messias@gmail.com"
```

**Nunca rodar `git commit` ou `git push` sem pedido explicito do utilizador.**
