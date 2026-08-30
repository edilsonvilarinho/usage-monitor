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
| A14 | Entrada inválida como terceiro motivo de migração em `ensureAutoStartCommandCurrent()` | `AutoStartManager.kt` |
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
- **Entrada inválida é o terceiro motivo de migração, e não "reescrever o arquivo de quem não
  pediu"** (A14). `ensureAutoStartCommandCurrent()` retorna cedo quando `isAutoStartEnabled()` é
  falso, então a condição nova só vê entrada que **existe** — a regra "entrada ausente não migra,
  senão ligaria a inicialização de quem a desligou" segue intacta, e é afirmada por teste. O que ela
  acrescenta é o caso de entrada que existe, que o usuário pediu, e que está comprovadamente
  quebrada. Sem ela a correção da A04 não alcança ninguém que já esteja afetado — inclusive quem
  abriu a #120, que está com o interruptor ligado e sem autostart. **Correção que não chega a quem
  reportou o defeito não é correção.**

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
| com `ABOVE`, `alwaysOnTopEffective=true`, e nenhuma janela testada consegue cobrir | pedido feito e honrado nas condições testadas | nenhuma — mas isso só descarta as duas primeiras hipóteses contra as janelas testadas; não prova que o sintoma original (com um cliente Wayland nativo, ex. terminal) está resolvido. Medido na A11: continua exigindo repetir contra o cliente exato do print da issue antes de fechar |

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
| A05 | 2026-08-29 | `feat(startup): read the linux graphics environment for the startup record` | `linuxGraphicsEnvironment()` | concluída | Função pura com o ambiente **injetado** (`(String) -> String?`), porque a suíte roda no Windows e ali `XDG_SESSION_TYPE` não existe. `XDG_SESSION_TYPE` é normalizado para minúsculas; `XDG_CURRENT_DESKTOP` vai **verbatim**, por ser lista separada por dois pontos. `gradlew.bat desktopTest --tests "com.usagemonitor.StartupDiagnosticsTest"`: **BUILD SUCCESSFUL in 6s**, XML com `tests="7" skipped="0" failures="0" errors="0"` (4 antigos + 3 novos: normalização, ausente/em branco e `getenv` que lança) |
| A06 | 2026-08-29 | `feat(autostart): tell a present linux entry apart from a working one` | `autostartEntryPresent` / `autostartEntryValid` | concluída | `inspectLinuxAutostartEntry` devolve `LinuxAutostartEntryState(present, valid)` — **dois booleanos, nunca o caminho**, que carregaria o nome do usuário no arquivo que a issue #123 empacota para uma issue pública. `valid` cobre os dois modos de falha silenciosa já vistos: `Exec=` apontando para executável podado com a árvore versionada, e `Path=` entre aspas. Leitor e teste de execução injetados, porque a suíte roda no Windows. `gradlew.bat desktopTest --tests "com.usagemonitor.AutoStartManagerTest"`: **BUILD SUCCESSFUL in 15s**, XML com `tests="25" skipped="0" failures="0" errors="0"` (21 + 4 novos: entrada boa, `Path` entre aspas, executável podado, arquivo ausente/em branco/ilegível) |
| A07 | 2026-08-29 | `feat(startup): carry the machine context in the startup record` | Campos opcionais no registro | concluída | Sete campos aditivos em `StartupDiagnosticsEntry`, todos default `null`: `osName`, `osVersion`, `sessionType`, `desktop`, `alwaysOnTopSupported`, `autostartEntryPresent`, `autostartEntryValid`. **`null` é "não medido", nunca "medido e falso"** — num arquivo do Windows um `false` afirmaria uma medida que ninguém fez. `gradlew.bat desktopTest --tests "com.usagemonitor.StartupDiagnosticsTest"`: **BUILD SUCCESSFUL in 6s**, XML com `tests="9" skipped="0" failures="0" errors="0"`. A retrocompatibilidade é provada desserializando uma linha literal de antes desta versão, sem nenhum dos sete campos |
| A08 | 2026-08-29 | `feat(startup): fill the machine context on every startup record` | Preenchimento dos campos | concluída | `StartupMachineContext.current()` resolvido **uma vez** num `remember` do `main()` e passado aos três pontos que gravam (`second-instance-exit`, `started`, `focus-request-served`) — a resolução lê a entrada de autostart do disco, e a resposta não muda dentro do processo. `AutoStartManager.currentPlatform()` virou `internal` para não abrir um segundo dono da leitura de `os.name`. `gradlew.bat desktopTest --tests "…StartupDiagnosticsTest" --tests "…AutoStartManagerTest"`: **BUILD SUCCESSFUL in 8s**, `tests="11"` e `tests="25"`, `failures="0" errors="0"` nos dois. **Linha real**, produzida por `gradlew.bat run` (exit 0, o guard recusou a segunda instância) em `~/.usage-monitor/diagnostics/startup.jsonl`: `{"ts":"2026-08-29T14:34:31.596Z","pid":9076,"version":"38.0.2","origin":"manual","outcome":"second-instance-exit","processStartedAt":"2026-08-29T14:34:30.867Z","startupLatencyMillis":729,"osName":"Windows 11","osVersion":"10.0","sessionType":null,"desktop":null,"alwaysOnTopSupported":true,"autostartEntryPresent":null,"autostartEntryValid":null}` — os quatro campos do Linux ficam `null` numa máquina Windows, que é o "não medido" pretendido, e `alwaysOnTopSupported` foi de fato lido do `Toolkit` |
| A09 | 2026-08-29 | `feat(startup): add the window shown outcome to the startup record` | `StartupOutcome.WINDOW_SHOWN` | concluída | Valor novo em enum existente, **exceção declarada** à regra do `CLAUDE.md` pelo mesmo motivo de `FOCUS_REQUEST_SERVED`: o `when` exaustivo sobre `wireValue` não compila sem o valor de fio. O segundo registro é por **desfecho** e não por campo, que é o que permite achar as duas linhas do mesmo arranque e comparar pedido com efetivo. `gradlew.bat desktopTest --tests "com.usagemonitor.StartupDiagnosticsTest"`: **BUILD SUCCESSFUL in 11s**, XML com `tests="12" skipped="0" failures="0" errors="0"` |
| A10 | 2026-08-29 | `feat(startup): record what the window did with the always on top request` | Segundo registro após o mapeamento | concluída | Dentro do `LaunchedEffect(window)` que **já existia** (`Main.kt`), sem composable nova. As duas leituras ficam na thread da interface e só a escrita vai para a IO. `gradlew.bat desktopTest --tests "…StartupDiagnosticsTest" --tests "…AutoStartManagerTest" desktopJar`: **BUILD SUCCESSFUL in 9s**, `tests="14"` e `tests="25"`, `failures="0" errors="0"`. **Linha real**, obtida com uma sonda descartável (`gradlew.bat -I probe-init.gradle -Dprobe.user.home=<temp> run`, que redireciona `user.home` para não disputar o `app.lock` nem escrever no `startup.jsonl` da instalação real): `{"ts":"2026-08-29T14:39:21.295Z","pid":32040,…,"outcome":"window-shown",…,"alwaysOnTopSupported":true,…,"alwaysOnTopRequested":true,"alwaysOnTopEffective":true}` e, do mesmo pid, `"outcome":"started"` com `"alwaysOnTopRequested":null,"alwaysOnTopEffective":null`. **Descoberta:** a linha `window-shown` sai **antes** da `started` — o efeito do conteúdo da janela roda antes do efeito de topo do `main()`. As duas linhas do mesmo arranque se acham pelo `pid`, nunca pela ordem. O processo da sonda (32040) foi encerrado e a instância real da máquina (32760) ficou intacta |
| A14 | 2026-08-29 | `fix(autostart): repair a broken linux entry on the next launch` | Entrada inválida migra | concluída | Terceiro motivo em `ensureAutoStartCommandCurrent()`, ao lado de `autoStartCommandNeedsMigration` e `linuxEntryPointsIntoVersionedTree`, recortado por `currentPlatform() == LINUX` como o segundo já é. `linuxAutoStartNeedsRepair` reusa o `inspectLinuxAutostartEntry` da A06 **sobre o texto que `readAutoStartCommand()` já leu** — no Linux ele é o próprio `.desktop`, e uma segunda leitura seria um segundo dono da mesma resposta. `gradlew.bat desktopTest --rerun --tests "…AutoStartManagerTest" --tests "…StartupDiagnosticsTest"`: **BUILD SUCCESSFUL in 3s**, XML com `tests="28" skipped="0" failures="0" errors="0"` e `tests="14" skipped="0" failures="0" errors="0"`. **Falsificação:** com `return state.present && !state.valid` trocado por `return false`, `28 tests completed, 1 failed` e a falha foi exatamente `a linux entry with a quoted working directory is repaired` — as outras duas (entrada válida não migra, entrada ausente não migra) continuaram verdes, que é o esperado de um `false` constante. Restaurado e reexecutado com `--rerun`. **Fecha R1** |
| A11 | 2026-08-30 | — (sem mudança de código) | Coleta na Bazzite | concluída, parcialmente conclusiva | Rodada nesta própria máquina Bazzite. `echo $XDG_SESSION_TYPE / $XDG_CURRENT_DESKTOP` → `wayland / KDE`. Entrada real da máquina estava quebrada como a issue descreve (`Path="/usr/lib/opt/usage-monitor/bin"`, e além disso apontando pra árvore de instalação, não pro launcher estável). Subindo o build do `main` (que já contém a A01–A14), `ensureAutoStartCommandCurrent()` reparou sozinha para `Exec="/home/edi/.local/bin/usage-monitor" --autostart` / `Path=/home/edi/.local/bin` — sem tocar no interruptor. `gio launch` na entrada reparada **subiu o processo**: `startup.jsonl` registrou `"origin":"autostart","outcome":"second-instance-exit"`, o oposto do defeito original (antes o `chdir` falhava e nada era escrito, porque o processo não chegava a inicializar a JVM). **Fecha a parte prática do R6** — a fiação de fato dispara numa Bazzite real, ainda que por `gio launch` e não por logon (isso continua sendo o que falta pra A13). Registro de `window-shown` trouxe `"sessionType":"wayland","desktop":"KDE","alwaysOnTopSupported":true,"alwaysOnTopRequested":true,"alwaysOnTopEffective":true"`. `xprop -id <janela> _NET_WM_STATE` → `_NET_WM_STATE_ABOVE, _NET_WM_STATE_STAYS_ON_TOP` presentes, batendo com o `alwaysOnTopEffective` do arquivo — **fecha o R5**: as duas fontes concordam neste boot. Ativar outra janela (`wmctrl -a "Steam"`) e reler `_NET_CLIENT_LIST_STACKING` mostrou o Usage Monitor continuando no topo da pilha. **Não é o veredito "com ABOVE e a janela ainda atrás"** da tabela abaixo — é uma quarta situação, não prevista nela, que a tabela precisou ganhar: "com ABOVE, e nada consegue cobrir". Tentativa de testar contra um cliente Wayland nativo (Konsole, como no print da issue) não deu resultado limpo — o KDE reaproveitou a sessão de terminal já aberta via D-Bus em vez de abrir uma janela nova testável, e a tentativa foi abortada sem repetir com uma janela isolada. **A reprodução original (terminal cobrindo a janela) não foi obtida nem descartada** |
| A12 | 2026-08-30 | — (sem mudança de código) | Correção do sempre-visível | **sem repro para corrigir, por ora** | O usuário reportante testou ao vivo nesta mesma Bazzite, com a versão instrumentada: Konsole (cliente Wayland nativo, o mesmo tipo de janela do print original) e um navegador com foco de teclado real, nenhum dos dois conseguiu cobrir a janela do Usage Monitor. Como nenhum código do sempre-visível foi tocado nesta branch, isso não é uma correção — é a constatação de que o sintoma não está presente nas condições testadas hoje. **A12 fica em espera**: se o sintoma voltar a acontecer, o roteiro (`xprop` + `tail startup.jsonl` no instante exato) é o que decide qual das três hipóteses da tabela de veredito é a real |
| A13 | — | — | Logon real na Bazzite | bloqueada | `gio launch` (A11) já prova que a fiação da migração dispara fora do Windows/CI, mas um logon real continua sendo a única prova de que o autostart do sistema operacional (não simulado por `gio launch`) sobe o app |

