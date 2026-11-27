# Descoberta de perfis `~/.claude_<nome>` — issue #691

Procedência: Claude Opus 5.5 (orquestração), implementação por `backend-dev`, Claude Code CLI no Windows 11,
2026-11-27, branch `feat/issue-691-claude-underscore-profiles`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Perfil Claude Code guardado em `~/.claude_<nome>` (grafia comum em aliases de `CLAUDE_CONFIG_DIR`) passa a ser
descoberto como `DISCOVERED`, igual a `~/.claude-<nome>`.

## Fatos confirmados (2026-11-27, `6ff7ef9b`)

- `AnthropicProfileRegistry.kt:312-318` (`sed -n 280,320p`): filtra só `name.startsWith(".claude-")`; rótulo é
  `removePrefix(".claude-")`.
- `AnthropicProfileRegistry.kt:285`: `add()` já descarta diretório não-`DEFAULT` sem `.credentials.json` nem
  `.claude.json` (`containsAnthropicFiles`) — a mesma regra vale para o novo prefixo sem código novo.
- `AnthropicProfileRegistry.kt:288-292`: id de perfil não padrão é `stableId(caminho absoluto)`; caminhos
  distintos não colidem no id. Rótulo colide: `.claude-x` e `.claude_x` dariam os dois `x`.
- `docs/integrations.md:45` documenta só `~/.claude-*`.

## Decisões

- Prefixos aceitos: `.claude-` e `.claude_`, mesma ordenação (`name.lowercase()`), mesma regra de arquivos.
- Colisão de rótulo: `-` ordena antes de `_`, então `.claude-x` mantém `x` (sem mudança para quem já usa) e
  `.claude_x` recebe o nome do diretório sem o ponto (`claude_x`). Determinístico e sem sufixo inventado.

## Atividades

| Id | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Descoberta `.claude_<nome>` + rótulo sem colisão + teste dos dois prefixos + `docs/integrations.md` |

## Ponto de situação

| Id | Comando | Resultado |
|---|---|---|
| A01 | `git log -1` | plano commitado (`0628d9e9`) |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.AnthropicProfileRegistryTest" --tests "*ArchitectureRulesTest*"` | 90 testes, 2 falhas: `AnthropicProfileRegistryTest.kt:261` `assertNotNull(work)` e `:296` `assertNotNull(underscore)` → `AssertionError` |
| A02 | verde: mesmo comando + `koverXmlReport -Pcoverage` (`test-runner`) | exit 0; `AnthropicProfileRegistryTest` 13/13, `ArchitectureRulesTest` 41/41, `Cancellation…` 13/13, `Prose…` 23/23. Teste de diretório `.claude_` vazio passa antes e depois: guarda de regressão, não vermelho |
| A02 | `node tools/ci/diff-coverage.mjs --base origin/main --kover build/reports/kover/report.xml` | 9/9 linhas cobertas (100%) |

## Problemas em aberto e riscos

- Não reproduzido com instalação real do Claude Code em `~/.claude_<nome>`; validação só por teste com diretório
  temporário.
