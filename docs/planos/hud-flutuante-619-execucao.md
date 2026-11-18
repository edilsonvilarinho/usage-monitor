# HUD flutuante em qualquer ponto da tela — issue #619

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-18,
branch `feat/issue-619-floating-hud` (worktree `.claude/worktrees/hud-flutuante`, base `origin/main` `36590e6d`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#619, com capturas): um modo em que a HUD não é obrigada a encostar numa borda — fica onde for largada, em
qualquer ponto da tela — e tem uma forma minimizada que não atrapalha o uso do computador. Restrição do dono: dentro do
design system e do tema buraco negro (Gargantua); a referência da forma minimizada é o anel com disco inclinado.

## Fatos confirmados (2026-11-18, `36590e6d`)

Varredura do agente `explorer` sobre `docs/hud-notch.md` e `Hud*.kt`.

- Bordas `TOP/BOTTOM/LEFT/RIGHT` em `HudNotchGeometry.kt:40`. Ao soltar o arrasto, `HudWindow.kt:291-301` chama
  `hudDragLanding` (`HudWindowDrag.kt:64`), que usa `nearestHudPlacement` (`HudNotchGeometry.kt:716-735`): borda mais
  próxima do centro. Posição livre só existe durante o arrasto. `HudPlacement(edge, offsetFraction)` em `:700`.
- Preferências `hudEdge`/`hudEdgeOffset` (`HudWindowPreferences.kt:73-74`), lidas em `readPersistedHudPlacement` (`:90`)
  e gravadas em `persistHudPlacement` (`:114-120`). `hudAutoRetract` (`:122-132`), padrão `false`.
- Botões (`HudHandles.kt`): `HudMoveHandle` (`:84`), `HudRetractHandle` (`:135`), `HudGearHandle` (`:183`).
- Faixa recolhida: `HudRetractedStrip.kt:80`, espessura 10 dp (`HudNotchGeometry.kt:267`), glifos `:174-182`
  (triângulo crítico, losango atenção, disco em dia, anel vazio sem projeção); abre por hover com intenção de 200 ms
  (`HudRetract.kt:21-31`).
- `AppHudBar.prompt.md`, seção Gestures (`:187-191`), ainda cita o clique direito "to Cards", que o `hud-notch.md` diz
  ter sido removido: divergência a corrigir quando a tela mudar.

## Decisões

- Mudança visual: passa pelo portão de protótipos (`prototipos/issue-619/`, fora do versionamento), 10 direções pedidas
  pelo dono. Nenhum Kotlin antes da escolha.
- O modo encaixado de hoje não muda; o flutuante é adicional.
- Comportamento nativo novo (janela solta, transparência, clique atravessável) nasce restrito à plataforma medida (#340).
- **Escolha do dono (2026-11-18): P05 · órbita radial**, com quatro mudanças (`prototipos/issue-619/briefing-r2.md`):
  o modo encaixado e a faixa recolhida de hoje ficam; o flutuante entra por um novo botão redondo na coluna dos botões
  (alfinete, mão, novo, engrenagem); campo expandido menor (~240 dp) com o fundo do corpo da barra HUD, sem gradiente
  dourado; disco de acreção do buraco girando (atrás de `AppMotionPolicy.continuous`); protótipo R2 com painel de
  parâmetros que exporta JSON, para o dono ajustar e devolver os valores.

## Atividades

| ID | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-flutuante-619-execucao.md` |
| A02 | 10 protótipos com direções distintas; escolha do dono | `prototipos/issue-619/` (fora do versionamento) |
| A03 | Preferências do modo flutuante | `HudWindowPreferences.kt` + `HudWindowPreferencesTest.kt` |
| A04 | Parâmetros e geometria pura do campo orbital | novo `HudOrbitGeometry.kt` + teste |
| A05 | Posição flutuante e plataforma suportada | novo `HudFloatingPlacement.kt` + teste |
| A06 | Botão "Soltar da borda" / "Encaixar na borda" | `HudHandles.kt` (ou arquivo novo) + teste de tela |
| A07 | Campo orbital e buraco minimizado (composables) | novos `HudOrbitField.kt`, `HudFloatingHole.kt` + testes |
| A08 | Host do modo flutuante na janela da HUD | `HudWindow.kt` + arquivos novos de estado/efeitos + testes de decisão |
| A09 | Documentação e design system | `docs/hud-notch.md`, `AppHudBar.prompt.md`, protótipo visual |

### Especificação por atividade

Parâmetros do dono (`prototipos/issue-619/parametros-dono.json`), valores padrão do modo: campo 180 dp, raio da
órbita 40 dp, anel da conta 28 dp, marca 8 dp, buraco minimizado 32 dp, inclinação do disco 29°, espessura do disco 3 %,
uma volta do disco a cada 3,5 s, uma volta da órbita a cada 180 s, fundo 100 % opaco, poeira densidade 100 % e brilho
200 %, borda 0 dp, rótulos `completo`, texto 8 sp, disco no token `hot`. Com rótulos completos o campo cresce até caber
os rótulos (no R2: 244 dp); nada é desenhado fora do campo.

- **A03.** `hudFloating: Boolean` (padrão `false`) e centro flutuante em px de tela (`hudFloatX`, `hudFloatY`; ausente =
  nunca posto). Mesmo padrão de `hudAutoRetract` (`HudWindowPreferences.kt:122-132`); teste de ida e volta como
  `HudWindowPreferencesTest.kt:144-156`.
- **A04.** `HudOrbitParams` com os padrões acima e `hudOrbitLayout(params, accountCount, labels, orbitPhase)` →
  diâmetro do campo, centro de cada conta (ângulo inicial −90°, passo 360°/n, sentido horário, ordem da lista),
  diâmetro do buraco minimizado. Ordem total e determinística.
- **A05.** `hudFloatingBounds(center, windowSize, workArea)` prende a janela dentro da área de trabalho e **nunca**
  encosta numa borda por conta própria; sem centro salvo, nasce no centro do notch atual. `hudFloatingSupported(os)`:
  só Windows (#340); outros sistemas não mostram o botão.
- **A06.** Botão redondo novo na ponta da engrenagem (fato: a ponta de perto já tem alfinete e mão; a de longe só a
  engrenagem, e `HUD_HANDLES_PER_SIDE = 2`, `HudNotchGeometry.kt:260`, já reserva a segunda vaga — a janela não cresce).
  Espelha a ponta de perto: engrenagem junto ao notch, botão novo por fora. Mesmo estilo dos de hoje, ícone de
  buraco com órbita; rótulo por objeto de rótulos (`localized`), "Soltar da borda" / "Release from edge" no encaixado e
  "Encaixar na borda" / "Dock to edge" no flutuante; ausente quando `hudFloatingSupported` é falso.
- **A07.** `HudOrbitField` (expandido: fundo do corpo da HUD, poeira, contas em `AppGargantuaRing` com arcos e marca,
  rótulos, buraco central) e `HudFloatingHole` (minimizado: buraco com disco inclinado girando só com
  `policy.continuous && !policy.reduced`, ponto de atualização quando `HudUpdateIndicator` existe). Animação infinita
  só atrás de `AppMotionPolicy` (trava o `waitForIdle`).
- **A08.** No modo flutuante, a mesma `HudWindowHost` troca o notch pelo campo: janela de tamanho fixo (campo + balão),
  recorte pelo `hudHitRegionMode`, hover de 200 ms abre o campo, arrasto livre persistido (A03/A05), clique numa conta
  abre `HudAccountBalloonContent`, engrenagem abre `HudWindowAppBalloon`, indicador de atualização igual ao de hoje
  (`hudUpdateIndicatorOf`). O botão novo alterna os modos. Nenhuma mudança no modo encaixado.
- **A09.** Seção "Modo flutuante" na `hud-notch.md`; contrato em `AppHudBar.prompt.md` (e correção da seção Gestures,
  que ainda cita o clique direito); tela no `prototipo-visual-opencode.html`.

## Ponto de situação

| ID | Estado | Execução |
|---|---|---|
| A01 | feito | sem harness (documento); conferido por leitura |
| A02 | feito | sem harness (protótipo); P01–P10 conferidos por captura headless do Chrome a 1400 px (escuro/claro); devolvidos uma vez P02–P08; escolhido P05. R2 (`prototipos/issue-619/r2-orbita-radial/`) devolvido uma vez (rótulos fora do campo, cena encaixada, botões) e aprovado: giro conferido por diferença de quadros `?t=0` × `?t=1500`, JSON de parâmetros e restauração por `#p=` conferidos |
| A03 | feito | vermelho: `gradlew.bat desktopTest --tests "*HudWindowPreferencesTest*"` → 3 falhas de asserção em 22 (`HudWindowPreferencesTest.kt:161`, `:181`, `:190`); verde: mesmo comando, 22 testes, 0 falhas |
| A04 | feito | vermelho: `gradlew.bat desktopTest --tests "*HudOrbitGeometryTest*"` com stub → falhas de asserção, entre elas `HudOrbitGeometryTest.kt:76` (a fase gira o anel); verde: mesmo comando, 13 testes, 0 falhas. Devolvida uma vez (nomes em português, declarações públicas) |
| A05 | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingPlacementTest*"` com stub → 11 de 11 falhas de asserção (ex.: `HudFloatingPlacementTest.kt:41` `assertEquals(1520, bounds.xPx)`); verde: mesmo comando, 11 testes, 0 falhas. Assinatura recebe `notchCenter` para o primeiro uso |
| A06 | feito | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest"` com stub → 4 falhas de asserção do finder (ex.: `Expected exactly '1' node … (ContentDescription = 'Soltar da borda')`); verde: mesmo comando, 97 testes, 0 falhas; `*HudWindowPreferencesTest*` 22/22. Botão por fora da engrenagem (`HudNotchMeasure.kt`), parte própria `HudFloatPart` (sem valor novo em `HudNotchPart`); `HudNotch.kt` 741 linhas. Só alterna e grava o modo; o efeito na janela é a A08 |
| A07a | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingHole*"` com stub → 6 falhas de asserção (`HudFloatingHoleMathTest.kt:53` `esperado=90.0, obtido=0.0`); verde: mesmo comando, 10 testes, 0 falhas; `*HudOrbitGeometryTest*` 13/13. Inclinação é porcentagem (`tiltDegrees` → `tiltPercent`, fórmula do R2). Devolvida uma vez (identificadores em português) |
| A07b | feito | vermelho: `gradlew.bat desktopTest --tests "*HudOrbitField*"` com stub → falhas de asserção em `HudOrbitFieldTest.kt:36` (`assertCountEquals(7)`), `:45`, `:53`, `:63`, `:70`, `:93`; verde: mesmo comando, 6 testes, 0 falhas. Devolvida uma vez (fase lida na composição recompunha o campo a cada quadro → lida só no `offset { }`; anel sem `active`/`attention`/`refreshing`). Sem teste para recomposição nem para `active` (a semântica não expõe) |
| A08a | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingLayoutTest*"` com stub → 9 de 12 falhas de asserção (`HudFloatingLayoutTest.kt:30` `expected:<878.0.dp x 300.0.dp> but was:<0.0.dp x 0.0.dp>`); verde: mesmo comando, 12 testes, 0 falhas. Janela fixa com balão cabendo dos dois lados; `hitRect` só do que está visível |
| A08b | feito | vermelho: `gradlew.bat desktopTest --tests "*HudFloatingHud*"` com stub → 7 falhas de asserção (`HudFloatingHudTest.kt:54`… `:102`, nós ausentes); verde: mesmo comando, 9 testes, 0 falhas; `*HudOrbitField*` 6/6. Devolvida uma vez (balão sem moldura `HudBalloon`; engrenagem sem indicador de atualização → `badgeTone` + frase na descrição, `:96`/`:102` vermelhos antes). Sem teste: hover, balão à esquerda, arrasto no expandido |

## Problemas em aberto e riscos

- Janela solta fora da borda muda a premissa da `hud-notch.md` (forma de encaixe, `hudHitRegionMode`, alfa no Linux);
  o custo depende da direção escolhida.
- Clique atravessável e transparência por proximidade (se escolhidos) são nativos por plataforma: medir antes de liberar.
- `HudNotch.kt` (721 linhas) e `HudNotchGeometry.kt` (735) estão perto do teto de 800: tudo do modo flutuante entra em
  arquivos novos; quem toca nesses dois começa extraindo.
- Nenhum teste exercita o sistema de janelas: A08 é aberto no app real no Windows antes do PR; Linux fica fora (A05).
