# Piloto de análise estática — issue #648

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-24, branch `chore/issue-648-lint-pilot` (worktree
`.claude/worktrees/lint-648`, base `origin/main` `889aca1f`), commits com a identidade `claude <claude@anthropic.com>`.
Parte da #644. Nada foi adotado: este documento é o relatório para a decisão do dono (A03 da issue).

## Como foi medido

| Ferramenta | Comando | Tempo |
|---|---|---|
| Compilador Kotlin 2.4.21 | `gradlew.bat compileKotlinDesktop compileTestKotlinDesktop --rerun --no-build-cache` | parte do build |
| detekt 1.23.8 (CLI, regras padrão, sem resolução de tipos) | `java -jar detekt-cli-1.23.8-all.jar --input src/commonMain/kotlin,src/desktopMain/kotlin` | 20 s |
| eslint 9 + typescript-eslint 8 (`recommendedTypeChecked`) | cópia de `server/` fora do repositório, `eslint src` | 5 s |

## Resultado

**Compilador:** 912 avisos, 75 em produção. Dos 75, 64 são API obsoleta (53 de `kotlinx-datetime`), e 11 são código
redundante. 752 dos de teste são um só: `runDesktopComposeUiTest` obsoleto. **Nenhum é defeito.**

**detekt:** 5.065 apontamentos. `MaxLineLength` 1.983, `MagicNumber` 1.498, `FunctionNaming` 393 (composables),
`LongParameterList` 302, `ReturnCount` 275, `LongMethod` 116, `CyclomaticComplexMethod` 104, `TooManyFunctions` 98: estilo
ou regra que o `ArchitectureRulesTest` já decide de outro jeito. Lidos caso a caso: `TooGenericExceptionCaught` (23),
`UnusedParameter` (27), `UnusedPrivateProperty` (5), `SwallowedException` (1), `ImplicitDefaultLocale` (1).

**eslint:** 3 apontamentos em 31 arquivos, nenhum defeito (parâmetro exigido pelo Express, `any` do zod, asserção à toa).
**`typescript-eslint` 8.74 não aceita o TypeScript 7 do servidor** (`peer typescript >=4.8.4 <6.1.0`); só rodou com o 5.9.3.

## Achados que podem ser defeito (lidos no código, não reproduzidos)

| Onde | O quê |
|---|---|
| `HistoryViewModel.kt:207` | `catch (Throwable)` em função suspensa sem relançar `CancellationException`: cancelar a leitura registra falha e pode publicar erro. É a regra da #598, num caso que o teste atual (`runCatching`) não vê |
| `AnthropicRepositoryImpl.kt:57` | o mesmo: cancelamento com a sessão trocada vira `ACCOUNT_CHANGED_DURING_FETCH` |
| `RemoteApiDataSource.kt:190` | cancelamento é gravado como `request_error` no diagnóstico do Codex, e depois relançado |
| `SettingsActions.kt:424` | `openTeamKeysManager(teamId)` ignora o time recebido; pode estar certo se o roteamento usa o time aberto |
| `UnusedParameter`, 26 demais | parâmetro morto; limpeza, sem efeito visível |

## Recomendação

1. **Não adotar detekt nem eslint agora.** O sinal é de 4 em 5.065, e o eslint com tipos não suporta a versão em uso.
2. **Virar teste a única regra que achou algo:** `catch (Throwable|Exception)` em função suspensa relança
   `CancellationException` antes. Precedente: a regra do `runCatching` da #598. Corrigir os 3 primeiros casos, com teste
   que reproduz cada um antes.
3. **Não ligar `allWarningsAsErrors`.** Com auto-merge do Dependabot, cada API obsoleta viraria PR vermelho. Zerar os 11
   redundantes e os 53 do `kotlinx-datetime` é limpeza de um PR.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | detekt, eslint | 5.065 e 3 apontamentos; 4 candidatos a defeito, nenhum reproduzido |
| A02 | compilador | 75 avisos em produção, 0 defeito; `allWarningsAsErrors` não recomendado |
| A03 | — | aguarda a decisão do dono |
