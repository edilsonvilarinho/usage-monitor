# Conta Ultra sem janela de 5h e rótulo do plano — issue #566

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`fix/issue-566-anthropic-ultra`, base `origin/main` (`ba6ba233`), commits com a identidade `claude <claude@anthropic.com>`.
Implementação pelo agente `backend-dev` (modelo do frontmatter em `.claude/agents/`). Origem: análise das issues do
`akitaonrails/ai-usagebar` dos últimos 30 dias (akitaonrails/ai-usagebar#566).

## Objetivo

Uma conta Ultra verificada recebe `"five_hour": null` em `GET /api/oauth/usage`. O DTO declara `fiveHour` não-nulo, então
a resposta não desserializa e a fonte Anthropic inteira falha. O plano Max 20x foi renomeado para Ultra e o rótulo ainda
diz "Max 20x".

## Fatos confirmados

- `AnthropicUsageDto.kt:19`: `@SerialName("five_hour") val fiveHour: AnthropicUsageWindow` — não-nulo, sem default.
- `AnthropicMapper.kt:31-37`: a cota `FIVE_HOUR` é adicionada sempre, sem ramo para ausência.
- `AccountPlanLabel.kt:19-27`: `rateLimitTier` com `20x` → `"$name 20x"` ("Max 20x").
- Artigo oficial (WebFetch de `support.claude.com/en/articles/17728952`, 2026-11-12): "The Max 20x plan is now called
  the Ultra plan." e "Verified Ultra accounts don't have a five-hour limit." O artigo **não** cita renomeação do Max 5x.
- Resposta de conta Ultra verificada (akitaonrails/ai-usagebar#566, números redigidos): `"five_hour": null`,
  `"seven_day": {"utilization": 0.0, "resets_at": "…", …}`, `"seven_day_sonnet": null`, `limits[]` sem `session`.
- Contas locais (`node probe.mjs` no scratchpad, 2026-11-12): `.claude` → `pro`/`default_claude_ai`, `five_hour`
  presente; `.claude-conta3` → `max`/`default_claude_max_5x`, `five_hour` presente. Nenhuma conta Ultra local: o
  fixture vem da issue de origem.
- Varredura (`explorer`, 2026-11-12): nenhum consumidor de produção assume a 5h — `TerminalStatusLineView.kt:38-42`,
  `HudModel.kt:124,383`, `ModalWindowsDecisions.kt:36,66-69`, `UsageAlert.kt:393-397`, `UsageSnapshot.kt:91-102`,
  `TerminalJson.kt:45-61` usam `firstOrNull`/`getOrNull`/`?:` ou percorrem a lista.

## Decisões

- `five_hour` nulo **omite** a cota 5h, para qualquer plano. Não vira 0%: número que a API não informa não é derivado
  (CLAUDE.md, camada data), e `null` é "não medido".
- `seven_day` continua obrigatório: nenhuma evidência de resposta sem ele.
- `20x` → "Ultra" (sem o nome do `subscriptionType`, que continua `max`). `5x` continua "Max 5x" até haver fonte
  oficial.
- Sem mudança de tela: o card e a HUD já desenham a lista de cotas que vier.

## Atividades

- A01 — este plano.
- A02 — `five_hour: null` desserializa e o mapper omite a cota 5h.
- A03 — rótulo "Ultra" para o tier `20x`.

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito com os fatos acima | — |

## Problemas em aberto e riscos

- Conta Ultra ainda não verificada tem 5h temporária e segue como hoje (o campo vem preenchido).
- Fixture da conta Ultra é redigido pela issue de origem, não capturado aqui.
