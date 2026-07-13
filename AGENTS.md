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
- Se o token estiver perto de expirar, `LocalCredentialDataSource` tenta refresh em `https://console.anthropic.com/v1/oauth/token`
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
- `AutoStartManager` escreve em `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`

## UI and presentation

- Nomes em ingles, comentarios em portugues.
- Evitar nested scope functions (`let`, `apply`, `run`). Preferir fluxo explicito.
- Componentes de UI devem ser **stateless**: dados por params, eventos por lambdas.
- `DashboardScreen` e o ponto stateful principal da UI.
- `UiState` aceita sucesso parcial: se pelo menos uma API responder, a tela mostra os dados e lista erros das APIs que falharam.

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
