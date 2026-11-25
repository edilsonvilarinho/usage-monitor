# Janela pré-aquecida pisca no arranque — issue #669

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-25,
branch `fix/issue-669-prewarm-flash` (worktree `.claude/worktrees/prewarm-flash-669`, base `origin/main` `4b784c4a`),
commits com a identidade `claude <claude@anthropic.com>`. Achado da #657 (A05).

## Objetivo

Nenhuma janela aparece no arranque sem o usuário pedir, sem perder o ganho do pré-aquecimento.

## Fatos confirmados (2026-11-25)

- 5 de 559 gravações com um quadro opaco (a conta da #657 era 4; a regra de 1 quadro achou mais uma). Nos artefatos dos runs 45494153000 e 45497278470 (380 gravações, ~10 quadros/s), lidos por script:

| Gravação | Quadro | Pixels | Após a HUD | Janela (imagem aberta) |
|---|---|---|---|---|
| `source` 22, `docked-top-hole-idle` | 4.278 ms | 246.547 | 1.425 ms | Configurações |
| `source+opens-and-stays` 4, `docked-left-idle` | 4.634 ms | 221.691 | 1.437 ms | Configurações |
| `source` 24, `docked-top-idle` | 6.089 ms | 346.477 | 2.795 ms | Ajuda |
| `source` 9, `floating-idle` | 6.247 ms | 345.065 | 2.904 ms | Ajuda |

- A janela aparece **já pintada**, com o conteúdo inteiro. Bate com `SETTINGS_PREWARM_DELAY_MILLIS` (0,8 s) e `OTHER_MODALS_PREWARM_DELAY_MILLIS`
  (2,5 s) mais o custo de criar. A quinta, da `v42.3.0-beta.15` publicada, não foi aberta.
- O app grava `render=DIRECT3D` no `app.log` das gravações; `SOFTWARE` é só do build Linux (`build.gradle.kts:149`).
- O Compose 1.12.2 chama `Window.setVisible` em corrotina própria (`AwtWindow…showJob`, lido no bytecode), depois da recomposição.

## Hipóteses ativas (não provadas)

1. Ao mostrar: a janela entra na composição do DWM antes de a opacidade valer.
2. Ao esconder: o DWM anima ou mantém a superfície da janela sem a opacidade.
3. A captura (`BitBlt`) mostra a janela que o monitor não mostraria. `DWMWA_CLOAK` tira a janela dos dois.

## Atividades

- **A01** — este plano.
- **A02** — sonda `probePrewarmFlash`: centenas de janelas pré-aquecidas, captura rápida, controle positivo.
- **A03** — correção em TDD, escolhida pela medição, com o tempo de pré-aquecimento e de primeira abertura antes e depois.
- **A04** — prova no runner Windows.

**Critério escrito antes de medir:** a sonda reproduz o defeito no código atual em K ciclos de N; a correção tem de dar 0 flash em N' ciclos
com N' tal que a chance de 0 sob a taxa medida seja menor que 1% (`(1 - K/N)^N' < 0,01`). O controle positivo acende em 100% dos ciclos.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness (plano); `frames.csv` de 380 gravações lidos por script, 3 imagens abertas | fatos acima |
| A02 (sonda) | `gradlew.bat desktopTest --tests "*PrewarmFlashSamplesTest*" --tests "*ArchitectureRulesTest*"` | vermelho (stub): 8 de 9, `expected:<[ClassifiedPrewarmFlash(cycle=null…)]> but was:<[]>`; devolvida 1× (flash na borda do ciclo saía sem ciclo), vermelho `:81 :94 :121 expected cycle=0/1, was cycle=null`; verde 13/13, `ArchitectureRulesTest` 41/41. A janela da sonda não tem harness: é a medição da A02 |
| A02 (1ª medição) | run [45606858789](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45606858789), sonda `62f6fbbf`, JVM quente, ~58 amostras/s | controle positivo: 300/300 ciclos acesos. Código atual: **0 de 1.200** ciclos com flash; janela mostrada por 26–33 ms (mediana). 1 trecho aceso por job fora de qualquer ciclo em 3 de 4 jobs e também no controle: arranque da sonda, hipótese. **Não reproduziu**: a sonda não tem arranque frio nem a HUD. Segue para partidas do app real |
