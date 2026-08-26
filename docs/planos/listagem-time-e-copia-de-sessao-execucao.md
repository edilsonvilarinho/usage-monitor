# Cópia de sessão do time (#102) e legibilidade das listagens (#104)

Plano de execução das issues [#102](https://github.com/edilsonvilarinho/usage-monitor/issues/102) e
[#104](https://github.com/edilsonvilarinho/usage-monitor/issues/104). As duas são da mesma família —
as listas do time — e uma toca o que a outra redesenha, então correm juntas.

A tabela de **pontos de situação** no fim é atualizada no mesmo commit da atividade que ela descreve;
cada entrada carrega o comando que rodou e o resultado, nunca a intenção.

Toda mudança de superfície visível desta linha entra em
[`prototipo-visual-opencode.html`](prototipo-visual-opencode.html) no **mesmo commit** — a regra de
precedência ("divergência entre o Compose e o protótipo é defeito do Compose") só se sustenta
enquanto o protótipo descrever o app inteiro.

---

## Contexto

### #102 — a cópia de sessão está do lado errado nas duas pontas

`CopySessionCommandButton` tinha dois modos: `isLocalSession = true` copiava
`claude --resume <uuid completo>`; `false` copiava só o uuid. No caminho do time o `false` era
**literal** em `TeamUsageScreen.kt` (linha da lista e detalhe), sem consultar device nenhum.

Disso saíam dois defeitos opostos:

- a sessão da **própria máquina**, quando aparece na lista do time, perdia o comando de retomada que
  ela poderia dar — o transcript está nesta máquina e o `--resume` funcionaria;
- a sessão de um **colega** oferecia um botão de cópia, que é o que a issue pede para não existir.

`TeamUsageScreen` nem recebia `localDeviceId`, embora `Main.kt` já o passe para a tela de presença
poucas linhas adiante, e a presença já faça exatamente essa comparação
(`TeamPresenceScreen.kt`, `entry.deviceId == localDeviceId`).

### #104 — capa e item leem igual

A hierarquia das listas do time é conta → (uuid) → integrante → sessões, e os níveis não se
distinguem. Medido antes da mudança:

| Evidência | Onde |
|---|---|
| Capa e item começam no **mesmo x** (`horizontal = 14dp` nos dois) | `TeamUsageScreen.kt` faixa da conta e `TeamMemberRow` |
| Capa e item usam o **mesmo marcador verde** (`accents.cacheRead`) | idem |
| Capa (62dp) era **mais baixa** que o item (63dp) | `TeamUsageScreenTest.kt` |
| `surfaceVariant` da capa **é o hover** do `AppDataRow` do item | `AppStructure.kt` |
| Os fundos vizinhos ficam dentro de ~3% de luminância | `AppTheme.kt` |
| O bloco de sessões eram **N `Box` irmãos**, sem nada que os unisse | `TeamUsageScreen.kt` |
| `DepthSurface` importado e nunca usado, com comentário afirmando o contrário | `TeamUsageScreen.kt` |

Parte da correção é conformar o Compose ao que o protótipo (§9) **já** especificava — um degrau de
superfície por nível e o marcador do integrante na cor da fonte, não no verde da conta. O resto são
decisões novas, que entram no protótipo junto.

---

## Decisões travadas

| Ponto | Decisão | Motivo |
|---|---|---|
| Sessão de outro integrante | Perde **só** o botão de copiar | A linha continua clicável e o detalhe segue abrindo: é ele que permite diagnosticar a sessão saturada de um colega, que é para o que a lista do time existe |
| Modos do botão | `CopySessionCommandButton` deixa de ter `isLocalSession` | Sem o ramo "copia só o uuid" nenhum call site o usa. Parâmetro sem consumidor é um segundo caminho para uma decisão que passa a ter um dono só |
| Hierarquia | Reforçar os níveis, não achatar | Achatar tiraria a comparação entre contas da visão global, que é a razão de a faixa existir |
| Recuo do item | Dentro da **coluna de identidade**, não da linha | A capa é totalizadora: seus números têm de ficar no mesmo x dos do integrante. Recuar a linha inteira desalinharia as colunas da faixa de legendas, que a issue #81 proibiu |
| Alcance | Uso do time, visão global **e** presença | As três repetem o mesmo defeito de peso e de rótulo |
| Protótipo | Atualizado nas seções §2, §6, §9, §10 e §10b | Toda superfície tocada é visível |

---

## Atividades

Uma atividade, um commit. Cada uma fecha sozinha: código, teste, protótipo e a linha de ponto de
situação juntos.

### A01 — O botão de copiar deixa de ter dois modos

`CopySessionCommandButton` perde `isLocalSession`; o payload é sempre `resumeSessionCommand` e o
rótulo sempre `copyResumeCommand`. `CliSessionsLabels.copySessionId` sai como código morto.
`CliSessionRow` mantém o parâmetro com semântica nova: `false` **não renderiza** o botão.

### A02 — `localDeviceId` chega à tela do time e decide o botão

`Main.kt` passa `teamSettings.deviceId.takeIf { it.isNotBlank() }` para `TeamUsageScreen`, que o
propaga até a linha da sessão e o painel de detalhe. `localDeviceId` nulo significa "nenhuma sessão é
desta máquina" — nenhum botão, que é o comportamento seguro.

### A03 — Escada de hierarquia nas listas de uso

Quatro alavancas, todas com tokens e primitivas existentes: recuo do item dentro da coluna de
identidade; marcador do integrante na cor da fonte; capa mais alta e com o e-mail em `titleMedium`;
e a palavra do nível na linha do integrante, emendada à máquina para não custar altura.

### A04 — O bloco de sessões vira um bloco

Guia vertical de 2dp ligando o cabeçalho de sessões e as linhas à linha do integrante, como
primitiva em `AppStructure.kt` — criada e consumida no mesmo commit. Limpeza do import morto de
`DepthSurface` junto.

### A05 — Mesma escada na presença

Marcador da capa distinto do item (na presença o marcador do item carrega **estado**, não
identidade), capa mais pesada e rótulo de nível na coluna de identidade.

---

## Riscos

| # | Risco | Tratamento |
|---|---|---|
| R1 | `TeamUsageScreenTest` fixa 62dp e 63dp, e a A03 muda os dois | O teste passa a afirmar a **relação** (capa mais alta que item). Dois números mágicos não dizem qual dos dois é a capa |
| R2 | As capturas do README renderizam as duas telas | `generateScreenshots` é manual e não roda no CI; regerar fica como pendência, não como atividade |
| R3 | A coluna de identidade encolhe com o recuo | As colunas numéricas não se movem e o piso da janela continua válido; `TextOverflow.Ellipsis` já está em todas essas linhas |
| R4 | `drawBehind` e escala de UI | A armadilha da issue #83 é sobre `Modifier.border` arredondar para cima e pintar por cima do conteúdo; a guia é traço de fundo, fora do fluxo de layout. Verificar mesmo assim em 100% e 115% |

---

## Pontos de situação

A coluna `Commit` guarda o **assunto** do commit, não o hash: um commit não pode conter o próprio
hash, e preencher o hash depois quebraria a regra de escrever a linha no mesmo commit da atividade.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-08-26 | `refactor(session): drop the second copy mode` | O botão de copiar deixa de ter dois modos | concluída | `CopySessionCommandButton` perdeu `isLocalSession`: o payload é sempre `resumeSessionCommand` e o rótulo sempre `copyResumeCommand`. `CliSessionsLabels.copySessionId` saiu como código morto, junto com o assert dele em `ResumeSessionCommandTest`. `CliSessionRow` **manteve** o parâmetro com semântica nova — `false` não renderiza o botão —, e é isso que preserva o call site do modal do time sem inventar um segundo nome para a mesma pergunta. O painel de detalhe do time (`TeamSessionDetailPane`) perdeu o botão nesta atividade e o recupera na A02, condicionado ao device; o import de `CopySessionCommandButton` saiu de `TeamUsageScreen` junto, senão o commit deixaria import morto. Teste 2 e 5 de `CopySessionCommandButtonTest` reescritos: em vez de afirmar o rótulo alternativo, afirmam **ausência** dos dois rótulos com `onAllNodesWithContentDescription(...).assertCountEquals(0)`. Protótipo §6 ganhou a nota da regra. `gradlew.bat desktopTest --tests "…CopySessionCommandButtonTest" --tests "…ResumeSessionCommandTest"`: BUILD SUCCESSFUL em 1m43s |
| A02 | 2026-08-26 | `feat(team): copy only the session of this machine` | `localDeviceId` chega à tela do time e decide o botão | concluída | `Main.kt` passa `teamSettings.deviceId.takeIf { it.isNotBlank() }` para `TeamUsageScreen` — a mesma expressão que a tela de presença já recebia 49 linhas abaixo, e não uma segunda origem para o mesmo valor. Ele desce por `TeamUsageContent` e `TeamUsageList` até a linha da sessão, onde `isLocalSession` vira `localDeviceId != null && member.deviceId == localDeviceId`; `member` já estava no escopo do `items`, que usa `member.deviceId` para a `testTag` desde antes. O painel de detalhe recuperou o botão pela mesma comparação, contra `detail.deviceId`, que `TeamSessionDetailUiState` já expõe — nenhum campo novo no estado. Três testes em `TeamUsageScreenTest`, e o terceiro é o que fecha a porta: **sem device local nenhuma sessão oferece botão**, que é o caso da instalação só de administração e o único em que o default silencioso poderia vazar o comando. O teste da sessão alheia afirma as duas coisas juntas — o id curto continua na tela e o botão não —, senão ele passaria também se a linha inteira sumisse. `ScreenshotGenerator.teamUsage` passa `LOCAL_DEVICE_ID`, que já é o `device-a1` expandido na captura. `gradlew.bat desktopTest --tests "…TeamUsageScreenTest" --tests "…CopySessionCommandButtonTest"`: BUILD SUCCESSFUL em 43s |
| A03 | 2026-08-26 | `fix(team): make the account band read as a cover` | Escada de hierarquia nas listas de uso | concluída | Quatro degraus, porque nenhum deles resolve sozinho: **palavra do nível** (`memberBandWithMachine`, "Integrante · MÁQUINA", espelhando o "Conta · N integrantes" que a faixa já tinha), **recuo** de `TEAM_NEST_INDENT` (12dp) por nível, **marcador** do integrante saindo de `accents.cacheRead` para `accents.anthropic` — igual ao protótipo, que já desenhava a cor da fonte ali — e **peso**, com `TEAM_ACCOUNT_VERTICAL_PADDING` de 10dp para `AppSpacing.md` e o e-mail de `titleSmall` para `titleMedium`. `titleSmall` é 12sp SemiBold e `labelMedium` é 12sp normal: capa e célula tinham o mesmo corpo. **O recuo entra dentro da coluna de identidade**, com um `Spacer` antes do ícone e a coluna cedendo a largura, para as colunas numéricas continuarem no x das da faixa — a comparação entre os dois números é a razão de a faixa existir, e a faixa de legendas é uma só para a lista inteira (issue #81). Fórmula em `TeamUsageList`: integrante = 12dp na visão global, 24dp quando o e-mail tem mais de uma conta e a sub-faixa de uuid ocupa o primeiro degrau, zero no modal de uma conta; sessões somam `TEAM_SESSION_INDENT` a isso. A máquina saiu da linha própria e foi para o rótulo, então **a altura da linha não mudou**: quarta linha por integrante seria clareza paga em rolagem. `TeamUsageScreenTest` deixou de fixar 62dp/63dp — os dois números descreviam o defeito (capa 1dp mais baixa que o item) sem que ninguém os lesse — e afirma agora a **relação**, com teto de 72dp para a linha não voltar a ser o card de 109dp; `bottom - top` e não `.height`, que ali resolve para o modificador de layout importado. Dois testes ajustados por causa do rótulo novo e um teste novo para ele. `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"`: BUILD SUCCESSFUL em 1m12s |
| A04 | 2026-08-26 | `feat(ui): tie a nested block to the row that opened it` | O bloco de sessões vira um bloco | concluída | `Modifier.appNestedGroupGuide` em `AppStructure.kt`: traço de 2dp — a mesma espessura de `AppSourceMarker`, pelo mesmo `MARKER_WIDTH` — desenhado na metade do recuo, encostado nem no conteúdo nem na borda do item. É `drawBehind` e **não** `Modifier.border`: aquele arredonda a espessura para cima e pinta depois do conteúdo, que é o defeito medido na issue #83. A primitiva e o primeiro consumidor entram no mesmo commit, porque a passada de conformidade de 2026-08-23 existiu justamente por causa de `AppWindowScaffold` e `AppToolbar` terem ficado em zero telas. Ela resolve o que a superfície não resolvia: as sessões são itens **irmãos** do integrante na mesma `LazyColumn` — aninhar lista em lista quebra a rolagem —, então até aqui nada na árvore dizia que aqueles itens pertenciam à linha de cima. Teste em `AppStructureTest` mede o que pode dar errado: o conteúdo com guia e o sem guia começam no **mesmo x**, ou seja, ela não consome layout. Import morto de `DepthSurface` removido e o comentário que afirmava que a linha do integrante morava dentro dele foi corrigido na A03. Protótipo: `<h3>` própria em §2 e a borda no bloco de §9. `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"`: BUILD SUCCESSFUL em 1m31s |

### Achados

| Data | Achado | Efeito no plano |
|---|---|---|
