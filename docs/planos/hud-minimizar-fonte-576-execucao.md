# Minimizar uma fonte na barra HUD sem desabilitá-la — issue #576

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-12, branch `feat/issue-576-hud-minimize`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação por agentes `frontend-dev` (TDD).

## Objetivo

O usuário quer encolher uma conta na HUD sem desabilitá-la: coleta, alertas, histórico e cadência continuam; só o
espaço no notch cai. Direção escolhida na rodada AP (`prototipos/issue-576/`): **AP1 · Anã branca** — botão
"Minimizar" no balão da conta; o anel contrai de 64 para 28 dp e a linha de foco (`focusLine`, ex. `5h 61%`) vai para
a direita do anel; "Restaurar" no mesmo balão; 520 ms em cada sentido.

## Fatos confirmados (2026-11-12, `0e77f163`)

- Identidade de conta: `UsageTargetKey.storageKey` (`domain/entity/UsageTargetKey.kt:32`), ex. `ANTHROPIC:<perfil>`,
  `CODEX`, `DEEPSEEK`; cobre todas as fontes (o `AnthropicProfileRegistry` só cobre perfis Anthropic).
- Preferências da HUD: `HudWindowPreferences.kt` (`PreferencesSettings`; `hudEdge`, `hudAutoRetract`…), sem chave por
  conta.
- Geometria: `hudNotchSizes` (`HudNotchGeometry.kt:162`), altura por conta na lateral em `verticalCollapsed`
  (`:419-453`); `HUD_RING_SIZE = AppGargantuaTokens.size` (64 dp, `:52`). Arquivo com 704 linhas: código novo de
  geometria vai em arquivo próprio.
- Célula: `HudRingItem` (`presentation/ui/HudRingStrip.kt:163`), `besideFrame` já põe texto ao lado do anel (`:335`).
- Balão: `HudAccountActions` (`HudWindow.kt:454`) desenha `cardActionsFor(...)` (`CardActions.kt:26-37`).
- Saída K1: janela só muda no fim, corpo desenhado (`hudNotchSizesWithDeparture`, `HudNotchGeometry.kt:218`;
  `HudPresence.kt:72-79`).

## Decisões

- **Minimizar não é desabilitar:** nada em `enabledApis`, `DashboardTargetFetcher` ou alertas muda.
- **Persistência:** conjunto de `storageKey` em `HudWindowPreferences` (chave `hudMinimizedAccounts`); chave de conta
  que deixou de existir é ignorada na leitura, não apagada.
- **Botão fora do `CardAction`:** a regra "nenhum valor novo em enum existente" proíbe `CardAction.MINIMIZE`, e o card
  da grade não minimiza. O balão da HUD ganha um controle próprio depois das ações de `cardActionsFor`.
- **Geometria:** conta minimizada mede `max(28 dp, linha de foco)` no eixo da faixa; o texto fica ao lado do anel nas
  duas orientações. Token `AppGargantuaTokens.minimizedSize = 28.dp` e `minimizeMillis = 520`, espelhados em
  `spacing.css`/`motion.css`.
- **Transição:** mesma regra do K1 — janela muda só quando a área encolhe (no fim) ou antes de crescer; o corpo é
  desenhado; sem overshoot; "reduzir animações" corta seco.

## Atividades

