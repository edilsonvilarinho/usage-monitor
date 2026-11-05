# Instalação da skill orchestrate — issue #526

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-05, branch
`chore/issue-526-skill-orchestrate`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Disponibilizar neste repositório a skill `orchestrate` de `edilsonvilarinho/orchestrator`, que executa um pedido por
fluxo multiagente com portal local.

## Fatos confirmados (2026-11-05)

- Origem: `git clone --depth 1`, commit `2cec9f77605994f1e74ec4177a7fbeea1fd79e28`, licença MIT, 23 arquivos em
  `skills/orchestrate/` (285 KB). O README manda copiar a pasta para `<projeto>/.claude/skills/orchestrate/`.
- Requisito: Node 24; `node --version` local: `v24.22.2`.
- Auditoria de `setup/agentes.mjs`, `portal/start.mjs`, `portal/emit.mjs` e `portal/server.mjs` (leitura integral) e de
  `collector.mjs`, `web/index.html` e `web/portal-dados.js` (`Grep` de APIs de risco): sem dependência, sem acesso
  externo; o único `spawn` sobe o próprio servidor e abre o navegador. Servidor só em `127.0.0.1`, com checagem de `Host`.
- `.claude/escritorio/` não está ignorado (`git check-ignore` saiu 1); o README manda ignorá-lo.
- `grep` em `.github`, `tools/ci`, `build.gradle.kts` e `src/desktopTest`: só `ci.yml:635` cita `.claude/skills`, e
  para a skill de relatório. Nada varre a pasta nova.

## Decisões

- **Cópia fiel:** a pasta fica idêntica à origem, para que uma atualização seja nova cópia e `diff`. `LICENSE` entra
  junto, porque a origem a mantém só na raiz do repositório.
- **`agentes.mjs instalar` não roda aqui:** a detecção por manifesto daria `./gradlew test`, e a tarefa `test` não
  existe neste projeto (`CLAUDE.md`, "Commands"). Os agentes `explorer` e `test-runner` do projeto ficam como estão.

## Atividades

- A01: este plano.
- A02: copiar a skill e a `LICENSE`; ignorar `.claude/escritorio/`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (documento) | plano criado |

## Problemas em aberto e riscos

- **Regra em conflito:** a skill delega edição a agentes `haiku`; o `CLAUDE.md` diz que Neferpitou "não delega edição"
  e o `test-runner` do projeto é `sonnet`. Sem decisão do dono, o `CLAUDE.md` continua valendo.
- **Primeira execução:** `verificar` responde `PENDENTE`, porque só 2 dos 5 agentes existem no projeto. Escolher
  "instalar" cria `backend-dev`, `frontend-dev` e `prototype-dev` com o comando de teste errado, a corrigir à mão.
- O portal lê transcripts do Claude Code, cujo formato não é API pública.
