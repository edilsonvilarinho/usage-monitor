# Fluxo de trabalho sempre ativo e automatizado — issue #496

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`chore/issue-496-workflow-automation`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O dono do repositório precisava pedir o "Fluxo de trabalho" do `CLAUDE.md` a cada sessão, e o fluxo dependia do
agente esperar o CI e mesclar à mão. Com a cobertura de linhas em 95% (#488), a suíte passa a ser o portão
automático: o GitHub mescla quando o `ci-ok` passa, e o agente só corrige o que fica vermelho.

## Fatos confirmados (2026-10-28, `524009ef`)

- `gh api repos/edilsonvilarinho/usage-monitor/branches/main/protection`: `404 Branch not protected`; `rulesets`:
  lista vazia. O `ci-ok` era "o check que a proteção exige" só no comentário do `ci.yml`.
- `allow_auto_merge: false`, `delete_branch_on_merge: false`, repositório público e de usuário.
- `.claude/settings.json` do projeto não existe; nenhum hook. O fluxo existia só como texto no fim do `CLAUDE.md`.
- `build/reports/kover/report.xml` traz `<sourcefile name>` com `<line nr mi ci>` por linha (formato JaCoCo):
  dá para medir a cobertura das linhas que um PR muda sem ferramenta nova. O servidor só escreve
  `json-summary`/`html`; falta um formato por linha (`lcovonly`).
- `dependabot/fetch-metadata` v3.1.0 = `25dd0e34f4fe68f24cc83900b1fe3fe149efef98`; `package-ecosystem` sai do nome
  do branch (`src/dependabot/update_metadata.ts`, `chunks[1]`). Branches reais deste repositório:
  `dependabot/gradle/…`, `dependabot/npm_and_yarn/server/…`, `dependabot/github_actions/…`.

## Decisões

- **Escopo aprovado pelo dono (2026-10-28):** itens 3, 4, 5, 6, 7 e 8 da proposta, mais as regras de comunicação no
  `CLAUDE.md`. Recusados: hook `UserPromptSubmit` (1) e guarda `PreToolUse` (2) — o fluxo fica como regra do
  `CLAUDE.md`, com gatilho explícito "todo pedido que altera o repositório".
- **Ruleset, não proteção clássica:** `main` exige PR e o check `ci-ok` (do app GitHub Actions), bloqueia apagar e
  force-push; `strict` desligado (não força rebase). Bypass só para admin e **só por PR** (`bypass_mode:
  pull_request`): nem o `gh` autenticado como dono empurra direto na `main`.
- **Auto-merge:** `gh pr merge --auto --merge` na abertura do PR; `delete_branch_on_merge` apaga a branch remota.
  Sem o ruleset o auto-merge ou é recusado ou mescla sem CI — por isso os itens 3 e 4 entram juntos.
- **Incidências contadas, não lembradas:** `tools/ci/ci-incidents.mjs` soma runs vermelhos do `ci.yml` na branch
  (cada tentativa vermelha de um run reexecutado conta) e falhas locais registradas com `--local`, guardadas no
  diretório comum do git (vale para todas as worktrees). `cancelled` não conta: é o `concurrency` trocando run.
- **Cobertura do diff, piso 90%:** linhas de produção que o PR adiciona ou altera e que o relatório instrumenta. Linha
  fora do relatório (comentário, classe excluída por nome no Kover) não entra. Sem linha instrumentada, passa.
  Só em `pull_request` (no merge commit, `HEAD^1..HEAD` é o PR inteiro).
- **Dependabot:** merge automático só de `semver-minor`/`semver-patch` de `gradle` e `npm_and_yarn`. Major e
  `github_actions` continuam manuais — o `dependabot.yml` já diz que atualização de Action é para revisar e pinar.
- **Release continua manual.** Nada aqui publica versão.

## Atividades

| Atividade | O que faz |
|---|---|
| A01 | Este plano |
| A02 | `CLAUDE.md`: fluxo sempre ativo, issue sempre (plano ≤ 15 linhas), resposta no chat ≤ 15 linhas |
| A03 | `tools/ci/ci-incidents.mjs` + testes `node:test`; job `ci-scripts` no `ci.yml` |
| A04 | `tools/ci/diff-coverage.mjs` + testes; passos no `desktop-windows` e no `server`; `lcovonly` no vitest |
| A05 | `.github/workflows/dependabot-automerge.yml` |
| A06 | Skill `usage-monitor-workflow`; `CLAUDE.md` e `docs/build-and-release.md` apontam para ela |
| A07 | GitHub: ruleset da `main`, `allow_auto_merge`, `delete_branch_on_merge`; registro em `docs/build-and-release.md` |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos, decisões e atividades | `gh issue create` → #496; `gh api …/branches/main/protection` → 404; `gh api …/rulesets` → `[]` |
| A02 | `CLAUDE.md`, "Fluxo de trabalho": gatilho "todo pedido que altera o repositÃ³rio, sem o usuÃ¡rio citar o fluxo"; passo 1 cria issue quando nÃ£o hÃ¡ e pÃµe nela o plano em â‰¤ 15 linhas; regra de resposta no chat â‰¤ 15 linhas | `git diff --stat CLAUDE.md` â†’ 10 inserÃ§Ãµes, 7 remoÃ§Ãµes; sem cÃ³digo, sem teste a rodar |
| A03 | `tools/ci/ci-incidents.mjs` (runs vermelhos + tentativas anteriores reexecutadas + falhas locais no diretÃ³rio comum do git; saÃ­da 3 no limite) com 6 testes `node:test`; job `ci-scripts` e saÃ­da `ci_scripts` no `changes`, no `ci-ok`; passo 5 do `CLAUDE.md` e seÃ§Ã£o CI do `build-and-release.md` | `node --test "tools/ci/test/*.test.mjs"`: 6 testes, 0 falhas. Dados reais: `--branch feat/issue-440-terminal-config-service` â†’ 2 de 4, saÃ­da 0; com `--limit 2` â†’ saÃ­da 3; `--branch feat/398-399-settings-telegram` (run reexecutado, tentativa 2 verde) â†’ 1 incidÃªncia da tentativa 1. `node --test tools/ci/test/` (diretÃ³rio) falha no Node 22 local: trocado por glob e `setup-node` 22 no job |
| A04 | `tools/ci/diff-coverage.mjs` (diff `--unified=0` Ã— Kover `<line>` / lcov `DA:`; resolvedor por formato com `owns`/`lookup`) com 8 testes; passos `Diff coverage` informativos em `desktop-windows` e `server`, `fetch-depth: 2`; `lcovonly` no `server/vitest.config.ts`; `CLAUDE.md` e `build-and-release.md`. Defeito achado na validaÃ§Ã£o e corrigido em TDD: na mediÃ§Ã£o do servidor, arquivo Kotlin do diff contava como "fora do relatÃ³rio" | Vermelho: teste "cada formato sÃ³ conta os prÃ³prios arquivos" â†’ `expected 0, actual 4`. Verde: `node --test "tools/ci/test/*.test.mjs"` 14 testes, 0 falhas. Dados reais: `gradlew.bat allTests koverXmlReport -Pcoverage` (`desktopTest FROM-CACHE`, BUILD SUCCESSFUL); `--base e3e24a5a --kover â€¦` (= PR #495) â†’ 48 de 80 (60,0%), saÃ­da 1; `--base 524009ef~12` â†’ 529 de 585 (90,4%), saÃ­da 0; `npm run test:coverage` 313 testes, linhas 95,84%, `--base 7f379975~1 --lcov â€¦` â†’ sem linha instrumentada, 0 fora do relatÃ³rio, saÃ­da 0. Piso nasce informativo por decisÃ£o do dono (2026-10-28) |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| Merge feito pelo auto-merge do Dependabot (ativado com `GITHUB_TOKEN`) não dispara o `ci.yml` nem o CodeQL no push da `main`: evento de `GITHUB_TOKEN` não cria run. O PR já rodou a suíte no merge commit; o CodeQL semanal continua | aceito |
| Piso de 90% no diff: o PR #495 mediu 60% (48 de 80, cola de janela). Decisão do dono (2026-10-28): nasce **informativo**. Vira bloqueante quando 10 PRs seguidos com Kotlin alterado medirem ≥ 90%, ou por decisão do dono; a troca é tirar o `exit 0` do aviso nos dois passos | aberto |
