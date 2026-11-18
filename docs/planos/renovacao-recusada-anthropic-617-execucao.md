# Renovação OAuth recusada não pula a coleta de uso — issue #617

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-18, branch
`fix/issue-617-rejected-refresh`, base `origin/main` (`a3276141`), commits com a identidade
`claude <claude@anthropic.com>`. Implementação pelo agente `backend-dev` (modelo do frontmatter em `.claude/agents/`).
Origem: akitaonrails/ai-usagebar#637 (corrigida lá no PR #638).

## Objetivo

Quando o endpoint de token responde 4xx (exceto 429) — refresh token aposentado (`400 invalid_grant`) — e o access
token atual ainda não venceu, seguir com ele para o `GET /api/oauth/usage` em vez de lançar e ficar na leitura retida.

## Fatos confirmados

- `LocalCredentialDataSource.kt:30`: `REFRESH_MARGIN_MS = 5 min` — a renovação começa antes de o token vencer
  (`grep -n "REFRESH_MARGIN_MS =" -r src/desktopMain`).
- `LocalCredentialDataSource.kt:97-101`: `needsRefresh && refreshToken.isNotEmpty()` chama `refreshToken(...)`.
- `LocalCredentialDataSource.kt:203-211`: status não-2xx → `throwIfRateLimited` (429 tipado) e, senão,
  `IllegalStateException("Token refresh falhou (HTTP <status>): <body>")`. A sessão não sai e o GET de uso não acontece.
- `LocalCredentialDataSourceTest.kt:396-416`: o teste atual fixa esse lançamento para 400 com token a 60 s de vencer.
- `UiState.kt:219-220` e `:287`: "Token refresh falhou" e "HTTP 401" já são classificados como aviso de autenticação.

## Decisões

- 4xx ≠ 429 **com `expiresAt > agora`**: a sessão sai com o access token atual (o GET decide). Diferença do upstream,
  que segue em qualquer caso: com o token **já vencido** o GET falharia com certeza, e "Token refresh falhou (HTTP 400):
  …" é a causa mais útil na tela — a lição da #64 foi justamente a renovação falhar sem rastro.
- 429 continua lançando `RateLimitedException` (backoff da #269); 5xx continua lançando — uma queda do endpoint de token
  não acrescenta requisição de uso.
- Nada é regravado no ficheiro quando a renovação falha.

## Atividades

- A01 — este plano.
- A02 — TDD em `LocalCredentialDataSource` + teste + decisão em `docs/integrations.md` ("Renovação do token OAuth").

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (documento) | plano revisado contra `LocalCredentialDataSource.kt` em `a3276141` |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCredentialDataSourceTest"` | 22 testes, 1 falha: `rejected refresh token keeps the session while the access token is still valid` (`:460`) — `IllegalStateException: Token refresh falhou (HTTP 400): {"error":"invalid_grant",...}` |
| A02 | verde: mesmo comando | `BUILD SUCCESSFUL`, 22 testes, 0 falhas; dois testes de falha passaram a token vencido, novo teste do 5xx com token válido |

## Problemas em aberto e riscos

- Na janela de 5 min o POST de renovação é repetido a cada poll (comportamento já existente, não muda aqui).
- O caminho 4xx com token válido não grava trilha (`LocalCredentialDataSource` não tem `BreadcrumbRecorder`): a falha
  reaparece na tela quando o token vence, pelo erro "Token refresh falhou".
- Sem captura real de `invalid_grant` nesta máquina: o corpo usado no teste é o documentado na ai-usagebar#637.
