# Catraca de comentários longos — issue #647

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `test/issue-647-comment-ratchet` (worktree `.claude/worktrees/comments-647`, base `origin/main` `c24f5f41`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Objetivo

Comentário narrativo para de crescer no Kotlin de produção e passa a cair, sem apagar restrição medida.

## Fatos confirmados

- Medição de 2026-11-24 em `79f30050` (34 commits antes da base), por script avulso: 23.426 linhas de comentário
  (21,5%), 1.512 blocos de 6+ linhas com 14.030 linhas, 1.336 linhas citando issue, zero `TODO`/`FIXME`.
- `ArchitectureRulesTest` já tem uma catraca global (`INLINE_LANGUAGE_CHECKS_CEILING`), exata: cair também reprova até o
  teto baixar. Dois PRs paralelos que baixam o teto para o mesmo número mesclam sem conflito e deixam a `main` vermelha.
- `ArchitectureRulesTest.kt` tem 660 linhas e `KotlinSourceScanner.kt`, 359.

## Decisões

- **Duas contagens, só produção:** blocos de 6+ linhas seguidas que são apenas comentário (delimitadores e linhas vazias
  de dentro do bloco contam; linha em branco fora encerra) e linhas de comentário com referência a issue (`#` seguido de
  2 a 4 dígitos e fim de palavra; cor hexadecimal não conta).
- **Teto com folga:** acima do teto reprova; abaixo de `teto − folga` reprova pedindo para baixar. A folga (1% do teto)
  evita a `main` vermelha por PRs paralelos.
- **A mensagem ensina:** diz os números, os 5 arquivos com mais blocos e o destino — fato medido vira teste ou vai para
  `docs/`, histórico fica no git e na issue, e só então o comentário sai.
- **Nada de remoção em massa.** O piloto (A03) toca 3 arquivos e só comentário.

## Atividades

- **A01** — este plano.
- **A02** — scanner de comentários e catraca no `ArchitectureRulesTest`, com o teto remedido na base.
- **A03** — piloto em `HudNotchGeometry.kt`, `AppTheme.kt` e `TeamSyncService.kt`; o teto baixa no mesmo commit.
- **A04** — regra no `CLAUDE.md` ("Regras de arquitetura e tamanho").

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | números acima, a remedir em A02 |
| A02 | `gradlew.bat desktopTest --tests "*ArchitectureRulesTest*"` | vermelho (stub): `ArchitectureRulesTest.kt:539 expected:<[CommentBlock(startLine=1, lineCount=6)]> but was:<[]>`; verde 41/41. Remedido em `5304c92d`: **1.515 blocos** de 6+ linhas (folga 15) e **1.332 linhas** citando issue (folga 13). Teto baixado de propósito para 1.514 reprova com os números, os 5 maiores arquivos e o destino do fato |

## Problemas em aberto e riscos

- Comentário que guarda medição de plataforma e some faz o próximo agente repetir o defeito. O piloto lista, por bloco
  removido, para onde o fato foi ou por que não era fato.
- Contagem global não diz se o comentário novo é bom; só impede que o total cresça.
