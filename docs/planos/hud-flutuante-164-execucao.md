# Barra HUD flutuante — plano de execução

Issue [#164](https://github.com/edilsonvilarinho/usage-monitor/issues/164), segunda passada. A
primeira entregou o modo (branch `feat/hud-minimal-164`, commits `1b2fc22`..`8333adc`); esta corrige
o que apareceu ao usá-lo.

## Problema

A pílula entregue tem 320×24dp fixos, `alwaysOnTop`, ancorada **imutavelmente** no canto superior
direito da tela. Três defeitos observados no uso real:

1. **Continua atrapalhando outras janelas.** O canto superior direito é onde IDE, navegador e editor
   põem controles, e o Compose Desktop não tem click-through parcial: o retângulo inteiro captura o
   clique, visível ou não. Com posição imutável, não há para onde tirá-la.
2. **O hover pisca.** A lista de fontes do commit `8333adc` sai por `HoverTooltipBox` →
   `TooltipBox` → `Popup`, e popup no Compose Desktop é camada **dentro** da janela.
3. **Não dá para mover.** Decisão registrada em `DesktopWindowFrame.kt` ("ancoragem é geometria de
   `Main.kt`, não gesto do usuário"), que esta passada reverte deliberadamente.

Referência trazida pelo usuário: **TBH: Task Bar Hero** — janela minúscula, sempre no topo,
encaixada na barra de tarefas. O que se aproveita dele é o modelo de ocupação: área mínima, posição
escolhida por quem usa, presença passiva.

## Diagnóstico do flicker

**Fatos confirmados (código + documentação da plataforma):**

- `HudBar` envolve a faixa inteira em `HoverTooltipBox`, que é `TooltipBox` do Material 3 e portanto
  um `Popup`.
- Compose Multiplatform **1.7.1** (`gradle/libs.versions.toml`), **sem `compose.layers.type`
  definido** em lugar nenhum do projeto. No default, `Popup` no desktop é camada dentro da janela,
  recortada pelos limites dela.
- A janela HUD tem 24dp de altura (`AppChrome.hud`); o conteúdo da tooltip tem
  `widthIn(min = 180.dp)` e uma linha por fonte. Não cabe.
- O anchor do `TooltipBox` é a faixa inteira (`fillMaxWidth().height(AppChrome.hud)`).

**Hipótese ativa (não confirmada por medição ao vivo):** o popup é recortado/reposicionado para
dentro dos 24dp e cobre o próprio anchor; o ponteiro passa a estar sobre o popup, o anchor recebe
`Exit`, a tooltip fecha, o ponteiro volta ao anchor — laço.

**Por que isso não bloqueia:** a correção remove o `Popup` do HUD. Sem popup não há recorte nem laço,
qualquer que seja o mecanismo exato. O precedente está no CLAUDE.md ("Piso de largura da tooltip de
cota"): popup que cobre o próprio alvo é defeito já conhecido deste app.

## Decisões

1. **Arrasto livre, com encaixe e posição persistida.** Clique curto continua abrindo a janela
   completa; o que separa clique de arrasto é o limiar de deslocamento, não dois gestos concorrentes
   — `clickable` dentro de `WindowDraggableArea` faz o filho consumir o `down` e o arrasto morre.
2. **O hover cresce a própria janela**, e o `Popup` sai. O hover mora no container raiz, não na
   pílula: preso aos 24dp de cima, mover o ponteiro para dentro da lista tiraria o hover e a janela
   colapsaria — o mesmo laço com outro nome.
3. **Acima da barra de tarefas, não sobre ela.** A área útil de `availableWindowAreaDp()` sai de
   `maximumWindowBounds` e já desconta a barra. Sobrepô-la exigiria limites físicos de tela e
   disputa de ordem-z com uma janela que também é topmost — fora de escopo, declaradamente.
4. **`HUD_PILL_WIDTH_DP` (320) vira teto, não largura.** A pílula mede o conteúdo. O papel que a
   largura fixa cumpria — não mudar de tamanho a cada coleta — passa a ser cumprido pelo teto mais as
   reticências que a faixa já usava.
5. **A largura é estimada por métrica de fonte, não medida na composição.** A escala `label*` é mono,
   então o avanço por caractere é determinístico. Medir e devolver a largura para a janela fecharia o
   laço `redimensionar → recompor → medir → redimensionar`.
6. **Opacidade quando parado e recolher ao ponto quando tudo estiver Normal.** As duas reduzem
   ocupação sem esconder o dado que importa: o ponto continua lá, e qualquer fonte fora de `ON_TRACK`
   devolve o texto.
7. **A linha do painel não é `AppDataRow`.** Aquela primitiva tem piso de 32dp mais 8dp de padding, e
   seis fontes dariam ~288dp de painel — uma janela, não um HUD. Mesma exceção que `AppChrome.hud` já
   abre ao furar o piso de 28dp do cromo.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Geometria pura: largura por conteúdo, altura do painel, encaixe e canto de expansão | `gradlew.bat desktopTest --tests "com.usagemonitor.HudWindowGeometryTest"` | `BUILD SUCCESSFUL`, 23 testes |
| A2 | Posição da pílula persistida em `PreferencesSettings` | `gradlew.bat desktopTest --tests "com.usagemonitor.HudWindowPreferencesTest"` | `BUILD SUCCESSFUL`, 6 testes |
| A3 | O hover cresce a janela; o `Popup` sai do HUD | `gradlew.bat allTests` | `BUILD SUCCESSFUL`, 1799 testes (11 em `DesktopWindowFrameTest`, 4 novos) |
| A4 | Arrasto da pílula, encaixe na borda e posição persistida | `gradlew.bat allTests` | `BUILD SUCCESSFUL`, 1801 testes (13 em `DesktopWindowFrameTest`, 2 novos) |
| A5 | Recolher ao ponto com tudo em `ON_TRACK` e translucidez sem hover | `gradlew.bat allTests` | `BUILD SUCCESSFUL`, 1803 testes (15 em `DesktopWindowFrameTest`, 2 novos) |
| A6 | Protótipo, design system e CLAUDE.md | inspeção do diff + balanceamento de tags do bloco HUD do protótipo (35/35 `div`, 27/27 `span`) | quatro estados no protótipo e no kit, `AppStatusDot` publicado, bloco "Barra HUD" do CLAUDE.md reescrito, cinco linhas novas na §15 |
