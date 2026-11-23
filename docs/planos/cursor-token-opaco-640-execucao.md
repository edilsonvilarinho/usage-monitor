# Cursor com token opaco — identificador da conta — issue #640

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-23, branch `fix/issue-640-cursor-opaque-token`,
commits com a identidade `claude <claude@anthropic.com>`. Implementação delegada ao `backend-dev`; testes ao
`test-runner`. Escopo reduzido escolhido pelo dono ("só a parte segura").

## Objetivo

Desde o Cursor IDE 3.27.12 o `cursorAuth/accessToken` deixou de ser JWT (opaco, prefixo `curs`). Sem
`cursorAuth/stripeMembershipAuthId`, o app não acha o identificador da conta e declara "sem sessão" com o usuário
logado. Esta entrega lê o identificador de `cursorAuth/cachedScopedProfile.authId` quando as duas fontes atuais faltam.
Não muda o transporte: se a rota recusar o token opaco, o card mostra a recusa, como já mostra hoje para sessão recusada.

## Fatos confirmados (2026-11-23, `d4061ac8`)

- `src/desktopMain/kotlin/com/usagemonitor/data/datasource/LocalCursorSessionDataSource.kt:43-44`: identificador =
  `cursorAuth/stripeMembershipAuthId`, senão o `sub` do JWT, senão `SIGNED_OUT`. Lido com `cat`.
- `cursorAuth/cachedScopedProfile` guarda `{"authId":"<issuer>|<userId>",…}`, o mesmo formato do `sub`: registrado pelo
  mantenedor do akitaonrails/ai-usagebar na #503 (commit `4a98814`), medido no macOS com Cursor 3.27.12.
- No mesmo relato, o cookie `WorkosCursorSessionToken` com o token opaco recebeu 401; o ai-usagebar resolveu caindo para
  a sessão do `cursor-agent` (commit `ffacb15c`). Transporte aceito pelo token opaco: desconhecido.
- Máquina do dono: Cursor de março/2026, token JWT (424 caracteres, 2 pontos, prefixo `eyJh`), sem
  `stripeMembershipAuthId` e sem `cachedScopedProfile` — medido com `sqlite3` read-only, sem imprimir valores.
- `DashboardScreenWarnings.kt:685`: `SIGNED_OUT` e `SESSION_REJECTED` têm o mesmo texto na tela; trocar o tipo da falha
  não muda o que o usuário lê.

## Decisões

- **Ordem do identificador:** `stripeMembershipAuthId` → `sub` do JWT → `cachedScopedProfile.authId`. O JWT continua
  preferido ao perfil em cache, como no ai-usagebar.
- `authId` vai inteiro, como o `sub` já vai hoje (formato `<issuer>|<userId>`).
- JSON inválido, sem `authId` ou com `authId` em branco: a leitura segue para `SIGNED_OUT`, como hoje. A checagem de
  caractere inseguro de cookie vale para o novo valor.
- Fora: fallback para `cursor-agent`, troca de transporte (Bearer), enum novo de falha (regra do `CLAUDE.md`).

## Atividades

- **A01** — este plano.
- **A02** — TDD em `LocalCursorSessionDataSourceTest`: vermelho com token opaco + `cachedScopedProfile` esperando o
  `authId`; casos de JSON inválido e de prioridade do JWT; código no data source; `docs/integrations.md`, seção Cursor.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `git worktree add .claude/worktrees/cursor-640 -b fix/issue-640-cursor-opaque-token origin/main` | plano escrito |
| A02 | vermelho: `gradlew.bat desktopTest --tests "*LocalCursorSessionDataSourceTest*" --tests "*ArchitectureRulesTest*"` | `opaque access token reads the account id from the cached scoped profile` FAILED: `CursorUsageException` (`SIGNED_OUT`) lançada em `LocalCursorSessionDataSource.kt:44` antes do `assertEquals` — o comportamento sob teste, não compilação; 37 testes, 1 falha |
| A02 | verde: mesmo comando | BUILD SUCCESSFUL; `LocalCursorSessionDataSourceTest` 9/9 (4 novos; só o caso opaco era vermelho, os de prioridade e JSON inválido já passavam), `ArchitectureRulesTest` 28/28 |

## Problemas em aberto e riscos

- Não verificado num Cursor ≥ 3.27.12 real: a chave e o formato vêm do relato externo. Se a rota recusar o token opaco,
  o card passa de "sem sessão" para a mesma faixa de sessão recusada; o problema de transporte segue aberto na #640.
