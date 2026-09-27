# Quebrar `runUsageMonitor` e impor regras de arquitetura (#298) — execução

## Contexto

[Issue #298](https://github.com/edilsonvilarinho/usage-monitor/issues/298): `runUsageMonitor`
(`Main.kt`) é um lambda `application {}` de ~2.400 linhas com a DI manual e o estado de todas as
janelas. O `CLAUDE.md` já registrava o `OutOfMemoryError` do ASM ao transformar o método inteiro. O
comentário do dono na issue amplia o pedido: nada de "super classes", as regras de clean code e de
arquitetura gravadas no `CLAUDE.md` e no `AGENTS.md`, e a arquitetura sempre respeitada.

Escopo aprovado:
- `Main.kt`;
- as violações de camada;
- um teste de arquitetura que imponha as regras.

Os demais arquivos grandes viram issues próprias.

## Fatos de partida

- `runUsageMonitor` tem 2.393 linhas pela varredura de chaves. Outras cinco funções de produção passam
  de 300 linhas:

  | Função | Linhas |
  |---|---|
  | `ApiUsageCard` | 483 |
  | `UsageHistoryLineChart` | 460 |
  | `HudNotch` | 348 |
  | `HudWindowHost` | 346 |
  | `TeamUsageList` | 327 |

- 16 arquivos de produção passam de 800 linhas.
- Havia 8 imports de `presentation` para `data`, contrariando `presentation → domain ← data`:
  - `UsageExportFormat`, em 5 arquivos;
  - `UsageExporter` e `CodexCliUsageExporter`, em `UsageExportRequests`;
  - `UPDATE_FEED_URL_ENV_VAR`, em `SettingsDialogContent`.
- O domain não importava infraestrutura nem outra camada. Havia só um link de KDoc para um DTO de
  `data`.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| M0 | Linha de base de compilação (local, daemon quente, 16 processadores) | `gradlew.bat compileKotlinDesktop --rerun-tasks --no-build-cache -Pkotlin.build.report.output=file` | 18,46 s de tarefa: análise 4,99 s, tradução IR 2,84 s, geração 8,71 s (lowering 2,48 s, geração IR 6,23 s), 71.269 linhas |
| M1 | Camadas: `UsageExportFormat` → `domain/entity`; porta `UsageExportEncoder` no domain, implementada por `DefaultUsageExportEncoder` em `data` e injetada nos dois view models de sessões e no retrato do Dashboard; `UPDATE_FEED_URL_ENV_VAR` → `domain/repository/AppUpdateRepository.kt`; link de KDoc do domain para `data` virou texto | `gradlew.bat desktopTest --tests "com.usagemonitor.data.*Export*" --tests "com.usagemonitor.presentation.CliSessionsViewModelTest" --tests "com.usagemonitor.presentation.CodexCliSessionsViewModelTest" --tests "com.usagemonitor.ui.CliSessionsScreenTest" --tests "com.usagemonitor.ui.SettingsDialogContentTest" --tests "com.usagemonitor.data.AppUpdateRepositoryImplTest"` | 146 testes, 0 falhas; zero imports de `data` em `presentation` e em `domain` |
