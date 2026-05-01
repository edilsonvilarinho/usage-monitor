# Refresh visual elegante para a app desktop

## Summary
- Evoluir a app inteira para uma direção `premium discreto`, com `animação elegante moderada` e consistência visual entre dashboard, histórico, settings e moldura da janela.
- Manter a arquitetura atual e o comportamento funcional; o foco é elevar acabamento visual, suavizar bordas, unificar superfícies e tornar motion/hovers/transições mais refinados.
- Preservar a leitura rápida dos dados e evitar efeitos chamativos demais ou animações contínuas pesadas.

## Key Changes
- Consolidar um mini design system em `src/commonMain/kotlin/com/usagemonitor/presentation/ui/theme/AppTheme.kt`:
  - Definir tokens de `shape`, `typography`, `elevation`, `surface tint` e `motion`.
  - Atualizar dark e light theme com mesma linguagem visual, priorizando dark-first mas mantendo paridade.
  - Padronizar raios mais suaves, contraste mais limpo e superfícies com profundidade sutil.
- Refinar os principais containers e chrome da app em `DashboardScreen.kt` e `DesktopWindowFrame.kt`:
  - Dar mais respiro ao layout, melhorar hierarquia entre conteúdo, banners, cards e footer.
  - Deixar title bar, dialogs e áreas de scroll visualmente integrados ao novo tema.
  - Aplicar cantos mais suaves, divisórias menos duras e fundos com variação discreta de superfície.
- Reestilizar os componentes de maior impacto visual:
  - Cards de API com bordas mais elegantes, layering melhor, estados de hover/drag/refresh coesos e animações com timing uniforme.
  - Footer, chips, botões, badges e banners seguindo a mesma escala de raio, padding e feedback visual.
  - Histórico e gráficos com leitura mais premium: cartões mais limpos, filtros/chips mais claros e line/arc charts com cores e transições consistentes.
- Unificar motion:
  - Centralizar durações/easings em tokens.
  - Usar entrance, expand/collapse, hover, refresh shimmer e dialog open/close com a mesma linguagem.
  - Evitar animação decorativa sem função; toda motion deve reforçar foco, atualização ou mudança de estado.

## Public APIs / Interfaces
- Nenhuma mudança de API de domínio ou dados.
- Mudanças internas esperadas na camada de UI:
  - `AppTheme` deve passar a fornecer tema completo, não só `colorScheme`.
  - Componentes visuais podem ganhar helpers/tokens internos de estilo e motion.
  - Sem alterar contratos funcionais de `DashboardViewModel`, `HistoryViewModel` ou repositories.

## Test Plan
- Executar `gradlew.bat desktopTest --tests "com.usagemonitor.ui.ComponentTest"` durante o refactor e ao final; hoje essa suíte já passa.
- Ajustar ou acrescentar testes de componentes apenas onde o redesign mudar estrutura acessível:
  - presença de labels/ações em cards, footer, settings e histórico;
  - estados minimizado/expandido e ações de histórico/refresh;
  - comportamento responsivo básico em larguras estreitas.
- Validar manualmente com `gradlew.bat run`:
  - dashboard com 1 e 2 colunas;
  - hover e clique na title bar e botões;
  - abertura/fechamento de settings e histórico;
  - dark e light theme;
  - estados loading, success parcial, erro e atualização em andamento.

## Assumptions
- Escopo desta rodada inclui `dashboard + histórico + settings + title bar`.
- A direção visual escolhida é `premium discreto`, sem rebranding forte nem mudança radical de cores da marca.
- As animações devem ser perceptíveis, mas moderadas, priorizando fluidez diária e baixo risco de fadiga visual.
- Não haverá mudança funcional de fluxo, persistência ou polling; apenas refinamento visual e de interação.
