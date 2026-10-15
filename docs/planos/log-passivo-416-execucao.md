# Log passivo independente da UI — issue #416

## Objetivo

A #416 (v41.10.0, Pop!_OS, Cosmic DE Wayland, `.deb`) chegou como uma captura de tela e nada mais: a HUD
aparece como retângulo preto opaco, longe da borda, e o usuário relata o app "travado". O caminho de
diagnóstico atual depende da própria interface (relatório de bug) ou de o usuário achar
`~/.usage-monitor/diagnostics/` sozinho. Este plano cria um log que grava falhas, travamentos e fatos da
janela sem depender da UI, e um comando que o coleta com a UI quebrada. A correção da #416 só entra
depois que esse log (ou o relator) confirmar a causa.

Resumo publicado na issue: <https://github.com/edilsonvilarinho/usage-monitor/issues/416#issuecomment-6192778925>.

## Diagnóstico da #416

### Fatos confirmados

- Desde a #294 (v41.0.0) a janela parada tem o tamanho da aberta: `hudDockedWindowBounds` usa
  `sizes.expanded` (`HudNotchGeometry.kt`). O recorte `Window.shape` (`hudRestHitRegion`) limita a área.
- Desde a #340 (v41.3.1) o recorte é só Windows (`hudUsesHitRegion`). No Linux a janela parada é o
  retângulo inteiro do balão, invisível só se o compositor compuser o alfa.
- Desde a #361 (v41.6.0) a HUD é a única janela de visualização.
- `docs/hud-notch.md` › "Fora do alcance dos testes": células de Linux vazias; Wayland ausente.

### Hipóteses ativas

- **H1** O alfa não é composto no Cosmic (XWayland) ou o Skiko em OpenGL não entrega alfa ali.
- **H2** O Cosmic ignora a posição pedida pela janela X11 gerenciada.
- **H3** "Travado" é o retângulo engolindo clique, não congelamento do EDT.
- **H4** Sem bandeja no Cosmic (AWT `SystemTray` exige XEmbed).

### Árvore de decisão (depois da evidência)

