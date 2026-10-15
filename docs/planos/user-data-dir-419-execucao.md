# Raiz de dados por execução (`--user-data-dir`) — issue #419

## Objetivo

Desdobramento da #418: `--user-data-dir <dir>` troca a raiz `~/.usage-monitor` desta execução. A
finalidade aprovada é **isolar dados para teste e para um segundo perfil, com instâncias em paralelo**,
e não uma instalação "portátil": as preferências continuam no registro/`java.util.prefs`, num nó
próprio da raiz.

## Fatos confirmados (antes de qualquer código, `main` em `dd8fff8b`)

- 22 funções `default*()` montam `user.home/.usage-monitor/...` cada uma por conta própria. Quase todas
  são o default de um parâmetro de construtor (`LocalApiKeyDataSource(settingsFile = …)`,
  `AppLogFile(logFile = …)`, `SingleInstanceGuard.tryAcquire(lockFile = …)`): o caminho já é injetável.
- As preferências têm dono único: `AppGraph.kt:97`, `Preferences.userRoot().node("com.usagemonitor")`. Os
  registros de perfil Anthropic e Codex usam nós **filhos** do nó injetado.
- 8 leituras de `user.home` são de **outras** ferramentas (Claude, Codex, Cursor, Gemini, Kilo,
  OpenCode) e não podem acompanhar a raiz.
- Contratos fora do app:
  - o NSIS grava o recibo em `$PROFILE\.usage-monitor\update-receipt.properties` (`.nsi:395`);
  - os dois atualizadores relançam o app **sem argumentos** (`.nsi:374`, `linux-updater.sh:253`);
  - a chave `Run` só passa `--autostart`.
- O `device_id` do time mora no `team.json`.

## Classificação dos arquivos

| Escopo | Arquivos | Com `--user-data-dir` |
|---|---|---|
| Dados | `api-keys.json`, `usage-history.db`, `codex-cli-history.db`, `dashboard-cache.json`, `proxy.json`, `team.json`, `telegram.json`, `web-access.json`, `antigravity-work/`, `diagnostics/*` (exceto o abaixo), nó de preferências | raiz nova |
| Instância | `app.lock`, `focus.request` | raiz nova — duas raízes rodam em paralelo |
| Instalação | `update-receipt.properties`, `update-ack`, `updates/`, `diagnostics/linux-update.log` | raiz padrão sempre: scripts de fora usam caminho fixo |

## Decisões aprovadas

1. Finalidade: isolamento para teste e segundo perfil, em paralelo; não portátil.
2. Fora da raiz padrão, atualização automática e autostart ficam **bloqueados com o motivo visível**.
   Repassar a raiz no relançamento mexeria no `.nsi` e no `linux-updater.sh`.
3. Preferências: nó irmão derivado do caminho canônico, `com.usagemonitor.data.<16 hex do SHA-256>`.
4. Bandeja: nome da pasta no tooltip quando a raiz não é a padrão.

## Atividades

| Item | O que faz |
|---|---|
| U1 | `AppDataDirectory` (`data/`): raiz única do processo, configurada uma vez no `main()`. Configurar depois da primeira leitura **lança erro**. As funções `default*()` derivam dela; os arquivos de instalação usam a raiz padrão fixa. Regra no `ArchitectureRulesTest` proíbe o literal `".usage-monitor` em produção fora do dono. Sem a opção, nenhum caminho muda |
| U2 | Nó de preferências derivado da raiz; raiz padrão continua `com.usagemonitor` |
| U3 | Parser `--user-data-dir <dir>`/`=<dir>`, caminho canônico, `Invalid` (saída 2) para valor ausente, arquivo no lugar ou pasta que não pode ser criada. O relançamento do `usage-monitor.exe` leva o caminho já absoluto (o exe GUI roda com outro diretório de trabalho). `--diagnostics` empacota a raiz pedida |
| U4 | Fora da raiz padrão: atualização automática e autostart desligados também na lógica, e não só no controle, com o motivo em Configurações → Sistema; nome da pasta no tooltip da bandeja. Protótipo e design system no mesmo commit |
| U5 | Medição no Windows com duas instâncias em paralelo; documentação |

## Riscos

- **R1 — renovação OAuth concorrente.** Duas instâncias leem as mesmas credenciais do Claude Code e
  podem renovar ao mesmo tempo; a rotação do refresh token pode invalidar a outra. A classe de risco já
  existe entre o app e o próprio Claude Code. Não medido.
