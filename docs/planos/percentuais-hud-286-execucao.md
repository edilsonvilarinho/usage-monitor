# Percentuais da 5h e da semanal no notch (#286) — execução

## Contexto

A [#286](https://github.com/edilsonvilarinho/usage-monitor/issues/286) diz que os usuários estranham
o percentual do notch. A captura anexada mostra a conta Anthropic colada na borda direita. O balão
traz **Sessão 5h 45%** e **Semanal 72%**, e o notch mostra só **72% Crítico**, sem dizer de qual
janela é o número.

O levantamento no código (2026-09-25, base `a6103be`) confirma a leitura da issue e acha duas causas
a mais.

| # | Achado | Onde |
|---|---|---|
| D1 | O notch imprime **um** percentual por conta: o da cota em foco (`HudAccount.focus`). | `HudRingItem`, `HudNotch.kt:775` |
| D2 | A cota em foco é a de **pior risco**, e no empate a de maior percentual. Ela não é fixa: o mesmo lugar mostra a 5h numa coleta e a semanal na seguinte, conforme o risco muda. | `buildHudAccounts`, `HudModel.kt` (`focusIndex`) |
| D3 | Nada perto do número diz a janela. O rótulo curto (`5h`, `7d`) existe em `HudQuota.shortLabel`, mas só chega à descrição de acessibilidade e ao balão. | `hudRingDescription`, `HudBalloon.kt` |
| D4 | A palavra ("Crítico") é o **pior** estado da conta (`statusLabel`), não o da cota impressa. Hoje as duas coincidem porque o foco é o pior risco; se o número passar a ser outro, as duas se descolam. | `HudModel.kt` (`worst`) |
| D5 | Os anéis já mostram as duas janelas (semanal por fora, 5h por dentro, #278), mas sem legenda no notch. Quem não abre o balão não sabe qual arco é qual. | `HudAccount.rings`, `AppUsageRing` |
| D6 | O mesmo número "da cota em foco" aparece no tooltip da bandeja, também sem a janela: "Anthropic — Padrão 72%". | `hudTraySummary`, `HudModel.kt` |

O foco é usado em mais três lugares, que **não mudam** com nenhuma das alternativas: o pulso de
atenção (`attentionRingIndex`), a célula compacta (`HudNotchSizes.compact`) e o tooltip da bandeja.

## As quatro alternativas

Esboços para uma conta com 5h 45% (atenção) e semanal 72% (crítico), na borda de cima e na borda
lateral.

### A1 — As duas janelas empilhadas, com rótulo (recomendada)

```
 (◎)  7d 72%          borda lateral:   (◎)
      5h 45%                          7d 72%
      Crítico                         5h 45%
                                      Crítico
```

- Uma linha por anel, **na ordem dos anéis** (de fora para dentro, a mesma de `HudAccount.rings`).
  Quem aprende que a semanal é o anel de fora lê a mesma ordem no texto.
- Rótulo curto em `onSurfaceVariant`, percentual em `onSurface`. A cor do risco continua só no arco
  e na palavra.
- Conta com uma cota só (DeepSeek, MiniMax, Kilo) continua como hoje: número sem rótulo.
- Resolve D1, D2, D3 e D5. O número nunca muda de significado entre coletas.
- **Custo:** o notch engrossa. Na borda de cima vai de 36dp para 42dp (duas linhas de 14dp mais a
  palavra); com três anéis (OpenCode Go, Antigravity) vai para 56dp. Na borda lateral a coluna vai de
  36dp para ~42dp (`7d 72%` tem seis caracteres).

### A2 — Só a cota em foco, com o rótulo da janela

```
 (◎)  72% 7d
      Crítico
```

- Mesmo critério de foco de hoje; acrescenta o `shortLabel` ao lado do número.
- Resolve D3 e D6 (a bandeja ganha o rótulo) com a menor mudança de geometria (+3 caracteres de
  largura, espessura igual).
- **Não resolve D2:** o número continua trocando de janela sozinho. Agora ele diz a troca, mas quem
  olha de relance ainda vê "72%" virar "45%" sem ter mudado nada no consumo.

### A3 — Janela fixa escolhida pelo usuário

Preferência nova "Percentual do notch", com quatro opções: **Mais crítica** (o comportamento de hoje),
**Sessão 5h**, **Semanal** e **Todas** (= A1). O controle fica em Configurações → Geral e no balão da
engrenagem.

- Dá controle a quem já se acostumou com o comportamento atual.
- **Custo:** mais uma preferência em `PreferencesSettings`, um segmentado nas Configurações e um no
  balão, e uma regra para fontes que não têm a janela escolhida (Cursor é só mensal, DeepSeek é só
  saldo). Sem a janela, a conta cai para "Mais crítica", e o texto precisa dizer isso. Na prática a
  pergunta volta para o usuário, que é justamente quem reclamou.

### A4 — Par compacto na ordem dos anéis, sem rótulo

```
 (◎)  72 · 45%
      Crítico
```

- Os dois números lado a lado, na ordem dos anéis, cada um no tom da própria cota.
- É a opção mais estreita que mostra as duas janelas. A largura é de 9 caracteres, quase a de hoje.
- **Custo:** depende de o usuário saber a ordem dos anéis, então só troca uma dúvida por outra (D3
  continua). O tom por número informaria o risco só pela cor, o que o sistema visual proíbe
  ("cor nunca informa sozinha").

### Recomendação

**A1**, com duas concessões.

1. **Na faixa compacta, e só nela,** a célula mostra a cota em foco com o rótulo (a A2). A faixa
   compacta existe porque a completa já não cabia na borda, e empilhar linhas ali desfaria o motivo.
2. **O tooltip da bandeja segue a A2:** "Anthropic — Padrão 7d 72%". Com duas janelas por conta, o
   tooltip passaria dos 127 caracteres do `szTip` com três contas.

A palavra do estado continua sendo a do **pior** risco da conta (D4). Com as duas janelas visíveis,
ela resume a conta, e não mais o número ao lado.

## Decisão do usuário

2026-09-25: **A1**, com as duas concessões acima (faixa compacta e bandeja com o foco rotulado).

## Plano de execução (A1)

Uma atividade, um commit. Branch `feat/286-hud-both-percentages`.

### P1 — Modelo: as linhas do notch (`commonMain`)

`src/commonMain/kotlin/com/usagemonitor/presentation/ui/HudModel.kt`

- Nova classe `HudStripLine(label: String?, percentText: String)` e a propriedade
  `HudAccount.stripLines: List<HudStripLine>`, com uma **dona única** da regra:
  - `rings.size <= 1` → uma linha, com `label = null` e o percentual do foco (o visual de hoje);
  - senão, uma linha por anel, **na ordem de `rings`**, com `label = quota.shortLabel`.
- Nova propriedade `HudAccount.focusLine: HudStripLine`, com o foco e o rótulo (com `label = null`
  quando há uma cota só), para a faixa compacta e para a bandeja.
- `hudTraySummary` passa a usar `focusLine` ("… 7d 72%").
- `focusIndex`, `attentionRingIndex`, `statusLabel` e `hudRingDescription` **não mudam**.

Testes em `HudModelTest`:
- 5h + 7d → `[7d 72%, 5h 45%]`, na ordem dos anéis e não na da API;
- cota única e saldo → uma linha sem rótulo;
- três janelas (OpenCode Go) → três linhas, de mensal a rolling;
- quarta cota (além de `MAX_HUD_RINGS`) fora das linhas;
- bandeja com o rótulo, e ainda cortada em 127 caracteres.

### P2 — Geometria (`HudNotchGeometry.kt`)

- Constante `HUD_STRIP_LINE = 14.dp` (linha `labelSmall`). A linha única sem rótulo continua com
  `HUD_PERCENT_LINE` (16dp), para as contas de uma cota não mudarem de tamanho.
- `horizontalCollapsed`: a largura do item passa a ser `ring + gap + max(largura de cada linha,
  palavra)`, e a espessura é `max(ring, soma das linhas + palavra)`, com o máximo tomado entre as
  contas.
- `verticalCollapsed`: a altura do item é `ring + soma das linhas + palavra`, e a largura da coluna é
  `max(ring, linha mais larga, palavra)`.
- Compacto: `focusLine` no lugar de `focus.percentText`.
- A largura da linha é `wordWidth("$label $percent")`, com a mesma folga de 1dp por texto
  (`charWidth`) que evitou o "04:5" (#185).
- `hudBalloonHeight` não muda. O balão já mostra as duas cotas.

Testes em `HudNotchGeometryTest`:
- espessura de cima 36 → 42dp com duas janelas, 56dp com três, e 36dp com uma;
- largura lateral pela linha mais larga;
- compacto medido sobre `focusLine`;
- `hudRestWindowBounds` e `hudOpenWindowBounds` continuam prendendo o centro com as alças.

### P3 — Composição (`HudNotch.kt`)

- Em `HudRingItem`, `percent()` vira `lines()`: uma `Column` com uma `Row` por linha. O rótulo é
  `labelSmall` em `onSurfaceVariant` e o percentual é `labelSmall` em `onSurface`; a linha única sem
  rótulo continua `labelMedium`. Número é `label*` (mono), pela regra de alinhamento de coluna.
- Os dois arranjos (em linha na borda de cima, em coluna na lateral) mudam, e o compacto usa
  `focusLine`.
- Nenhuma animação nova. A troca de valor já acontece por coleta, sem transição.

Testes em `HudNotchTest`:
- "7d 72%" e "5h 45%" exibidos nas quatro bordas;
- o nó do notch com o tamanho de `hudNotchSizes`, parado e aberto (a costura que a barra antiga
  quebrou);
- o balão de cada conta ainda cabendo na janela aberta.

`HudNotchTextFitTest`: as linhas novas medidas contra a geometria nas escalas de 100% a 200%. É o
único teste que pega o arredondamento em densidade fracionária.

### P4 — Documentação e capturas (mesmo commit da tela, pela regra de precedência)

- `CLAUDE.md`, seção **Barra HUD — notch**: o parágrafo "Um anel por conta, um arco por cota" passa a
  dizer que o notch imprime **uma linha por anel com o rótulo**, que o foco ficou para o pulso, a
  célula compacta e a bandeja, e por que (#286: o número trocava de janela sozinho).
- `docs/design-system/components/shell/AppHudBar.prompt.md`, `AppHudBar.jsx` e
  `ui_kits/desktop-app/Hud.jsx`: as linhas no lugar do percentual único.
- `docs/planos/prototipo-visual-opencode.html`: o mockup do notch com as duas linhas, nas bordas de
  cima e lateral.
- `HelpCatalog.kt`, linhas 276 (PT) e 533 (EN): "o percentual e a palavra do estado" vira "o
  percentual de cada janela e a palavra do estado".
- `ScreenshotFixtures.kt` já tem 5h + 7d. Regenerar `gradlew.bat generateScreenshots` (`img/hud.png`,
  `img/hud-rest.png`, `img/hud.gif`) e `gradlew.bat generateHelpMedia` (a demo de modos de janela).
- Tabela de pontos de situação deste arquivo, no mesmo commit.

### P5 — Verificação

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HudModelTest" --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.ui.HudNotch*" --tests "com.usagemonitor.ui.HelpContentTest"
gradlew.bat allTests
gradlew.bat run
```

No `gradlew.bat run`, conferir:
- o notch nas bordas de cima e lateral com Anthropic (5h + 7d) e uma fonte de cota única;
- com sete contas, a faixa compacta mostrando o foco com rótulo;
- o tooltip da bandeja com "7d";
- a escala da interface em 115% e em 150%, sem texto cortado;
- a demo da Ajuda (`HelpContentTest` já quebrou uma vez por texto que empurrou "Como ativar" para
  baixo da dobra).

## Riscos

- **Espessura do notch.** O notch passa de 36dp para 42–56dp, na borda de cima ou na lateral, e cobre
  mais da tela sob ele. Se incomodar, a saída é a A2 na faixa completa também, e não uma fonte menor
  que 10sp.
- **Contas com três janelas** (OpenCode Go) ficam com quatro linhas de texto. Se a faixa passar de
  `HUD_MAX_ALONG_FRACTION`, ela já cai para a compacta sozinha, e essa regra não muda.
- **Palavra × número (D4).** Quem vê "5h 45%" em cima de "Crítico" pode achar que o crítico é a 5h.
  A ordem das linhas (a de fora primeiro) e o balão respondem, e essa é a leitura a conferir no app.

## Pontos de situação

| Data | Atividade | Modelo | Comando | Resultado |
|---|---|---|---|---|
| 2026-09-25 | P0 | Claude Opus 5.5 | `gh issue view 286`; captura da issue; leitura de `HudModel.kt`, `HudNotch.kt`, `HudNotchGeometry.kt`, `AppHudBar.prompt.md`, `HelpCatalog.kt` | Diagnóstico D1–D6 e quatro alternativas acima. Nenhum código alterado. Usuário escolheu A1. |
| 2026-09-25 | P1–P4 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…HudModelTest" --tests "…HudNotchGeometryTest" --tests "…HudNotch*"` | Verde na primeira passada: 4 classes, **94 testes, 0 falhas**. Cobre linhas na ordem dos anéis, linhas estáveis quando o foco troca, cota única sem rótulo, três janelas, bandeja com "7d 72%", espessura 36/42/56dp, coluna lateral, célula compacta, "7d 9%"/"5h 28%" nas quatro bordas e as linhas medidas de 100% a 200%. |
| 2026-09-25 | P4 | Claude Opus 5.5 | `gradlew.bat generateScreenshots generateHelpMedia` | `img/hud.png`, `img/hud-rest.png`, `img/hud.gif` e `window-modes.gif` regerados; conferido na captura: "7d 41%" sobre "5h 68%" e a palavra embaixo. `dashboard.gif`, `history.gif`, `team.gif` e `updates.gif` saíram diferentes sem mostrar a HUD e foram descartados. |
| 2026-09-25 | P5 | Claude Opus 5.5 | `gradlew.bat allTests` | 216 classes, **2196 testes, 0 falhas** (7m38s). Pendente: olhar o notch lateral e a escala 150% no `gradlew.bat run`. |
