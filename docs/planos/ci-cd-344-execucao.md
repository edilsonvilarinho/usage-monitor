# CI/CD reorganizado e enxuto (#344) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (desktop) |
| **Data** | 2026-09-28 |
| **Issue** | [#344](https://github.com/edilsonvilarinho/usage-monitor/issues/344) |
| **Branch** | `ci/reorganize-pipeline-344`, criada de `main` (`8f26660`) |
| **PR alvo** | `main` |

## Contexto

A issue diz que o CI/CD "está muito desorganizado" e que, ao lançar um release, "antes de terminar
ele dispara actions de commits na main". Referências citadas: `akitaonrails/ai-usagebar` (CI por
push/PR com `concurrency`, release só por tag com job que confere tag × manifestos) e
`vinzdg/codenotch` (workflows por área com `paths`).

## Decisões do usuário

1. O repositório é **público**: runner padrão não consome cota de minutos. O alvo é **tempo de
   espera, fila e desorganização**, não minuto faturado.
2. **A versão vem da tag.** Sem commit de bump na `main`: o release deixa de disparar `CI` e
   `CodeQL` na `main` e de esperar por eles.

Isto reverte a decisão 1 da #299 (pular por árvore verificada em vez de proteger a `main`): o
esquema existia porque a `main` não tem proteção e o release dependia do CI da `main`. Com o release
independente e o check único `ci-ok`, o push na `main` volta a rodar a suíte uma vez — nenhum humano
espera por ele.

## Levantamento (runs de 28/09/2026)

| Fluxo | O que roda hoje | Medido |
|---|---|---|
| PR (cada push) | `CI`: gate, tests Win 7,0 min → tests-linux 2,7 min **em série**, installer Win, 3 containers do updater, verified-tree; `CI Server` sobe um runner mesmo sem `server/` | ~10 min de wall-clock (run `36438624931`); 9–10 jobs por push, mesmo em PR só de docs |
| Merge na `main` | `CI` pula a suíte pelo `gate`, mas sobe 2 runners Windows + 3 containers para dizer "pulei"; `CodeQL` 4,1 + 1,4 min | 7 jobs (runs `36423428488`, `36423428510`) |
| Release | bump direto na `main` → `CI` completo (tests 6,7 min) + `CodeQL`; a tag → `resolve-ci-gate` fica **7,0 min em polling** esperando o marcador daquele CI; builds Linux 4,3 / Win 3,6 / macOS 5,2 + 4,2 | runs `36424097873`, `36424098133`, `36424113853` — é o sintoma da issue |
| Cache | **11,96 GB de 10 GB**, 294 entradas. Cada tag grava ~0,9 GB (`setup-java cache: 'gradle'` + JDK no macOS) num ref que nenhum outro run lê | `gh api .../actions/cache/usage`, `gh cache list` |
| Dependabot | 6 PRs numa semana, cada um com o CI completo | runs de 28/09 03:26 |

## Atividades

- **A1** — tag para de gravar cache (`build-macos` e `codeql.yml` só leem).
- **A2** — versão vem da tag (`-PappVersion`, `git describe` como fallback local); release valida a
  tag (`verify-version`), roda o próprio `verify` em paralelo com os builds, sem `resolve-ci-gate`.
- **A3** — um `ci.yml` com job `changes` + jobs em paralelo + agregador `ci-ok`; `ci-server.yml`,
  `gate`, `verified-tree` e `release-gate-marker` saem.
- **A4** — `CodeQL` ignora docs; Dependabot agrupa por ecossistema.
- **A5** — proteção da `main` exigindo `ci-ok` (configuração do repositório, com aprovação).
- **A6** — depois da sequência verde da #342: Linux vira o gate do PR e o Windows roda por recorte.
  Fora desta issue.

## Pontos de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A1 — tag e CodeQL só leem cache | `python -c "yaml.safe_load(...)"` nos 4 workflows; `gh api .../actions/cache/usage` antes | YAML válido; cache em 11,96 GB / 294 entradas antes da mudança, das quais ~0,9 GB por tag (`setup-java-*-gradle-*` e `setup-java-jdk-*` em `refs/tags/v*`) |
| A2 — versão vem da tag | `gradlew.bat -q properties` (sem propriedade / `-PappVersion=v99.1.2` / `GIT_DIR=/nonexistent`); `generateAppVersionSource` com e sem `-PappVersion`; `gradlew.bat allTests`; YAML do release | `41.3.1` / `99.1.2` / `1.0.0` (com `0.0.0` o plugin do Compose recusa o Dmg na configuração, por isso `1.0.0`); `AppVersion.kt` regenera nas duas direções (sem o `inputs.property` ficaria velho); `BUILD SUCCESSFUL in 4m 45s`; YAML válido, `verify` e builds dependem só de `verify-version` |
| A3 — `ci.yml` único com `changes` + `ci-ok` | `python -c "yaml.safe_load(...)"`; `grep` por `ci-server`/`verified-tree`/`tests-linux` nos docs | YAML válido; 8 jobs (`changes`, `pricing-parity`, `desktop-windows`, `desktop-linux`, `installer-windows`, `linux-updater`, `server`, `ci-ok`), nenhum `needs` entre jobs de teste; referências antigas só em `docs/planos/`. Comportamento no GitHub verificado no run do PR (linha seguinte) |
