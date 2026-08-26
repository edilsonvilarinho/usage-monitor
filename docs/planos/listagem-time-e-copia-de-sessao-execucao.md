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

### Achados

| Data | Achado | Efeito no plano |
|---|---|---|
