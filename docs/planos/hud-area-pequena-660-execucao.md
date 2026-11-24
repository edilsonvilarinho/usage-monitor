# HUD em tela muito pequena para a escala — issue #660

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `test/issue-660-small-area` (worktree `.claude/worktrees/hud-exception-660`, base `origin/main` `5a687c4e`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Decisão do dono (2026-11-24)

Opção A: escrever a exceção em `docs/hud-notch.md` e conferir a regra só quando o conteúdo cabe. A geometria não muda.

## Decisões

- **A exceção não é um número de tela:** a regra vale quando o tamanho que a geometria pede cabe na área útil. Janela
  encaixada: `sizes.expanded` cabe na área nos dois eixos. Alças: `sizes.withHandles` cabe ao longo da borda.
- O teste pula só o cenário que não cabe **e conta quantos pulou**: se mais de 25% não cabem, o teste reprova, para o
  gerador não virar vazio sem ninguém ver.

## Atividades

- **A01** — plano. **A02** — os dois testes sem `@Ignore`, com a condição e a contagem. **A03** — `docs/hud-notch.md`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | decisão do dono registrada |
