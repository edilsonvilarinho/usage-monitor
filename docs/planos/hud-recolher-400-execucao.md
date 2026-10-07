# Recolher a barra HUD quando parada (#400) — execução

## Contexto

[Issue #400](https://github.com/edilsonvilarinho/usage-monitor/issues/400): a HUD fica sempre no
tamanho cheio colada na borda (print da issue: borda esquerda, Claude 7d 26% · 5h 3% e OpenAI 7d 4% ·
5h 0%, as duas em Atenção). O pedido é um modo **opcional** em que, parada, ela fica recolhida mas
visível, expande com o ponteiro em cima e recolhe ao sair, ligado por um botão na própria barra, como a
mão de mover. A animação saiu de uma rodada de 15 opções em HTML.

Decisões do usuário:

| Pergunta | Resposta |
|---|---|
| Animação | uma das 15, escolhida por ele, vira a do app (não é seletor nas Configurações) |
| O que aparece recolhida | faixa fina + um ponto por conta com o pior risco (cor **e** forma) |
| Crítico com a barra recolhida | só a faixa avisa; a barra não abre sozinha |
| Onde fica o botão | só na barra, ao lado da mão |
| Animação escolhida | **Z2 · Íris do eclipse** |

## Restrições herdadas (`docs/hud-notch.md`)

- A janela não muda de tamanho nem de origem (#294): recolher e expandir são recorte dentro dela.
- O recorte da área de clique é só do Windows (#340).
- "Reduzir animações" = corte seco; cor nunca informa sozinha; nenhum valor novo em enum existente.
- `HudWindow.kt`, `HudNotch.kt` e `HudNotchGeometry.kt` perto do teto de 800 linhas: código novo em
  arquivos próprios.

## Atividades

| # | Atividade | Arquivos |
|---|---|---|
| A0 | Rodada Z: 15 protótipos HTML (borda esquerda do print e borda de cima), escolha Z2 | `build/gargantua-preview/hud-retract-options.html` (ignorado pelo git) |
| A1 | Quadro puro da íris, inversos para inverter no meio, durações | `GargantuaEclipseIris.kt`, `AppGargantuaTokens`, `tokens/motion.css` |
| A2 | Faixa recolhida, área de clique recolhida, intenção de 200ms, reserva de duas alças por ponta | `HudRetract.kt`, `HudNotchGeometry.kt`, `HudNotchShape.kt` |
| A3 | Preferência `hudAutoRetract`, nasce desligada | `HudWindowPreferences.kt` |
| A4 | Faixa com pontos de risco, recorte de camada da íris, alfinete ao lado da mão, host | `HudRetractedStrip.kt`, `HudHandles.kt`, `HudNotch.kt`, `HudWindow.kt` |
| A5 | Testes de quadro, geometria, preferência e tela | `GargantuaEclipseIrisTest`, `HudNotchGeometryTest`, `HudWindowPreferencesTest`, `HudNotchTest` |
| A6 | Documentação: notch, protótipo, contrato e kit do `AppHudBar`, tabela da skill, `CLAUDE.md` | `docs/hud-notch.md`, `prototipo-visual-opencode.html`, `AppHudBar.*`, skill `usage-monitor-visual-options` |
| A7 | Medição manual no Windows 11 e risco do Linux declarado | tabela "Fora do alcance dos testes" em `docs/hud-notch.md` |

## Ponto de situação

| Atividade | Modelo | Comando | Resultado |
|---|---|---|---|
| A0 | Claude Opus 5.5 | Chrome headless `--screenshot` com `?mid=0`, `0.35`, `0.4`, `0.5`, `0.75`, `1` sobre o HTML | 15 opções + referência, quadros conferidos; Z1 (corpo não deslizava), Z4 e Z8 (luz fora do corpo) corrigidos antes de mostrar |
| A1–A5 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.HudWindowPreferencesTest" --tests "com.usagemonitor.presentation.ui.components.GargantuaEclipseIrisTest"` | verde: `HudNotchTest` 61/61, `GargantuaEclipseIrisTest` 5/5, geometria e preferência sem falha |
| A5 (teto de função) | Claude Opus 5.5 | `gradlew.bat allTests` | 1ª passada: 2616 testes, 1 falha — `ArchitectureRulesTest`: `HudNotch` 339 e `HudWindowHost` 307 linhas (teto 300). Extraídos `HudRetractParts`, `rememberHudHover`/`HudHoverSources` e `HudAutoRetractState`: `HudNotch` 290, `HudWindowHost` 300 |
| A1–A5 | Claude Opus 5.5 | `gradlew.bat allTests` | 2ª passada: 2616 testes, 0 falhas |
| A5 (GIF) | Claude Opus 5.5 | `gradlew.bat generateGargantuaPreview` → `build/gargantua-preview/hud-gargantua-retract.gif` (130 quadros de 20ms, borda esquerda) | conferido quadro a quadro: faixa com ◆ e ●, disco com fio dourado crescendo do meio, notch inteiro com mão e engrenagem; o alfinete fica fora da cena de 340dp |
| A6 | Claude Opus 5.5 | revisão dos documentos | escrito no mesmo conjunto de mudanças |
| A7 | — | não executado | **pendente**: faixa de 10dp recebendo o ponteiro com opacidade 55% e 100% no Windows; Linux sem recorte |

## Riscos

1. **A faixa fina pode não receber o ponteiro no Windows.** A janela transparente só recebe evento onde
   o alfa do fundo é ≥ 1/255, e a opacidade da HUD multiplica esse alfa (piso de 55%,
   `hudWindowOpacityPercent`). Para a faixa de 10dp isso não foi medido.
2. **Linux**: sem recorte (#340), a área vazia da janela também recebe o ponteiro e pode abrir a barra
   recolhida sem querer. Declarado no PR.
3. **Borda de cima**: o ponteiro passa muito ali; é o motivo dos 200ms de intenção. Se não bastar, o
   número sobe — é constante única (`HUD_RETRACT_INTENT_MILLIS`).
