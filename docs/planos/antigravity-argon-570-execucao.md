# Grupo "Gemini 4 Argon" no Antigravity — issue #570

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`test/issue-570-antigravity-argon`, base `origin/main` (`93e19ecf`), commits com a identidade
`claude <claude@anthropic.com>`. Implementação pelo agente `backend-dev` (modelo do frontmatter em `.claude/agents/`).
Origem: akitaonrails/ai-usagebar#441, #443, #450, #453.

## Objetivo

Garantir, com teste, que o grupo "Gemini 4 Argon" do `agy` 1.4+ vira cota própria sem sobrescrever o Gemini semanal
nem derrubar a fonte — o defeito que o ai-usagebar teve.

## Fatos confirmados

- Leitura real (`agy.exe --sandbox --print-timeout 30s --output-format json --print /usage` pelo PowerShell, agy 1.5.7,
  2026-11-12): `command.data.groups[]` = "Gemini Models" (`gemini-weekly`, `window:"weekly"`, `remaining_fraction`
  0.9758…) e "Claude and GPT models" (`3p-weekly`, `remaining_fraction` 1); `num_turns:0`, `total_tokens:0`. Sem Argon.
- Forma do Argon (akitaonrails/ai-usagebar#450, `agy` 1.4.0): grupo "Gemini 4 Argon", bucket
  `gemini-argon-wtu-weekly`, `remaining_fraction` 0, `reset_time` 2026-10-24T01:10:47Z; mesmo
  `groups[].buckets[]` com `id`, `window`, `remaining_fraction`, `reset_time`.
- `AntigravityUsageMapper.kt:70-73` percorre todos os grupos; rótulo = grupo curto + janela (`:131-148`):
  "Gemini 4 Argon" → "Antigravity Gemini 4 Argon 7d", distinto de "Antigravity Gemini 7d".
- `grep -ri argon` em `src/`: nenhum resultado.
- Incidente desta sessão: duas chamadas ao `agy` pelo Git Bash (`/usage` e `/quota`) viraram prompt de modelo
  (`num_turns:1`, 17.661 e 57.600 tokens), porque o MSYS reescreve `/usage` como caminho — exatamente o que
  `docs/integrations.md` (Antigravity, "Guard rails") proíbe. O app chama sem shell e não sofre isso.

## Decisões

- Atividade de teste: o mapper genérico já deve cobrir o Argon. Teste verde de primeira é caracterização, registrado
  como tal; se falhar, a correção segue TDD.
- Ordem e limite de anéis da HUD com 4+ cotas e o pool Argon esgotado dominando o foco (#453 de origem) ficam fora:
  sem amostra real nesta conta.

## Atividades

- A01 — este plano.
- A02 — teste de caracterização do payload de três grupos.

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito com os fatos acima | — |
| A02 | caracterização, sem vermelho: o mapper genérico já cobre o grupo. `gradlew.bat desktopTest --tests "com.usagemonitor.data.AntigravityUsageMapperTest"` → 9 testes, 0 falhas na primeira execução; três rótulos distintos, Gemini `used` 2, Argon `used` 100 com reset 2026-10-24T01:10:47Z. `AntigravityUsageMapper.kt` intocado | verde |

## Problemas em aberto e riscos

- Argon esgotado (0% restante) pode virar a cota em foco da HUD enquanto o Gemini compartilhado ainda tem saldo
  (akitaonrails/ai-usagebar#453). Não medido aqui.
