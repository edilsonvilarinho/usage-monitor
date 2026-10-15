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
