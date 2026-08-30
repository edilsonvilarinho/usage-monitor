# Ícone estável na entrada de menu do Linux — execução

**Issue:** [#133](https://github.com/edilsonvilarinho/usage-monitor/issues/133)
**Branch:** `fix/linux-menu-icon-stable` · **Worktree:** `../usage-monitor-133`

## Contexto

Ao investigar a issue #120 na Bazzite do reportante, o ícone do Usage Monitor sumiu do menu de
aplicativos do KDE. A causa imediata foi cache do Plasma (`kbuildsycoca6 --noincremental` resolveu).
Mas inspecionando o `.desktop` de **menu** (`~/.local/share/applications/usage-monitor.desktop`,
gerado só pelo instalador `.sh` — arquivo diferente do `.desktop` de **autostart**, já corrigido na
#120) apareceu um defeito estrutural real, registrado na issue #133:

```
Exec="/home/edi/.local/bin/usage-monitor"                                                      # launcher estável
Icon=/home/edi/.local/share/usage-monitor/versions/38.0.2/Usage Monitor/lib/Usage Monitor.png  # árvore versionada
```

`Exec=` usa o launcher estável, que sobrevive a atualizações. `Icon=` não — ele nomeia a pasta da
versão instalada no momento em que o `.sh` foi rodado. O `linux-updater.sh` retém só a versão atual
mais a imediatamente anterior (cenário S10 do harness já prova isso); toda pasta mais antiga é
apagada a cada atualização bem sucedida. Resultado: duas atualizações depois de instalado, o arquivo
que o `Icon=` nomeia deixa de existir e o ícone some do menu — silenciosamente, sem log, sem erro.

É a mesma classe de defeito do `Path=` entre aspas do autostart (#120), e a correção segue o mesmo
princípio já estabelecido naquele trabalho: **"correção que não chega a quem já está afetado não é
correção"**.

## Decisão de desenho

Investigação por dois agentes (exploração + validação de plano) convergiu numa opção estritamente
melhor que só corrigir o instalador `.sh`:

- **O app já carrega os próprios bytes do ícone no classpath** (`/icons/app_icon.png`, embutido pelo
  `build.gradle.kts`), e já os usa para o ícone da janela (`loadWindowIcon()` em `Main.kt`). Não há
  necessidade de ler o `.png` de dentro da árvore versionada — o app tem cópia própria, sempre
  correta, sempre disponível.
- **O padrão de auto-reparo já existe**, para o problema irmão: `AutoStartManager.
  ensureAutoStartCommandCurrent()` roda a cada abertura do app e conserta uma entrada de autostart
  já quebrada, sem exigir que o usuário desligue/religue nada.

Replicar esse padrão para a entrada de **menu** (arquivo diferente, dono diferente — autostart mora
em `~/.config/autostart/`, menu em `~/.local/share/applications/`) alcança quem já está afetado na
próxima abertura do app, não lê da árvore prunável (sem corrida com o `linux-updater.sh`), e é
testável no Windows do CI com a mesma disciplina de função pura + wrapper de IO já usada em
`AutoStartManager`/`LinuxInstallLayout`.

**Escopo: só Kotlin**, confirmado com o usuário. O instalador `.sh` continua escrevendo o caminho
antigo numa instalação nova; o primeiro lançamento do app já conserta — sem gap prático. Nenhuma
mudança em `linux-updater.sh`: ele já relança o app promovido (passo 6) *antes* de podar as versões
antigas (passo 9), e é esse relançamento que já dispara o reparo em Kotlin antes de qualquer coisa
ser apagada.

## Micro-atividades

| # | Micro-atividade | Toca |
|---|---|---|
| A01 | Branch/worktree + este plano + comentário na issue #133 com a tabela de atividades | `docs/planos/` |
| A02 | Constante + membros de `LinuxInstallLayout` + `resolveLinuxMenuDesktopFilePath` | `LinuxInstallLayout.kt` |
| A03 | Funções puras `linuxMenuIconNeedsRepair` / `rewriteLinuxMenuIconLine` + testes | `LinuxMenuIcon.kt` (novo) |
| A04 | `ensureLinuxMenuIconCurrent` (wrapper de IO) + testes injetados | `LinuxMenuIcon.kt` |
| A05 | Fiação em `Main.kt` (constante `internal` + chamada no `LaunchedEffect`) | `Main.kt` |
| A06 | Verificação ao vivo na Bazzite: build da branch, `.desktop` de menu quebrado à mão é reparado na próxima abertura | — |
| A07 | PR pra `main` + comentário de fechamento na issue #133 | — |

## Pontos de situação

Uma linha por micro-atividade, escrita **no mesmo commit** da micro-atividade.

| # | Data | Commit | Micro-atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-08-30 | `docs(plan): open the execution plan for the linux menu icon issue` | Este plano | concluída | Worktree criada com `git worktree add ../usage-monitor-133 -b fix/linux-menu-icon-stable main`. Plano publicado como comentário na issue #133 |
| A02 | 2026-08-30 | `feat(update): add a stable menu icon path to the linux install layout` | Constante + membros de `LinuxInstallLayout` + `resolveLinuxMenuDesktopFilePath` | concluída | `LINUX_MENU_ICON_FILE_NAME = "icon.png"`, `LinuxInstallLayout.iconPath`/`iconFile` ao lado de `markerPath`/`currentPath`; `resolveLinuxMenuDesktopFilePath` espelha `resolveLinuxStableLauncherPath` (sai de `user.home`, não de `XDG_DATA_HOME`), resolvendo `~/.local/share/applications/usage-monitor.desktop` — arquivo diferente do `~/.config/autostart/` que `AutoStartManager` já resolve. `gradlew.bat desktopTest --tests "com.usagemonitor.update.LinuxInstallLayoutTest"`: **BUILD SUCCESSFUL**, `tests="19" skipped="0" failures="0" errors="0"` (17 anteriores + 2 novos: caminho do ícone na listagem de paths, e o resolvedor da entrada de menu) |
| A03 | 2026-08-30 | `feat(update): add pure functions to detect and repair a stale menu icon` | Funções puras `linuxMenuIconNeedsRepair`/`rewriteLinuxMenuIconLine` + testes | concluída | `linuxMenuIconNeedsRepair`: entrada ausente não repara (mesma regra do autostart); `Icon=` já correto não repara; `Icon=` na árvore versionada ou linha `Icon=` ausente reparam. `rewriteLinuxMenuIconLine` troca só a linha `Icon=`, preservando todas as outras na ordem original — provado com uma linha `Comment=` hand-edited sobrevivendo byte a byte. **Descoberta:** `lineSequence()` sobre um texto terminado em `\n` produz um último elemento vazio; sem descartá-lo antes de reconstruir, o `joinToString` deixava uma linha em branco extra antes do `Icon=` novo — os dois testes de reescrita falharam na primeira tentativa (`ComparisonFailure` com `[\n]Icon=` no lugar de `[]Icon=`), corrigido trocando para `split("\n")` + descarte do último elemento vazio. `gradlew.bat desktopTest --tests "com.usagemonitor.update.LinuxMenuIconTest"`: **BUILD SUCCESSFUL**, `tests="6" skipped="0" failures="0" errors="0"` |
| A04 | 2026-08-30 | `feat(update): add the IO wrapper that keeps the menu icon current` | `ensureLinuxMenuIconCurrent` (wrapper de IO) | concluída | Gate em `LinuxInstallOriginResolver.current() == MANAGED_XDG` (o mesmo já testado, sem reinventar checagem de marcador); copia o ícone do classpath (`APP_ICON_RESOURCE_PATH`, promovida de `private` para `internal` em `Main.kt` — mesmo recurso do ícone da janela, sem segundo literal do caminho) para `layout.iconFile` só se ainda não existir; lê a entrada de menu e, se precisar, reescreve por arquivo temporário + rename. Tudo em `runCatching`, nunca lança. **Não é unit-testada isoladamente** — mesma decisão já registrada para `AutoStartManager.ensureAutoStartCommandCurrent()` (R6 do plano da #120): é IO de ponta a ponta sobre caminhos reais do Linux, e a suíte roda no Windows; a prova é a A06, ao vivo. `gradlew.bat desktopTest --tests "com.usagemonitor.update.LinuxInstallLayoutTest" --tests "com.usagemonitor.update.LinuxInstallOriginTest" --tests "com.usagemonitor.update.LinuxMenuIconTest" --tests "com.usagemonitor.update.LinuxAppUpdateInstallerTest" --tests "com.usagemonitor.AutoStartManagerTest"`: **BUILD SUCCESSFUL**, `19`/`13`/`6`/`18`/`28` testes, `failures="0"` em todos. `WindowsInstallOriginTest` falha nesta máquina Linux **antes** desta mudança também (4 falhas idênticas rodando `main` sem alteração nenhuma) — teste que assume execução no Windows, não regressão desta atividade |
| A05 | 2026-08-30 | `feat(update): repair the menu icon on every app startup` | Fiação em `Main.kt` | concluída | Chamada a `ensureLinuxMenuIconCurrent()` dentro do `LaunchedEffect(settings)` já existente, em `Dispatchers.IO`, ao lado de (mas **fora** do `if (AutoStartManager.isAutoStartSupported())`) `AutoStartManager.ensureAutoStartCommandCurrent()` — o `if` ali é sobre autostart, não sobre Linux, e a função nova já se protege sozinha. `gradlew.bat compileKotlinDesktop desktopJar`: **BUILD SUCCESSFUL**, nenhum erro novo (só os avisos preexistentes de `FlowPreview`, iguais em número aos de antes da mudança) |
| A06 | 2026-08-30 | — (sem mudança de código) | Verificação ao vivo na Bazzite | concluída | **`gradlew.bat run` não serve para este teste**: o processo em execução é o `java` do Homebrew, nunca um caminho dentro de `versions/`, então `LinuxInstallOriginResolver` classifica como `UNMANAGED` e a função corretamente não faz nada — confirmado como comportamento certo, não defeito. Repeti com o binário **real**: `gradlew.bat createDistributable`, copiei o app-image gerado para `~/.local/share/usage-monitor/versions/999.0.0-test/` (pasta descartável, fora de `current`) e rodei `bin/Usage Monitor` diretamente — dessa forma `ProcessHandle.current().info().command()` aponta para dentro de `versions/`, e a origem resolve `MANAGED_XDG` de verdade. Simulei a instalação já afetada editando à mão `~/.local/share/applications/usage-monitor.desktop` para `Icon=…/versions/30.0.0/…` (versão que nem existe, o caso real de quem já foi podado). Ao abrir: `~/.local/share/usage-monitor/icon.png` foi criado (PNG 512×512 válido) e a linha `Icon=` foi reescrita para esse caminho estável — `Type=`/`Version=`/`Name=`/`Exec=`/`Terminal=`/`Categories=` saíram **byte a byte iguais**. `desktop-file-validate` seguiu sem erros novos. Processo de teste e a pasta `versions/999.0.0-test/` removidos depois. **Efeito colateral bom**: a instalação real desta máquina (38.0.2) ficou com a entrada de menu já corrigida, sem esperar a próxima atualização. `gradlew.bat allTests`: `1639` testes, `4` falhas — as mesmas 4 do `WindowsInstallOriginTest` já confirmadas como pré-existentes (ambiente Linux rodando teste que assume Windows), nenhuma regressão |
