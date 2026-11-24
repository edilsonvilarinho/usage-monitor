# Defeitos que escapam de cada PR — issue #645

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `feat/issue-645-escaped-defects` (worktree `.claude/worktrees/escaped-defects-645`, base `origin/main`
`c24f5f41`), commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Objetivo

Responder com número se a automação está melhorando: que fração dos PRs de produto precisou de correção depois do
merge, por área. É a linha de base do critério da #644 ("até 1% em 30 dias").

## Fatos confirmados (2026-11-24, `c24f5f41`)

- `gh pr list --state merged --search "merged:>=2026-10-22" --json …`: 108 PRs mesclados até 2026-11-23.
- `git log origin/main --since=2026-10-22`: 3 commits `revert`. `756ce628` entrou pelo PR #630 (reverte o #628, já
  mesclado). `1a412e66` e `ed632212` entraram pelo próprio PR #622 e revertem commits dele: não escaparam.
- O corpo do PR quase sempre cita a própria issue e a #342; citar não basta como vínculo. O #624 corrige o modo
  flutuante do #622 e o corpo não cita a #619 nem o #622. Só a referência explícita não acha esse caso.
- `gh pr list --json files` corta em 100 arquivos por PR (#444, #446): os arquivos saem do git.
- A `main` só recebe merge commit; `git rev-list M^1..M` dá os commits de cada PR.

## Decisões

- **Correção:** PR mesclado com título `fix`/`revert` (Conventional Commits, escopo opcional), que altera código de
  produto. `fix` só de documentação, CI, skill ou teste não conta.
- **Código de produto:** `src/commonMain/`, `src/desktopMain/`, `src/installer/`, `server/src/`, `build.gradle.kts`.
- **Candidato a defeito que escapou:** correção com ao menos um PR de produto de origem mesclado **até 14 dias** antes,
  achado por (a) `reference` — corpo do PR ou da issue que ele fecha cita o número do PR de origem ou da issue que ele
  fechou —, ou (b) `blame` — linha que a correção removeu ou trocou foi escrita por commit do PR de origem. Documentação
  e teste não entram no `blame`.
- **O script só levanta candidatos; quem confirma é gente ou agente lendo o caso.** O veredito fica versionado em
  `tools/ci/escaped-defects-verdicts.json` (`escaped`, `origin`, `reason`); candidato sem veredito sai como `pendente`
  e **não** entra na taxa, mas aparece no total.
- **Taxa:** PRs de origem distintos com defeito confirmado ÷ PRs de produto mesclados no período. Duas correções do
  mesmo PR contam um PR.
- **Área:** a de mais linhas alteradas na correção — `hud-window`, `screen`, `data`, `server`, `other`.
- **Limite conhecido:** correção que só adiciona linhas e não cita a origem não é achada. Defeito que ninguém relatou
  também não.

## Atividades

- **A01** — este plano.
- **A02** — `tools/ci/escaped-defects.mjs`, parte pura, com `tools/ci/test/escaped-defects.test.mjs`.
- **A03** — a casca (`gh`, `git blame`), CLI `--since <data> [--json]`, execução real desde 2026-10-22.
- **A04** — conferência manual de cada candidato, `escaped-defects-verdicts.json` e a linha de base nesta página.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | conferido contra a lista dos 108 PRs e os 3 reverts |
| A02 | `node --test "tools/ci/test/escaped-defects.test.mjs"` | vermelho (stub): `fix: x`, `actual: false, expected: true`; verde 13/13. Ajuste de especificação na revisão (área de `desktopMain/presentation`, área pela origem, validação de veredito, `missedByScript`): vermelho `actual: 'data', expected: 'screen'`; verde 17/17 |
| A03 | `node --test "tools/ci/test/*.test.mjs"` e `node tools/ci/escaped-defects.mjs --since 2026-10-22` | devolvida 1x (linha sem título, área e todas as origens; sem vermelho). Vermelho do ajuste: 6 falhas em 27, `actual: undefined, expected: 0.5`; verde 27/27 e 46/46 na pasta, em execução independente. `parseArgs`, `parseRemovedRanges` e `parseBlame` nasceram sem vermelho prévio: provados por mutação (cada quebra reprovou na asserção). `gh`, `git diff` e `git blame` sem harness: validados pela execução real, 27 correções |
| A04 | `node tools/ci/escaped-defects.mjs --since 2026-10-22` | 16 vereditos lidos caso a caso (PR, issue e `git log -S`): 11 origens confirmadas, 4 recusadas, 1 pendente; saída em "Linha de base" |

## Linha de base (2026-10-22 a 2026-11-23)

| Área da origem | PRs de produto | Com defeito confirmado | Taxa |
|---|---|---|---|
| `hud-window` | 16 | 3 | 18,8% |
| `screen` | 26 | 4 | 15,4% |
| `data` | 19 | 2 | 10,5% |
| `server` | 2 | 0 | 0,0% |
| `other` (instalador) | 8 | 2 | 25,0% |
| **Total** | **71** | **11** | **15,5%** |

- Só 1 das 11 origens virou revert (#628): a taxa de revert (1,4% dos PRs de produto) esconde 10 defeitos.
- O #628 como **correção** do #622 segue pendente: não há prova de que a pausa da GPU veio do modo flutuante. Não muda a
  taxa, porque o #622 já está confirmado pelos #624 e #632.
- As 11 correções sem origem foram olhadas por `git log -S` quando havia dúvida: o código corrigido nasceu fora da janela
  de 14 dias (#475, #563, #580, #613, #642, #643) ou a causa é externa (#568). As demais não têm prova de origem recente.
- Conferência feita por Claude Opus 5.5, não pelo dono do produto.

## Problemas em aberto e riscos

- O `blame` acusa PR recente que só moveu a linha de lugar; por isso o veredito manual.
- Período curto (33 dias): a taxa por área tem poucos casos e não serve para comparar áreas entre si.
