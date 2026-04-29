# Usage Monitor

Desktop app em Kotlin Multiplatform + Compose Desktop para acompanhar consumo e quotas de uso de APIs de IA em um único painel. Hoje a app integra Anthropic, Codex e MiniMax, mostra janelas de uso de curto e longo prazo, persiste histórico localmente e permite configurar preferências como tema, idioma, APIs ativas e auto-start no Windows.

## O que a app faz

- Consolida métricas de uso de múltiplas APIs em um dashboard único.
- Atualiza automaticamente os dados a cada 10 minutos.
- Permite refresh manual global ou por card/API.
- Exibe estado parcial com sucesso mesmo quando uma ou mais APIs falham.
- Persiste snapshots de uso para consulta de histórico.
- Oferece tela de histórico separada do dashboard principal.
- Suporta tema claro/escuro, idioma PT/EN e auto-start no Windows.

## Screenshots

### Dashboard

![Dashboard](img/1.png)

### Histórico

![Histórico](img/2.png)

### Configurações

![Configurações](img/3.png)

## APIs monitoradas

### Anthropic

- Endpoint atual: `GET https://api.anthropic.com/api/oauth/usage`
- Auth: bearer token lido de `~/.claude/.credentials.json`
- Token usado: `claudeAiOauth.accessToken`
- Se o token estiver perto de expirar, o datasource local tenta refresh em `https://console.anthropic.com/v1/oauth/token`
- A app trabalha com janelas `five_hour` e `seven_day`

### Codex

- Endpoint: `GET https://chatgpt.com/backend-api/codex/usage`
- Auth:
  - bearer token de `~/.codex/auth.json` em `tokens.access_token`
  - cookie `cap_sid` lido de `~/.codex/cap_sid`
- O mapper converte `primary_window` e `secondary_window` para quotas de 5h e 7d

### MiniMax

- Endpoint: `GET https://www.minimax.io/v1/token_plan/remains`
- Auth: variável de ambiente `MINIMAX_API_KEY`
- A chave nunca deve ser hardcoded
- A app atualmente filtra quotas do modelo `MiniMax-M*`

## Stack

- Kotlin Multiplatform
- Compose Multiplatform Desktop
- Ktor + OkHttp
- Kotlinx Serialization
- Kotlin Coroutines
- Kotlinx Datetime
- Multiplatform Settings
- SQLite JDBC
- NSIS para instalador Windows

## Arquitetura

O projeto segue arquitetura em três camadas com dependência unidirecional:

`presentation -> domain <- data`

Regras principais:

- `domain` não pode importar Compose, Ktor ou qualquer detalhe de infra.
- Entidades e contratos do domínio não conhecem HTTP, JSON, ficheiros locais ou UI.
- `presentation` concentra ViewModels, `UiState` e componentes Compose.
- `data` contém DTOs, mappers, repositories e contratos de data source.
- Implementações que dependem de ficheiros locais do utilizador ficam em `desktopMain`.

## Source sets

| Source set | Papel |
|---|---|
| `commonMain` | Código partilhado de domain, data, presentation e UI |
| `desktopMain` | Bootstrap desktop, datasources locais, janela e integrações JVM |
| `commonTest` | Testes de domain, mappers e ViewModels |
| `desktopTest` | Testes de componentes Compose Desktop |
| `src/installer` | Scripts, idiomas e assets do instalador NSIS |

## Estrutura funcional

### Bootstrap e DI

O grafo é montado manualmente em `src/desktopMain/kotlin/com/usagemonitor/Main.kt`:

`HttpClient(OkHttp)` -> data sources locais/remotos -> repositories -> use cases -> `DashboardViewModel` / `HistoryViewModel` -> telas Compose

Dependências importantes montadas no bootstrap:

- `LocalCredentialDataSource`
- `LocalCodexAuthDataSource`
- `RemoteApiDataSource`
- `LocalUsageHistoryDataSource`
- `AnthropicRepositoryImpl`
- `CodexRepositoryImpl`
- `MiniMaxRepositoryImpl`
- `UsageHistoryRepositoryImpl`

### Dashboard

- O dashboard mostra cards por API com quotas e consumo.
- O footer expõe versão da app, contador para próximo refresh, acesso a histórico e configurações.
- O utilizador pode atualizar uma API específica sem recarregar o resto.
- Se ao menos uma API responder, a UI continua em `Success` e lista erros parciais.

### Polling e lifecycle