---

## Problemas em aberto e riscos

| # | Risco | Estado |
|---|---|---|
| R1 | A correção do `Path=` só chega às instalações já feitas quando o `.desktop` é reescrito. `ensureAutoStartCommandCurrent()` hoje só reescreve por dois motivos — argumento `--autostart` ausente e caminho dentro da árvore versionada — e **nenhum dos dois cobre o `Path=` com aspas** | **fechado na A14** — entrada inválida virou o terceiro motivo. A objeção "reescreve o arquivo de quem não pediu" não se sustentava: a função retorna cedo quando o autostart está desligado, então a condição só vê entrada que existe |
| R6 | A fiação da A14 dentro de `ensureAutoStartCommandCurrent()` **não é exercitada pela suíte**: a função lê `~/.config/autostart` e `currentPlatform()`, e no Windows o ramo do Linux nunca roda. O que os testes cobrem é a decisão pura (`linuxAutoStartNeedsRepair`) | **parcialmente fechado na A11** — `gio launch` na Bazzite real exercitou a fiação de ponta a ponta (repara e depois sobe pelo `.desktop` reparado). Falta só o logon de verdade (A13) pra provar que o autostart do SO, e não uma simulação, também dispara |
| R2 | A janela do sintoma "sempre visível" pode não ser corrigível no app: se o KWin recebe a `_NET_WM_STATE_ABOVE` e a ignora por regra de janela, não há chamada da AWT que resolva. A A12 tem esse desfecho previsto como resultado válido | **não reproduzido, com o usuário reportante testando ao vivo na própria Bazzite** — dois testes manuais com foco real: um Konsole (cliente Wayland nativo) e um navegador com o cursor digitando dentro dele. Nos dois, a janela do Usage Monitor continuou visível, sem ser coberta. **Nenhum código do sempre-visível foi alterado nesta branch** — só a instrumentação (A05–A10) —, então isso não é "corrigido", é "não reproduzido nas condições testadas hoje". O gatilho original (do print da issue) pode depender de algo não testado ainda: desbloqueio de tela, troca de monitor, jogo em tela cheia. Ver R7 |
| R3 | O registro dos campos novos só vale para arranques **posteriores** à instalação desta branch; nenhum boot já ocorrido pode ser investigado com eles | aberto |
| R4 | A linha `window-shown` sai **antes** da `started` (medido na A10): o efeito do conteúdo da janela roda antes do efeito de topo do `main()`. Quem ler o arquivo tem de casar as duas pelo `pid`, não pela ordem | aberto — é comportamento, não defeito |
| R5 | `alwaysOnTopEffective` é lido logo após a primeira composição. Se o compositor só resolver a `_NET_WM_STATE_ABOVE` depois disso, a leitura pode registrar o valor da AWT antes de o sistema tê-lo recusado — a AWT guarda o sinalizador, não o veredito do compositor. É exatamente por isso que a A11 confere com `xprop`, e não só com o arquivo | **fechado na A11** — as duas fontes concordaram (`alwaysOnTopEffective=true` no arquivo, `_NET_WM_STATE_ABOVE` presente no `xprop`) |
| R7 | A11 não conseguiu reproduzir o sintoma original (terminal cobrindo a janela) nem contra prová-lo ausente: a tentativa de abrir um Konsole novo foi engolida pela sessão D-Bus já existente do terminal do agente, em vez de abrir uma janela independente testável. O teste feito (ativar a janela do Steam) usa outro cliente XWayland, não um cliente Wayland nativo como o do print da issue | **fechado — o próprio usuário reportante testou ao vivo** com o Konsole real da máquina (cliente Wayland nativo, o tipo de janela do print original) e depois com um navegador com foco de teclado ativo. Nos dois casos a janela do Usage Monitor permaneceu visível. Fica em aberto só se o gatilho real for outro (tela bloqueada/desbloqueada, troca de monitor, app em tela cheia) — não testado |
