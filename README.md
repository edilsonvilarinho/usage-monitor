# Usage Monitor

Desktop app em Kotlin Multiplatform + Compose Desktop para acompanhar consumo e quotas de uso de APIs de IA em um único painel. Hoje a app integra Anthropic, Codex e MiniMax, mostra janelas de uso de curto e longo prazo, persiste histórico local em SQLite, permite reorganizar/minimizar cards, verifica atualizações publicadas no GitHub Releases e oferece auto-start em Windows e Linux.

## O que a app faz

- Consolida métricas de uso de múltiplas APIs em um dashboard único.
- Atualiza automaticamente os dados a cada 10 minutos.
- Permite refresh manual global ou por card/API.
- Exibe estado parcial com sucesso mesmo quando uma ou mais APIs falham.
- Persiste snapshots de uso para consulta de histórico.
- Oferece tela de histórico separada do dashboard principal com tendência, média por hora e previsão de esgotamento.
- Permite reordenar cards por drag-and-drop e minimizar/expandir cada card com persistência local.
- Verifica releases novas e tenta preparar instalação automática quando há pacote compatível com a plataforma.
- Suporta tema claro/escuro, idioma PT/EN e auto-start em Windows e Linux.

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
- `data` contém DTOs, mappers, repositories e contratos de data source, incluindo histórico e checagem de updates.
- Implementações que dependem de ficheiros locais, SQLite, processo do SO ou instalador ficam em `desktopMain`.

## Source sets

| Source set | Papel |
|---|---|
| `commonMain` | Código partilhado de domain, data, presentation e UI |
| `desktopMain` | Bootstrap desktop, datasources locais, SQLite de histórico, `AutoStartManager`, `DesktopAppUpdateInstaller`, janela e integrações JVM |
| `commonTest` | Testes de domain, mappers e ViewModels |
| `desktopTest` | Testes de componentes Compose Desktop |
| `src/installer` | Scripts, idiomas e assets do instalador NSIS |

## Estrutura funcional

### Bootstrap e DI

O grafo é montado manualmente em `src/desktopMain/kotlin/com/usagemonitor/Main.kt`:

`HttpClient(OkHttp)` -> data sources locais/remotos -> repositories de uso/histórico/update -> use cases -> `DashboardViewModel` / `HistoryViewModel` -> telas Compose

Dependências importantes montadas no bootstrap:

- `LocalCredentialDataSource`
- `LocalCodexAuthDataSource`
- `RemoteApiDataSource`
- `LocalUsageHistoryDataSource`
- `AnthropicRepositoryImpl`
- `CodexRepositoryImpl`
- `MiniMaxRepositoryImpl`
- `UsageHistoryRepositoryImpl`
- `AppUpdateRepositoryImpl`
- `DesktopAppUpdateInstaller`

Observações adicionais:

- `CURRENT_APP_VERSION` é gerada a partir de `build.gradle.kts` em tempo de build.
- O `Main.kt` também persiste ordem dos cards, estado minimizado e preferências de auto-start.

### Dashboard

- O dashboard mostra cards por API com quotas e consumo.
- O footer expõe versão da app, contador para próximo refresh, acesso a histórico e configurações.
- O utilizador pode atualizar uma API específica sem recarregar o resto.
- Se ao menos uma API responder, a UI continua em `Success` e lista erros parciais.
- Os cards podem ser reordenados por drag-and-drop sem perder a posição das APIs ocultas.
- Cada card pode ser minimizado; o estado fica persistido entre sessões.
- A UI também exibe banners persistentes para problemas de configuração e para updates disponíveis.

### Histórico e forecast

- Cada card pode abrir um diálogo de histórico focado na API correspondente.
- A tela de histórico suporta intervalos de `24h`, `7 dias` e `30 dias`.
- O repositório de histórico calcula consumo acumulado, média por hora e previsão de esgotamento.
- A série ativa ignora snapshots de janelas antigas após reset para não distorcer o forecast.

### Polling e lifecycle

- `DashboardViewModel` usa `SupervisorJob`.
- O polling acontece num `while (true) + delay(1_000)` com ciclo de 600 segundos.
- Ao fim de cada ciclo, a app dispara nova coleta das APIs habilitadas.
- Cada snapshot bem-sucedido é persistido para histórico.
- Ao fim de cada ciclo, a app também verifica se existe release mais nova publicada.
- `viewModel.onDestroy()` precisa ser chamado no fechamento da janela.
- `Main.kt` fecha `DashboardViewModel`, `HistoryViewModel` e `HttpClient` ao encerrar a aplicação e no shutdown hook.
- Se um update automático for preparado com sucesso, a app fecha para delegar a instalação ao launcher temporário.

