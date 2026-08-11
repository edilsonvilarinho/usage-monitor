# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bat
# Rodar a aplicação Desktop
gradlew.bat run

# Compilar sem rodar
gradlew.bat desktopJar

# Todos os testes (domain + data + ViewModel + UI)
gradlew.bat test

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

- `QuotaInfo`: entidade com `percentageUsed` e `remaining` calculados. `UsageUnit` diferencia `TOKENS` (Anthropic) de `REQUESTS` (MiniMax).
- `ApiUsageStats`: agrega lista de `QuotaInfo` por API.
- Interfaces `AnthropicRepository` / `MiniMaxRepository`: o domain define o contrato; `data` implementa.
- Use cases usam `operator fun invoke()` — chamados como `useCase()`.

### Camada data (`commonMain/data/` + `desktopMain/data/`)

- DTOs com `@Serializable` + `@SerialName` para mapear snake_case do JSON.
- `AnthropicRateLimitDto` **não é JSON** — populado manualmente dos headers HTTP.
- `LocalCredentialDataSource` em **desktopMain** (usa `java.io.File`). Lê `~/.claude/.credentials.json` → `claudeAiOauth.accessToken`. Valida `expiresAt`.
- `RemoteApiDataSource`: Anthropic faz `POST /v1/messages` com payload mínimo (claude-haiku, max_tokens=1) só para ler os headers de rate limit. MiniMax faz `GET /v1/token_plan/remains`.
- `MiniMaxRepositoryImpl` lê `System.getenv("MINIMAX_API_KEY")` — nunca hardcode.
- Ambos os repos usam `Result.runCatching { }` para encapsular falhas.

### Camada presentation (`commonMain/presentation/`)

- `UiState`: `sealed interface` com `Loading`, `Success(data)`, `Error(message)`. Se uma API falhar e outra tiver sucesso, emite `Success` com dados parciais.
- `DashboardViewModel`: `StateFlow<UiState>` + polling silencioso via `while(true) + delay(10 * 60 * 1_000L)`. Escopo com `SupervisorJob` — falha de uma coroutine não cancela as outras. Chamar `onDestroy()` ao fechar janela.
- Componentes UI: **todos stateless** (recebem dados via parâmetros, emitem eventos via lambdas). `DashboardScreen` é o único stateful.
- Timezone de reset: sempre `TimeZone.of("America/Sao_Paulo")` com label `BRT`.

### Injeção de dependências

Manual, em `Main.kt` (desktopMain). Sem framework. Sequência: `HttpClient(OkHttp)` → datasources → repos → use cases → `DashboardViewModel` → `DashboardScreen`.

## Integração com time (`server/`)

Recurso opcional, desligado por default. Servidor Node.js **self-hosted pela empresa** (Express 4 + TypeScript + SQLite) que recebe os turnos indexados de cada máquina e devolve a visão agregada por conta Anthropic. Contrato da API e passo a passo de deploy no Dokploy em [`server/README.md`](server/README.md).

- **Chave de agrupamento:** o `accountUuid` da conta (`UsageAccountKey.providerAccountId`), sem o `organizationUuid` — este é nulo em parte das instalações e usá-lo na chave quebraria o agrupamento entre máquinas da mesma conta.
- **Configuração** em `~/.usage-monitor/team.json` (`LocalTeamSettingsDataSource`), com escrita atômica e `restrictToOwnerReadWrite`. Nunca em `PreferencesSettings`: a chave do servidor é segredo e as preferências vão em claro para o registro.
- **Envio:** `TeamSyncService`, laço de 30s, roda com a janela fechada. Marcador em `team_sync_state` (mesmo `usage-history.db`), pela conexão **compartilhada** do `LocalCliSessionDataSource` — duas conexões para o mesmo arquivo dariam `SQLITE_BUSY`.
- **Cada passada indexa antes de enviar** (`ensureIndexFresh`, ligado ao mesmo `SyncCliSessionIndexUseCase` da tela de Sessões CLI). O serviço só enxerga turno que já está no índice: sem isso a latência seria a do laço de background (10min), não a dos 30s. Falha na indexação não cancela o envio do que já está indexado. Abrir o modal do time chama `requestImmediateSync()` e antecipa uma passada.
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
| Anthropic | `POST https://api.anthropic.com/v1/messages` | `Authorization: Bearer {accessToken}` do credentials.json |
| MiniMax | `GET https://www.minimax.io/v1/token_plan/remains` | `Authorization: Bearer {MINIMAX_API_KEY}` |

Response MiniMax retorna `model_remains[]` com cotas em **requests** (não tokens), timestamps em epoch milliseconds.
