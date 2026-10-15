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
