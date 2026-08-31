# OpenRouter como fonte de cota — execução

| | |
|---|---|
| **Modelo** | Claude Sonnet 5 |
| **Nível de esforço** | não configurado explicitamente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-30 |
| **Branch** | `feat/openrouter-integration-138` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

Issue: [#138](https://github.com/edilsonvilarinho/usage-monitor/issues/138).

O levantamento (endpoint, auth, mapeamento no domínio, acento visual) veio no corpo da issue e em
três comentários com teste real contra a API. Este documento registra a execução — código, testes,
design system — e é atualizado **no mesmo commit** de cada atividade que descreve.

## O que a issue já resolveu (não repetir aqui)

| Ponto | Decisão |
|---|---|
| Endpoint | `GET https://openrouter.ai/api/v1/credits`, chave normal (mesma de inferência) |
| Shape | `{ "data": { "total_credits": number, "total_usage": number } }` |
| Mapeamento | `UsageUnit.CURRENCY_USD`, `used = 0`, `total = total_credits - total_usage`, `hasKnownResetAt = false`, `periodEndAt = Instant.DISTANT_FUTURE` — mesmo padrão do saldo do DeepSeek |
| Acento | escuro `#FF6FA8` (6,80:1 vs surface escura), claro `#B23368` (5,77:1 vs surface clara), matiz ~335°, identidade própria (não reusa nenhum acento existente) |
| `/api/v1/key` | **descartado como fonte** — `limit`/`limit_remaining` são teto opcional por chave, ficam `null` mesmo com saldo real (`$5` testado) |

## Atividades

- **Não é cota a mais em fonte existente.** `ApiSource.OPENROUTER` é valor novo no enum — mesma
  exceção declarada de `OPENCODE_GO`/`AppUpdateSupport`: os `when` exaustivos sobre `ApiSource`
  são os pontos que a fonte nova precisa preencher, e o erro de compilação garante que nenhum
  ficou pra trás.
- **`requiresApiKey()` (`SettingsDialogContent.kt`) é uma cadeia de `==`, não um `when`.** Não quebra
  a compilação se o `ApiSource.OPENROUTER` for esquecido ali — é o único dos pontos de fiação que
  o compilador **não** garante. Checklist manual na A03.
- **Sem endpoint de teste dedicado (sandbox).** A validação contra API real usa a própria conta do
  usuário — mesmo risco já aceito para o `usage`/refresh da Anthropic e para o OpenCode Go: endpoint
  não versionado formalmente, shape pode mudar sem aviso.

### Execução — uma atividade, um commit

| # | Atividade | Arquivos principais |
|---|---|---|
| A01 | Enum, rótulos, acento e os `when` exaustivos | `ApiSourcePresentation.kt` (`displayName`, `isObservedActivitySource`), `AppAccents.kt`, `ApiUsageCardFormatting.kt` (`accentColorFor`), `docs/design-system/tokens/colors.css` |
| A02 | DTO, mapper, chamada HTTP e repositório | `OpenRouterCreditsDto.kt`, `OpenRouterMapper.kt`, `RemoteApiDataSource.kt` (`fetchOpenRouterCredits`), `OpenRouterRepository.kt` / `OpenRouterRepositoryImpl.kt` |
| A03 | Chave em `api-keys.json`, Configurações → APIs e fiação no `Main.kt` | `LocalApiKeyDataSource.kt` (`ApiKeySettings.openRouter`), `API_KEY_DEPENDENT_SOURCES`, `SettingsDialogContent.kt` (`requiresApiKey()` — checklist manual), DI em `Main.kt` |
| A04 | Testes de mapper, repositório, HTTP e chave | `OpenRouterMapperTest.kt`, `OpenRouterRepositoryImplTest.kt`, `RemoteApiDataSourceHttpTest`, `LocalApiKeyDataSourceTest.kt` |
| A05 | Testes de componente (card, aba APIs, diálogo de chave) | `ComponentTest.kt` |
| A06 | Suíte completa | `allTests` |
| A07 | Protótipo, design system e UI kit | `prototipo-visual-opencode.html`, `docs/design-system/readme.md` ("seis acentos, sete fontes" → oito), `ui_kits/desktop-app/*.jsx` |
| A08 | Fechamento da issue #138 | comentário de encerramento + `gh issue close` |

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A00 | Levantamento e decisão (issue #138) | testes reais contra `/api/v1/credits` e `/api/v1/key`, dois estados de conta (`$0` e `$5`) | Viabilidade confirmada, todos os pontos em aberto fechados |
| A01 | Enum `ApiSource.OPENROUTER`, `OpenRouterRepository`/`GetOpenRouterUsageUseCase` (domain, com default que falha — mesmo padrão do `OPENCODE_GO`), rótulo, acento (`#FF6FA8`/`#B23368`) e os cinco `when` exaustivos (`displayName`, `sourceLabelFromKey`, `warningActionFor`, `accentColorFor`, `fetchTarget`) | `gradlew.bat compileKotlinDesktop` | `BUILD SUCCESSFUL`. Nenhum call site de `DashboardViewModel(...)` quebrou — todos param no último argumento posicional obrigatório (`recordUsageSnapshot`) e nomeiam o resto |
| A02 | DTO (`OpenRouterCreditsDto`/`Response`), mapper (`OpenRouterMapper`, mesmo padrão do `DeepSeekMapper`: `used=0`, `total=total_credits-total_usage`, `hasKnownResetAt=false`), `RemoteApiDataSource.fetchOpenRouterCredits` e `OpenRouterRepositoryImpl` | `gradlew.bat compileKotlinDesktop` | `BUILD SUCCESSFUL` |
| A03 | Chave em `api-keys.json` (`ApiKeySettings.openRouter`, DTO, `toDomain`/`write`, `requireLocalKeySource`), `requiresApiKey()` em `SettingsDialogContent.kt` (risco A01/A02 fechado — era o único ponto sem `when` exaustivo), `API_KEY_DEPENDENT_SOURCES` e DI completa no `Main.kt` (repo, use case, `remember`) | `gradlew.bat compileTestKotlinDesktop` | `BUILD SUCCESSFUL` |
| A04 | `OpenRouterMapperTest` (4), `OpenRouterRepositoryImplTest` (4), `RemoteApiDataSourceHttpTest` (+2, 14 no total), `LocalApiKeyDataSourceTest` (8, com OpenRouter no round-trip e no `withoutKey`) | `gradlew.bat desktopTest --tests "com.usagemonitor.data.OpenRouter*" --tests "com.usagemonitor.data.RemoteApiDataSourceHttpTest" --tests "com.usagemonitor.data.LocalApiKeyDataSourceTest"` | `BUILD SUCCESSFUL`, 30 testes, 0 falhas |
| A05 | Card do saldo OpenRouter (mesmo desenho do DeepSeek), rótulo acessível do lápis de chave na aba APIs (`Gerenciar chave — OpenRouter`) | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.ComponentTest"` | `BUILD SUCCESSFUL`, 88 testes, 0 falhas |

_(demais linhas entram conforme cada atividade fecha)_

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| `requiresApiKey()` não é `when` exaustivo — pode esquecer `OPENROUTER` sem erro de compilação | fechado na A03 |
| Auth de `/api/v1/credits` com chave normal confirmada só empiricamente (2 testes, 1 conta) — sem doc oficial declarando isso contrato estável | aceito, mesmo risco já coberto para outras fontes não documentadas oficialmente (OpenCode Go, `usage` da Anthropic) |
| `total_credits`/`total_usage` testados só com `0` e `5` (inteiros) — shape com centavos/decimais e com `usage > 0` não verificado | aberto |
| `is_free_tier` não testado com uso real (só a transição `true → false` ao comprar crédito, sem nenhuma chamada de inferência feita) | aberto |
| Endpoint sem versionamento formal — pode mudar shape sem aviso | aceito |

## Desvios do plano e achados da execução

_(preenchido ao final da última atividade)_