- **R2 — `web-access.json` copiado** com a mesma porta: a segunda instância falha ao subir o servidor.
- **R3 — `team.json` copiado** leva o mesmo `device_id`: as duas instâncias aparecem como um aparelho só.
- **R4 — textos de ajuda** citam `~/.usage-monitor/...` e ficam inexatos numa raiz alternativa.
- **R5 — nós de raízes alternativas** ficam no registro depois de a pasta ser apagada.
- **R6 — Linux e macOS** não medidos nesta máquina (#342).

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
|---|---|---|---|---|
| U1 | 2026-10-15 | `refactor(data): resolve every app data path from one root (#419)` | concluída | `data/AppDataDirectory.kt`: objeto do processo que delega para `AppDataRoot`, uma classe testável que o teste instancia sem tocar no estado global. `file()` dá o escopo de dados e instância; `installationFile()` dá a raiz padrão fixa. Configurar depois da primeira leitura (inclusive de `isDefault`) lança `IllegalStateException`. As 22 montagens viraram chamadas ao dono: 18 `file(…)` e 4 `installationFile(…)` (`update-ack`, `update-receipt.properties`, `updates`, `diagnostics/linux-update.log`). O recibo e o diretório do Antigravity mantêm o fallback relativo de antes quando falta `user.home`. `--diagnostics` lê o `linux-update.log` da raiz de instalação (`installationDiagnosticsDir`, que por padrão é a mesma pasta). Regra nova no `ArchitectureRulesTest`: literal que começa em `".usage-monitor` (seguido de `/` ou `"`) só no dono. Texto de ajuda (`~/.usage-monitor/…`) e o marcador `.usage-monitor-managed` não casam, e há teste do padrão. `gradlew.bat allTests`: **BUILD SUCCESSFUL**, `desktopTest` com 2876 testes, 0 falhas (`AppDataRootTest` 5, `DiagnosticsExportTest` 5, `ArchitectureRulesTest` 13). `gradlew.bat run --args="--diagnostics"`: zip com `environment.txt`, `app.log`, `startup.jsonl`, `hud-window.jsonl`, `breadcrumbs.jsonl`, os mesmos arquivos da L8 da #416, lidos da raiz padrão. Zip apagado |
| U2 | 2026-10-15 | `feat(data): keep each data root's preferences in its own node (#419)` | concluída | `AppDataRoot.preferencesNodeName`: a raiz padrão continua em `com.usagemonitor`, sem migração para quem nunca usou a opção; uma raiz configurada usa o nó **irmão** `com.usagemonitor.data.<16 hex do SHA-256 do caminho>`. Irmão porque `removeNode` apaga a subárvore. Hash porque o caminho cru pode passar de 80 caracteres e traz `/`, que no `java.util.prefs` separa nós. Ler o nome conta como leitura da raiz. `AppGraph` abre o nó por ele; os registros de perfil Anthropic e Codex recebem o nó injetado e seguem sem mudança. `gradlew.bat desktopTest --tests "com.usagemonitor.data.AppDataRootTest" --tests "com.usagemonitor.architecture.*"`: 21 testes, 0 falhas. O isolamento real no registro fica para a medição da U5 |
| U3 | 2026-10-15 | `feat(cli): run with another data root through --user-data-dir (#419)` | concluída | **Parser:** `--user-data-dir <dir>` e `=<dir>`, pela mesma leitura de valor do `--locale` (`optionValue`, que não consome a próxima opção). Sem pasta, `Invalid` com saída 2. `Diagnostics` vira `data class` com a pasta. Ajuda com a linha nova. **Disco** (`resolveDataRoot`): caminho relativo resolve contra `user.dir`; o resultado é canônico (`perfil/../perfil` = `perfil`); cria a pasta que falta; arquivo no lugar ou pasta que não pode ser criada dão erro; pedir a própria raiz padrão é `Default`, para não abrir um segundo nó de preferências. A raiz padrão sai de `defaultAppDataRoot()`, que não trava o `configure`. **`main()`:** configura antes de qualquer arquivo, no `--diagnostics` e no arranque normal. **Relançamento:** `withUserDataDir` troca o valor digitado pelo caminho absoluto (ou remove a opção na raiz padrão), porque o exe GUI sobe com outro diretório de trabalho. `gradlew.bat desktopTest --tests "com.usagemonitor.CommandLineTest" --tests "com.usagemonitor.DataRootResolutionTest" --tests "com.usagemonitor.ConsoleLauncherTest" --tests "com.usagemonitor.architecture.*"`: 49 testes, 0 falhas. `gradlew.bat run --args="--diagnostics --user-data-dir <scratchpad>\u3-raiz"`: pasta criada e zip com **só** `environment.txt`, prova de que a exportação leu a raiz nova (vazia), e não a padrão. Zip apagado. `--user-data-dir <…>\arq` (arquivo): `Error: '…\arq' is a file. --user-data-dir needs a folder.`, saída 2. O arranque completo numa raiz isolada fica para a U5 |
| U4 | 2026-10-15 | `feat(settings): keep startup and updates off on an isolated data root (#419)` | concluída | **Atualização:** `AppUpdateSupport.ISOLATED_DATA_ROOT`, valor novo por exceção declarada (precedente de `UNSUPPORTED_ARCHITECTURE`: o `when` exaustivo de `autoUpdateHint` obriga o texto, e sem o valor a raiz isolada cairia num motivo falso). Com a raiz isolada, o controlador nasce **sem instalador**, e o `DashboardViewModel` só recebe instalador por ele, então nenhum caminho baixa ou aplica; o motivo vence qualquer outro; a poda da pasta `updates/` (instalação) não roda. **Autostart:** `AutoStartToggle(available)` desligado mostra o motivo e aparece desligado mesmo com o sistema ligado (o valor é da outra instância). O efeito de arranque (`AutoStartResolution`) não lê nem reescreve a entrada nem o ícone de menu do Linux; o achado é que ele **reescrevia** a entrada no arranque sem clique (`ensureAutoStartCommandCurrent`). `SettingsActions.changeAutoStart` tem segunda trava. **Bandeja:** `trayAppName` põe `Usage Monitor (<pasta>)` no começo do tooltip, porque o corte de 127 caracteres come o fim. A ajuda passa a dizer que atualização e início com o sistema ficam desligados. **Visual:** duas linhas novas no painel de indisponibilidade da §12 do protótipo e nota da bandeja na §3; motivos registrados no kit `Settings.jsx`. Primitiva: nenhuma nova, porque o `AppSwitch` já tem o contrato de motivo. `gradlew.bat allTests`: **BUILD SUCCESSFUL**, `desktopTest` com 2897 testes, 0 falhas (`AutoStartToggleTest` 3, `AutoUpdateToggleTest` 18, `IsolatedDataRootTest` 3). A tela real fica para a U5 |
| U5 | 2026-10-15 | `docs(data): measure two data roots side by side and document --user-data-dir (#419)` | concluída | **Medição no Windows**, com a instalada (41.11.0-beta.5, pid 40944, raiz padrão) aberta: `gradlew.bat run --args="--user-data-dir <scratchpad>\u5-raiz"` subiu (pid 30436, 41.10.0, `window-shown` em 3,1 s) **sem** `IllegalStateException`, ou seja, nenhuma leitura da raiz antes do `configure`. Na raiz nova: `app.lock`, `usage-history.db`, `codex-cli-history.db`, `team.json` e `diagnostics/{app.log,breadcrumbs.jsonl,hud-window.jsonl,startup.jsonl}`. O índice CLI leu as sessões de `~/.claude`, o que confirma que as pastas de outras ferramentas não mudam. Na raiz padrão: **zero** linhas do pid 30436 em `startup.jsonl` e `app.log`. Registro: nó novo `HKCU\Software\JavaSoft\Prefs\com.usagemonitor.data.9f1b26e1181efb0f`, e `com.usagemonitor` intocado. Chave `Run` idêntica antes e depois (`"…\Usage Monitor.exe" --autostart`). Segunda abertura na mesma raiz: o pid 568 registra `second-instance-exit` e o 30436 registra `focus-request-served`, tudo no `startup.jsonl` da raiz isolada; a raiz padrão não ganhou linha. Instância de teste encerrada por `taskkill` (sinal normal, sem `/F`) em cerca de 1 s; a instalada seguiu de pé. **Fatos à parte:** os dois `error` "validar perfil Anthropic falhou: accessToken ausente" da instância isolada também aparecem 8 vezes no `app.log` da instalada, então são pré-existentes e não vêm da raiz isolada. **Não verificado:** a aba Sistema e o tooltip da bandeja na tela real, porque não foi possível capturar a janela; ficam cobertos pelos testes de componente da U4. O nó de teste `com.usagemonitor.data.9f1b26e1181efb0f` ficou no registro (R5). Documentação: tabela da linha de comando nos dois READMEs, decisões em `docs/build-and-release.md`, regra no `CLAUDE.md` |
