# Imagens antes/depois das telas no PR — issue #651

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `ci/issue-651-before-after` (worktree `.claude/worktrees/before-after-651`, base `origin/main` `b527c552`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Objetivo

O PR mostra quais telas mudaram e o antes/depois de cada uma, para revisar imagem em vez de código. Informativo.

## Fatos confirmados (2026-11-24, `b527c552`)

- `gradlew.bat generateScreenshots -PscreenshotOutputDir=…` três vezes seguidas, Windows 11: 18 arquivos, **18 iguais byte
  a byte** nas três (`sha256sum`); 67 s, 52 s e 49 s. Em Linux não foi medido.
- Cenas (`ScreenshotGenerator.kt`): dashboard, hud, hud-rest, new-integrations, history, settings, theme-presets,
  settings-team, cli-sessions, cli-breakdown, cli-session-detail, codex-session-detail, team-usage, team-trend, presence
  (claro e escuro), presence-accounts e `hud.gif`. O CI não chama a task.

## Cenas × protótipo (`prototipo-visual-opencode.html`)

Sem cena: Comparação (9c), Análise (9d), Administração de chaves (11), Configurações › Alertas (12b), Telegram (12b-2),
APIs (12c), Contas (12d), Acesso web (12g), Relatório de bug (12f), Ajuda (14b), Guia (14c), Dica (14d), Novidades (4d),
faixa de atualização (4c) e, da HUD, buraco negro, modo flutuante e as quatro bordas. O relatório diz que cobre 17 cenas:
"nenhuma tela mudou" vale só para elas. Acrescentar cenas é outra issue.

## Decisões

- **Base e PR no mesmo runner e no mesmo job**, base = `HEAD^1` do merge commit, em worktree à parte. Sem referência
  versionada: imagem velha aprovada por engano não existe.
- **Igualdade de bytes primeiro**; diferente, decodifica o PNG (zlib do Node, sem dependência) e conta pixels e caixa.
- **Estados:** `igual`, `mudou`, `nova`, `removida`. Erro ao gerar a base ou o PR é erro do relatório, nunca "igual".
- **Saída:** tabela no resumo do run e artefato `screen-diff` com `index.html` (antes, depois, diferença).
- **Informativo:** `continue-on-error` e fora do `ci-ok`; só em `pull_request` com área `desktop`.

## Atividades

- **A01** — este plano e a medição do determinismo.
- **A02** — `tools/ci/screen-diff.mjs` em TDD.
- **A03** — job no `ci.yml`.
- **A04** — prova no próprio PR: commit temporário que muda um token e acusa as telas que o usam, desfeito em seguida.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `generateScreenshots` ×3 e `sha256sum` | 18/18 iguais no Windows 11 |
| A02 | `node --test "tools/ci/test/screen-diff.test.mjs"` | vermelho (stub): `Expected values to be strictly deep-equal` e `The input did not match the regular expression /\| \`b\.png\` \| mudou \| 1 \| 1,2 1x1 \|/`; verde 9/9 e 71/71 na pasta, em execução independente. CLI sobre `img/*.png` com um bloco 60×40 alterado: só `dashboard.png`, 2.400 pixels, caixa `20,20 60x40` |
| A03 | `yaml.safe_load` | job `screen-diff` lido, fora de `ci-ok.needs`; o run é a A04 |

## Problemas em aberto e riscos

- O gerador espera com `Thread.sleep`; runner lento pode capturar antes de assentar e acusar mudança falsa. A prova em A04
  inclui um PR sem mudança visual, que tem de dar 17 `igual`.
- Offscreen não vê a janela nativa; isso é da #657.
