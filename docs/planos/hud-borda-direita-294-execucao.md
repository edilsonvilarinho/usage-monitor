# Pisca do notch na borda direita (#294) — execução

## Contexto

[Issue #294](https://github.com/edilsonvilarinho/usage-monitor/issues/294): vídeo do notch da HUD
piscando na **borda direita**. O usuário confirmou a borda. O vídeo em si não foi decodificado (a
máquina não tem ffmpeg). O diagnóstico saiu do código e da medição abaixo.

## Causa

- `hudWindowBounds` põe a janela da borda direita em `x = área.x + área.largura - espessura`. A
  espessura parada era `notch + 16dp` e a aberta `notch + 10 + 264 + 16dp`: **a origem X andava 274dp
  a cada abrir e fechar.** Na de baixo, o mesmo em Y.
- A E11 (`profundidade-movimento-hud-notch-execucao.md`) já tinha medido que janela transparente que
  muda de origem mostra o conteúdo antigo no lugar novo, com o redimensionamento do Compose (duas
  chamadas AWT), com `setBounds` numa chamada só e com ele antes do estado. Ela fixou a origem só ao
  longo da borda, e o KDoc de `hudRestWindowBounds` admitia o quadro em branco embaixo e à direita.

## Medição (H0, spike descartável, não commitado)

`HudShapeSpikeTest`: uma `ComposeWindow` transparente, sempre no topo, colada na borda direita, com
um retângulo vermelho de 80 unidades no lugar do notch. `Robot.createScreenCapture` de uma linha em
laço por 400ms a cada troca, 10 trocas por modo. Windows 11, área útil 1280×752, escala 1,5.

| Modo | Quadros | Fora do lugar |
|---|---|---|
| (a) tamanho e posição em duas chamadas, origem anda (o app) | 667 | 39–49 (notch em 926–1005 em vez de 1200–1279 ao abrir; ausente ao fechar) |
| (a2) `setBounds` numa chamada só, origem anda | 667–669 | 40–44 |
| (b) janela fixa no tamanho da aberta, `Window.shape` alternando entre o notch e `null` | 670 | **0** |

Clique com `Robot` na área transparente, sobre uma janela opaca por baixo, com a HUD trazida à frente
antes de cada tentativa:

| Tentativa | Clique chegou à janela de baixo |
|---|---|
| sem recorte | 0 (engolido, confirma C11) |
| com recorte | 1 |
| sem recorte, de novo | 0 |
| com recorte, de novo | 1 |

As duas primeiras rodadas do spike tinham o teste de clique inválido: a janela de baixo não estava
visível (foi aberta atrás do terminal) e, depois, subia na ordem Z ao receber o primeiro clique.

## Desenho entregue

- `hudDockedWindowBounds` substitui `hudRestWindowBounds` e `hudOpenWindowBounds`: parada e aberta são
  **a mesma janela**, com o tamanho da aberta.
- `hudRestHitRegion`: função pura com a área de clique da janela parada. É o notch recolhido mais a
  margem de sombra (16dp) nas bordas de dentro, posicionado pela mesma conta do layout de `HudNotch`.
- `HudWindowHost` aplica a região como `Window.shape` num `SideEffect`. Parada, recorte; aberta ou
  arrastando, `null`. A ordem do hover não mudou: o recorte sai antes do quadro que abre o balão e
  volta 200ms depois de ele fechar.
- `HudHitRegionApplier` guarda o último retângulo, porque `Window.getShape()` devolve cópia em
  `Path2D`. Se `setShape` lançar (sem `PERPIXEL_TRANSPARENT`), a HUD segue sem recorte.
- Efeito colateral: as duas faixas de 38dp das alças, que a E11 deixava engolindo clique com a HUD
  parada em cima e à esquerda, também saem da área de clique.

## Riscos

- **macOS e Linux não foram medidos.** O recorte depende de `PERPIXEL_TRANSPARENT`. Sem ele, o
  fallback é a área do balão engolir clique.
- O recorte corta a pintura: sombra ou dica que passe de 16dp sai cortada com a HUD parada.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| H0 | Spike de medição (acima) | `gradlew.bat desktopTest --tests "com.usagemonitor.HudShapeSpikeTest"` | origem andando: 39–49 de ~668 quadros fora do lugar; janela fixa + recorte: 0 de 670; clique fora do recorte passa (2/2), sem recorte é engolido (0/2). Spike apagado |
| H1–H4 | `hudDockedWindowBounds` + `hudRestHitRegion`, `Window.shape` no host, testes nas quatro bordas (recorte contém o notch com a margem, encosta na borda, fica dentro da janela e não cobre o balão), KDoc e `CLAUDE.md` | `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.ui.HudNotchTextFitTest" --tests "com.usagemonitor.HudWindowPreferencesTest"` | 85 testes, 0 falhas |
| H5 | Suíte completa | `gradlew.bat allTests` | 2211 testes, 0 falhas, 0 ignorados (3m38s) |
| H6 | Verificação manual no app: HUD nas bordas direita e de baixo, hover 10×, clique numa janela logo ao lado do notch parado, arrasto até outra borda e volta | `gradlew.bat run` | **pendente**: exige o usuário |
