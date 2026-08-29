# Linux: `sempre visível` e `iniciar com o sistema` no Bazzite — plano de execução

| | |
|---|---|
| **Modelo** | Claude Opus 5 (1M context) — `claude-opus-5[1m]` |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-29 |
| **Issue** | [#120](https://github.com/edilsonvilarinho/usage-monitor/issues/120) |
| **Branch** | `fix/linux-autostart-and-always-on-top`, criada de `main` (`bc7c848`) |
| **Worktree** | `../usage-monitor-120` |
| **PR alvo** | `main` |

A seção **Pontos de situação** é atualizada a cada micro-atividade, **no mesmo commit da
micro-atividade**. A coluna `Evidência` guarda o comando que rodou e o **resultado real**, nunca a
intenção.

## Contexto

O relato da issue #120 é de uma máquina **Bazzite 44** (Fedora Atomic, KDE Plasma) e junta duas
queixas numa só: o interruptor *iniciar com o sistema* não inicia o app no logon, e o interruptor
*sempre visível* não impede que o terminal cubra a janela.

As duas queixas **não têm o mesmo grau de prova**, e o plano as separa por causa disso.

### O defeito provado — `Path=` entre aspas no `.desktop`

`AutoStartManager.buildLinuxDesktopEntry` (`AutoStartManager.kt:283`) escreve as duas chaves com o
mesmo `quoteDesktopValue`:

```
Exec="/home/user/.local/bin/usage-monitor" --autostart
Path="/home/user/.local/bin"
```

A *Desktop Entry Specification* define regras de aspas **apenas para a chave `Exec`** (seção "The
Exec key"). `Path` é do tipo `string` e é lido verbatim: a GLib guarda o valor em `info->path` e o
passa como `working_directory` do `g_spawn`; o KIO o passa para `QProcess::setWorkingDirectory`. Um
diretório cujo nome literal começa com `"` não existe, o spawn falha com erro de `chdir`, e **nada
aparece para o usuário** — o interruptor continua ligado porque `isAutoStartEnabled()` só testa a
existência do arquivo (`AutoStartManager.kt:26`).

O teste existente afirma o `Exec=` (`AutoStartManagerTest.kt:127`) e **nunca afirmou o `Path=`** —
foi por ali que o defeito passou.

### O sintoma sem prova — `alwaysOnTop` ignorado

Para o `alwaysOnTop` ignorado no Bazzite/KDE **não há evidência nenhuma**, só hipóteses: o app pode
não estar pedindo, a AWT pode estar engolindo o pedido, ou o KWin pode estar recusando a
`_NET_WM_STATE_ABOVE`. As três exigem correções diferentes, e duas delas não são corrigíveis no app.

**Nenhuma linha de correção especulativa entra nesta branch.** As micro-atividades A05 a A10 fazem
o app **registrar** o que pediu e o que o sistema devolveu; a A11 mede na máquina real; e só a A12
corrige — ou documenta que não é corrigível.

### Fora de escopo

- **Windows e macOS.** O `Exec=`/`Path=` é exclusivo do `.desktop`; a chave `Run` e o plist não têm
  a chave e não são tocados.
- **A latência do logon.** Já foi medida e explicada em
  [`arranque-no-logon-execucao.md`](arranque-no-logon-execucao.md) (A05): é a fila que o ambiente
  serializa, não custo do app.

## Micro-atividades

| # | Micro-atividade | Toca |
|---|---|---|
| A01 | Este plano, com a tabela de pontos de situação e o roteiro de coleta | `docs/planos/` |
| A02 | Comentário vivo criado na issue #120 com a tabela | GitHub |
| A03 | Teste **vermelho**: `buildLinuxDesktopEntry` deve escrever `Path=` sem aspas | `AutoStartManagerTest.kt` |
| A04 | Correção: `Path=$parentDir` sem `quoteDesktopValue`; `Exec=` intocado | `AutoStartManager.kt` |
| A05 | `linuxGraphicsEnvironment()` — função pura, ambiente injetado | `StartupDiagnostics.kt` |
| A06 | `autostartEntryPresent` / `autostartEntryValid` — função pura, leitor injetado | `AutoStartManager.kt` |
| A07 | Campos opcionais em `StartupDiagnosticsEntry`, todos default `null` | `StartupDiagnostics.kt` |
| A08 | Preenchimento dos campos no registro de arranque | `StartupDiagnostics.kt`, `Main.kt` |
| A09 | `StartupOutcome.WINDOW_SHOWN` + `wireValue` | `StartupDiagnostics.kt` |
| A10 | Segundo registro após o mapeamento da janela, com o pedido e o efetivo | `Main.kt` |
| A11 | Build instalada na Bazzite; roteiro de coleta rodado; resultado bruto no plano | `docs/planos/` |
| A12 | Correção do sempre-visível **ditada pela medição** de A11 — ou o registro de que não é corrigível | a definir por A11 |
| A13 | Logon real na Bazzite provando o autostart | — |

### Decisões travadas

- **`Path=` sem aspas, `Exec=` com aspas.** A especificação define regras de aspas só para o `Exec`.
  Manter as duas iguais era simetria estética contra o que a especificação diz, e custou o arranque
  inteiro no Linux.
- **`autostartEntry*` são booleanos, nunca o caminho.** O caminho carrega o nome do usuário, e este é
  o mesmo arquivo que a issue #123 vai empacotar para uma issue pública. Booleano responde à mesma
  pergunta sem carregar identidade.
- **`WINDOW_SHOWN` é exceção declarada** à regra de não criar valor em enum existente, pelo mesmo
  motivo de `FOCUS_REQUEST_SERVED`: há um `when` exaustivo sobre `wireValue`, e o erro de compilação
  garante que o valor de fio existe.
- **Campo novo com default é retrocompatível; valor novo de enum não é** — por isso a A07 é aditiva
  e a A09 é justificada em separado.
- **Nenhuma composable nova em `main()`** — o método já estourou o backend JVM uma vez
  (`CLAUDE.md`). A A10 entra no `LaunchedEffect(window)` que já existe.
- **A leitura do ambiente gráfico é função pura com o ambiente injetado.** A suíte roda no Windows,
  onde `XDG_SESSION_TYPE` não existe; ler `System.getenv` direto tornaria a função não testável.

### Roteiro de coleta da A11 (na Bazzite)

```bash
echo "$XDG_SESSION_TYPE / $XDG_CURRENT_DESKTOP"
cat ~/.config/autostart/usage-monitor.desktop
gio launch ~/.config/autostart/usage-monitor.desktop      # revela o erro de chdir, se houver
xprop -id "$(xdotool search --name 'Usage Monitor' | head -1)" _NET_WM_STATE
tail -5 ~/.usage-monitor/diagnostics/startup.jsonl
```

| Medição | Veredito | O que a A12 faz |
|---|---|---|
| sem `_NET_WM_STATE_ABOVE` e `alwaysOnTopEffective=false` | o app não pediu | defeito nosso — reaplicar `isAlwaysOnTop` após o mapeamento |
| sem `ABOVE` e `alwaysOnTopEffective=true` | a AWT engoliu o pedido | reasserção em `WindowListener.windowOpened` |
| com `ABOVE` e a janela ainda atrás | KWin/XWayland ignora | não é corrigível no app — documentar no README e apontar a regra de janela do KWin |

### Verificação da branch

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.AutoStartManagerTest"
gradlew.bat desktopTest --tests "com.usagemonitor.StartupDiagnosticsTest"
gradlew.bat allTests
```

Mais o roteiro da A11 e o logon real da A13 na Bazzite.

## Pontos de situação

Uma linha por micro-atividade, escrita **no mesmo commit** da micro-atividade. A coluna `Commit`
guarda o **assunto** do commit, não o hash: um commit não pode conter o próprio hash.

| # | Data | Commit | Micro-atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-08-29 | `docs(plan): open the execution plan for the linux autostart and always on top issue` | Este plano | concluída | Worktree criada com `git worktree add ../usage-monitor-120 -b fix/linux-autostart-and-always-on-top main`: `Preparing worktree (new branch 'fix/linux-autostart-and-always-on-top')` / `HEAD is now at bc7c848`. O defeito do `Path=` foi lido no código (`AutoStartManager.kt:290`, `quoteDesktopValue` aplicado às duas chaves) e a ausência de asserção sobre ele confirmada em `AutoStartManagerTest.kt:124-128`, que só afirma o `Exec=` |
| A02 | 2026-08-29 | `docs(plan): publish the live status comment on issue 120` | Comentário vivo na issue | concluída | `gh issue comment 120 --body-file status-120.md` devolveu `https://github.com/edilsonvilarinho/usage-monitor/issues/120#issuecomment-5462954374`. O comentário abre com `🤖 Escrito por Claude Code, a pedido de @edilsonvilarinho` e carrega a mesma tabela desta seção; as atualizações seguintes usam `--edit-last` sobre ele |
| A03 | 2026-08-29 | `test(autostart): assert the linux desktop entry writes Path verbatim` | Teste vermelho do `Path=` | concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.AutoStartManagerTest"`: **BUILD FAILED**, `21 tests completed, 1 failed` — `linux desktop entry writes the working directory verbatim[desktop] FAILED / java.lang.AssertionError at AutoStartManagerTest.kt:149`. A falha é a linha `assertTrue(entry.contains("\nPath=/home/edils/.local/bin\n"))`, ou seja, o valor sai entre aspas hoje. **O teste discrimina o defeito**: os outros 20 continuam verdes |
| A04 | 2026-08-29 | `fix(autostart): write the linux desktop entry Path key verbatim` | Correção do `Path=` | concluída | `Path=$parentDir` sem `quoteDesktopValue`; o `Exec=` fica intocado, porque ali as aspas são o que a especificação manda. `gradlew.bat desktopTest --tests "com.usagemonitor.AutoStartManagerTest"`: **BUILD SUCCESSFUL in 8s**, e o XML traz `tests="21" skipped="0" failures="0" errors="0"`. O teste da A03, que falhava em `AutoStartManagerTest.kt:149`, passa; os 20 anteriores seguem verdes, incluindo o que afirma o `Exec=` entre aspas |
| A05 | — | — | `linuxGraphicsEnvironment()` | pendente | — |
| A06 | — | — | `autostartEntryPresent` / `autostartEntryValid` | pendente | — |
| A07 | — | — | Campos opcionais no registro | pendente | — |
| A08 | — | — | Preenchimento dos campos | pendente | — |
| A09 | — | — | `StartupOutcome.WINDOW_SHOWN` | pendente | — |
| A10 | — | — | Segundo registro após o mapeamento | pendente | — |
| A11 | — | — | Coleta na Bazzite | bloqueada | Depende de máquina Linux Bazzite física, que o agente não tem |
| A12 | — | — | Correção do sempre-visível | bloqueada | Ditada pela medição da A11 |
| A13 | — | — | Logon real na Bazzite | bloqueada | Depende de máquina Linux Bazzite física |

---

## Problemas em aberto e riscos

| # | Risco | Estado |
|---|---|---|
| R1 | A correção do `Path=` só chega às instalações já feitas quando o `.desktop` é reescrito. `ensureAutoStartCommandCurrent()` hoje só reescreve por dois motivos — argumento `--autostart` ausente e caminho dentro da árvore versionada — e **nenhum dos dois cobre o `Path=` com aspas** | aberto |
| R2 | A janela do sintoma "sempre visível" pode não ser corrigível no app: se o KWin recebe a `_NET_WM_STATE_ABOVE` e a ignora por regra de janela, não há chamada da AWT que resolva. A A12 tem esse desfecho previsto como resultado válido | aberto |
| R3 | O registro dos campos novos só vale para arranques **posteriores** à instalação desta branch; nenhum boot já ocorrido pode ser investigado com eles | aberto |
