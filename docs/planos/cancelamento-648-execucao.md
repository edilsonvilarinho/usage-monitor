# Cancelamento engolido por `catch (Throwable)` — issue #648

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `fix/issue-648-cancellation` (worktree `.claude/worktrees/cancellation-648`, base `origin/main` `5a687c4e`),
commits com a identidade `claude <claude@anthropic.com>`. Continua `analise-estatica-648-execucao.md`. Parte da #644.

## Decisão do dono (2026-11-24)

Não adotar detekt nem eslint; virar teste a regra que achou algo e corrigir os 3 casos, cada um reproduzido antes.

## Fatos confirmados (leitura do código, `5a687c4e`)

- `HistoryViewModel.kt:207`, `AnthropicRepositoryImpl.kt:44,57`, `RemoteApiDataSource.kt:179,190`: `catch (Throwable)` em
  função suspensa sem `catch (CancellationException)` antes. Nenhum foi reproduzido.
- `OpenCodeGoRepositoryImpl.kt:38` relança tudo no fim: entra na regra por uniformidade, sem defeito.
- A regra da #598 no `ArchitectureRulesTest` só vê `runCatching`.

## Decisões

- **Regra:** em `suspend fun` de produção, `catch` de `Throwable` ou `Exception` exige, na mesma cadeia, um `catch` de
  `CancellationException` antes que relance. Lambda suspensa (`launch { }`) em função comum fica fora: o scanner mede `fun`.
- **Cada correção começa pelo teste que reproduz.** Caso que não reproduz não é corrigido como defeito: recebe só o `catch`
  que a regra pede, e o plano diz isso.
- `SettingsActions.openTeamKeysManager(teamId)` fica fora: precisa de prova de que o time errado abre.

## Atividades

- **A01** — plano. **A02** — `HistoryViewModel`. **A03** — `AnthropicRepositoryImpl`. **A04** — `RemoteApiDataSource`.
- **A05** — regra no `ArchitectureRulesTest` (arquivo `ProseArchitectureRulesTest` se a #646 já tiver mesclado) e os demais
  pontos que ela acusar.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | leitura do código | pontos acima |
| A02 | `gradlew.bat desktopTest --tests "*HistoryViewModelTest*" …` | **não reproduzido**: o teste passou sem o `catch`, porque `breadcrumbs.recordFailure` já relança o cancelamento antes de publicar. Teste mantido; `catch` só pela regra. Verde 12/12 |
| A03 | `gradlew.bat desktopTest --tests "*AnthropicRepository*" …` | **defeito reproduzido**: `AnthropicRepositoryImplTest.kt:143 AssertionError` (`assertNull(result)`): cancelar com a sessão trocada devolvia falha em vez de propagar. Verde 5/5. A `catch` de `loadAnthropicSession` fica para a A05 |
| A04 | `gradlew.bat desktopTest --tests "*RemoteApiDataSource*" …` | **defeito reproduzido**: `RemoteApiDataSourceCodexDiagnosticsTest.kt:123 AssertionError`: cancelamento gravava `request_error` no diagnóstico. Verde 5/5. A `catch` interna (`parse_error`) fica para a A05. Recorte das três: 83/83 |
| A05 | `gradlew.bat desktopTest --tests "*ArchitectureRulesTest*" …` | vermelho: `CancellationArchitectureRulesTest.kt:34 expected:<[load]> but was:<[]>`; devolvida 1x (ignorava `suspend fun` de expressão), novo vermelho `:112`, `:122`, `:136`, `:146`. A regra acusou **7 pontos** em 7 arquivos, corrigidos só com o `catch`; nos dois com transação, com o mesmo `rollback` da falha (sem ele o `finally` com `autoCommit = true` confirmaria o pendente). Estes 7 não têm teste que reproduza defeito. Verde 13/13 (0,5 s) e 178/178 no recorte, em execução independente. A lista dos 8 pontos da devolução foi conferida: só `LocalCodexCliSessionDataSource` violava |
