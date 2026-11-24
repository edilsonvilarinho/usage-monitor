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
| A02 | `node --test "tools/ci/test/hud-pulses.test.mjs"` | vermelho (stub): `actual: [], expected: [3000, 4000, 5000]`; verde 12/12. Devolvida 1x na revisão: app que some ou cai saía `ok`. Vermelho do ajuste: `ruido de 100 px e some, ou fica`: `actual: 'pulsed', expected: 'not-visible'` (4 falhas em 16); verde 16/16, confirmado em execução independente. Não coberto: app que some e volta antes do fim |
| A03 | `[Parser]::ParseFile` nos dois `.ps1` e compilação isolada do tipo C# | 0 erros de parse; tipo compilado sem ser chamado. Devolvida 1x: coluna `foreground` era marca de janela ativa, cabeçalho entre aspas e `runner.temp` em `env` de job. Scripts de captura sem harness de teste; a sonda é o run desta branch |
| A03 (sonda) | run [45469197337](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45469197337) | desktop interativo (sessão 2, 1024×768, 96 dpi), `render=DIRECT3D`, janela com alfa capturada, ~9,4 quadros/s. `beta.15` `pulsed` (3 pulsos), `beta.16` `ok`. Console do runner e ícones atrás da HUD |
| A04 | run [45470705430](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45470705430) (`d57718a1`) | `beta.15` 5/5 `pulsed`, sempre 3 pulsos; `beta.16` 5/5 `ok`; `source` (esta branch) 5/5 `ok`. Artefatos conferidos: HUD visível nas 15 (mediana 1.417–1.419 px depois de 15 s, mínimo 1.410), fundo estável nas 15, nenhum app encerrado. 65–88 s por tag; 121–264 s compilando |
| A05 | `real-app.yml` sem `push` | decisão abaixo; YAML validado pelos dois runs, o corte do gatilho só depois do merge |

## Decisão (A05)

**O runner do GitHub serve.** Abre o produto instalado, captura a janela transparente e separou a versão com defeito das
sem defeito em 15 de 15 gravações. Não é preciso runner próprio nem gravação local para este tipo de defeito.

O que ele **não** prova: só o modo flutuante com auto-recolher, parado, por 30 s; a 9,4 quadros/s, pulso abaixo de ~200 ms
pode escapar; um monitor, 100% de escala, sem barra de tarefas; Linux e macOS não. `source` compila um clone raso, com
versão `1.0.0`. O workflow fica manual. Ligá-lo a PR de HUD e acrescentar cenários (hover, arrasto, encaixado) é outra
decisão, com outra issue.

## Problemas em aberto e riscos

- O runner não tem GPU: o Skiko pode cair para software e mudar o tempo, mas o aquecimento não depende da API gráfica.
- A captura podia não ver a janela com alfa por pixel: descartado pela sonda da A03.
- Um detector que só reprova a `beta.15` pode estar ajustado a ela. A invariante "sem entrada, nenhum pulso" reduz o risco.
