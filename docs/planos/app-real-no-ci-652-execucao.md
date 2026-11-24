# App real gravado no CI — issue #652

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `ci/issue-652-real-app-recording` (worktree `.claude/worktrees/real-app-652`, base `origin/main` `c24f5f41`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Objetivo

Descobrir se o runner Windows do GitHub abre o app de verdade, grava a tela e reprova sozinho um defeito de janela que
os testes não viram. É experimento: a resposta "não consegue" também encerra a issue.

## Fatos confirmados (2026-11-24, `c24f5f41` e tags)

- Defeito de referência: `v42.3.0-beta.15` tem o aquecimento da #627. `hudGpuWarmUpCycles` = 3 no Windows sem redução
  de movimento; início 3.000 ms depois do arranque, 600 ms aberta e 400 ms fechada por ciclo. O efeito visível foi
  relatado no #629; a reversão saiu na `v42.3.0-beta.16`.
- As duas tags têm `UsageMonitor-Setup-<versão>.exe` publicado. NSIS por usuário (`RequestExecutionLevel user`), pasta
  `$LOCALAPPDATA\<PRODUCT_NAME>`, executável `Usage Monitor.exe` (`src/installer/UsageMonitor.nsi:122,124,138`); `/S`
  não abre o app.
- `--user-data-dir <dir>` isola dados e desliga atualização e autostart (`docs/build-and-release.md`). Preferências em
  `com.usagemonitor.data.<16 hex do SHA-256 do caminho canônico>` (`AppDataDirectory.kt:88-103`), no registro do
  usuário, com maiúscula escapada por `/` (`hudFloating` → `hud/Floating`).
- Chaves: `hudFloating`, `hudAutoRetract` (`HudWindowPreferences.kt`), `reducedMotion`, `releaseNotesSeenVersion`,
  `onboardingCompletedVersion`, `startupTipsEnabled`. Sem as três últimas, guia e dica abrem sozinhos (`StartupGuide.kt`).
- Sem conta não há coluna nem buraco (`HudBlackHoleTest.kt:86`). O último painel vem de `<raiz>/dashboard-cache.json`
  (`DashboardCacheDto`, `LocalDashboardCacheDataSource.kt`).
- `<raiz>/diagnostics/app.log` recebe `hud-window: hud pedido … obtido …; … render=<api>` (`HudWindowFacts.kt`).

## Decisões

- **Produto instalado, não `gradlew run`:** `beta.15` (tem de reprovar) e `beta.16` (tem de passar) pelo instalador
  publicado; a `main` é o terceiro caso, por `createDistributable`, só depois de os dois primeiros funcionarem.
- **Sem credencial e sem rede:** conta sintética por `dashboard-cache.json`; regra de firewall de saída bloqueia o
  executável. Nada de chave, nem falsa.
- **Sem entrada humana:** ponteiro parado longe da HUD. Nesse cenário a HUD **nunca** pode abrir; qualquer pulso de
  área reprova. É a invariante, e não a forma do defeito da #627.
- **Sinal:** a cada ~50 ms, pixels que diferem da área de trabalho lisa capturada antes do app e a caixa que os contém.
  Pulso = subida acima do repouso e volta. Quadros e CSV vão no artefato, para conferência por gente.
- **Detector puro em `tools/ci/hud-pulses.mjs`**, com teste; captura em `tools/real-app/record.ps1` (sem harness).
- **Workflow `real-app.yml`:** `workflow_dispatch`, mais `push` só nesta branch enquanto durar o experimento
  (`workflow_dispatch` exige o arquivo na `main`). Fora do `ci-ok`.

## Atividades

- **A01** — este plano.
- **A02** — detector de pulsos em TDD.
- **A03** — captura e workflow; sonda: há desktop? que API gráfica? a janela transparente aparece no quadro?
- **A04** — prova: `beta.15` reprovada e `beta.16` aprovada, 5 vezes cada; depois a `main`.
- **A05** — decisão (runner do GitHub, runner próprio ou gravação local) e remoção do gatilho `push`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | conferido contra `HudGpuWarmUp.kt` da tag `v42.3.0-beta.15` e os arquivos citados |

## Problemas em aberto e riscos

- O runner não tem GPU: o Skiko pode cair para software e mudar o tempo, mas o aquecimento não depende da API gráfica.
- A captura pode não ver a janela com alfa por pixel; a sonda da A03 responde antes de qualquer conclusão.
- Um detector que só reprova a `beta.15` pode estar ajustado a ela. A invariante "sem entrada, nenhum pulso" reduz o risco.
