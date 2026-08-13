# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bat
# Rodar a aplicação Desktop
gradlew.bat run

# Compilar sem rodar
gradlew.bat desktopJar

# Todos os testes (domain + data + ViewModel + UI)
# A task raiz `test` não existe neste projeto KMP — use `allTests`.
gradlew.bat allTests

# Apenas testes do commonTest (domain, mappers, ViewModel)
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.*"
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"

# Apenas testes de componente UI (desktopTest)
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"

# Limpar build cache
gradlew.bat clean
```

Antes de rodar, definir variável de ambiente:
```bat
set MINIMAX_API_KEY=sua_chave_aqui
```

Servidor de time (`server/`, opcional — só quem usa a integração com time):
```bash
cd server
npm install
npm test        # vitest + supertest
npm run dev     # http://localhost:3000
```

## Arquitetura

KMP Desktop (JVM único alvo). Código organizado em três camadas com dependências unidirecionais: `presentation → domain ← data`.

### Source sets

| Source set | Conteúdo |
|---|---|
| `commonMain` | domain + data (exceto leitura de ficheiros) + presentation/UI |
| `desktopMain` | `LocalCredentialDataSource` (usa `java.io.File`) + `Main.kt` (DI + janela) |
| `commonTest` | Testes unitários de domain, mappers e ViewModel |
| `desktopTest` | Testes de componente Compose (`runDesktopComposeUiTest`) |

### Camada domain (`commonMain/domain/`)

Núcleo puro — **zero imports de Ktor, Compose ou bibliotecas externas**.

- `QuotaInfo`: entidade com `percentageUsed` e `remaining` calculados. `UsageUnit` diferencia `TOKENS`, `REQUESTS` (MiniMax), `PERCENTAGE` (Anthropic/Codex) e `CURRENCY_USD` (saldo DeepSeek). `currencyCode` (default `"USD"`) diz em que moeda os valores monetários estão. **Não crie valores novos em `UsageUnit`**: os `when` exaustivos do card, do histórico e do gráfico quebram; campo novo com default é retrocompatível, valor de enum novo não é.
- `ApiUsageStats`: agrega lista de `QuotaInfo` por API.
- Interfaces `AnthropicRepository` / `MiniMaxRepository`: o domain define o contrato; `data` implementa.
- Use cases usam `operator fun invoke()` — chamados como `useCase()`.

### Camada data (`commonMain/data/` + `desktopMain/data/`)

- DTOs com `@Serializable` + `@SerialName` para mapear snake_case do JSON.
- `LocalCredentialDataSource` em **desktopMain** (usa `java.io.File`). Lê `~/.claude/.credentials.json` → `claudeAiOauth.accessToken`. Valida `expiresAt`. A origem sai de `AnthropicCredentialStore`: o ficheiro tem prioridade sempre; **no macOS**, quando ele não existe, cai na entrada `Claude Code-credentials` do Keychain via `security` (só para o perfil padrão — perfis de `CLAUDE_CONFIG_DIR` não têm entrada lá). A gravação pós-refresh volta para a mesma origem.
- `RemoteApiDataSource`: Anthropic faz `GET /api/oauth/usage` (headers `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-code/1.0.0`) e lê a utilização das janelas direto do corpo JSON. MiniMax faz `GET /v1/token_plan/remains`.
- **Créditos de uso (Anthropic)**: `extra_usage` é a fonte primária — `monthly_limit` e `used_credits` vêm em unidades menores da moeda (55000 = R$ 550,00) e `currency` traz a moeda real da conta, que **não é sempre USD**. `spend` só reforça (o `percent` dele vem arredondado; a moeda cai para "USD" quando o recurso está desligado). `AnthropicMapper` só cria a terceira `QuotaInfo` quando `is_enabled` é `true`; o rótulo `AnthropicQuotaLabels.EXTRA_CREDITS` é chave da série histórica e não pode ser renomeado.
- `MiniMaxRepositoryImpl` lê `System.getenv("MINIMAX_API_KEY")` — nunca hardcode.
- Ambos os repos usam `Result.runCatching { }` para encapsular falhas.

### Camada presentation (`commonMain/presentation/`)

- `UiState`: `sealed interface` com `Loading`, `Success(data)`, `Error(message)`. Se uma API falhar e outra tiver sucesso, emite `Success` com dados parciais.
- `DashboardViewModel`: `StateFlow<UiState>` + polling silencioso via `while(true) + delay(10 * 60 * 1_000L)`. Escopo com `SupervisorJob` — falha de uma coroutine não cancela as outras. Chamar `onDestroy()` ao fechar janela.
- Componentes UI: **todos stateless** (recebem dados via parâmetros, emitem eventos via lambdas). `DashboardScreen` é o único stateful.
- Timezone de reset: sempre `TimeZone.of("America/Sao_Paulo")` com label `BRT`.
- **Copiar sessão** (`CopySessionCommandButton`): a tela mostra o id truncado em 8 (`shortSessionId`), que **não** retoma nada — `claude --resume` só volta direto para a conversa com o session ID inteiro; com um prefixo ele cai no seletor interativo. Por isso o botão copia `claude --resume <uuid completo>` (`resumeSessionCommand`). No modal do time o transcript é de outra máquina, então ali `isLocalSession = false` copia só o identificador — comando que cairia num seletor vazio seria pior que botão nenhum. A escrita no clipboard passa por `rememberClipboardWriter()`, injetável, para o teste de componente não apagar o clipboard de quem roda a suíte.
- **Semáforo de sessão** (`SessionPulseViewModel`, laço de 30s): faz os botões de Sessões CLI e de time do card piscarem quando há sessão com turno nos últimos 5 min (`ACTIVE_SESSION_WINDOW_MILLIS`) e veredito `ATTENTION`/`SATURATED`. O corte de 5 min é `sinceEpochMillis` na consulta, **nunca** um valor novo em `CliSessionRange` — os `when` exaustivos dos chips quebrariam. O laço espera `isAppVisible` e indexa antes de ler, senão a latência seria a do laço de background (10min). Leitura que falha **mantém** o pulso anterior; quem o apaga é `SessionPulse.prunedAt`, pela idade dos alertas — sem isso um servidor de time fora do ar deixaria o botão piscando indefinidamente. `sessionPulseFrame` é função pura (fase → severidade + alpha) para o pisca ser testável sem Compose, e `rememberSessionPulseFrame` **não cria transição infinita** sem pulso: uma animação sem fim trava o `waitForIdle` dos testes de componente.

### Empacotamento

`TargetFormat.Exe`/`Msi` (Windows), `Deb`/`Rpm` (Linux) e `Dmg` (macOS). O jpackage **não faz cross-compile**: o `.dmg` só sai rodando em macOS, por isso o release depende do job `build-macos` (`macos-latest` arm64 + `macos-15-intel` x64) em `.github/workflows/release-linux.yml`. Os DMGs vão sem assinatura Apple — o Gatekeeper exige liberação manual, documentada no README.

Auto-start (`AutoStartManager`): registro `Run` no Windows, `.desktop` no Linux, LaunchAgent (`~/Library/LaunchAgents/com.usagemonitor.app.plist` + `launchctl`) no macOS. O enum `Platform` é exaustivo em três `when` do arquivo — valor novo quebra a compilação nos três.

### Injeção de dependências

Manual, em `Main.kt` (desktopMain). Sem framework. Sequência: `HttpClient(OkHttp)` → datasources → repos → use cases → `DashboardViewModel` → `DashboardScreen`.

## Integração com time (`server/`)

Recurso opcional, desligado por default. Servidor Node.js **self-hosted pela empresa** (Express 4 + TypeScript + SQLite) que recebe os turnos indexados de cada máquina e devolve a visão agregada por conta Anthropic. Contrato da API e passo a passo de deploy no Dokploy em [`server/README.md`](server/README.md).

- **Chave de agrupamento:** o `accountUuid` da conta (`UsageAccountKey.providerAccountId`), sem o `organizationUuid` — este é nulo em parte das instalações e usá-lo na chave quebraria o agrupamento entre máquinas da mesma conta.
- **Chave de time é por pessoa, emitida pelo app do admin** (`server/src/repositories/teamKeyRepository.ts`). Ela nasce sem conta e se amarra a um `accountUuid` por `POST /v1/claim` (o botão "Testar conexão") ou dentro de um ingest. **Nenhum `GET` reivindica** — senão bastaria varrer uuid para adotar conta alheia. Depender só do ingest travou o time em produção: numa máquina que já enviou todo o histórico, trocar a chave não gera requisição nenhuma, e a conta fica sem dona com a leitura recusada.
- **O marcador de identidade do `TeamSyncService` inclui a chave de time**, não só o apelido. É o que faz trocar a chave forçar um envio mesmo sem turno pendente. Índice único em `team_key_accounts(account_key)`: uma conta pertence a no máximo uma chave, e é ele — não uma checagem prévia — que decide a corrida entre dois ingests. `maxAccounts` (default 1) cobre a máquina logada em duas contas da empresa, que usa a **mesma** chave para as duas.
- **`label` da chave é texto livre do admin, não verificado.** É onde ele digita o e-mail da pessoa. Quem prova o vínculo é o `accountUuid` ao lado; a UI mostra os dois juntos de propósito.
- **`TEAM_API_KEY` virou legado.** Com `TEAM_LEGACY_KEY_MODE=open` (default) ela continua lendo tudo, que é o comportamento anterior; `off` a rejeita e é o passo que efetiva o isolamento. `TEAM_ADMIN_TOKEN` monta `/api/admin/*` e vale como credencial **de leitura** em `/v1/team`, `/v1/session` e `/v1/member` — é o que evita uma família de rotas admin paralela. No `/v1/ingest` ele é recusado: admin lê, não escreve consumo em nome de ninguém.
- **Modo admin é independente de conta.** `TeamIntegrationSettings.isAdminMode` não entra em `isConfigured`/`isActive`: administrar não exige chave de time, apelido nem conta marcada. O botão da barra inferior (`FooterBar.onOpenAdminOverview`, `null` esconde) abre a mesma `TeamUsageScreen` em modo global.
- **`TeamMemberUsage.memberKey`** (`accountKey/deviceId`, ou só `deviceId` fora do modo global) é a identidade da linha na tela. O `deviceId` sozinho não serve: na visão global a mesma máquina em duas contas expandiria as duas juntas e a remoção acertaria a conta errada.
- **Na visão global o recorte de 5h é deslizante.** Cada conta reseta a quota numa hora, e ancorar numa delas daria um número que não corresponde a nenhuma. `GetAdminTeamOverviewUseCase` resolve a janela com `CliQuotaWindows()` vazio e a tela avisa.
- **Configuração** em `~/.usage-monitor/team.json` (`LocalTeamSettingsDataSource`), com escrita atômica e `restrictToOwnerReadWrite`. Nunca em `PreferencesSettings`: a chave do servidor é segredo e as preferências vão em claro para o registro.
- **Envio:** `TeamSyncService`, laço de 30s, roda com a janela fechada. Marcador em `team_sync_state` (mesmo `usage-history.db`), pela conexão **compartilhada** do `LocalCliSessionDataSource` — duas conexões para o mesmo arquivo dariam `SQLITE_BUSY`.
- **Cada passada indexa antes de enviar** (`ensureIndexFresh`, ligado ao mesmo `SyncCliSessionIndexUseCase` da tela de Sessões CLI). O serviço só enxerga turno que já está no índice: sem isso a latência seria a do laço de background (10min), não a dos 30s. Falha na indexação não cancela o envio do que já está indexado. Abrir o modal do time chama `requestImmediateSync()` e antecipa uma passada.
- **Identidade (apelido) viaja no ingest, não numa rota própria.** O servidor grava o `alias` no upsert de `team_members`, dentro do `POST /v1/ingest`. Por isso `TeamSyncService` envia um payload **só com o membro** (`sessions`/`turns` vazios, `PushTeamUsageUseCase(force = true)`) sempre que o apelido difere do último confirmado — marcador em memória, por conta. Sem isso, quem renomeia e para de usar o CLI fica com o nome velho na tela do time indefinidamente. O commit do campo nas Configurações chama `requestImmediateSync()` para não esperar os 30s.
- **Renomear nunca duplica integrante**: a chave é `(account_key, device_id)`. Duas linhas para a mesma máquina só aparecem quando o `deviceId` muda, e o único caminho que o gera é `LocalTeamSettingsDataSource` com `team.json` ausente ou ilegível — daí o arquivo corrompido ir para `.corrupt` em vez de ser sobrescrito. Para limpar um fantasma já criado existe `DELETE /api/v1/member` e o botão de remover no modal, bloqueado para o próprio `deviceId`.
- **Leitura:** `TeamUsageViewModel`, laço ao vivo de 5s, só com a janela aberta. Mesmas restrições anti-flicker do `CliSessionsViewModel`.
- **Precificação no cliente:** o servidor devolve tokens por `(deviceId, sessionId, model)` e não calcula custo. `WindowedSessionAccumulator` (domain) aplica `ModelPricingTable` — a mesma classe que o índice local usa, para os dois modais não divergirem.
- **Não trafega conteúdo de prompt ou resposta**, só metadados de uso.

## Convenções de código

- **Nomes em inglês**, comentários em português.
- Evitar scope functions aninhadas (`let`, `apply`, `run`). Preferir fluxo explícito.
- Commits: Conventional Commits em inglês + `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`.

## Endpoints externos

| API | Endpoint | Auth |
|---|---|---|
| Anthropic | `GET https://api.anthropic.com/api/oauth/usage` | `Authorization: Bearer {accessToken}` do credentials.json + `anthropic-beta: oauth-2025-04-20` |
| MiniMax | `GET https://www.minimax.io/v1/token_plan/remains` | `Authorization: Bearer {MINIMAX_API_KEY}` |

Response Anthropic retorna `five_hour`/`seven_day` com `utilization` em **percentual** (0–100) e `resets_at` em ISO 8601 (pode ser nulo), mais `extra_usage`/`spend` com os créditos de uso em unidades menores da moeda da conta.

Response MiniMax retorna `model_remains[]` com cotas em **requests** (não tokens), timestamps em epoch milliseconds.
