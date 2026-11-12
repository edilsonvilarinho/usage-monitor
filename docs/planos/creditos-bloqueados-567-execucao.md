# Linha de créditos de uso bloqueada — issue #567

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`fix/issue-567-blocked-credits-row`, base `origin/main` (`ba6ba233`), commits com a identidade
`claude <claude@anthropic.com>`. Implementação pelos agentes `backend-dev` e `frontend-dev` (modelo do frontmatter em
`.claude/agents/`). Origem: akitaonrails/ai-usagebar#456.

## Objetivo

Quando os créditos de uso param pelo servidor (sem saldo, limite mensal atingido), a linha "Créditos de uso" some do
card. Mantê-la, com o gasto, o limite e o motivo por palavra.

## Fatos confirmados

- Resposta real de `GET /api/oauth/usage` (conta `~/.claude`, `node probe.mjs` no scratchpad, 2026-11-12):
  `"extra_usage":{"is_enabled":false,"monthly_limit":57000,"used_credits":0,"utilization":0,"currency":"BRL",
  "decimal_places":2,"disabled_reason":"out_of_credits","user_disabled":false,"spend_limit_reached":false,
  "credits_ever_enabled":true}`.
- Conta sem créditos (`~/.claude-conta3`, mesmo comando): `is_enabled:false`, `monthly_limit:null`,
  `disabled_reason:null`, `credits_ever_enabled:false`.
- `AnthropicCreditsResolution.kt:98-99`: qualquer `is_enabled:false` → `quota = null`, outcome `DISABLED`.
- `AnthropicUsageDto.kt:53-55`: `disabled_reason`, `user_disabled`, `spend_limit_reached` declarados e sem leitor.
- `ApiUsageCardFormatting.kt:169-175`: linha de créditos mostra "Reinicia no início do mês".
- `QuotaInfo.kt:16-40`: nenhum campo de estado/bloqueio por cota.

## Decisões

- Direção visual escolhida pelo usuário (2026-11-12): **protótipo A** (`prototipos/issue-567/a-indicador/`). Linha
  igual à de hoje (valor, trilho `info`); a linha de reinício vira `AppStatusIndicator` com palavra: crit "Sem créditos ·
  trabalho extra pausado", warn "Limite mensal atingido".
- Bloqueado = `is_enabled:false` **e** `user_disabled:false` **e** `monthly_limit > 0` **e** (`disabled_reason ==
  "out_of_credits"` ou `spend_limit_reached`). Qualquer outro `is_enabled:false` continua escondido (sem amostra real).
- Campo novo com default em `QuotaInfo` (`QuotaBlockReason?`, tipo novo), sem valor novo em enum existente
  (`AnthropicCreditsOutcome`, `ApiUsageNotice`, `UsageUnit` intocados).

## Atividades

- A01 — este plano.
- A02 — domain + data: `QuotaInfo` ganha o motivo de bloqueio; `resolveExtraCredits` mantém a linha bloqueada.
- A03 — card: indicador no lugar do reinício; protótipo e design system atualizados no mesmo commit.

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito com os fatos acima | — |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.*"` → 4 falhas de asserção (`AnthropicCreditsResolutionTest.kt:52` esperado `OUT_OF_CREDITS`, obtido `null`; `:81` `SPEND_LIMIT_REACHED`; `:106` precedência; `AnthropicMapperTest.kt:393` linha ausente) com `QuotaBlockReason`/`blockReason` em stub; cache: `DashboardCacheMapperTest.kt:412` `AssertionError` no ida-e-volta; verde: `desktopTest --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.domain.*"` → 1282 testes, 0 falhas. `out_of_credits` vence `spend_limit_reached` quando os dois vêm; nome desconhecido no cache vira `null` e a cota fica | verde |
| A03 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `ComponentTest.kt:940` `Expected exactly '1' node … 'Sem créditos · trabalho extra pausado'`, `:966` `'Monthly limit reached'`; 1ª entrega devolvida (só o `ApiUsageCard`, que só `DashboardScreen` usa); vermelho da HUD: `HudNotchTest.kt:311` mesmo texto ausente no balão, `ApiUsageCardTextsTest.kt:87` "reinício ainda aparece" na tooltip; verde: `gradlew.bat allTests koverXmlReport -Pcoverage` → 4221 testes, 0 falhas; `diff-coverage.mjs --base origin/main` → 32/32 linhas (100%); relatório de testes sem portão em falha, Kotlin 95,2%. `INLINE_LANGUAGE_CHECKS_CEILING` 693 → 691 (a tooltip saiu do `== AppLanguage.PT`). Protótipo (card e balão 3 da HUD) e `pattern-quota-row.html` registram o estado | verde |

## Problemas em aberto e riscos

- `spend_limit_reached:true` sem amostra real: a forma vem do DTO e da issue de origem.
- Outros valores de `disabled_reason` desconhecidos seguem escondidos.
