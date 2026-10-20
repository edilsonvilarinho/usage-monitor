# HUD perde a transparência ao cruzar monitores no Linux (#416) — execução

## Contexto

[Issue #416](https://github.com/edilsonvilarinho/usage-monitor/issues/416), execução na [#426](https://github.com/edilsonvilarinho/usage-monitor/issues/426): com a HUD no monitor
secundário, a barra vira um **retângulo preto opaco** de 442×557 que fica sobre todas as janelas e
engole clique em toda a sua área. Fora do Windows a janela não tem recorte (`hudUsesHitRegion`,
#340) e a HUD é a única janela do app (#361): não sobra caminho pela interface, e o relato chegou
como "congela completamente, fica travado para sempre".

A primeira tentativa (`a1812d57`, branch `fix/issue-416-secondary-monitor-freeze`) partiu de três
hipóteses — renderização OpenGL, buffer preso ao contexto do monitor de origem e travamento da EDT
no arrasto — e **não resolveu**.

## O que foi medido

Ambiente: Bazzite 44 (Kinoite) · KDE Plasma · Wayland com XWayland · Java 17.0.15 · NVIDIA
RTX 4070 SUPER · dois monitores, `DP-1 0,1440 3440x1440` (primário) e `DP-2 421,0 2560x1440`
(secundário).

### Estado do app com o defeito à vista

Medido com a v41.11.0-beta.7 rodando (pid 2859, `hudScreenId = :0.1`):

| Medida | Comando | Resultado |
|---|---|---|
| Profundidade da janela da HUD | `xwininfo -id 20971589` | **`Depth: 24`** em `2539,422 442x557`, `IsViewable` |
| Estado da janela | `xprop -id 20971589 _NET_WM_STATE` | `_NET_WM_STATE_ABOVE, _NET_WM_STATE_STAYS_ON_TOP` |
| Visuais do servidor X | `xdpyinfo` | 1 tela; `depths (7): 24,1,4,8,15,16,32`; vários TrueColor de 32 |
| Troca de device | `~/.usage-monitor/diagnostics/app.log` | `hud-device :0.0->:0.1`; `alfa px=true`; `render=SOFTWARE_FAST` |
| EDT | `jstack 2859` | `AWT-EventQueue-0` parada em `getNextEvent` — **ociosa, não travada** |

### Sonda do mecanismo

AWT puro (`JFrame` sem decoração, `setBackground(Color(0,0,0,0))`), sem Skiko e sem Compose, para
isolar o toolkit. Amostragem paralela de `xwininfo` confirmando a profundidade real da janela X:

| Etapa | Device | `colorModel.pixelSize` | `isTranslucencyCapable` | `xwininfo` |
|---|---|---|---|---|
| 1 nasce no primário | `:0.0` | 32 | `true` | 32bpp |
| 2 movida ao secundário | `:0.1` | **24** | **`false`** | **24bpp** |
| 3 depois do reparo | `:0.1` | 32 | `true` | 32bpp |
| 4 de volta ao primário | `:0.0` | **24** | **`false`** | **24bpp** |
| 5 reparo no primário | `:0.0` | 32 | `true` | 32bpp |

Os dois devices respondem `default=24bpp`, `perPixelTranslucent=true` e têm uma configuração de
32bpp disponível.

## Diagnóstico

Uma janela X11 de profundidade 24 **não tem canal alfa**: ela não pode ser transparente, pinta o
retângulo inteiro. A janela nasce no device padrão e, por ser `transparent=true`, o AWT escolhe ali
uma `GraphicsConfiguration` de 32 bits. Quando a janela cruza para outro `GraphicsDevice`, o AWT
substitui essa configuração pela **configuração padrão do device de destino**, que é a de 24 bits —
e o alfa morre.

**O sintoma não é "quebra no secundário": é "quebra ao cruzar qualquer fronteira de monitor e nunca
mais se recupera"** (etapa 4 da sonda). Voltar para o primário não conserta, porque a configuração
padrão do primário também é de 24 bits. Só reiniciar o app resolve — o "para sempre" do relato.

### Por que a primeira tentativa não pegou

`java.awt.Window.setBackground` só troca a `GraphicsConfiguration` por uma com alfa quando
`alfaAntigo == 255 && alfaNovo < 255`. O fundo da HUD já é `Color(0,0,0,0)`: a reaplicação de
`a1812d57` faz alfa 0 → alfa 0, a condição é falsa e **nada acontece** — mas o log registra
`reaplicar=ok`. O toggle de `isVisible` e o empurrão de ±1px no tamanho também não trocam a
configuração.

A troca de `skiko.renderApi` de `SOFTWARE` para `OPENGL` não ataca a causa e arrisca a #421 (tela
preta no Wayland com NVIDIA, que é esta máquina). Os 5 s em `LinuxOpenGLRedrawer.swapBuffers` que
motivaram tirar o arrasto ao vivo só existem no caminho OpenGL.

## Decisões do usuário

1. **Reverter `a1812d57`** e refazer: volta `skiko.renderApi = SOFTWARE` no Linux (#421) e o
   arrasto ao vivo.
2. **Rede de segurança**: se o reparo falhar em algum ambiente, a HUD recorta a janela ao notch no
   Linux. O pior caso vira um notch sozinho, nunca mais um retângulo preto que engole clique.
3. Issue própria com o plano, referenciando a #416.
4. Teste ponta a ponta nesta máquina, cenário a cenário, antes e depois.

## Como reconhecer o travamento

"Travado" **não é** a EDT parada — o dump de threads prova que ela está ociosa. É a janela da HUD em
profundidade 24 (sem alfa), pintando 442×557 de preto, com `_NET_WM_STATE_ABOVE` e sem recorte, logo
engolindo clique em toda a área.

```bash
xwininfo -id $(DISPLAY=:0 xdotool search --name "Usage Monitor" | head -1) | grep Depth
```

`Depth: 24` é o defeito; `Depth: 32` é o estado são.

## Matriz de cenários

| # | Cenário | Antes | Depois |
|---|---|---|---|
| C1 | App abre com placement salvo no secundário (autostart) | 24 | — |
| C2 | App abre com placement no primário | 32 | — |
| C3 | Arrastar do primário para o secundário | 24 | — |
| C4 | Arrastar de volta do secundário para o primário | 24 | — |
| C5 | Trocar resolução / religar um monitor | coberto por desenho (o gatilho é a falta de alfa, não a troca de monitor) | — |
| C6 | Bloquear e desbloquear a sessão | coberto por desenho | — |

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Sonda do mecanismo em AWT puro, matriz de cenários, este plano e a issue de execução | `javac AlphaProbe.java && java AlphaProbe` com amostragem paralela de `xwininfo` | `32 → 24 → 32 → 24 → 32` nos dois níveis (AWT e janela X). Confirmado que voltar ao primário **também** perde o alfa: o defeito é cruzar a fronteira, não o monitor de destino |
| A2 | Revert de `a1812d57` (volta `skiko.renderApi = SOFTWARE` no Linux e o arrasto ao vivo); preservado só o `deviceId` nas facts, com teste de que o id do Windows (`\\Display2`, uma barra) sobrevive ao saneamento | `sh gradlew desktopTest --tests "com.usagemonitor.HudWindowFactsTest"` | 4 testes, 0 falhas. Na primeira passada o teste novo acusou que `\\\\Display2` (duas barras) casa com o caminho UNC do saneamento e vira `<caminho>` — fixado o valor real, de uma barra |
| A3 | `HudWindowTranslucency.kt`: decisão pura restrita ao Linux, reparo `background = null` → `Color(0,0,0,0)` na EDT, log `hud-alpha` com a profundidade antes/depois, `pixelSize` também na linha `hud-window`. `HudWindowHost` passou de 300 linhas com o efeito novo, então as duas contas do arrasto saíram para `HudWindowDrag.kt` (e viraram testáveis pela primeira vez) | `sh gradlew desktopTest --tests "com.usagemonitor.HudWindowTranslucencyTest" --tests "com.usagemonitor.HudWindowDragTest" --tests "com.usagemonitor.HudWindowFactsTest" --tests "com.usagemonitor.architecture.*"` | verde. Duas falhas reais na primeira passada: o `ArchitectureRulesTest` acusou `HudWindowHost` com 303 linhas (limite 300), corrigido pela extração; e o teste do saneamento mostrou a linha em 207 caracteres com uma mensagem de exceção longa — daí o teto `HUD_TRANSLUCENCY_FAILURE_LENGTH`, para o motivo não empurrar a profundidade para fora do log |
