# HUD flutuante alcança os cantos no Linux — issue #625

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-19,
branch `fix/issue-625-floating-corners` (worktree `.claude/worktrees/floating-corners`, base `origin/main` `a4fe353e`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Relato de um usuário do Linux no `v42.3.0-beta.13`: a HUD flutuante não chega aos cantos da tela. Fazer o buraco
alcançar qualquer ponto da área útil em qualquer gerenciador de janelas, sem depender de a janela poder sair da tela.

## Fatos confirmados (2026-11-19, `a4fe353e`)

Pacote `--diagnostics` do usuário (`usage-monitor-diagnostics-2026-11-19T170233`), lido com `grep` no `app.log`.

- Ambiente (`environment.txt`): `XDG_CURRENT_DESKTOP=Pantheon`, `XDG_SESSION_TYPE=x11`, `screen.0 bounds=0,0 1920x1080
  insets=t30`, alfa por pixel disponível.
- Janela flutuante de 778×452 a 778×546. Sete arrastos com `pedido 1499,…` deram `obtido 1142,…` (= 1920 − 778), e
  pedidos `y=-55/-173/-211/-241` deram `y=30` (fim do painel). Cada um seguido de `hud movida pelo sistema: pedido …
  obtido …` (`HudSystemMoveProbe`, `HudWindowFacts.kt`): quem move é o gerenciador, não o app.
- Reabertura: `hud pedido 1499,-211 778x546 obtido 1142,30 778x546 (diferente)`.
- Causa no código: `hudFloatingBounds` (`HudFloatingPlacement.kt:19-36`) prende só a âncora (o buraco) na área e põe a
  janela em `âncora − largura/2`, saindo da tela por desenho; `hudFloatingColumnParts` (`HudFloatingLayout.kt:73-125`)
  põe o buraco sempre no centro da janela. Com a janela presa, o buraco para a 389 px da borda direita.
- No arrasto, o buraco é preso nos **limites** do monitor (`pointerScreen()?.bounds`, `HudWindow.kt:295`), não na área útil.
- Sem medição: bordas esquerda e de baixo (nenhum arrasto até elas no log).

## Decisões

- **Janela sempre dentro da área útil; o buraco se desloca dentro dela.** A "janela virtual" de hoje (buraco no centro)
  continua sendo o modelo do arrasto e da posição gravada; a janela real é ela presa na área útil, e o buraco fica em
  `centro virtual − origem real`. Vale em todas as plataformas: no Windows o resultado só muda perto da borda.
- Recusada a janela `POPUP` no Linux: foco e teclado de janela sem gerenciador não foram medidos fora do COSMIC.
- Balão posicionado pela coluna (encostado nela com um vão), não pela borda da janela: com o buraco no centro é a mesma
  posição de hoje.
- Lado do balão decidido pelo X do buraco na tela, não pelo centro da janela.
- Sem mudança de desenho (cores, tamanhos, componentes): protótipo e design system não mudam; `docs/hud-notch.md` sim.

## Atividades

| ID | Atividade |
|---|---|
| A01 | Este plano |
| A02 | `hudFloatingColumnParts` com o centro do buraco na janela (padrão: centro); balão encostado na coluna; `columnSlide` pelo topo real da janela |
| A03 | `HudFloatingHud` recebe o centro do buraco e o usa no layout, no topo do balão e no fallback do anel |
| A04 | `hudFloatingBounds`/`hudFloatingWindowGeometry`: janela presa na área útil, centro do buraco na janela, lado do balão pelo buraco; `HudWindow` usa a geometria no arrasto (área útil do monitor do ponteiro); `docs/hud-notch.md` |

## Ponto de situação

| ID | Estado | Execução |
|---|---|---|
| A01 | feito | Plano escrito; sem harness de teste (documento). |
| A02 | feito | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudFloatingLayoutTest"` → 37 testes, 3 falhas na asserção (`HudFloatingLayoutTest.kt:401` buraco deslocado: `expected DpRect(left=560.0.dp…) but was DpRect(left=336.0.dp…)`; `:417` idem à esquerda; `:436` perto do topo: coluna `expected top=30.0.dp but was top=366.0.dp`). Verde: `--tests "com.usagemonitor.HudFloatingLayoutTest" --tests "com.usagemonitor.ui.HudFloatingHudTest"` → 37 + 23, 0 falhas; testes antigos sem edição. |
| A03 | feito | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudFloatingHudTest"` → 25 testes, 2 falhas na asserção (`HudFloatingHudTest.kt:291` centro X do buraco ≠ 583 pedido, buraco ainda no centro da janela; `:303` balão ao lado da coluna deslocada); mensagem literal da :291 não capturada (XML sobrescrito pela execução verde). Devolvida 1×: o teste da :303 media a caixa do balão (+`HUD_BALLOON_GAP`), não o corpo. Verde: `--tests "com.usagemonitor.ui.HudFloatingHudTest" --tests "com.usagemonitor.ui.HudWindowContentTest" --tests "*ArchitectureRulesTest*"` → 60 testes, 0 falhas. |
| A04 | feito | A04a (pura) vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudFloatingPlacementTest" --tests "com.usagemonitor.HudFloatingHostTest"` → 56 testes, 24 falhas na asserção (`HudFloatingPlacementTest:115` caso do log: `expected HudPxRect(xPx=1142, yPx=30, …) but was HudPxRect(xPx=1491, yPx=-203, …)`; `HudFloatingHostTest:41` `expected (0.0.dp, 0.0.dp) but was (-70.0.dp, 0.0.dp)`). Testes antigos que afirmavam a janela fora da área foram invertidos (cantos, campo no canto, área com origem deslocada, minimizado/expandido nos cantos). A04b (`HudWindow.kt`, sem harness de janela, #342): centro ao vivo no arrasto, janela pela origem presa, área útil do monitor do ponteiro. Verde do recorte: `desktopTest` Placement 12, Host 44, Layout 37, HudWindowDrag 15, ui.HudFloatingHud 25, ui.HudWindowContent 7, ArchitectureRules 28 → 168, 0 falhas; `desktopJar` ok. |

## Problemas em aberto e riscos

- Regra #342: `HudWindow.kt` muda (arrasto). Sem teste de janela; o PR declara o risco e a medição é o beta no Linux.
- Durante o arrasto encostado na borda, o conteúdo se move dentro da janela a cada tique (recomposição, sem
  redimensionamento AWT). Custo de quadro não medido.
