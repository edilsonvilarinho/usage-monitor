# HUD no Linux: balão cortado pelo recorte de clique (#340) — execução

## Contexto

[Issue #340](https://github.com/edilsonvilarinho/usage-monitor/issues/340): no elementary OS 6.1
(Ubuntu 20.04, X11, Gala) o balão da HUD aparecia só como a tira de 16dp da margem de sombra, com a
cauda, e as alças saíam cortadas — nas bordas direita e de cima. Regressão da #294 (`d3ff05b`,
v41.0.0): a janela passou a ter sempre o tamanho da aberta e, parada, recortada por `Window.shape`;
ao abrir, `window.shape = null` não tira o recorte no X11, e o recorte corta também a pintura. Até
25/09 (janela que cresce ao abrir, sem recorte) o balão abria inteiro na mesma máquina.

## Decisões do usuário

1. Recorte **só no Windows**, a única plataforma medida. Linux e macOS ficam sem recorte; o custo
   aceito é a área vazia do balão poder engolir clique com o notch parado (não medido).
2. Issue com o diagnóstico e o plano antes da correção; commits atômicos e rastreados.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | `hudUsesHitRegion` (só `WINDOWS`); host não compõe `ApplyHudHitRegion` fora do Windows; teste por plataforma; `docs/hud-notch.md` e `CLAUDE.md` | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.HudNotchGeometryTest"`; `gradlew.bat allTests` | verde isolado; a suíte completa acusou antes `HudWindowHost` com 301 linhas (limite 300) — corrigido encurtando o trecho novo. Na suíte completa resta 1 falha intermitente sem relação com a HUD, `DashboardViewModelRefreshPersistenceTest` (timeout de tempo real sob carga), que passa isolado |
| A2 | `ArchitectureRulesTest`: `window.shape`/`setShape` só em `HudWindow.kt` e `DesktopWindowFrame.kt` (padrão ignora comentário, string e o `shape =` do Compose, com teste do padrão); regra transversal no `CLAUDE.md`. Achado: `DesktopWindowFrame` também faz `window.shape = null` ao maximizar — mesmo risco no X11, sem relato; registrado na #340, sem mudança neste release | `gradlew.bat desktopTest --tests "com.usagemonitor.architecture.*"`; `gradlew.bat allTests` | arquitetura verde; suíte completa 2348 testes, 1 falha: `DashboardViewModelRefreshPersistenceTest` (timeout de tempo real), a mesma nas três rodadas completas e verde isolada |
| A3 | #331 incluída no hotfix a pedido do usuário. Diagnóstico medido (log temporário no teste, suíte completa): prazo inicial e prazo da coleta **idênticos** (`equal=true`, `callback=null`); isolado, 27 ms de diferença. Causa: `publishNextPoll` gravava só se o prazo diferisse do da tela, e o inicial nunca é gravado. Correção: comparar com o último prazo gravado (`lastPersistedNextRefreshAt`); teste novo com relógio congelado; `docs/presentation.md` | teste novo sem a correção: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.DashboardViewModelRefreshPersistenceTest"`; com a correção: `gradlew.bat allTests` | sem a correção: 4 testes, 1 falha (o novo); com: 2349 testes, 0 falhas, 0 ignorados. Antes, a mesma falha em 4 de 4 rodadas completas, inclusive na `main` limpa |