- `DashboardViewModel` usa `SupervisorJob`.
- O polling acontece num `while (true) + delay(1_000)` com ciclo de 600 segundos.
- Ao fim de cada ciclo, a app dispara nova coleta das APIs habilitadas.
- Cada snapshot bem-sucedido é persistido para histórico.
- `viewModel.onDestroy()` precisa ser chamado no fechamento da janela.
- `Main.kt` também fecha o `HttpClient` ao encerrar a aplicação e no shutdown hook.

## Preferências e persistência

Preferências do utilizador:

- Store: `PreferencesSettings(Preferences.userRoot().node("com.usagemonitor"))`
- Chaves persistidas:
  - `enabledApis`
  - `isDark`
  - `language`
  - `autoStart`

Persistência adicional:

- Histórico de uso é salvo localmente por `LocalUsageHistoryDataSource`
- `AutoStartManager` escreve em `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`

Observação importante:

- A app inicia com `enabledApis` vazio por padrão. As APIs são ativadas nas configurações.

## Requisitos locais

- Windows para experiência completa de desktop e instalador
- JDK 17
- Credenciais locais válidas para os serviços que quiser monitorar

### Variável obrigatória para MiniMax

```bat
set MINIMAX_API_KEY=your_key_here
```

Se a MiniMax estiver habilitada e a env var não existir, o projeto falha explicitamente.

### Ficheiros de autenticação esperados

- Anthropic: `~/.claude/.credentials.json`
- Codex: `~/.codex/auth.json`
- Codex cookie: `~/.codex/cap_sid`

## Como rodar

Todos os comandos abaixo usam `gradlew.bat` no PowerShell:

```bat
gradlew.bat run
gradlew.bat desktopJar
gradlew.bat build
gradlew.bat allTests
gradlew.bat desktopTest
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"
gradlew.bat createDistributable
gradlew.bat packageInstaller
gradlew.bat clean
```

Importante:

- A task raiz `test` não existe neste projeto KMP.
- Use `allTests`, `desktopTest` ou `build`.

## Build e distribuição

- `desktopJar` gera o JAR executável.
- `createDistributable` gera a distribuição desktop.
- `packageInstaller` empacota o instalador NSIS quando o NSIS estiver instalado.
- O projeto configura `TargetFormat.Exe`, `TargetFormat.Msi`, `TargetFormat.Deb` e `TargetFormat.Rpm`.
- A versão atual da app vem de `build.gradle.kts` e é propagada para `CURRENT_APP_VERSION`.

## Testes

- `commonTest` cobre domain, mappers e `DashboardViewModel`
- `desktopTest` cobre componentes Compose Desktop
- A suíte agregada esperada do projeto é `gradlew.bat allTests`

## Regras de código

- Nomes em inglês; comentários em português.
- Evitar nested scope functions como `let`, `apply` e `run`; preferir fluxo explícito.
- Componentes de UI devem ser stateless: dados por parâmetros, eventos por lambdas.
- `DashboardScreen` é o principal ponto stateful da UI.
- Mantenha a separação de camadas; não atravesse dependências do domínio.
- Datasources que leem ficheiros do utilizador devem ficar em `desktopMain`, não em `commonMain`.

## Regras operacionais importantes

- Não hardcode segredos.
- `MINIMAX_API_KEY` deve vir sempre do ambiente.
- Falha de uma API não deve cancelar as outras; por isso o uso de `SupervisorJob`.
- Ao alterar o bootstrap, preserve o fechamento explícito de `viewModel` e `HttpClient`.
- Ao alterar o dashboard, preserve o comportamento de sucesso parcial do `UiState`.

## Instalador NSIS

Lições importantes já validadas neste projeto:

- Use `SetCompressor zlib` no topo do `.nsi`
- Evite launch bloqueante com `ExecWait` no fluxo de sucesso

Sintomas conhecidos:

- Freeze em 99% costuma indicar problema de compressão LZMA
- Freeze na tela final costuma indicar processo bloqueante

Diagnóstico esperado:

- Analisar logs e imagens com atenção
- Confirmar o ponto exato do freeze antes de propor correção

## Convenção de commit

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

Regra obrigatória:

- Nunca rodar `git commit` ou `git push` sem pedido explícito do utilizador.

## Ficheiros relevantes

- `AGENTS.md`: regras de trabalho e manutenção do repositório
- `build.gradle.kts`: build, versão, distribuição e tarefas do instalador
- `docs/research.md`: pesquisa histórica de integrações e decisões iniciais
- `src/installer/UsageMonitor.nsi`: script do instalador NSIS
