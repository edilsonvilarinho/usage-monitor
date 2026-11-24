# HUD em tela muito pequena para a escala — issue #660

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `test/issue-660-small-area` (worktree `.claude/worktrees/hud-exception-660`, base `origin/main` `5a687c4e`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644.

## Decisão do dono (2026-11-24)

Opção A: escrever a exceção em `docs/hud-notch.md` e conferir a regra só quando o conteúdo cabe. A geometria não muda.

## Decisões

- **A exceção não é um número de tela:** a regra vale quando o tamanho que a geometria pede cabe na área útil. Janela
  encaixada: `sizes.expanded` **mais a margem de sombra** (`HUD_SHADOW_MARGIN`, 16 dp: duas ao longo da borda, uma na outra
  dimensão). Alças: `sizes.withHandles` mais duas margens ao longo da borda. A 1ª versão deste plano esqueceu a margem, e o
  teste achou o caso entre os dois limites: alças de 412 dp numa borda de 419 dp, centro preso pela conta com margem (444 dp)
  e até 9 dp para fora. Está dentro da exceção aprovada: telas em que notch, alças e sombra não cabem.
- O teste pula só o cenário que não cabe **e conta quantos pulou**: se mais de 25% não cabem, o teste reprova, para o
  gerador não virar vazio sem ninguém ver.

## Atividades

- **A01** — plano. **A02** — os dois testes sem `@Ignore`, com a condição e a contagem. **A03** — `docs/hud-notch.md`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | decisão do dono registrada |
| A02 | `gradlew.bat desktopTest --tests "*HudGeometryPropertyTest*" --tests "*ArchitectureRulesTest*"` | vermelho sem `@Ignore`: alças `centro=2127.74 alcance=940.0 área=[1641.74, 2563.57]` e janela `300.44..648.44` fora de `300.44..644.43`; segundo vermelho com a condição sem a margem (444 > 418,98). Verde 9/9, 0 ignorados. Cenários fora da regra: 23,6% (janela) e 19,4% (alças), teto 25%. Mutações 2/2 reprovadas: sem o recorte à área (`:45`) e reserva sem alças (`:82`) |
| A03 | sem harness (`docs/hud-notch.md`) | exceção escrita com os dois casos medidos |