| Id | O quê |
|---|---|
| A01 | Este plano. |
| A02 | Conjunto de contas minimizadas: funções puras (alternar, ler, gravar) + chave em `HudWindowPreferences`. |
| A03 | `HudAccount.minimized` e geometria (`hudNotchSizes` nas duas orientações) com teste de tamanho. |
| A04 | Célula minimizada estática (anel 28 dp + linha de foco ao lado, sem pílula), medida igual à geometria. |
| A05 | Funções puras da transição: `minimizeMillis = 520` (+ `motion.css`), quadro (anel 64→28 dp, fade cruzado), progresso com corte seco, regra do tamanho da janela. |
| A05b | Transição no notch: célula e corpo desenhados pelo quadro, janela pela regra do A05. |
| A06 | Botão Minimizar/Restaurar (`HudMinimizeButton`, primitiva `CardIconActionButton`), rótulo por `localized`. |
| A07 | Ligação em `HudWindow.kt`: estado lido da preferência, botão no balão depois de `cardActionsFor`, alternar e gravar. |
| A08 | Docs: `docs/hud-notch.md`, design system (tokens e contrato), `prototipo-visual-opencode.html`, histórico AP na skill de opções visuais. |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano criado. | Sem harness de teste (documento); validado por leitura dos pontos citados no código em `0e77f163`. |
| A02 | `HudMinimizedAccounts.kt` (alternar, gravar na ordem de publicação, ler descartando entrada inválida) + `hudMinimizedAccounts` em `HudWindowPreferences`. | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HudMinimizedAccountsTest" --tests "com.usagemonitor.HudWindowPreferencesTest"` → 9 falhas por asserção, ex. `HudMinimizedAccountsTest.kt:20 expected:<[UsageTargetKey(source=CODEX, profileId=null)]> but was:<[]>`. Verde: mesmo comando → `BUILD SUCCESSFUL`, 25 testes, 0 falhas. |
| A03 | `HudAccount.minimized`, `buildHudAccounts(minimizedTargets)`, `AppGargantuaTokens.minimizedSize = 28.dp`, ramos minimizados em `hudNotchSizes` (helpers em `HudNotchMinimized.kt`); minimizada não soma pílula de estado. | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.presentation.HudModelTest"` → 3 `AssertionError` (`HudNotchGeometryTest.kt:599`, `:614`, `HudModelTest.kt:542`). Verde: mesmo comando → `BUILD SUCCESSFUL`, 74 testes, 0 falhas. |
| A04 | `HudRingItem` minimizada: anel em `minimizedSize` (traço, vão e marca na mesma razão), linha de foco ao lado nas duas orientações, sem pílula; largura mínima = reserva da janela (`minimizedRingWithFocusWidth`, a estimativa tem 1 dp de folga). | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest"` → `HudNotchTest.kt:236 Did not expect any node but found '1' node … contains 'Crítico'`; medição: `HudNotchTest.kt:257 expected:<76.0.dp> but was:<75.0.dp>`. Verde: mesmo comando → `BUILD SUCCESSFUL`, 74 testes, 0 falhas. |
| A06 | `hudMinimizeActionLabel` (Minimizar/Restaurar, PT/EN) e `HudMinimizeButton` (ícone − / +, devolve a chave da conta). | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.HudMinimizeActionLabelTest" --tests "com.usagemonitor.ui.HudMinimizeButtonTest"` → `HudMinimizeActionLabelTest.kt:11 expected:<[Minimizar]> but was:<[]>`, `HudMinimizeButtonTest.kt:21 Expected exactly '1' node … ContentDescription = 'Minimizar'`. Verde: mesmo comando → 5 testes, 0 falhas; `desktopTest --tests "com.usagemonitor.architecture.*"` → 17 testes, 0 falhas. |
| A07 | `HudWindowHost` lê `readHudMinimizedAccounts` uma vez, passa `minimizedTargets` a `buildHudAccounts`; alternar = `toggleHudMinimizedAccount` + `persistHudMinimizedAccounts`; `HudMinimizeButton` no balão depois de `cardActionsFor`. Desvio de TDD registrado: o estado do host entrou antes do vermelho; o vermelho é do botão no balão. | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest"` → `HudNotchTest.kt:531 Expected exactly '1' node … ContentDescription = 'Minimizar'`. Verde: `desktopTest --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.architecture.*"` → 75 + 17 testes, 0 falhas. `HudWindow.kt`: 537 linhas. |
| A05 | `HudMinimizeMotion.kt`: `hudMinimizeMillis`/`hudMinimizeProgress` (animação reduzida = 1 no primeiro quadro), `hudMinimizeFrame` (ease-in-out sem overshoot, alfas somam 1), `hudMinimizeWindowSize` (mantém o maior até o fim); token `--dur-gargantua-minimize: 520ms`. | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.HudMinimizeMotionTest"` → 8/8 por asserção, ex. `HudMinimizeMotionTest.kt:16 expected:<520> but was:<0>`. Verde: mesmo comando → 8 testes, 0 falhas. |
| A05b | `rememberHudMinimizeProgress` (um `Animatable` por conta, corte seco com animação reduzida) içado ao `HudWindowHost`; `HudRingItem` usa o quadro (anel e alfa da linha de foco; `fullTextAlpha` removido); `hudHeldNotchSizes` segura janela e notch no maior tamanho enquanto anima (`HudWindowHold.kt`). | Vermelho: `HudNotchTest.kt:296 anel no meio da transição: 28.0.dp`, `HudNotchTest.kt:310 expected:<181.0.dp> but was:<116.0.dp>`, `HudMinimizeWindowHoldTest.kt:15 a janela encolheu antes do fim da transição`. Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.HudMinimizeWindowHoldTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.presentation.ui.HudMinimizeMotionTest" --tests "com.usagemonitor.architecture.*"` → 2 + 77 + 8 + 17, 0 falhas. |
| A08 | Docs: seção "Minimizar uma conta (#576)" em `docs/hud-notch.md`; `--gargantua-minimized-size: 28px` em `spacing.css`; nota em `AppGargantuaRing.prompt.md`; mockup lateral com a célula minimizada e o botão Minimizar em `prototipo-visual-opencode.html`; linha AP na skill de opções visuais. | Headless Chrome (`--headless=new`) em `prototipo-visual-opencode.html`: exit 0, 0 linhas CONSOLE do documento (só erros de registro do Windows). Recorte da região do notch: botão Minimizar visível na barra do balão; a célula minimizada fica abaixo do recorte, como já ocorre com o item `$2.27`. Sem harness de teste (documentação). |

## Problemas em aberto e riscos

- Host de janela (#342): mudança de tamanho da HUD só testada no Windows; abrir no Linux (X11) antes do merge ou
  declarar o risco no PR.
- Lateral: anel de 28 dp + vão + linha de foco (ex. `5h 61%`) pode passar de 64 dp e alargar a faixa em poucos dp;
  aceito para manter a linha ao lado do anel, como na AP1.
- Corpo do notch: segura o tamanho maior durante os 520 ms e assume o novo no fim (salto), em vez de recolher desenhado
  como no protótipo AP1; o anel anima. Refinar com o corpo interpolado se o usuário pedir.
- Cobertura do diff: 92,6% (113/122; `gradlew.bat allTests koverXmlReport -Pcoverage` + `diff-coverage.mjs`, 4272
  testes, 0 falhas). Sem teste: `HudWindowHold.kt:18-27` (composable do `HudWindowHost`, que nenhum teste de tela monta —
  #342; a regra pura `hudHeldNotchSizes` é testada) e `HudRingStrip.kt:92`.
- HUD no navegador (#388) e modo terminal continuam mostrando a conta inteira (fora do escopo).
