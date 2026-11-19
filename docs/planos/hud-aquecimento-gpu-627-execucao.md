# Aquecimento da GPU para a expansão da HUD — issue #627

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-19,
branch `fix/issue-627-hud-gpu-warmup` (worktree `.claude/worktrees/hud-warmup-627`, base `origin/main` `3e92ed7c`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Relato do dono do produto no `v42.3.0-beta.14`, Windows 11, HUD flutuante: as primeiras expansões depois do arranque
engasgam e, a partir da 4ª, a animação fica normal. Tirar o engasgo das primeiras expansões sem trocar o renderizador
nem o desenho da íris.

## Fatos confirmados (2026-11-19, `3e92ed7c`)

Instrumentação temporária, fora do repositório (worktree descartável `diag-expand-jank`): marcos do pipeline de
abertura em `HudWindow.kt` e carimbo de `System.nanoTime()` a cada leitura do quadro da íris em `rememberHudIrisFrame`
(`HudRetractedStrip.kt`), lida na fase de desenho. App rodado com `gradlew.bat run` e depois `java @args`.

- Modo do usuário: `hud/Floating=true`, `hud/Auto/Retract=true` (registro `HKCU\Software\JavaSoft\Prefs\com.usagemonitor`).
- Renderizador padrão (Direct3D), pausas > 33 ms entre pinturas da íris durante a abertura de 420 ms:
  1ª 227 + 354 ms; 2ª 86 + 305 ms; 3ª 93 ms; 4ª–17ª nenhuma (16 ms por quadro), salvo picos isolados de 53 e 84 ms.
- Do hover ao 1º quadro: sempre ~220 ms (200 ms são o `hudOpenDelayMillis`). O engasgo é no meio da animação.
- `-Xcomp -XX:TieredStopAtLevel=1`: 1ª 159 + 417 ms; 2ª 149 + 44 ms; 3ª 101 ms; lisa da 4ª. **Não é JIT.**
- `-Dskiko.renderApi=SOFTWARE_FAST`: 1ª a 11ª lisas (maior pausa 15–16 ms). **O custo é de primeira execução da GPU.**
  CPU desse modo: 67,2 s em 113 s de processo (inclui arranque), contra ~16% de um núcleo no Direct3D — recusado.
- Protótipo de aquecimento (3 ciclos de abrir/fechar 3 s após o arranque, conteúdo a alfa 0,01 com
  `CompositingStrategy.ModulateAlpha`): 1ª 107 + 35 ms; 2ª e 3ª lisas.
- Sem leak: 10 amostras de 30 s com o app parado, WS 668→669 MB, handles 1515→1519, threads 114→113.

## Decisões

- **Aquecer no arranque, como o protótipo medido**: mesmos ciclos, tempos, alfa e estratégia de composição.
- **Só Windows** (regra #340): única plataforma medida; decisão por função pura com teste da lista de plataformas.
- **Nunca com "Reduzir animações"**: sem tween, não há o que aquecer.
- **Uso real tem precedência**: hover ou arrasto interrompe o aquecimento; o alfa volta a 1 na hora e o estado de
  abertura fica com o dono dele (o `LaunchedEffect` do hover).
- Camada `graphicsLayer` com `ModulateAlpha` fica sempre no conteúdo (alfa 1 fora do aquecimento), como no protótipo:
  aquecer por um caminho de desenho diferente do real foi o que se quis evitar.
- Sem mudança visual: protótipo e design system não mudam; a decisão entra em `docs/hud-notch.md`.

## Atividades

- **A01** — este plano.
- **A02** — `HudGpuWarmUp.kt` (desktopMain, pacote `com.usagemonitor`): `hudGpuWarmUpCycles(platform, motionReduced): Int`
  (Windows e sem redução → 3; senão 0) e as constantes `HUD_GPU_WARM_UP_ALPHA = 0.01f`, `..._START_DELAY_MILLIS = 3_000L`,
  `..._OPEN_MILLIS = 600L`, `..._CLOSE_MILLIS = 400L`. Teste em `src/desktopTest/.../HudGpuWarmUpTest.kt` cobrindo as
  quatro plataformas × redução.
- **A03** — no mesmo arquivo, `suspend fun runHudGpuWarmUp(cycles, target: HudGpuWarmUpTarget, interrupted: () -> Boolean): HudGpuWarmUpOutcome`
  (`SKIPPED`, `COMPLETED`, `INTERRUPTED`). `HudGpuWarmUpTarget`: `setWarming`, `setWindowOpen`, `setExpanded`,
  `suspend awaitFrame()`. Sequência: `cycles <= 0` → `SKIPPED` sem chamada nenhuma; `delay(START)`; `setWarming(true)`;
  por ciclo `setWindowOpen(true)`, `awaitFrame()`, `setExpanded(true)`, `delay(OPEN)`, `setExpanded(false)`, `delay(CLOSE)`;
  fim: `setWindowOpen(false)`, `setWarming(false)` → `COMPLETED`. `interrupted()` checado antes de cada passo; se
  verdadeiro, só `setWarming(false)` (nunca toca `windowOpen`/`expanded`) → `INTERRUPTED`. Cancelamento da coroutine
  também deixa `setWarming(false)` (`finally`). Testes com `runTest` (tempo virtual) e alvo falso que grava as chamadas.
- **A04** — ligar no `HudWindow.kt` (`LaunchedEffect(Unit)` com `AutoStartManager.currentPlatform()`,
  `LocalAppMotionPolicy.current.reduced` e `{ hovered || drag.dragging }`) e `HudWindowContent.kt` (parâmetro
  `warmingUp: Boolean`; raiz com `graphicsLayer { alpha; compositingStrategy = ModulateAlpha }`); `docs/hud-notch.md`.
- **A05** — medir as 3 primeiras expansões no app real com a instrumentação temporária sobre esta branch; investigar a
  sobra da 1ª (107 ms no protótipo) e registrar resultado.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | revisado contra as medições acima |
| A02 | `gradlew.bat desktopTest --tests "com.usagemonitor.HudGpuWarmUpTest"` | vermelho (stub devolvendo 0): `HudGpuWarmUpTest.kt:15 expected:<3> but was:<0>`; verde: 4 testes |
| A03 | `gradlew.bat desktopTest --tests "com.usagemonitor.HudGpuWarmUpTest" --tests "*ArchitectureRulesTest*"` | vermelho (stub devolvendo `SKIPPED`): 6 de 11 na asserção, ex. `HudGpuWarmUpTest.kt:126 expected:<COMPLETED> but was:<SKIPPED>`; verde: 11/11 e `ArchitectureRulesTest` 28/28. A02 e A03 dividem os dois arquivos novos e foram para um commit só |
| A04 | `gradlew.bat desktopTest --tests "com.usagemonitor.HudGpuWarmUpTest" --tests "*HudFloating*" --tests "*ArchitectureRulesTest*"` e `gradlew.bat desktopJar` | cola de janela sem harness de unidade (o `LaunchedEffect` e a camada não têm teste direto; a lógica é do A03). Vermelho real do `ArchitectureRulesTest.kt:507`: `HudWindow.kt::HudWindowHost: 307 linhas (limite 300)`, resolvido extraindo `rememberHudGpuWarmingUp`; verde: 170 testes, 0 falhas; `desktopJar` exit 0. `motion.reduced` da janela no lugar de `LocalAppMotionPolicy` (o efeito fica fora do `AppTheme`) |

## Problemas em aberto e riscos

- Durante ~3 s, a janela fica aberta (área de clique da janela inteira) e quase invisível: clique nessa área no
  arranque cai na HUD. Mitigado por: hover interrompe o aquecimento.
- Sobra de 107 + 35 ms na 1ª expansão com o protótipo; causa não medida (hipótese: alfa 0,01 não percorre exatamente o
  mesmo caminho do desenho opaco). A05.
- Linux e macOS sem medição: ficam sem aquecimento.
