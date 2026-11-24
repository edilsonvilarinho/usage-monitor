# Testes de propriedade da HUD — issue #650

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `test/issue-650-hud-properties` (worktree `.claude/worktrees/hud-property-650`, base `origin/main` `b19c688f`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Objetivo

Sortear combinações de contas, borda, monitor, escala e ações e conferir regras fixas, para achar o caso que ninguém
escreveu à mão.

## Fatos confirmados (2026-11-24, `b19c688f`)

- Linha de base da #645: `hud-window` tem 3 origens com defeito em 16 PRs de produto.
- Geometria pura: `hudNotchSizes` (`HudNotchGeometry.kt:167`), `hudWindowBounds` (`:576`), `hudDockedWindowBounds`
  (`:630`), `hudRestHitRegion` (`:652`), `hudDragWindowBounds` (`:690`), `hudHostWindowBounds`, `hudMaxAlong`
  (`HudWindowGeometry.kt`), `hudFloatingBounds` (`HudFloatingPlacement.kt:28`), `hudFloatingColumnParts`
  (`HudFloatingLayout.kt:76`).
- Regras escritas em `docs/hud-notch.md`: notch parado na área útil (346); janela de origem e tamanho fixos ao abrir
  (209); alças nunca fora da tela (330); a faixa **vira** compacta quando a completa passa de 45% da borda horizontal ou
  80% da lateral (56 — não é teto da compacta, como a 1ª versão deste plano dizia); janela flutuante
  sempre na área útil, buraco se desloca (705). Exceções: auto-ocultação da barra (351) e arrasto livre (349).
- "Alvo de clique contido na janela" **não** está escrito no documento.
- Estado do balão: `HudNotchBalloonState` (`ringHovered`, `holeClicked`, `holeHovered`, `holeRowSelected`, `closeHole`) e
  `hudFloatingBalloonAfterMinimize` (`HudFloatingHost.kt:185`). `FollowAccounts` é composable com efeitos.

## Decisões

- **Sem dependência nova:** `kotlin.random.Random(seed)` e um laço. Semente fixa por teste no CI; a falha imprime semente,
  iteração e entrada, e a mesma semente refaz o caso.
- **Invariante só entra se estiver no `docs/hud-notch.md` ou no comentário da função.** Regra que o código não promete não
  vira teste; o que parecer regra e não estiver escrito é listado para o dono decidir.
- **Falha que aparecer é achado:** para e vira caso mínimo na issue. Não se estreita o gerador para o teste passar, salvo
  entrada impossível no produto, com a prova de que é impossível.
- **A entrada passa pelo caminho do produto:** na lateral, `HudWindow.kt` aplica o auto-recolher (`HudAutoMinimize.kt`) antes
  de `hudNotchSizes`. A 1ª rodada o pulou e deu contraexemplo que o produto não alcança.
- **Prova do teste:** cada propriedade reprova com uma quebra proposital da função, desfeita em seguida.
- Orçamento: até 5 s somados na suíte local.

## Atividades

- **A01** — este plano.
- **A02** — gerador e executor com semente, e as propriedades da geometria.
- **A03** — sequência aleatória de ações sobre o balão; caso-guia: reintroduzir a #631 e ver reprovar.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | regras conferidas contra `docs/hud-notch.md` |
| A02 | `gradlew.bat desktopTest --tests "*HudGeometryPropertyTest*" --tests "*ArchitectureRulesTest*"` | vermelho do executor: `HudGeometryPropertyTest.kt:16` e `:22`, `AssertionError`; verde 37 testes, 2 ignorados, em execução independente. Mutações 5/5 mortas: origem/tamanho aberto × fechado (`:64`), flutuante na área útil (`:115`), finitude (`:197`), determinismo (`:150`), regra do compacto (`:100`). **Dois contraexemplos** pelo caminho do produto, ignorados com o menor caso: janela encaixada fora da área útil (RIGHT, 1534×344 dp, 1 conta, 3 cotas, sobra 4 dp) e alças fora da tela (BOTTOM, 922×885 dp, 10 contas, sobra 18 dp). 1ª rodada descartada: pulava o auto-recolher e lia mal a regra do compacto |
| A03 | `gradlew.bat desktopTest --tests "*PropertyTest*" --tests "*ArchitectureRulesTest*"` | sem vermelho de implementação: o produto já cumpre as regras. Vermelho por mutação, 10/10: caso-guia #631 reintroduzido reprova em `:244 expected null, but was:<Account(index=0)>`, menor caso `Open(Account(0)), Toggle(0)`; as outras 9 nas invariantes do estado e do flutuante. Verde 39 testes, 2 ignorados (#660), em execução independente; 0,1 s e 0,3 s nas classes novas. `FollowAccounts` fora: nenhum teste o roda. Referências de linha do `docs/hud-notch.md` trocadas pelo texto da regra |

## Problemas em aberto e riscos

- Propriedade não vê a janela nativa; isso é da #652.
- Sorteio com semente fixa acha menos que semente livre, mas não deixa o CI instável.
