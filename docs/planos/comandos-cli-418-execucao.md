# Opções de linha de comando — issue #418

## Objetivo

A #418 traz só uma captura de `code --help` (VS Code 1.142.0, recortada em `--user-data-dir`) e o texto
"Implementar esses comandos". O VS Code é um editor, então a lista não se transpõe inteira: este plano
separa as opções que têm análogo no Usage Monitor das que não têm e implementa o escopo aprovado:
parser, `--help`/`--version`, saída visível no console do Windows e `--locale`. `--user-data-dir` vai
para uma issue própria.

## Fatos confirmados (antes de qualquer código)

- Argumentos existentes: `--autostart` (`StartupOrigin.AUTO_START_ARGUMENT`, gravado pelo instalador na
  chave `Run`) e `--diagnostics` (`DiagnosticsExport.ARGUMENT`, #416). Argumento desconhecido é
  ignorado sem aviso.
- `Usage Monitor.exe` instalado é do **subsistema GUI** do Windows (campo `Subsystem` do cabeçalho PE = 2).
- `jvmToolchain(17)`; o runtime empacotado é o 17.0.20.1; JNA não está no classpath do app.
- O plugin Compose 1.12.1 expõe só `windows { console }`, que vale para o launcher **principal**. Ligado,
  o atalho e o autostart abririam uma janela de console. Não há `--add-launcher` na DSL.
- O JDK 17 traz os dois modelos de launcher do jpackage em `jdk.jpackage`:
  `jpackageapplauncher.exe` (console, subsistema 3) e `jpackageapplauncherw.exe` (GUI).
- `AutoStartManager` e `WindowsInstallOriginResolver` leem `jpackage.app-path`. Um app inteiro rodando
  pelo launcher de console gravaria esse executável na chave `Run`, e o login abriria um console.
- O NSIS empacota `build/installer/files` inteiro (`File /r`), e a desinstalação faz `RMDir /r "$INSTDIR"`.
- O instalador **não** mexe no `PATH`.
- No Linux, `--diagnostics` já funcionou no `.deb` do relator da #416 (L11 de
  `log-passivo-416-execucao.md`). Uma opção `--x` simples atravessa o launcher nativo.
  `--update-ack=X` (com `=`) foi recusado ali como opção da JVM (#118).

## Mapeamento da captura

| Opção do VS Code | Análogo no Usage Monitor | Decisão |
|---|---|---|
| `-d --diff`, `-m --merge`, `-g --goto`, `-a --add`, `--remove`, `-w --wait`, `--agents` | nenhum: são operações de editor sobre arquivos e pastas | fora |
| `-n --new-window` | contraria a instância única e a HUD como janela única | fora |
| `-r --reuse-window` | já é o comportamento padrão (pedido de foco à instância viva) | fora: seria um apelido sem efeito |
| `--locale <locale>` | idioma da interface (`AppLanguage` PT/EN) | **S4** |
| `--user-data-dir <dir>` | raiz `~/.usage-monitor` | issue separada: 40 arquivos citam `.usage-monitor`, há 70 leituras de `user.home` (várias de **outras** ferramentas, como `~/.claude`, que não podem mudar de lugar), e as preferências vivem no registro (`Preferences.userRoot().node("com.usagemonitor")`) |
| `-h --help`, `-v --version` (fora do recorte, mas presentes em todo CLI) | — | **S2** |

## Atividades

| Item | O que faz |
|---|---|
| S0 | Medir a saída do exe GUI no console e a alternativa de launcher de console (sem código) |
| S1 | `CommandLine.kt`: parser puro (`Help`/`Version`/`Diagnostics`/`Launch`/`Invalid`), testado; `main()` passa a decidir por ele; opção desconhecida vira aviso no `stderr` e no `app.log`, sem impedir a abertura |
| S2 | `--help`/`-h` e `--version`/`-v`: saída em inglês/ASCII (precedente da #416), antes do log, da instância única e do Compose |
| S3 | Windows: `usage-monitor.exe` (launcher de console do jpackage do **mesmo** JDK do `createDistributable`) + `app/usage-monitor.cfg`, adicionados em `prepareInstallerFiles`. Sem opção de ação, o processo de console relança `Usage Monitor.exe` destacado e sai: a chave `Run` e a origem da instalação continuam vendo o exe GUI |
| S4 | `--locale <pt\|en>`: idioma só desta execução, sem gravar a preferência. Um dono só para o idioma efetivo (`LanguageSelection`); escolher outro idioma nas Configurações encerra o override. Ignorado quando o app já está aberto (a instância nova sai pelo pedido de foco) |
| S7 | Documentação (`docs/build-and-release.md`, `README`, `CLAUDE.md`, template de bug) e issue nova para `--user-data-dir` (#419) |

S5 e S6 do levantamento (`-n`/`-r` e `--user-data-dir`) ficaram fora do escopo aprovado.

## Riscos

- **R1 — sem `PATH`.** O instalador não põe a pasta no `PATH`; o comando funciona pelo caminho completo
  (`%LOCALAPPDATA%\Usage Monitor\usage-monitor.exe`). Escrever o `PATH` do usuário pelo NSIS tem risco
  conhecido de truncar a variável no limite de string do NSIS; fica como decisão separada.
- **R2 — Linux e macOS não medidos nesta máquina** (#342). O parser e a saída são JVM pura; o launcher de
  console é só Windows. `--locale pt` (com espaço) é a forma documentada; `--locale=pt` também é aceita,
  mas a forma com `=` foi a que o launcher do Linux recusou na #118.
- **R3 — launcher de console sem ícone e sem recurso de versão.** O modelo do JDK não passa pelo passo
  que o jpackage usa para gravar ícone e versão no exe principal. O exe só aparece no terminal.
- **R4 — relançamento destacado.** O exe GUI filho não fica preso ao console, mas o fechamento do
  terminal com o app aberto só foi verificado com o app já em execução (caminho do pedido de foco).

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
|---|---|---|---|---|
| S0 | 2026-10-15 | este commit | concluída | Script PowerShell num terminal real (ConPTY), contra a instalação 41.11.0-beta.5. **A** — `& 'Usage Monitor.exe' --diagnostics`: voltou em 10 ms, sem texto e com `$LASTEXITCODE` vazio; o zip foi gerado (o processo rodou destacado). **B** — `cmd /c` sobre um `.cmd` que chama o exe: esperou (797 ms), `ERRORLEVEL=0`, **sem texto**. **C** — cópia da instalação com `jpackageapplauncher.exe` (extraído do `ms-17.0.15` por `jimage extract`) renomeado para `usage-monitor.exe` e `app/usage-monitor.cfg` copiado do `.cfg` principal: texto completo no terminal, esperou (914 ms), `exit=0`. O launcher renomeado acha o `.cfg` pelo próprio nome. No Git Bash a saída aparece nos três casos, porque o `stdout` é um pipe; a medição que vale é a do console. Zips de teste apagados |
| S1 | 2026-10-15 | `feat(cli): parse the command line in one place (#418)` | concluída | `CommandLine.kt`: `parseCommandLine` devolve `Diagnostics` ou `Launch(autostart, unknownOptions)`, com `trim` por argumento, igual ao que `StartupOrigin.from` sempre fez. `main()` decide por `when` exaustivo; `DiagnosticsExport.isRequested` saiu e o caso dele foi para `CommandLineTest`. Opção desconhecida: aviso em inglês/ASCII no `stderr` e linha `warn` (`source: command-line`) no `app.log`. **O app abre mesmo assim**: chave `Run` antiga ou launcher de outra plataforma não podem impedir a abertura. `StartupOrigin.from` fica como está, com a mesma constante, e o teste dele segue verde. `gradlew.bat desktopTest --tests "com.usagemonitor.CommandLineTest" --tests "com.usagemonitor.DiagnosticsExportTest" --tests "com.usagemonitor.StartupDiagnosticsTest" --tests "com.usagemonitor.architecture.*"`: 36 testes, 0 falhas |
| S2 | 2026-10-15 | `feat(cli): print help and version from the command line (#418)` | concluída | `-h`/`--help` e `-v`/`--version` no parser, tratados no `main()` antes do log, da instância única e do Compose. **Opção de leitura vence ação**: `--help --diagnostics` não grava zip, e a ajuda vence a versão. A ajuda lista só o que existe; `--autostart` fica fora (é a marca da chave `Run` para o registro de arranque, e digitá-la só falsifica a origem). O aviso de opção desconhecida passa a apontar para `--help`. Teste de que os três textos são ASCII. `gradlew.bat desktopTest --tests "com.usagemonitor.CommandLineTest" --tests "com.usagemonitor.architecture.*"`: 23 testes, 0 falhas. `gradlew.bat run --args="--help"` e `--args="-v"`: texto completo, `Usage Monitor 41.10.0`, saída 0, sem janela. A saída no exe **instalado** do Windows depende do S3 |
| S3 | 2026-10-15 | `feat(cli): ship a console launcher for the Windows command line (#418)` | concluída | **Build:** `prepareInstallerFiles` grava `usage-monitor.exe` (o `jpackageapplauncher.exe` lido pelo sistema de arquivos `jrt:` do `compose.desktop.application.javaHome`, o mesmo JDK do jpackage) e `app/usage-monitor.cfg` (cópia do `.cfg` principal). Medido em `build/installer/files`: subsistema 3 no novo, 2 no `Usage Monitor.exe`. `makensis /V4` lista os dois arquivos nas seções de instalação **e** de atualização (`File /r`). Nada muda no `.nsi`: a desinstalação já faz `RMDir /r`. **App:** `ConsoleLauncher.guiRelaunchCommand` (função pura, só Windows, nome comparado sem caixa) reconhece o launcher de console por `jpackage.app-path`. Sem opção de ação, o processo relança `Usage Monitor.exe` destacado (saídas descartadas) e sai, então a chave `Run` e a origem da instalação seguem vendo o exe GUI. Exe GUI ausente: mensagem e saída 1. `gradlew.bat desktopTest --tests "com.usagemonitor.ConsoleLauncherTest" --tests "com.usagemonitor.CommandLineTest" --tests "com.usagemonitor.architecture.*"`: 28 testes, 0 falhas. `gradlew.bat packageInstaller`: `UsageMonitor-Setup-41.10.0.exe`. **Medição no console (PowerShell, ConPTY)** com `build/installer/files/usage-monitor.exe`: `--help` imprime a ajuda (`exit=0`); `-v` imprime `Usage Monitor 41.10.0` (`exit=0`); `--bogus` imprime o aviso e volta em 266 ms (`exit=0`). **Depois** do retorno, o filho GUI (pid 4236, 41.10.0) grava o aviso no `app.log`, registra `second-instance-exit` e a instância viva (41.11.0-beta.5) registra `focus-request-served`. Isso prova que quem abriu o app foi o exe GUI, e não o de console. **R4 continua aberto:** o app já estava aberto, então "fechar o terminal com o app aberto pelo `usage-monitor.exe`" não foi exercitado |
| S4 | 2026-10-15 | `feat(cli): open the interface in another language for one run (#418)` | concluída | **Parser:** `--locale <v>` e `--locale=<v>`; `pt`/`en` sem caixa, com região aceita e descartada (`pt-BR`, `en_US` — a forma do `code --locale`); o último vence; sem valor (ou seguido de outra opção, que não é consumida) e valor desconhecido viram `Invalid`, saída 2 com a mensagem. Ajuda e versão continuam vencendo; `--locale` inválido barra também o `--diagnostics`. O comentário do `trim` do S1 dizia, sem evidência, que a chave `Run` antiga chegava com espaço; foi corrigido para o que é fato: o `trim` repete `StartupOrigin.from`, que tem teste. **Idioma efetivo:** `LanguageSelection` passa a ser o único leitor de `storedLanguage`. `AppGraph(breadcrumbs, languageOverride)` a monta; o exportador, os três pontos do `TelegramBotWiring` e o `AppShellState` (estado inicial e `changeLanguage`) leem dela. Escolher nas Configurações grava e encerra o override; o `--locale` nunca é gravado. `gradlew.bat desktopTest --tests "com.usagemonitor.CommandLineTest" --tests "com.usagemonitor.LanguageSelectionTest" --tests "com.usagemonitor.ConsoleLauncherTest" --tests "com.usagemonitor.architecture.*"`: 41 testes, 0 falhas. `gradlew.bat allTests`: **BUILD SUCCESSFUL**, `desktopTest` com 2868 testes, 0 falhas. `gradlew.bat run --args="--locale xx"`: `Error: 'xx' is not a supported --locale. Use pt or en.`, saída 2. **Não verificado na interface real:** o app instalado estava aberto, e a instância nova sai pelo pedido de foco antes de compor (comportamento documentado na ajuda). A HUD em inglês por `--locale en` só se vê com o app fechado |
| S7 | 2026-10-15 | `docs(cli): document the command-line options (#418)` | concluída | `README.md`/`README.pt-BR.md` ganham "Command line"/"Linha de comando": tabela das quatro opções, o porquê do `usage-monitor.exe` e o comando com caminho completo (sem `PATH`). `docs/build-and-release.md` registra as decisões (dono do `argv`, aviso que não bloqueia, segundo launcher, relançamento, `LanguageSelection`, sem `PATH`). `CLAUDE.md` ganha a regra de três linhas. O template `bug_report.yml` passa a dar o comando do `usage-monitor.exe` e mantém o caminho antigo, que **cria o zip, mas não imprime** (medido na S0, caso A). CHANGELOG não atualizado: parado desde 2026-09-02, as notas saem dos commits da release. `--user-data-dir` aberto como #419, com os fatos medidos (40 arquivos com `.usage-monitor`, 70 leituras de `user.home`, preferências no registro, trava da instância única em `~/.usage-monitor/app.lock`) e as decisões pendentes. Só documentação; nada a compilar |
| S3b | 2026-10-15 | `test(cli): build the console launcher paths with the platform separator (#418)` | concluída | **Regressão do S3, achada no CI do push `9f42f804`:** `desktop-linux` (consultivo, #342) reprovou 2 de 2868 testes (`ConsoleLauncherTest.kt:20` e `:31`). O run anterior (`c404c6f1`) estava verde. O teste usava o literal `C:\Users\someone\…`; no Linux `\` não separa diretório, `File(...).name` devolve a string inteira e a função devolve `null`. A produção não é afetada, porque só chama com `isWindows = true`, no Windows. O caminho passa a ser montado com `File(pai, nome)` sobre `java.io.tmpdir`. `gradlew.bat desktopTest --tests "com.usagemonitor.ConsoleLauncherTest" --tests "com.usagemonitor.architecture.*"`: 16 testes, 0 falhas (Windows). A validação no Linux é o próprio `desktop-linux` deste push. **Lacuna registrada no mesmo ponto:** nenhum job do `ci.yml` roda `prepareInstallerFiles`, e o `installer-windows` só liga para `src/installer/*` e `update/*`. O caminho da release (Temurin 17) foi verificado localmente: `gradlew.bat -Dorg.gradle.java.home=…/ms-17.0.15 prepareInstallerFiles` → runtime `17.0.15`, `usage-monitor.exe` de 419.328 bytes, subsistema 3, `-v` com saída 0 |