## Preferências e persistência

Preferências do utilizador:

- Store: `PreferencesSettings(Preferences.userRoot().node("com.usagemonitor"))`
- Chaves persistidas:
  - `enabledApis`
  - `isDark`
  - `language`
  - `autoStart`
  - `cardOrder`
  - `minimizedCards`

Persistência adicional:

- Histórico de uso é salvo por `LocalUsageHistoryDataSource` em `~/.usage-monitor/usage-history.db`
- O histórico aplica retenção de 30 dias
- Snapshots sem janela de reset conhecida não entram no histórico
- `AutoStartManager` escreve em `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` no Windows
- No Linux, o auto-start usa `~/.config/autostart/usage-monitor.desktop` ou `XDG_CONFIG_HOME/autostart/usage-monitor.desktop`

Observação importante:

- A app inicia com `enabledApis` vazio por padrão. As APIs são ativadas nas configurações.

## Requisitos locais

- Windows ou Linux para rodar a app desktop
- JDK 17
- Credenciais locais válidas para os serviços que quiser monitorar

Notas por plataforma:

- Windows: fluxo completo com `packageInstaller`, instalador NSIS, auto-start por registry e preparação automática via `.msi`.
- Linux: suporte a auto-start por `.desktop`, empacotamento nativo do Compose e instalação automática quando houver `.deb` e `pkexec`.

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
- `createDistributable` gera a distribuição desktop em `build/compose/binaries/main/app/Usage Monitor`.
- `packageDistributionForCurrentOS` gera os pacotes nativos da plataforma atual e é usado na pipeline de release.
- `packageInstaller` empacota o instalador NSIS quando o NSIS estiver instalado.
- `build-with-icon.ps1` é um fluxo auxiliar Windows para gerar distributable, aplicar ícone com `rcedit` e chamar o NSIS manualmente.
- O projeto configura `TargetFormat.Exe`, `TargetFormat.Msi`, `TargetFormat.Deb` e `TargetFormat.Rpm`.
- A versão atual da app vem de `build.gradle.kts` e é propagada para `CURRENT_APP_VERSION`.
- Tags `v*` disparam `.github/workflows/release-linux.yml`, que hoje publica artefatos Linux e Windows no GitHub Release.

## Testes

- `commonTest` cobre domain, mappers, histórico/forecast, layout dos cards e `DashboardViewModel`
- `desktopTest` cobre datasource SQLite e componentes Compose Desktop do dashboard, histórico e configurações
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
- Ao alterar histórico, preserve a retenção local e a regra de ignorar quotas sem reset conhecido.
- Ao alterar releases, alinhe `build.gradle.kts`, `CURRENT_APP_VERSION`, assets de release e o instalador/plataforma suportada.

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

## Automação e documentos auxiliares

Materiais a tratar como fonte de verdade:

- `AGENTS.md`
- `README.md`

Materiais auxiliares já presentes no repositório:

- `.agents/skills/slash/commit-push.md`
- `.agents/skills/slash/release.md`
- `.claude/skills/nsis-installer.md`
- `build-with-icon.ps1`

Observações importantes:

- `docs/research.md` é histórico de descoberta e não substitui o contrato atual implementado no código.
- Arquivos auxiliares legados como `CLAUDE.md` e `.claude/skills/*` devem ser validados contra `AGENTS.md` e este `README` antes de serem reutilizados.

## Ficheiros relevantes

- `AGENTS.md`: regras de trabalho e manutenção do repositório
- `build.gradle.kts`: build, versão, distribuição e tarefas do instalador
- `src/desktopMain/kotlin/com/usagemonitor/Main.kt`: bootstrap manual, preferências e composição principal
- `src/commonMain/kotlin/com/usagemonitor/presentation/viewmodel/DashboardViewModel.kt`: polling, estado parcial, snapshots e update flow
- `src/commonMain/kotlin/com/usagemonitor/data/repository/UsageHistoryRepositoryImpl.kt`: agregação histórica e forecast
- `.github/workflows/release-linux.yml`: pipeline de release para artefatos Linux e Windows
- `docs/research.md`: pesquisa histórica de integrações e decisões iniciais
- `src/installer/UsageMonitor.nsi`: script do instalador NSIS
