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
| A02 (reprodução) | run [45608664012](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45608664012), app real instrumentado, 720 partidas, captura de 24×24 px a 33–41 Hz (tela inteira: 29–33 Hz) | **32 de 720 (4,4%)**: `full` 9/360, `small` 23/360. 32/32 com uma amostra acesa que começa 5–19 ms depois de `opacity=0` e cruza o `componentShown` (7–26 ms); nenhuma ao esconder; sempre `DialogWindow` (Configurações, Ajuda), nunca Sessões CLI; o AWT declara opacidade 0.0 em todas. **Hipótese 2 descartada; 1 ou 3 abertas** (o DWM ou a captura) |
| A03 (escolha) | run [45612516405](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45612516405), 240 partidas por variante, mesmo código | `base` 7/240; espera de 150 ms entre opacidade e mostrar 2/240; `DWMWA_CLOAK` **0/240**; `DWMWA_CLOAK` com opacidade 1 **0/240**. `HRESULT` 0 nas 2.880 chamadas (ligar e desligar), 0 janela sem esconder, cloak nunca retirado antes de a janela sumir. Dois quadros sob cloak: mediana igual (54/13/28 ms contra 52/13/30); **pior caso em Configurações 484 ms contra 93** |
| A03 (correção) | `gradlew.bat desktopTest --tests "*ModalPrewarm*" --tests "*AwaitUntilTest*" --tests "*WindowsDwmCloak*" --tests "*AppDialogWindow*" --tests "*ModalRequestAction*" --tests "*ArchitectureRulesTest*"` | vermelho (stubs): `ModalPrewarmTest.kt:33 :52 :71 :96 :119 :133`, `WindowsDwmCloakTest.kt:28`, `AppDialogWindowTest.kt:136`, `AwaitUntilTest :143 :152`, todos `AssertionError`; devolvida 2× (opacidade 0 removida, espera por quadro em janela oculta, turno AWT perdido, relatório que não conferia com o arquivo); verde 111/111. `WindowsDwmCloakTest` lê `DWMWA_CLOAKED` = 1 e 0 numa janela nunca mostrada |
| A03 (sonda do ganho) | `gradlew.bat desktopTest --tests "*PrewarmFlashSamplesTest*" --tests "*ArchitectureRulesTest*"` | vermelho (stub) `PrewarmFlashSamplesTest.kt:155 :164 :171 :182 :190 :198`, `AssertionError` (`expected:<42> but was:<null>`, `expected:<DIALOG_STATE> but was:<WINDOW_STATE>`…); verde 19/19, `ArchitectureRulesTest` 41/41 |
| A03 (ganho) | run [45618622534](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45618622534), sonda `openAfter`, processo novo a cada medida, 75 por código | primeira abertura depois do pré-aquecimento: mediana 49 ms antes e 49 depois, p95 54 e 60, máximo 64 e 63: **ganho preservado**. O controle sem pré-aquecer não mediu (0 de 75: fecha aos 300 ms, antes do registro). Duração do pré-aquecimento: mediana 93 → **231 ms, máximo 1.235**. Causa, no `app.log` do run 45612516405: a 1ª chamada JNA, na EDT, custa mediana 55 ms, p95 147 e até **4.113 ms**; as seguintes 1–2 ms. Esperar a janela sumir custa 12–30 ms |
| A03 (carga fora da EDT) | aceite da A03 mais `--tests "*AwaitUntilTest*" --tests "*WindowCloakPreload*"` | vermelho (stub) `WindowCloakPreloadTest.kt:30 :43 :55 :60`, `AssertionError`; verde `WindowCloakPreloadTest` 5/5, `ModalPrewarmTest` 6/6, `AwaitUntilTest` 2/2, `WindowsDwmCloakTest` 1/1, `ArchitectureRulesTest` 41/41 |
| A03 (cobertura) | recorte da A03 com `koverXmlReport -Pcoverage`; `node tools/ci/diff-coverage.mjs --base origin/main --kover …` | 1ª medida 81,1% (30/37); vermelho `WindowsDwmCloakTest.kt:48 AssertionError` (os outros três casos cobrem código que já existia: caracterização); verde 139/139; cobertura do diff **97,4%** (37/38; falta `WindowsDwmCloak.kt:71`, o valor padrão do parâmetro). `Main.kt` e `AppDialogWindow.kt` ficam fora do Kover; o caminho deles é a A04 |
| A04 (prova) | runs [45617803652](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45617803652) (`9131b417`) e [45621361069](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45621361069) (`63e46dc7`), app real, 480 partidas cada, 30–41 amostras/s | **0 flash em 960** (o critério pedia 110: taxa 39/960 = 4,1%; com a correção, e as 480 das variantes, 0 em 1.440). 2.880/2.880 janelas pré-aquecidas, todas escondidas e fora do cloak no fim, nenhum processo caiu. Cloak na EDT: máximo **9 ms** (era 4.113). Pré-aquecimento com a carga fora da EDT: Configurações mediana 73, p95 104, máximo 270 ms (no `9131b417`: 141, 341 e 2.315). Falta a prova de 30 execuções com a regra de 1 quadro, que é da #657 |

## Problemas em aberto e riscos

- A abertura pedida pelo usuário também mostra a janela com opacidade 0 e sem cloak: a mesma piscada, antes da entrada animada, é possível e não foi medida.
- Não se sabe se o monitor mostra o quadro ou só a captura (`BitBlt`/`CopyFromScreen`): o cloak tira a janela dos dois.
- Medido só em `windows-latest` (1024×768, 96 dpi, Direct3D por software do runner). Windows 11 com GPU real não foi medido.
- Linux e macOS não pré-aquecem: caminho inalterado.
