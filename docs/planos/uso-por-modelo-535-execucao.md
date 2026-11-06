# Custo do Haiku 5.5 e coluna de uso em "Modelos da sessão" — issue #535

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-06, branch
`fix/issue-535-model-usage-share`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

A tabela "Modelos da sessão" mostrava `claude-haiku-5-5` com custo `—` e "sem tarifa", embora o modelo esteja em
`ModelPricingTable`. A issue pede também um indicador percentual de uso por modelo.

## Fatos confirmados (2026-11-06, `f6480eb4`)

- `sqlite3 -readonly ~/.usage-monitor/usage-history.db` na sessão `584efeca`: Haiku 5.5 com 30 turnos, 64 de entrada,
  10.882 de saída, 244.871 de cache lido, 42.431 de cache gravado 5m; Opus 5.5 com 16 turnos.
- Recálculo pela tarifa base do Haiku 5.5 dá US$ 0,0132, exatamente a diferença entre o total da sessão
  (US$ 1,2207) e o custo do Opus na tabela (US$ 1,2075): o total já precificava o Haiku, só o corte por modelo não.
- `CliSessionModelMix.kt:88` (`toModelBreakdown`) monta `CliUsageGroupRow` sem `longPrompt`, e
  `ModelPricing.groupCostMicros` devolve `null` para modelo com faixa quando a parte longa não foi medida.
- `WindowedSessionAccumulator.kt:85` e `LocalCliSessionUsageGroups.kt:41` já passam `longPrompt`: o defeito é só do
  detalhe da sessão, que parte dos turnos.
- Turnos da sessão: Opus 16 de 46 (35%), Haiku 30 de 46 (65%).

## Decisões

- **Protótipo A, "Coluna gêmea", escolhido pelo autor em 2026-11-06:** coluna `% de uso` de 110dp depois de
  `% do custo`, com valor escrito e trilha neutra. O protótipo B (marcador na trilha de custo) não foi escolhido.
- **Base do uso é turnos, escolhida pelo autor em 2026-11-06.** Tokens com cache ficou de fora: cache lido domina o
  número.
- Sem tarifa nenhuma na sessão (base `TOKENS`, caso do Codex), a coluna não aparece: a sessão já mostra a participação
  em tokens, e a tabela do Codex mantém o layout.
- Modelo sem tarifa continua com uso medido: turno existe mesmo sem preço.
- Campo novo com default em `ModelShare`; nenhum valor novo em `ModelShareBasis`.

## Atividades

- **A01** Plano.
- **A02** `toModelBreakdown` soma a parte de prompt longo por turno (bug).
- **A03** `ModelShare` ganha a fatia de uso por turnos, calculada em `sessionModelMixOf`.
- **A04** Coluna `% de uso` em `SessionModelTable`, com protótipo, design system e `docs/presentation.md`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness: documento | Plano criado; fatos medidos com os comandos citados acima |
| A02 | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.CliSessionModelMixTest"` | Vermelho: 12 rodados, 3 falhas, `CliSessionModelMixTest.kt:143` `Expected value to be true.` em `isCostComplete`; verde: 12 testes, 0 falhas |
| A03 | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.CliSessionModelMixTest"` | Vermelho: 16 rodados, 3 falhas, `CliSessionModelMixTest.kt:99` `expected:<0.6> but was:<null>`; verde: 16 testes, 0 falhas |

## Problemas em aberto e riscos

- Largura: as colunas fixas passam de 598dp (comentário antigo, defasado) para 708dp mais o mínimo de 150dp de Modelo;
  conferir em janela de 960dp na atividade A04.
- TUI, linha resumida da sessão, CSV, JSON e PDF ficam fora desta issue.
