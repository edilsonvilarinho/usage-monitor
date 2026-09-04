# Profundidade visual do design system — plano de execução

Issue [#223](https://github.com/edilsonvilarinho/usage-monitor/issues/223), execução das 7 direções
e dos bugs confirmados em [#222](https://github.com/edilsonvilarinho/usage-monitor/issues/222)
(análise completa, sem implementação).

## Problema

`docs/design-system/readme.md` declara que profundidade vem só de borda 1dp e espaçamento — "not
from shadow, not from gradient". A regra em si não muda nesta execução. O que muda é que ela está
sendo aplicada de forma inconsistente e, em pelo menos um arquivo, quebrada: tokens que não existem
em `tokens/*.css`, uma primitiva documentada em prosa e nunca publicada, e um kit de referência
reincidindo no antipadrão que a própria passada de conformidade de 2026-08-27 já corrigiu no app.

## Decisões

1. **Nenhuma atividade reintroduz sombra pesada, gradiente, tint de card ou glow de acento.** Todas
   usam tokens/técnicas já declarados no design system e não usados direito — é auditoria e extensão,
   não mudança de regra.
2. **A04 (regra única de aninhamento) precede A06 (generalizar a escada de 3 superfícies).** Sem a
   regra escrita, generalizar reproduziria a mesma ambiguidade (`--bg` vs. `--surface`) em telas
   novas.
3. **A02 (`AppTooltipSurface` publicada) precede A07 (ligar `--shadow-2` nos flutuantes).** O tooltip
   é o primeiro consumidor real de `--shadow-2`; sem a primitiva existir como arquivo, não há onde
   aplicar a sombra.
4. **Uma atividade, um commit**, seguindo a convenção do repositório — código, kit, protótipo e
   `CLAUDE.md`/readme da mesma decisão entram juntos.

## O que foi verificado antes de decidir

- `tokens/shape.css` define só `--r1`..`--r4` e `--shadow-0`/`--shadow-2`/`--shadow-8`. `--r10` e
  `--e8` (usados em `AppConfirmationDialog.jsx:6`) não existem — `var()` inválida cai no valor
  herdado, então o único diálogo elev-8 documentado renderiza sem radius e sem sombra reais.
- **Achado adicional, fora do corpo de #222**: `AppConfirmationDialog.jsx:7` usa `var(--t13)` para o
  título. `tokens/typography.css` define a escala fechada 10/12/14/16/20/28 — `--t13` também não
  existe. Mesma classe de bug do `--r10`/`--e8`, mesmo arquivo, entra na mesma atividade (A02).
- `AppTooltipSurface` não existe em `components/` (nenhum `.jsx`, `.prompt.md` ou `.d.ts` com esse
  nome) — confirmado por busca no diretório.
- `Settings.jsx:38` monta `<div style={{border, background: 'var(--surface)', borderRadius:
  'var(--r3)'}}>` em vez de compor com `AppPanel`.
- `AppDataRow.jsx:18`: `background: guide ? 'var(--bg)' : (hoverable && hover ? 'var(--raised)' :
  'transparent')` — `guide=true` desliga hover incondicionalmente, mesmo com `onClick` presente.

## Riscos declarados

1. **A04 é decisão de gosto, não bug.** As duas construções de aninhamento (`--bg` afundando vs.
   `--surface` plano com peso de texto) não estão erradas isoladamente. A atividade escreve a regra
   no readme antes de tocar código — se a escolha for "as duas são válidas, com critério", A06 aplica
   esse critério em vez de eliminar uma das duas.
2. **A06 tem 3 telas (Dashboard, History, CLI Sessions) com aninhamentos de forma diferente** — o
   commit pode precisar ser 1 por tela em vez de 1 só, se a generalização não for mecânica em alguma
   delas. Decisão fica para quando A04 estiver fechada e o aninhamento real de cada tela for lido.
3. **A08 (massa no gráfico via `color-mix` sob a curva) toca `History.jsx` e `SessionDetail.jsx` no
   kit — e o Compose real correspondente**, se existir consumidor direto da curva (`HistoryChart` ou
   equivalente). Precisa localizar o componente Compose antes de estimar se é 1 ou 2 commits.
4. **Nenhuma atividade tem, a priori, teste de componente Compose associado** — a maior parte do
   trabalho é `docs/design-system/` (kits `.jsx`, protótipo `.html`), que não roda em `gradlew.bat
   allTests`. Onde a atividade também mexe em Compose (A02 tooltip, A03 Settings, A05 AppDataRow, A06
   generalização, A08 gráfico, A09 tipografia, A10 identidade vendor), a suíte
   `com.usagemonitor.ui.*` roda como verificação.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Issue de execução e plano | `gh issue create --repo edilsonvilarinho/usage-monitor` | issue [#223](https://github.com/edilsonvilarinho/usage-monitor/issues/223) criada com as 10 atividades e dependências (A04→A06, A02→A07), referenciando #222. Plano publicado neste arquivo, na branch `feat/design-system-depth-223` |
| A02 | Corrigir os 3 tokens quebrados em `AppConfirmationDialog.jsx` | inspeção de `tokens/shape.css` e `tokens/typography.css` contra o `.jsx` | `--r10` → `--r4`, `var(--e8)` → `var(--shadow-8)` (os dois do corpo de #222) e `--t13` → `--t12` (achado nesta execução, mesma classe de bug). O título passou a `--t12` por paridade com `AppPanel.jsx:37` e `AppBanner.jsx:23`, os dois outros títulos em mono+600 do sistema — nenhum usa `--t14`. `.prompt.md` e `.d.ts` do componente não citavam os tokens quebrados, não precisaram de mudança. O `AppConfirmationDialog` do Compose (`AppStates.kt:367`) usa `AlertDialog` do Material sem `shape`/`elevation` custom — não lê os tokens do design system diretamente, então não há lado Compose a corrigir nesta atividade |
| A03 | Publicar `AppTooltipSurface` | leitura de `AppControls.kt:703-737` (Compose) contra o `.jsx`/`.d.ts`/`.prompt.md` novos | **A primitiva Compose já existia** — `fun AppTooltipSurface` em `AppControls.kt`, nascida na passada de conformidade, com KDoc completo. A lacuna era só no design system: nunca virou `.jsx`/`.prompt.md`/`.d.ts`. Anatomia confirmada por leitura direta do Kotlin: `color = MaterialTheme.colorScheme.surfaceVariant` → `preset.raised` (`AppTheme.kt:326`) → `--raised`; `shape = AppShapes.small` → 6dp → `--r2`; `tonalElevation`/`shadowElevation = AppElevation.raised` → 2dp → `--shadow-2`; borda 1dp com `outlineVariant` → `--border`. **Divergência real e documentada contra `AppMenu`**: aquele abre em `--surface`, este em `--raised` — um rung acima, não um erro de cópia. Publicado em `components/core/` (ao lado de `AppMenu`, mesma família de popup flutuante), com entrada nos dois lugares do índice do readme (`## Index` e a tabela da passada de conformidade, que ganhou a data de publicação) |
| A04 | Corrigir `Settings.jsx` para compor com `AppPanel` | leitura de `AppPanel.jsx` (spread de `style` por último, `flexDirection` sobrescrevível) e do `.win`/`.body` do protótipo (§12) | A moldura de nav+conteúdo trocou o `<div>` à mão por `<AppPanel style={{ flexDirection: 'row', overflow: 'hidden', minHeight: 420 }}>` — `AppPanel` já expõe `background: var(--surface)`, borda 1dp e `--r3`, e o `style` sobrescreve só `flexDirection` (default `column`) pra caber `AppSettingsNav` ao lado do conteúdo. **Confirmado no Compose que não há lado a corrigir ali**: `SettingsDialogContent.kt:267` usa `Surface(color = MaterialTheme.colorScheme.background)` preenchendo a janela inteira — é a própria `DialogWindow` que dá a moldura, sem painel aninhado; o antipadrão existia só no kit |
| A05 | Declarar regra única de aninhamento | leitura de `AppStructure.kt:526-553` (`Modifier.appNestedGroupItem`, KDoc com a régua de 3 degraus), `AppStructure.kt:403-468` (`AppGroupBand`) e `TeamUsageScreen.kt:815-834`/`:980-992` (uso real dos dois) | **Achado maior do que o previsto**: a régua real já está escrita e testada no Kotlin — `appNestedGroupItem` (usado em `TeamUsageScreen.kt` pra aninhar sessão sob integrante) pinta fundo `--surface`, **não** `--bg`. O guideline (`pattern-nested-guide.html`) estava desatualizado contra a própria implementação que documenta — não é ambiguidade de duas respostas válidas, é doc errada. Corrigido `--bg` → `--surface` nas duas linhas filhas, nota nova explicando o porquê e a distinção contra `AppGroupBand` (que resolve outra pergunta: rótulo de grupo **sem** linha própria, contra filhos de uma linha-pai que **já é** dado completo). `AppGroupBand.prompt.md` e o índice do readme ganharam referência cruzada. **Achado adicional não corrigido nesta atividade**: a régua completa de 3 degraus (`--raised` → transparente → `--surface`) só existe de fato porque a faixa mais externa de conta em `TeamUsageScreen.kt` (`TeamAccountGroupHeader`, `surfaceVariant` direto) é uma reimplementação à mão que não chama `AppGroupBand` — a própria primitiva que a issue #117 publicou pra evitar isso. Fica registrado como achado; corrigir extrapolaria o escopo desta atividade (que é declarar a regra, não caçar toda reincidência do antipadrão) |
