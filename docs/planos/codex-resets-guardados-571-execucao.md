# Resets de limite guardados no Codex — issue #571

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`feat/issue-571-codex-banked-resets`, base `origin/main` (`93e19ecf`), commits com a identidade
`claude <claude@anthropic.com>`. Implementação pelos agentes `backend-dev` e `frontend-dev` (modelo do frontmatter em
`.claude/agents/`). Origem: akitaonrails/ai-usagebar#466.

## Objetivo

Mostrar ao usuário do Codex quantos resets de limite a conta tem guardados, inclusive "nenhum", sem inventar o que a API
não diz.

## Fatos confirmados

- Resposta real de `GET https://chatgpt.com/backend-api/wham/usage` (conta Plus, `node codex.mjs` no scratchpad,
  2026-11-12), chaves de topo: `plan_type`, `rate_limit`, `code_review_rate_limit`, `additional_rate_limits`,
  `model_usage`, `credits`, `spend_control`, `rate_limit_reached_type`, `promo`, `rate_limit_reset_credits`.
  Bloco: `"rate_limit_reset_credits":{"available_count":1,"applicable_available_count":0}`.
- ai-usagebar (`src/openai/types.rs`, lido via `gh api`): lê `available_count` e uma lista opcional `credits[]`
  (`status`, `title`, `expires_at`). A conta real acima não traz `credits[]`.
- `CodexUsageDto.kt:21-35`: só `rate_limit` é lido; `rate_limit_reset_credits` não existe em `src/`.

## Decisões

- Exibe só `available_count`. Bloco ausente → `null` (não medido, nada na tela); `0` → "nenhum" explícito.
- `applicable_available_count` não é exibido: semântica não documentada e a amostra (1 disponível, 0 aplicável) não
  basta para nomeá-la.
- `credits[]` com validade fica fora: sem amostra real.
- Campo novo com default em `ApiUsageStats`, persistido no cache; nenhum valor novo em enum existente.
- Superfície: balão da HUD (única de visualização). Direção visual escolhida pelo usuário entre protótipos.

## Atividades

- A01 — este plano.
- A02 — protótipos em `prototipos/issue-571/`; usuário escolhe.
- A03 — data/domain: leitura do bloco, campo em `ApiUsageStats`, cache.
- A04 — balão da HUD na direção escolhida; protótipo e design system no mesmo commit.

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito com os fatos acima | — |
| A02 | três protótipos em `prototipos/issue-571/` (`a-rodape`, `b-linha-propria`, `c-junto-a-cota`), capturados com Edge headless e conferidos por screenshot; sem harness (pasta não versionada). Escolha do usuário em 2026-11-12: **B — linha própria** ("Resets de limite", indicador ponto + palavra: "1 disponível" ok, "nenhum" off; bloco ausente sem linha) | escolhido B |
| A03 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.CodexMapperTest" --tests "com.usagemonitor.data.DashboardCacheMapperTest"` → 3 falhas de asserção (`CodexMapperTest.kt:101`, `:110` esperado 1, obtido `null`; `DashboardCacheMapperTest.kt:34` ida-e-volta) com o campo em stub; verde: `desktopTest --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.domain.*"` → BUILD SUCCESSFUL, 0 falhas. `ApiUsageStats.bankedResetCount: Int? = null`; `CodexRepositoryImpl` intocado (o `.copy()` preserva o campo) | verde |
| A04 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest"` → 4 falhas de asserção (`HudNotchTest.kt:1463` `Expected exactly '1' node … 'Resets de limite'`, `:1470` '2 disponíveis', `:1476` 'nenhum', `:1482` 'Rate-limit resets'); ligação: `HudModelTest` `expected:<1> but was:<null>`; altura: `HudNotchGeometryTest` `expected:<26.0.dp> but was:<0.0.dp>`; verde: `gradlew.bat allTests koverXmlReport -Pcoverage` → 4235 testes, 0 falhas; `diff-coverage.mjs --base origin/main` → 14/14 linhas (100%); relatório sem portão em falha, Kotlin 95,2%. `hudBalloonHeight` soma a linha quando há contagem (a janela reserva o balão mais alto, `docs/hud-notch.md`). Protótipo: CSS `.nbreset`, nota e balão "3d" do Codex (renderizado no Edge headless); `AppHudBar.prompt.md` atualizado | verde |

## Problemas em aberto e riscos

- Endpoint não documentado: o bloco pode sumir ou mudar de forma; ausência degrada para `null`.
