# Timer da próxima coleta na HUD (#293) — execução

## Contexto

[Issue #293](https://github.com/edilsonvilarinho/usage-monitor/issues/293): a contagem até a próxima
coleta (#185) era o último item da faixa do notch, com ícone ↻ de 12dp e `05:42`. No notch vertical
(bordas esquerda e direita) ela quebrava em duas linhas e ocupava ~38dp de coluna (ícone 12, linha
14 e vão 12) para mostrar uma informação que é do app inteiro. A issue pede que o timer continue
visível e ocupe menos espaço.

## Decisões do usuário

1. Entre quatro formas propostas (filete de progresso, minutos compactos `6m`/`42s`, as duas juntas,
   ou `05:42` sem ícone), a primeira escolha foi **o filete de progresso na borda interna**.
2. Olhando no app, o filete foi recusado: *"ficou muito difícil de entender"*. Sem número ao lado,
   não dizia o que media, e rente à borda se lia como o contorno do notch.
3. A contagem voltou a ser texto, `↻ 05:42` **numa linha só** também na lateral, e o usuário pediu
   *"uma animação que envolva tempo"*: o ↻ virou **um relógio que esvazia**.

## Desenho entregue

- O ícone da contagem é um relógio de 12dp (`HudCountdownClock`): aro e setor. O setor começa cheio
  logo depois da coleta, esvazia no sentido horário a partir das 12h e volta cheio na coleta.
- Ícone e `mm:ss` numa linha só nas quatro bordas. Na lateral isso economiza uma linha (antes, ícone
  em cima e tempo embaixo), e há teste afirmando a diferença de altura.
- A volta inteira é `DashboardViewModel.pollInterval`. A fração é `hudRefreshFraction`, função pura
  limitada a [0, 1]. Sem intervalo, o ícone continua sendo o ↻.
- O passo de cada segundo desliza em 900 ms: transições finitas, uma por tique, e nunca animação
  infinita. Com "Reduzir animações", o passo vira salto.
- O balão da engrenagem usa o mesmo relógio.
- "Sem projeção", quebrado em duas linhas na lateral, passa a ser centralizado.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Filete no notch, contagem fora da faixa, `pollInterval` exposto, testes de geometria, fração, componente e bitmap; docs (CLAUDE.md, `AppHudBar`, kit `Hud.jsx`, protótipo, ajuda PT/EN) | `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.ui.HudNotch*"` | 70 testes; primeira passada com 3 falhas (dois testes ainda procuravam `02:00` como texto; o teste de bitmap comparava pixels de posições diferentes, que o brilho do topo muda). Corrigidos, a segunda passada ficou verde |
| A2 | Suíte completa | `gradlew.bat allTests` | 2208 testes, 0 falhas, 0 ignorados |
| A2b | Filete invisível no app: estava lá (medido no print, preenchimento até ~55%), mas com 2dp colado na borda de 1dp se lia como o contorno. Passou a 3dp, afastado 3dp da borda (`HUD_REFRESH_TRACK_INSET`), com pontas arredondadas. Junto, "Sem projeção" quebrado em duas linhas passou a ser centralizado no notch vertical | `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.ui.HudNotch*"` | verde |
| A3 | Revisão no app: o usuário não entendeu o filete ("ficou muito difícil de entender"). Filete revertido; a contagem volta a ser `↻ 05:42`, agora numa linha só também na lateral | olhar no app | recusado o filete |
| A4 | O ↻ vira um relógio de 12dp cujo setor esvazia até a coleta (a pedido: "uma animação que envolva tempo"), com passo deslizante de 900 ms por tique e salto com "Reduzir animações"; testes de geometria (uma linha, fração), componente e bitmap | `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.ui.HudNotch*" --no-build-cache` | verde |
| A5 | Documentação do relógio (CLAUDE.md, contrato e kit `AppHudBar`, protótipo) e suíte completa | `gradlew.bat allTests --no-build-cache` | 2210 testes, 0 falhas, 0 ignorados |
| A6 | Olhar no app e regenerar capturas/demos da HUD | `gradlew.bat run`, `generateScreenshots`, `generateHelpMedia` | pendente |
