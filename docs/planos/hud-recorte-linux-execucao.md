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
