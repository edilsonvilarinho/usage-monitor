# Benchmark do repositório contra pares — issue #687

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-27, branch `feat/issue-687-repo-benchmark`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação delegada ao `prototype-dev` (tooling).

## Objetivo

Dar ao dono uma régua externa e repetível do nível do repositório: em que posição o `usage-monitor` fica, eixo a
eixo, num grupo de apps com o mesmo objetivo e de repositórios grandes da mesma stack. Sem nota única: a fusão
esconde o eixo fraco.

## Fatos confirmados (2026-11-27, `5aa401b6`)

- `gh repo view --json visibility`: `edilsonvilarinho/usage-monitor PUBLIC`.
- Pares (`gh repo view`, 2026-11-27): `steipete/CodexBar` ★23377 Swift, push 2026-11-21; `Nihondo/AgentLimits` ★72
  Swift, push 2026-10-31; `methol-dev/usage-bar` ★1 Swift, push 2026-10-02 (`methol/usage-bar` redireciona);
  `ktorio/ktor` ★14581; `Kotlin/kotlinx.coroutines` ★13817; `JetBrains/compose-multiplatform` ★19502. Nenhum arquivado.
- API pública do Scorecard (`GET https://api.securityscorecards.dev/projects/github.com/<owner>/<repo>`):
  `ktorio/ktor` score 4.6 (2026-11-23, 14 checks); `Kotlin/kotlinx.coroutines` 5 (2026-11-23, 14 checks). Sem JSON
  (repo não varrido) para `usage-monitor`, CodexBar, AgentLimits, usage-bar e compose-multiplatform.
- Scorecard: nota 0–10 por check e agregado ponderado por risco; checks inconclusivos (`score: -1`) saem da média.
  O projeto declara as checagens como heurísticas, com falso positivo e falso negativo (README `ossf/scorecard`).
- Docker local parado (`docker info`: pipe `dockerDesktopLinuxEngine` inexistente); o CLI do Scorecard não roda
  por imagem nesta máquina hoje.
- Faixas DORA (elite/alto/médio/baixo) mudam a cada ano por análise de perfil latente e divergem entre fontes de
  fornecedores; não se usam faixas aqui, só os números brutos.

## Decisões

- **Eixos:** (1) Scorecard agregado e por check; (2) entrega em janela de 90 dias pelo GitHub: releases publicadas,
  mediana em horas de criado→mesclado dos PRs mesclados, taxa de revert (PRs mesclados cujo título começa por
  `Revert` ou `revert`) sobre PRs mesclados.
- **Posição, não nota:** para cada eixo, posição do repositório alvo no grupo (1 = melhor) e percentil. Direção:
  Scorecard e releases maiores são melhores; mediana de merge e taxa de revert menores são melhores.
- **`null` é não medido:** repositório sem Scorecard na API e sem JSON local fica `null` e fora da posição daquele
  eixo; nunca vira zero.
- **Scorecard do CLI:** `--scorecard-dir <pasta>` lê `<owner>__<repo>.json` (saída `--format json` do CLI) e tem
  precedência sobre a API.
- **Fora:** SonarQube e CodeScene (exigem análise por repositório), faixas DORA, bloqueio de PR.

## Atividades

| ID | Atividade | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/` |
| A02 | Script com parte pura testada e casca `gh`/API | `tools/ci/repo-benchmark.mjs`, `tools/ci/repo-benchmark-peers.json`, `tools/ci/test/repo-benchmark.test.mjs` |
| A03 | Rodar contra o grupo e registrar a linha de base | `docs/planos/` |

## Ponto de situação

| ID | Estado | Comando e resultado |
|---|---|---|
| A01 | feito | plano escrito; fatos medidos com os comandos citados acima |

## Problemas em aberto e riscos

- Scorecard da API cobre só repositórios varridos pela OpenSSF; os pares pequenos e o próprio alvo dependem do CLI.
- Taxa de revert subestima defeito (a #645 mediu 1 revert em 105 merges contra 15,5% de defeito que escapou); fica
  como proxy comparável entre repositórios, não como taxa de falha.
- Repositórios grandes mesclam PR por bot ou fora do GitHub (ex.: Gerrit/Space); mediana de merge pode não refletir
  o fluxo real deles. A03 registra quantos PRs entraram na conta de cada um.
