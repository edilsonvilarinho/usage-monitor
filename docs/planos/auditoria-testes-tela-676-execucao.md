# Auditoria dos testes de tela — issue #676

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-25,
branch `test/issue-676-screen-audit` (worktree `.claude/worktrees/screen-audit-676`, base `origin/main` `a3a7388d`),
commits com a identidade `claude <claude@anthropic.com>`. Pedido do dono depois da medida cancelada da #653.

## Objetivo

Saber o que cada teste de tela feito pelo Claude prova, e corrigir o que não prova. Régua: quebrar de propósito e ver se reprova.

## Fatos confirmados (2026-11-25, `a3a7388d`, gravações do run 45626890285)

- **Gravação, injeção em 54 gravações reais** (`inject.mjs`, 46 defeitos × 9 cenários). Passam como `ok`: HUD no lugar errado o tempo todo (9/9 cenários);
  some por 60 quadros (~6 s) e volta (9/9); some e só volta no último 0,5 s (9/9); anda 100 px sozinha (8/9); 45% do tamanho (7/9); 145% (7/9); pulso de 1 quadro
  até 4× (7 a 9); no arrasto, some para sempre. A "prova" 155/155 e 270/270 só cobria os defeitos que o próprio Claude escolheu.
- **A folga era injustificada:** em 270 gravações e mais de 48 mil quadros parados, a caixa varia 0 a 1 px, a área ±0,8%, e a HUD nunca some. O limite era +50% de área
  (+25% no buraco), qualquer posição.
- **Nunca são julgados:** saída do app (`record.ps1` grava em `probe.json` e ninguém lê), `app.log`, EDT parada, conteúdo.
- **Pode terminar verde sem medir:** defeito plantado sem expectativa para o cenário é pulado em silêncio (`real-app.yml:232`).
- **`screen-diff` (#651):** informativo, com `continue-on-error`; erro de decodificação some.
- **Cloak (#669):** a cola (`prewarmWindow`, o `OPEN`, `Main.kt`) não tem teste; `release()` recusado e `awaitHidden` no teto são ignorados; `WindowsDwmCloakTest` sai sem
  asserção fora do Windows e conta como verde no Linux.
- **Piloto (#653):** nada barra repetição; "imagens diferentes" não prova (a HUD anima); passo conta turno, não ação; `done-incomplete` e `max-steps` saem com 0;
  ninguém grava o `pilot-ledger.json`; controles contaminados por outros defeitos.

## Decisões

- **Cena por cenário** (`tools/real-app/scenes.json`): caixa e área do repouso medidas; todo quadro parado tem de ficar em ±2 px e ±2%. Só para `source`; versão
  publicada continua com os limites antigos. Mudança legítima da HUD atualiza a cena no mesmo PR.
- Os vereditos antigos continuam na frente: defeito plantado não muda de nome.
- O que não dá para medir vira `not measured` e nunca `ok`.

## Atividades

- **A01** plano e inventário. **A02** mutação. **A03** sensibilidade. **A04** piloto contra modelo adversário. **A05** correções, uma por commit.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `explorer` (inventário), `inject.mjs` e `base.mjs` sobre os artefatos | fatos acima |