- Skiko em `SOFTWARE` resolve o preto → gate de render API por ambiente, função pura testada.
- Continua preto → no Linux sem alfa a janela parada encolhe para o notch (restrito ao Linux, regra #340).
- Posição recusada → limitação documentada em `hud-notch.md`.

## Achados das três semanas anteriores (2026-09-24 → 2026-10-14)

| # | Achado | Evidência |
|---|---|---|
| A1 | Janela parada no tamanho expandido sem recorte no Linux | #294 + #340 |
| A2 | HUD única janela, sem alternativa quando falha | #361 |
| A3 | Comportamento de janela entregue sem medição em Linux/Wayland | tabela de `hud-notch.md` |
| A4 | Nenhum `CoroutineExceptionHandler` nos escopos `SupervisorJob`; falha não-fatal vira marcador de queda | L2 abaixo |
| A5 | 250 `runCatching` em produção, 72 registram a falha | grep |

## Atividades

| Item | O que faz |
|---|---|
| L2 | Sonda de propagação de exceção; KDoc do `CrashHandler` corrigido pelo medido |
| L1 | `app.log` JSONL com nível, rotação por tamanho (1 MB × 3) e redação de caminho/e-mail |
| L4 | `LocalBreadcrumbRecorder` replica `ERROR`/`CRASH` no `app.log` |
| L3 | Toda falha não tratada vai ao `app.log` com pilha e origem (`edt`/`coroutine`/`thread`), direto no `CrashHandler` |
| L5 | Watchdog do EDT: sem resposta em 5 s grava a pilha da `AWT-EventQueue` |
| L6 | Tee de `System.err` para o `app.log` |
| L7 | Fatos de janela no arranque: translucência suportada, render API, bounds pedidos × obtidos |
| L8 | `--diagnostics`: zip da pasta de diagnóstico sem abrir janela |
| L9 | Template de issue e docs apontam para o `--diagnostics` |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| L2 | `UncaughtExceptionRoutingTest`: exceção em `launch` num escopo `SupervisorJob(Dispatchers.IO)` **chega** ao handler default; exceção em `EventQueue.invokeLater` **chega**, e o EDT segue despachando depois dela. O KDoc do `CrashHandler` dizia o contrário e foi corrigido. Consequência: hoje toda falha não tratada de coroutine ou do EDT grava `pending-crash.json` e é oferecida como queda no arranque seguinte (semântica corrigida em L3) | `gradlew.bat desktopTest --tests "com.usagemonitor.UncaughtExceptionRoutingTest"`: 3 testes, 0 falhas |
| L1 | `AppLogFile` (`data/diagnostics/`): JSONL com `ts`, `pid`, `version`, `level`, `source`, `thread`, `message` e, com exceção, `error`, `stack` (25 quadros) e `causes` (4). Mensagem e exceção pela fábrica da trilha (`sanitizeBreadcrumbMessage`, `breadcrumbFailureReasonOf`): caminho, e-mail e segredo redigidos. Rotação por tamanho (1 MB, `app.log` → `app.1.log` → `app.2.log`). **Desvio do plano:** escrita síncrona em todos os níveis, sem fila para INFO — o volume é de falhas e travamentos, e a fila perderia justamente a linha de quem está caindo | `gradlew.bat desktopTest --tests "com.usagemonitor.data.AppLogFileTest"`: 5 testes, 0 falhas |
| L4 | `LocalBreadcrumbRecorder` ganha `errorSink`: todo passo `ERROR` (os `recordFailure` existentes) vai também para o `app.log`, com a mensagem já redigida pela fábrica; falha no sink não perde o passo nem chega a quem chamou. `main()` cria o `AppLogFile` antes de tudo e liga o sink. Função e não o tipo do log no construtor: o recorder é público e `AppLogFile` é `internal` | `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalBreadcrumbRecorderTest" --tests "com.usagemonitor.architecture.*"`: 21 testes, 0 falhas |
| L3 | **Redesenhado pelo medido em L2.** O plano previa `CoroutineExceptionHandler` nos 18 escopos; a sonda mostrou que a falha de coroutine **já chega** ao `CrashHandler` pelo último recurso do kotlinx, e um handler por escopo a desviaria dele. Em vez disso o `CrashHandler` (agora `internal`) grava toda falha no `app.log` com pilha, causas e origem — `uncaughtOriginOf`: EDT pelo nome da thread, coroutine pela suprimida `DiagnosticCoroutineContextException` (classe interna do kotlinx, presa pela sonda), o resto como `thread`. Só `thread` é `fatal`; EDT e coroutine são `error`. O marcador `pending-crash.json` **continua** em todos os casos: EDT que cai pode deixar janela morta e coroutine que cai leva o laço dela — defeitos que não se relatam pela tela | `gradlew.bat desktopTest --tests "com.usagemonitor.CrashHandlerTest" --tests "com.usagemonitor.UncaughtExceptionRoutingTest" --tests "com.usagemonitor.architecture.*"`: 30 testes, 0 falhas |
| L5 | `EdtWatchdog` (thread daemon iniciada no `main()`): ping no EDT a cada 1 s; sem atendimento em 5 s grava **uma** linha `warn` com a pilha do EDT (num `EdtStallSnapshot` sintético) e, ao destravar, quanto durou. Laço do próprio watchdog parado por mais de 3 intervalos = processo suspenso (tampa fechada): recomeça o ping em vez de denunciar. Relógio monotônico (`nanoTime`). EDT desconhecido antes do primeiro ping é achado pelo nome; o prefixo `AWT-EventQueue` passa a ter dono único (`EDT_THREAD_NAME_PREFIX`, `CrashHandler.kt`) | `gradlew.bat desktopTest --tests "com.usagemonitor.EdtWatchdogTest" --tests "com.usagemonitor.CrashHandlerTest" --tests "com.usagemonitor.UncaughtExceptionRoutingTest" --tests "com.usagemonitor.architecture.*"`: 35 testes, 0 falhas. Teste com EDT real preso em `Thread.sleep(900)` grava a pilha com `Thread.sleep` |
| L6 | `StderrCapture` instalado no `main()` logo após o `AppLogFile`: o `stderr` segue para a saída original e cada linha completa vira `warn` com `source: stderr`. Quadros de pilha (`at …`, `... N more`, `Caused by:`, `Suppressed:`) ficam fora — a falha já entra estruturada pelo `CrashHandler`. Teto de 60 linhas por minuto; o excedente vira uma linha com a contagem. Guarda de reentrância por thread. Charset de `sun.stderr.encoding` (JDK 17 não tem `PrintStream.charset()`) | `gradlew.bat desktopTest --tests "com.usagemonitor.StderrCaptureTest" --tests "com.usagemonitor.architecture.*"`: 14 testes, 0 falhas |
| L7 | `HudWindowFacts.kt`: 2 s depois de a janela da HUD existir, uma linha `info` (`source: hud-window`) com posição/tamanho pedidos (`windowState`) × obtidos (`window.x/y/width/height`), `isWindowTranslucencySupported` nos três modos e o render API efetivo do Skiko (`ComposeWindow.renderApi`). `null` = não medido. Mensagem cabe nos 200 caracteres da fábrica (teste com a linha mais longa possível). `AppLogFile` passa a usar lock do processo: a HUD cria a própria instância, e duas instâncias girando o mesmo arquivo perderiam linhas. **Risco de plataforma (#342):** `HudWindow.kt` é host de janela; a mudança só **lê** a janela (nenhuma chamada que a altere), não aberta no Linux (X11) | `gradlew.bat desktopTest --tests "com.usagemonitor.HudWindowFactsTest" --tests "com.usagemonitor.data.AppLogFileTest" --tests "com.usagemonitor.architecture.*"`: 19 testes, 0 falhas |
| L8 | `--diagnostics` (`DiagnosticsExport.kt`), tratado na primeira linha do `main()` — antes do log, da instância única e do Compose. Gera `~/usage-monitor-diagnostics-<UTC>.zip` e imprime o caminho. **Lista do que entra:** `app.log` (+ girados), `startup.jsonl`, `hud-window.jsonl`, `breadcrumbs.jsonl`, `linux-update.log`, `pending-crash.json`; ficam fora os recorders de créditos/Codex (corpo de resposta) e `pending-crash.png` (captura de tela). Toda linha passa por `redactBreadcrumbIdentity` (o `linux-update.log` recebe saída de ferramentas do sistema). `environment.txt` medido na hora: XDG, `WAYLAND_DISPLAY`/`DISPLAY`, translucência nos três modos, bandeja e `alwaysOnTop` — as perguntas da #416 sem janela. Saída do CLI em inglês/ASCII: o console do Windows corrompeu o acento | `gradlew.bat desktopTest --tests "com.usagemonitor.DiagnosticsExportTest" --tests "com.usagemonitor.architecture.*"`: 16 testes, 0 falhas. `gradlew.bat run --args="--diagnostics"`: zip gerado com `environment.txt`, `startup.jsonl`, `hud-window.jsonl`, `breadcrumbs.jsonl`, sem abrir janela (zip apagado depois) |
| L2b | Correção da sonda: a de coroutine passa a rodar numa JVM filha (`CoroutineRoutingProbeMain`, classpath por arquivo de argumentos por causa do limite de linha do Windows). Na JVM da suíte, o coletor global do `kotlinx-coroutines-test` guardava a falha proposital e derrubava o `runTest` seguinte de outra classe (`AnthropicRepositoryImplTest`: `UncaughtExceptionsBeforeTest`) — achado pelo `allTests`, não pelas execuções isoladas. A JVM filha é também a situação de produção, sem `runTest` | `gradlew.bat allTests`: 2.817 testes, 0 falhas (antes da correção: 2.817, 1 falha) |
| L9 | Template de issue (`bug_report.yml`, campo Diagnostics) pede o zip do `--diagnostics`, com o comando do `.deb`/`.rpm` (binário achado por `dpkg -L`/`rpm -ql`, porque o caminho varia entre instalações) e o do Windows; versões antigas continuam com os arquivos avulsos. Decisões em `docs/build-and-release.md` (após o registro de arranque) e uma linha de regra no `CLAUDE.md` | `gradlew.bat allTests`: 2.817 testes, 0 falhas |
| L10 | Fato novo da #416: na beta v41.11.0-beta.1 o app "roda normal", mas não arrasta nem redimensiona. Lido no código: a HUD é `resizable = false` por desenho; as janelas internas são `undecorated` sem alça de redimensionar; todo arrasto pede posição ao sistema (`windowState.position` na HUD, `WindowDraggableArea` nas internas), sem `_NET_WM_MOVERESIZE`. Hipótese principal: o Cosmic/XWayland ignora a posição pedida (H2). Registro novo: cada arrasto grava `window-drag` com início, pedido e obtido (`não moveu`/`moveu`/`moveu diferente`) — HUD por `HudDragProbe` (uma linha em cada ponta do gesto), janelas internas por `Modifier.windowDragProbe` na barra de título, que só observa (passe `Initial`, sem consumir). Linha sai por `windowDragSink` ligado no `main()`: `presentation` não importa `data`. **Risco de plataforma (#342):** `HudWindow.kt` e `DesktopWindowFrame.kt` são hosts de janela; a mudança só lê posição. Arrasto não exercitado em janela real nesta máquina | `gradlew.bat allTests`: 2.822 testes, 0 falhas |
