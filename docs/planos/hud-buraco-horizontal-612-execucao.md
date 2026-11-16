# Buraco negro das minimizadas também na borda horizontal — issue #612

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-16,
branch `feat/issue-612-horizontal-black-hole` (worktree `.claude/worktrees/issue-612`, base `origin/main` `cc37bd08`),
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Pedido (#612, com capturas): "o mesmo comportamento vertical quando estiver minimizado tem que aplicar no horizontal".
Na borda de cima e de baixo, as contas minimizadas passam a ir para o buraco negro (#600, #602, #611), como na lateral.
Segundo pedido: "não precisa desse texto indicando quantidades de APIs minimizadas". O rótulo "N minimizadas" sai da
célula do buraco nas duas bordas.

## Fatos confirmados (2026-11-16, `cc37bd08`)

Todos lidos nos arquivos citados (varredura do agente `explorer`, conferida por `Grep`).

- O buraco é desligado na horizontal por retorno antecipado: `HudBlackHole.kt:53-55` (`if (edge.isHorizontal)` →
  `holeMembers = emptyList()`). Quem chama: `HudNotchGeometry.kt:205`, `:442` (`verticalCollapsed` fixa `HudEdge.LEFT`),
  `HudNotch.kt:207`, `HudRingStrip.kt:117`.
- Faixa horizontal: `horizontalCollapsed` (`HudNotchGeometry.kt:401-434`). Altura = anel de 64 dp + linha de foco, mais
  8 dp de padding de cada lado. A conta minimizada mede `minimizedRingWithFocusWidth` (`HudNotchMinimized.kt:14-15`):
  anel de 28 dp + 6 dp + a cota em foco. Vão de 12 dp entre itens.
- A célula do buraco (`HUD_BLACK_HOLE_CELL_SIZE` = 236 dp, `HudBlackHoleGeometry.kt:23`) só entra na conta da vertical
  (`HudNotchGeometry.kt:464`, `:482`).
- Desenho: a horizontal desenha todos os índices como `HudRingItem` (`HudRingStrip.kt:137`, `:193-200`). A vertical
  põe `HudBlackHoleCell` depois da última conta (`:172-186`) e o voo em `HudBlackHoleFlights` (`:220`).
- Rótulo de contagem: `hudBlackHoleCountLabel` (`HudBlackHoleLabels.kt:6`), desenhado em `HudBlackHoleCell.kt:66-67`,
  também usado na semântica (`:49`, `:60`) e no cabeçalho da lista (`HudBlackHoleList.kt:47`). A largura do rótulo entra
  no piso da coluna vertical (`HudNotchGeometry.kt:467-474`).
- Auto-minimizar é só vertical: `HudAutoMinimize.kt:24`.
- Testes que fixam "horizontal inalterada": `HudBlackHoleTest.kt:75-81`, `HudBlackHoleCellTest.kt:56-61`,
  `HudNotchTest.kt:343-351` e `HudNotchGeometryTest.kt:619-624`. Esses testes mudam de sentido nesta issue.

## Decisões

- Mudança visual: passa pelo portão de protótipos (`prototipos/issue-612/`, fora do versionamento). Nenhum Kotlin
  antes da escolha do usuário.
- O rótulo sai do desenho, mas a contagem continua na descrição semântica da célula: sem texto, o leitor de tela perde a
  quantidade. O cabeçalho da lista não muda, porque não foi pedido.
- Auto-minimizar segue só na vertical: o pedido é sobre as minimizadas, e a regra de 45% da horizontal não muda.
- **Restrição do dono (2026-11-16, captura `prototipos/issue-612/captura-gigante.png`):** a célula de 236 dp da #611
  "ficou gigante, não pode". O buraco tem de ser compacto nas duas bordas. As três primeiras direções (célula cheia,
  órbita inclinada larga, célula pendente) foram descartadas antes da entrega por partirem dos 236 dp.
- **Escolha do dono (2026-11-16): C1 · Órbita compacta** (`prototipos/issue-612/c1-orbita-compacta/index.html`,
  constantes em `:90-101`, glifo em `:327-342`). A mesma célula serve às duas bordas:
  - célula quadrada de 96 dp = 2·(raio 33 + extensão 15);
  - anel central de 40 dp (antes 64 dp, `HUD_RING_SIZE`), com o filamento terminando em 0,62·20 = 12,4 dp;
  - órbita de raio 33 dp, passo min(40°, 360°/n), volta de 60 s e rastro de 5° mantidos;
  - glifo = disco de 22 dp em `core`, contorno de 1,2 dp em r 10,4 dp (contínuo em execução, tracejado 2/2 ocioso), marca
    de 15 dp em `hot`;
  - **um único** arco de cota por fora do disco: a cota de maior fração usada (empate: a primeira na ordem), traço
    2,5 dp, folga 1,5 dp, raio 13,75 dp, trilho em volta inteira. Extensão = 13,75 + 1,25 = 15 dp;
  - com 10 membros, corda 2·33·sen 18° = 20,4 dp < 22 dp: os discos se sobrepõem 1,6 dp; as marcas de 15 dp não se
    tocam. Aceito pela escolha.
- Encaixe na janela: a célula de 96 dp ocupa o vão da conta **mais** o padding transversal (8 dp de cada lado, centrada
  por `requiredSize`). A espessura da faixa horizontal e a largura da coluna vertical sobem no máximo para
  96 − 2·8 = 80 dp de conteúdo. Com isso a faixa fica em 96 dp no total, como no protótipo.
- O voo do sumidouro (520 ms, `hudSinkFrame`) vale nas duas bordas: na horizontal, o deslocamento é ao longo da faixa.

## Atividades

| Id | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/hud-buraco-horizontal-612-execucao.md` |
| A02 | Protótipos com direções distintas; escolha do usuário | `prototipos/issue-612/` (fora do versionamento) |
| A03 | TDD: geometria C1 (célula 96, anel 40, raio 33, glifo 22/marca 15, um arco), sem o rótulo de contagem no desenho e na conta da coluna (contagem segue na semântica) | `HudBlackHole*.kt`, `HudNotchGeometry.kt` |
| A04 | TDD: buraco na borda horizontal (arranjo, espessura da faixa, célula e voo ao longo da faixa) | `HudBlackHole.kt`, `HudNotchGeometry.kt`, `HudRingStrip.kt` |
| A05 | (absorvida pela A03: o rótulo sai junto com a geometria, que dependia da largura dele) | — |
| A06 | `docs/hud-notch.md`, `AppHudBar.prompt.md` e protótipo de tela | docs |

## Ponto de situação

| Id | Situação | Comando e resultado |
|---|---|---|
| A01 | feito | sem harness de teste (documento); validado pela leitura dos arquivos citados em "Fatos confirmados" |
| A02 | feito | sem harness (protótipo); C1–C3 conferidos por captura headless do Chrome (`--screenshot`, n=7 e n=10, escuro/claro, 1x/2x); C1 e C2 devolvidos uma vez (anel central ausente; texto sob o anel e glifos cobrindo marcas), C3 uma vez (célula cortada em 2x); escolhido C1 |

## Problemas em aberto e riscos

- Coluna vertical com buraco cai de 236 dp para 96 dp de largura e a célula de 262 dp (célula + rótulo + vão) para
  96 dp: a conta do auto-minimizar (#596, `HudAutoMinimizeTest`) muda; recolher uma conta de 110 dp já encurta a coluna.
- Glifos encostam na borda do notch (a célula usa o padding transversal): o contorno do notch não pode cortá-los.
- Mudança no tamanho da janela da HUD horizontal: o risco de sistema de janelas da #342 vale (suíte só no Windows).
