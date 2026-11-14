# Barra HUD vertical: auto-minimizar contas excedentes — issue #596

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-14,
branch `feat/issue-596-hud-auto-minimize`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido: com várias APIs habilitadas a barra HUD vertical fica "muito fina" e o usuário não vê as estrelas nem o fundo.
Rodada de 3 protótipos (`prototipos/issue-596/`: P1 auto-minimizar, P2 piso de largura, P3 duas colunas) → **P1 ·
auto-minimizar** (`prototipos/issue-596/p1-auto-minimizar/index.html`): quando a coluna completa passa do teto da borda,
a conta de **menor uso** vira linha minimizada (anel 28 dp + valor ao lado, a mesma da #576), uma por vez, até caber; as
demais ficam completas (linhas + pílula). Com 7 fontes numa área útil de 900 dp: hoje 80 × 740 dp (compacto) → 102 × 680 dp.

## Fatos confirmados (2026-11-14, `1270bf2a`)

- Compacto: `compact = accounts.isNotEmpty() && fullAlong > maxAlong` (`HudNotchGeometry.kt:184`), um degrau só.
- Teto: `maxAlong = área útil / escala × hudMaxAlongFraction` (`HudWindowGeometry.kt:35-36`), 0,8 nas laterais (`HudNotchGeometry.kt:264`).
- Largura vertical: `max(64 dp, pílula mais larga, linha mais larga) + 2×8` (`verticalCollapsed`, `HudNotchGeometry.kt:424-465`);
  no compacto some a pílula e só fica a `focusLine` → ~80 dp.
- Minimizada já existe (#576): `HudAccount.minimized` (`HudModel.kt:110`), definida por `buildHudAccounts(minimizedTargets)`
  (`HudModel.kt:396`); conjunto vem da preferência `hudMinimizedAccounts` (`HudWindow.kt:179`, `HudWindowPreferences.kt:169`).
- Uma lista só alimenta geometria e desenho: `accounts` (`HudWindow.kt:187`) → `hudWindowSizes` (`:240`) e `HudNotch` (`:344`).
- Animação de minimizar dispara pela troca do flag de cada conta (`HudMinimizeTransition.kt:35-37`, `LaunchedEffect(account.minimized, …)`).
- Botão "Restaurar/Minimizar" (`HudWindow.kt:506-512`) chama `toggleHudMinimizedAccount` (`HudMinimizedAccounts.kt:10-14`):
  numa conta minimizada só pela regra nova, o toggle **adicionaria** a conta ao conjunto do usuário (ela não está nele).
- Estrelas: 156 fixas (`HudStarfield.kt:94-96`); caixas proibidas sobre textos/pílulas e um poço por anel
  (`HudStarfieldCanvas.kt:126-181`) — barra estreita deixa pouca área livre.

## Decisões

- **Só a borda vertical.** A horizontal (teto 45%) fica como está; fora do escopo da issue.
- **Ordem**: menor uso primeiro; uso = maior `fraction` das cotas da conta, 0 sem cota; empate pela posição na lista
  (a de cima sai primeiro) — a regra do P1 escolhido (`index.html:245`).
- **Uma por vez, até caber**; se nem todas minimizadas couberem, cai no compacto de hoje (sem mudança).
- **Função pura** `hudAutoMinimizedTargets(accounts, edge, fallbackLabel, maxAlong)` no `HudNotchGeometry.kt`, medindo com
  a mesma `verticalCollapsed`; `HudWindow` aplica o resultado (`copy(minimized = true)`) **antes** da geometria e do
  desenho, então a animação da #576 vale sem código novo.
- **Sem botão de restaurar** na conta auto-minimizada: restaurá-la estouraria o teto, e o toggle a gravaria como
  minimizada pelo usuário. Ela volta sozinha quando houver espaço (outra fonte desligada, tela maior).
- Preferência do usuário intocada: a regra nova nunca grava em `hudMinimizedAccounts`.

## Atividades

| ID | O quê |
|---|---|
| A01 | Este plano |
| A02 | `hudAutoMinimizedTargets` em TDD (`HudNotchGeometryTest`) |
| A03 | `HudWindow` aplica o conjunto antes de `hudWindowSizes`/`HudNotch`; conta auto-minimizada sem botão de restaurar (teste de tela) |
| A04 | `docs/hud-notch.md`, protótipo visual e design system registram a regra |

## Ponto de situação

| ID | Execução | Resultado |
|---|---|---|
| A01 | Sem harness (texto): protótipos P1/P2/P3 conferidos por captura; escolha do usuário via pergunta | P1 escolhido |
| A02 | `hudAutoMinimizedTargets` em `HudAutoMinimize.kt` (o `HudNotchGeometry.kt` passaria de 750 linhas); `verticalCollapsed` vira `internal`. 7 casos em `HudAutoMinimizeTest` (Haiku, `frontend-dev`) | Vermelho (stub `emptySet()`): `gradlew.bat desktopTest --tests "com.usagemonitor.HudAutoMinimizeTest"` → 4/7 falham com `AssertionError` em `HudAutoMinimizeTest.kt:42, :56, :68, :80` (os 4 casos que esperam conjunto não vazio; texto literal da mensagem não guardado pelo agente). Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.HudAutoMinimizeTest"` → 7 + 40 + 17, 0 falhas |
| A03 | `HudAccount.autoMinimized`; `applyHudAutoMinimize` e `hudMaxAlong` (extraído de `hudWindowSizes`); `HudWindow` aplica a regra antes de `rememberHudMinimizeProgress`/`hudWindowSizes`/`HudNotch`; botão de restaurar escondido por `hudShowsMinimizeButton` (Haiku, `frontend-dev`) | Vermelho (stubs): `hudMaxAlong` → `esperado 400.0.dp, veio 0.0.dp`; `applyHudAutoMinimize` → `expected:<[(false, false), (false, false), (true, true)]> but was:<[(false, false), (false, false), (false, false)]>`; conta do usuário → `expected:<true> but was:<false>`; `HudMinimizeButtonVisibilityTest` → `Expected value to be false.` Verde: `gradlew.bat desktopTest --tests "com.usagemonitor.HudAutoMinimizeTest" --tests "com.usagemonitor.HudNotchGeometryTest" --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.presentation.*"` → 1 350 casos, 0 falhas |
| A04 | Regra registrada em `docs/hud-notch.md:69-73`, `docs/design-system/components/shell/AppHudBar.prompt.md:64-69` e no protótipo `docs/planos/prototipo-visual-opencode.html:1187-1189` (cena "3e"). Devolvida 1× (arquivo da regra errado; "maior `fraction`" ambíguo) (Haiku, `prototype-dev`) | Sem harness (texto e mockup): `grep -n "#596"` nos três arquivos; script do mockup sem erro de sintaxe (`vm.Script`); captura headless da cena 3e conferida, zero linhas `CONSOLE` |

## Problemas em aberto e riscos

- Conta em sessão ativa (`sessionActive`) pode ser auto-minimizada se tiver menos uso; o P1 não a protege. Reabrir só com pedido.
- #342: mudança em host de janela (`HudWindow.kt`) sem medição no Linux — o PR declara o risco.
