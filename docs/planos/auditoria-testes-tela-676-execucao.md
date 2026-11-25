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
| A02 (mutação antes) | `mutate.mjs` (troca um operador ou constante por vez e roda os testes do alvo), `a3a7388d` | mortos: `hud-pulses` **67,1%** (73 vivos), `screen-diff` 76,4% (66), `plant-defect` 83,3% (6), `ai-pilot` 79,3% (53), `ai-pilot-run` **55,0%** (49); o driver caiu por erro do script e fica para a medida de depois. Vivos que são furo: limiar de pulso 1,5 → 2,5; mínimo de quadros 2 → 3; janela antes do arrasto invertida; ruim e boa trocadas no executor; pago sem chave; janela da cobertura; canal de cor na comparação de telas |
| A03 (sensibilidade antes) | `inject.mjs`, 46 defeitos × 9 cenários × 6 gravações | pega 128 de 423 células, 293 passam (9 são a linha sem defeito) |
| A05a (cena estrita) | `node --test "tools/ci/test/*.test.mjs"`; `rejudge.mjs` em 805 gravações; `inject.mjs` de novo | vermelho: `idle 149`, `idle 1050`, `idle x +3`, `x +9`, `strictly unequal to: 2`; verde 321/321. Rejulgado com `--scene`: 270/270 `ok` e 155/155 plantados iguais; nas provas antigas mudaram só as 4 piscadas da #669 (`flash`, regra já existente). Injeção: pega **355** de 423, passam 68 (9 sem defeito, 54 dentro da abertura permitida do hover e do arrasto, 5 de arranque lento) |
| A05b (detector) | `mutate.mjs` depois; `hud-boundaries.test.mjs` (46) e 19 casos em `hud-pulses.test.mjs` | mutantes mortos **67,1% → 96,0%** (309/322). Restam 13: `return null` × `undefined` com chamador que trata igual, e a tolerância de 28,36 px, inatingível com inteiro. Os casos novos fixam comportamento existente: a prova é a mutação, não o vermelho |
| A05c (saúde e workflow) | `recording-health.test.mjs`; CLI nas 270 pastas reais; `yaml.safe_load`; `Parser::ParseFile` 8/8 | vermelho `actual: [ok, 0], expected: [app-exited, 1]`; `actual: ok, expected: app-error`; 270/270 `ok`. `source` reprova por queda, erro fora da lista ou sem dado; job que julga 0 cenário reprova; arquivos do piloto não disparam mais a gravação |
| A05e (defeitos novos) | `plant-defect.test.mjs` 14/14 (vermelho `:143`, `:164`); `compileKotlinDesktop` nas 3 cópias, saída 0; run [45668385648](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45668385648) | `hud-misplaced`, `hud-vanishes` e `hud-smaller`: **45/45** no app real, com o motivo medido (`x 434, esperado 394`). Os 12 defeitos: 137/137 julgados; 1 job sem julgamento (runner com `0xC0000142` em todos os passos, inclusive os do GitHub) |
| A05 (código bom) | run [45668390754](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45668390754), `88aa4a8e`, 15 × 9 | **135/135 `ok`** com cena estrita, saúde 135/135 `ok`, 1ª tentativa |
| A05f (comparação de telas) | `mutate.mjs`; `screen-diff.test.mjs` 33/33 | 76,4% → **95,9%** (10 equivalentes, 2 de CSS); arquivo não comparado e erro de decodificação aparecem e saem 2 |
| A05g (cloak) | PR #677 (`2dd23f18`), CI 45666637269, gravação 9/9 | vermelho em 9 asserções (`ModalPrewarmTest.kt:119 :138 :180 :194 :207 :236 :244 :252 :260`), verde 133/133, diff 95,7%. Janela ainda visível não perde o cloak; recusas vão para a trilha; `WindowsDwmCloakTest` pula fora do Windows |
| A05d (piloto) | `ai-pilot-{adversary,loop,replay,limits,window,main}.test.mjs`; replay das 11 transcrições pagas; `mutate.mjs` depois | motor 79,3% → **95,6%**, executor 55,0% → **92,3%**, driver 72,7% → **98,2%**. **Causa raiz do laço:** o `zoom` era limitado a meia tela (384 px) e a barra tem 430; toda chamada era inválida e nada barrava repetição de inválida. Agora os dois laços param em `stuck` (passos 31 e 11, eram 60) e nenhuma execução legítima teve ação recusada por repetição. A 1ª correção do agente passava no teste dele e falhava na transcrição real (tela diferente no teste). Sem chave em arquivo, saída ou processo filho (teste de ponta a ponta) |
| A05 (prova final) | run [45672620350](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45672620350), `509d298d`, 12 defeitos × 3 | 37/37 jobs, 1ª tentativa, **138/138** vereditos exatos, saúde `ok` em 138; `node --test "tools/ci/test/*.test.mjs"` 531/531 (eram 303) |

## O que continua sem prova

- **Conteúdo:** a gravação julga caixa, área e tempo; texto ou número errado com a mesma área passa. Estado aberto (hover, arrasto) só tem invariante de tempo.
- **Ambiente:** só `windows-latest`, 1024×768, 96 dpi, um monitor, sem GPU real. Linux e macOS não são gravados.
- **Janelas modais:** só a regra de flash; nenhuma é aberta e julgada.
- **Cola do `AppDialogWindow`/`Main.kt`:** fora do Kover, coberta só pela gravação.
- **Piloto:** o encanamento tem prova; que a IA acha defeito, **não**.
- **Suíte Compose** (71 arquivos em `desktopTest/.../ui`): não auditada.
