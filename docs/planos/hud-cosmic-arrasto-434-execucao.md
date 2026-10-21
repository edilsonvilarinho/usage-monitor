# COSMIC: nenhuma janela arrasta e a HUD ganha a borda de janela ativa (#434) — execução

Issue: [#434](https://github.com/edilsonvilarinho/usage-monitor/issues/434), relatada na [#416](https://github.com/edilsonvilarinho/usage-monitor/issues/416), continuação da [#430](https://github.com/edilsonvilarinho/usage-monitor/issues/430).
O texto abaixo é o corpo da issue no momento da abertura; a fonte viva do andamento é a tabela
de pontos de situação no fim deste arquivo.

## Resumo

A `v41.11.0-beta.9` (#430) não resolveu no COSMIC. O relator da #416 (Pop!_OS + COSMIC) mandou um relato novo, por conversa, com capturas de tela. O que ele mostra muda a leitura do defeito:

1. **"Travado" quer dizer "não arrasta".** Nenhuma janela do app se move no COSMIC: nem a HUD, nem a janela **Análise** pela barra de título. É, ao pé da letra, o título da #416 ("App fica travado na tela").
2. **Uma borda ciano contorna o retângulo inteiro da janela da HUD**, e não só o desenho dela. É o realce de janela ativa do COSMIC desenhado no limite da janela X11. Para quem vê, parece que "o app roda dentro de uma janela sem título e sem fundo".
3. **O relator reproduziu o mesmo defeito com um hello world** em Kotlin, com janela transparente, e diz que **corrigiu** nele. O defeito não depende do Compose nem da HUD.

Até agora a medição foi toda no KWin (Bazzite), que não reproduz. O relator testa cada beta, e cada volta custa um dia. Esta issue monta **um COSMIC aqui** para reproduzir, medir e corrigir sem depender dele.

## Hipóteses

- **H1: o COSMIC ignora a posição que uma janela X11 gerenciada pede para si mesma.** Todo arrasto do app é feito pelo cliente, que pede a posição nova da janela: na HUD em `HudWindowFrame.kt:175` (`window.location = point`) e nas janelas com moldura própria pelo `WindowDraggableArea` (`DesktopWindowFrame.kt:160`). Se o compositor descarta o pedido, nada arrasta. É a mesma causa da #430: a janela abre sempre no centro do monitor (`731,163`), seja qual for o pedido. **Correção candidata:** pedir ao compositor que ele faça o arrasto (`_NET_WM_MOVERESIZE`, EWMH), como fazem Chrome e Electron com barra própria no X11.
- **H2: janela não gerenciada escapa do realce, do mosaico e da colocação.** No AWT, `Window.Type.POPUP` vira janela *override-redirect*. Ela não ganha borda, não entra no mosaico e fica na posição pedida. O custo possível é o foco de teclado (F1 da HUD) e o empilhamento. Só a medição decide.

**Avaliadas e descartadas como correção** (vieram de uma consulta a outro assistente):

- `-Dsun.java2d.opengl`/`xrender`: valem para o pipeline do Java2D, e o Compose desenha pelo Skiko. O OpenGL do Skiko já foi testado na #416: a interface ficou parada em `swapBuffers` e a mudança foi revertida em `ba453161`.
- `-Dsun.java2d.uiScale=1`: quebra a escala em telas de alta densidade.
- `-Djdk.gtk.version=3`: só afeta o *look and feel* GTK.
- "a causa é a renderização por software": contradiz a medição da #426. A transparência cai porque o AWT troca a janela para a configuração de 24 bits do monitor, e não pelo modo de renderização.

## Ambiente de reprodução

| Opção | Decisão |
|---|---|
| **A. `cosmic-comp` aninhado num distrobox Fedora 44** | **primeira.** O repositório do Fedora 44 tem `cosmic-comp 1.10.0` (conferido). Roda numa janela do Bazzite, sem reiniciar nem mexer na imagem. Tem um monitor só: cobre H1 e H2, mas não a troca de monitor |
| **B. VM Pop!_OS 24.04 (QEMU no distrobox, `virtio-vga-gl,max_outputs=2`)** | só se a A não reproduzir, ou para a transparência com dois monitores |
| C. `rpm-ostree install` do COSMIC no Bazzite | descartada: exige reiniciar a cada mudança e altera a imagem do sistema |

## Plano

Branch própria em worktree; plano versionado em `docs/planos/hud-cosmic-arrasto-434-execucao.md`, com a tabela de pontos de situação atualizada **no mesmo commit** de cada atividade.

| # | Atividade | Muda comportamento? |
|---|---|---|
| A1 | Esta issue, a worktree, o plano e o aviso na #416. A A6 da #430 (posição na abertura) passa para cá | não |
| A2 | Pedir ao relator o hello world com a correção, a versão do `cosmic-comp`, se o mosaico automático está ligado e os pacotes `--diagnostics` da beta.9 | não |
| A3 | Ambiente A num script versionado. Com o app numa raiz de dados de rascunho, verificar: abre centralizada? A HUD e a Análise arrastam? Aparece a borda ciano? **Reproduzir é a condição para seguir;** se não reproduzir, monta-se o B | não |
| A4 | Sonda `probeCosmicWindow` em AWT puro, no molde da `probeHudAlpha`: posição pedida depois do mapeamento (respeitada? dura?), arrasto por `_NET_WM_MOVERESIZE`, tipos de janela (`NORMAL`/`UTILITY`/`POPUP`) quanto a posição, borda, mosaico, foco e topo, e a profundidade de 32 bits | não |
| A5 | Correção **só no COSMIC**, atrás de função pura com teste por desktop (regra da #340). O que a A4 sustentar: arrasto pelo compositor na HUD e nas janelas e, talvez, a HUD não gerenciada. A forma de chegar ao X11 (JNA ou as classes internas do AWT) é decidida aqui | sim, só no COSMIC |
| A6 | Medição: não-regressão no KDE (K1–K5 da #430), o COSMIC aninhado, a VM se for preciso, `allTests` | não |
| A7 | Beta, teste do relator, PR com as plataformas testadas (#342) | não |

## Relacionadas

- #416, o relato original
- #426, a transparência ao cruzar monitores
- #430, a adoção do monitor do compositor (`v41.11.0-beta.9`)
- #340, a regra de restrição por plataforma

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Issue #434 aberta com o relato novo, as hipóteses H1/H2, a avaliação das flags sugeridas e o plano; worktree `../usage-monitor-434` na branch `fix/issue-434-cosmic-window-drag`; o registro da beta.9 que estava só na branch `fix/issue-430-cosmic-open-position` trazido para cá (a A6 da #430 continua aqui); comentário na #416 apontando para a #434 | `gh issue create`; `git worktree add ../usage-monitor-434 -b fix/issue-434-cosmic-window-drag origin/main`; `git cherry-pick 952b64ee`; `gh issue comment 416` | issue #434 aberta; worktree criada a partir de `b223088f` |
