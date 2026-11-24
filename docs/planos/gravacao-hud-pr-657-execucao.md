# Gravar o app real em todo PR de HUD — issue #657

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `ci/issue-657-hud-recording` (worktree `.claude/worktrees/hud-recording-657`, base `origin/main` `889aca1f`),
commits com a identidade `claude <claude@anthropic.com>`. Parte da #644; continua a #652.

## Objetivo

PR que mexe na HUD ou em janela grava o app sozinho, em mais cenários que "parado no modo flutuante". Informativo.

## Fatos confirmados (2026-11-24, `889aca1f`)

- #652: `real-app.yml` manual; 15/15 vereditos certos; 121–264 s por job que compila, 65–88 s por tag; ~9,4 quadros/s.
- `record.ps1` grava um cenário por execução; `seed.ps1` fixa `hudFloating` e `hudAutoRetract`.
- Chaves de posição: `hudEdge`, `hudEdgeOffset`, `hudMinimizedAccounts`, `hudFloatX/Y` (`HudWindowPreferences.kt`).
- Um workflow próprio já fica fora do `ci-ok` sem tocar no `ci.yml`.

## Decisões

- **Gatilho `pull_request` com `paths` no próprio `real-app.yml`:** `src/desktopMain/**/Hud*.kt`, `**/*Window*.kt`,
  `**/*Frame*.kt`, `**/presentation/ui/Hud*.kt`, `src/commonMain/**/AppGargantua*.kt`, `**/Hud*.kt` do `commonMain`,
  `tools/real-app/**`, `tools/ci/hud-pulses.mjs` e o próprio workflow. Caso `source`, 1 tentativa.
- **Uma compilação, vários cenários** no mesmo job, em sequência, cada um com pasta de dados e saída próprias.
- **Cada cenário tem a invariante escrita antes:**

| Cenário | Entrada | Invariante |
|---|---|---|
| `floating-idle` | ponteiro parado | nenhum pulso |
| `docked-<borda>-idle` (4) | ponteiro parado, auto-recolher ligado | nenhum pulso |
| `docked-top-hover` | ponteiro entra no notch e sai | exatamente 1 pulso, que abre até 1,5 s depois de entrar e fecha até 2,5 s depois de sair |

- Arrasto e buraco negro ficam para a A04: dependem de achar o alvo na tela, e o hover prova primeiro se a entrada
  sintética chega ao app.
- **Prova por cenário:** um defeito conhecido ou plantado reprovado, e a `main` aprovada, 5 vezes cada.

## Atividades

- **A01** — este plano.
- **A02** — detector: modo `--expect hover`, em TDD.
- **A03** — cenários em `seed.ps1`/`record.ps1`, laço no workflow e gatilho de PR; 5 rodadas da `main`.
- **A04** — arrasto e buraco negro.
- **A04, invariantes escritas antes (branch `ci/issue-657-drag-hole`, base `35285e34`):**
  - `floating-drag`: pressiona o buraco, anda (-200, +120) px em 10 passos, solta e sai. A HUD termina recolhida (repouso
    ±10% do inicial) e o centro da caixa de repouso andou (-200, +120) ±8 px. Abertura durante o arrasto é permitida.
  - `docked-top-hole-idle`: três contas, duas minimizadas, sem auto-recolher, ponteiro parado: nenhum pulso.
  - `docked-top-hole-hover`: o mesmo, ponteiro no buraco e sai: exatamente 1 pulso, nos prazos do hover.
- **A05** — prova com defeito plantado por cenário; critério para bloquear.

## A05 — defeito plantado e critério (branch `ci/issue-657-planted-defects`, base `4b784c4a`)

**Correção da A04.** Nas gravações dos cenários do buraco, o buraco nem sempre estava na tela, e o veredito saiu `ok` assim mesmo:
`docked-top-hole-hover` em 4 das 10 do run 45484824806 (1 `source`, 3 `beta.15`) e `docked-top-hole-idle` em 1 das 3 gravações de PR
(run 45486243792). Repouso 239×80 px em vez de 357×106. Esses `ok` não valem como prova do buraco.

- **Causa (lida no código, não reproduzida por teste):** `CURSOR` e `ANTIGRAVITY` sem credencial falham como erro de configuração, e
  `statsRetainedAfterFailure` (`DashboardViewModelSupport.kt:117`) apaga a leitura. O arranque lê o cache e coleta ao mesmo tempo
  (`DashboardViewModel.kt`, `init`): se a falha chega antes do cache, a conta fica na tela; se chega depois, some. Candidato a defeito do
  app, fora desta atividade.
- **Correção do cenário:** a semente grava `nextRefreshAtMillis` no futuro (nos cenários do buraco), e o arranque não coleta.
- **Guarda de cena:** o detector recusa com `wrong-scene` o cenário cujo repouso é mais estreito que o mínimo (300 px no buraco).
- **Margem:** a lista do buraco sobe o repouso só 1,56 a 1,60×, contra o limiar de 1,5. Nos cenários do buraco o limiar passa a 1,25
  (o ruído do repouso é de 0,6%).

**Defeitos plantados**, por substituição exata de texto no código compilado só no runner (`tools/real-app/defects.json`):

| Defeito | O que quebra | Veredito exigido |
|---|---|---|
| `drag-not-kept` | a posição solta não é guardada | `floating-drag`: `not-moved` |
| `drag-half-way` | o arrasto anda metade no eixo X | `floating-drag`: `wrong-place` |
| `hover-stays-open` | o painel não fecha ao sair | `docked-top-hover` e `docked-top-hole-hover`: `not-closed`; `floating-drag`: `not-closed` |
| `hover-opens-late` | o painel abre 2 s atrasado | `docked-top-hover` e `docked-top-hole-hover`: `opened-late` |
| `hole-ignores-hover` | a lista do buraco não abre no hover | `docked-top-hole-hover`: `not-opened` |
| `hole-opens-alone` | a lista do buraco abre sozinha | `docked-top-hole-idle`: `pulsed`; `docked-top-hole-hover`: `extra-pulse` |
| `opens-and-stays` | o painel abre no arranque e não fecha | os 6 cenários parados: `open-at-rest` |
| `opens-late-and-stays` | o painel abre 6 s depois e não fecha | os 6 cenários parados: `pulsed` |

**Critério para virar bloqueante, escrito antes de medir:**

1. Cada um dos 9 cenários reprova pelo menos um defeito em 5 de 5 (os 6 antigos já: `beta.15`, 30/30).
2. Nenhum veredito errado em 30 execuções completas do `source` (270 gravações). Zero em 30 dá no máximo 9,5% por execução, com 95% de
   confiança: sozinho não basta para bloquear.
3. Por isso, ao bloquear, o cenário só reprova se falhar em **duas** gravações seguidas (até 0,9%, se os alarmes forem independentes —
   hipótese). O defeito plantado também precisa reprovar nas duas.
4. `not-visible`, `wrong-scene` e falta de gravação são "não medido": avisam e nunca bloqueiam. Acima de 1 em 30, volta a ser informativo.
5. Ligar o bloqueio é decisão do dono.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness de teste (plano) | conferido contra `real-app.yml` e `HudWindowPreferences.kt` |
| A02 | `node --test "tools/ci/test/hud-pulses.test.mjs"` | vermelho (stub): `actual: 'ok', expected: 'not-opened'`, idem `opened-late`, `not-closed`, `extra-pulse` e `not-visible`; verde 25/25, em execução independente; os 16 casos anteriores sem mudança |
| A03 | run [45477540049](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45477540049) (PR, `819610a0`) | 6/6 `ok`, HUD visível nos 6 (repouso 1.069–1.420 px); job em 5 min 54 s com a compilação. Entrada sintética (`SendInput`) chega ao app |
| A05 (parcial) | run [45478731870](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45478731870) | **60/60 vereditos certos:** `source` 30/30 `ok`; `v42.3.0-beta.15` 30/30 reprovada nos 6 cenários (25 `pulsed`, 5 `extra-pulse`). Nenhuma das 60 com tela vazia; hover abre em 694–1.111 ms e fecha em 367–531 ms. Em 2 gravações o detector viu 2 dos 3 pulsos: é o ponto cego dos ~9,4 quadros/s. Os 5 jobs da `beta.15` saíram vermelhos porque o passo herdou o código do detector; `exit 0` explícito e esperado por cenário fixados |
| A04 (detector) | `node --test "tools/ci/test/hud-pulses.test.mjs"` | vermelho (stub): `actual: 'ok'` contra `expected: 'not-moved'`, `'wrong-place'`, `'not-closed'` e `'not-visible'`; verde 38/38, em execução independente. |
| A04 (cenários) | `Parser::ParseFile` nos dois scripts; `seed.ps1` local em `docked-top-hole-idle`, `floating-idle`, `floating-drag`; `python yaml.safe_load`; `node --test "tools/ci/test/hud-pulses.test.mjs"` | sem harness para PowerShell: 0 erros de parse; 3 contas e `hudMinimizedAccounts=CURSOR,ANTIGRAVITY` só nos cenários do buraco, sementes antigas iguais; YAML válido com 9 cenários; 38/38. **Falta:** o run no PR e a prova com defeito plantado (A05) |
| A04 (1º run) | run [45483354886](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45483354886) (PR #667, `ec795b3d`) | 8/9: `floating-drag` deu `wrong-place`. Andou (-180, +108): falta exatamente o 1º passo de (-20, +12). `HudDragState.begin` lê o ponteiro já depois do 1º evento e descarta o delta dele; com mouse real é ~1 px. O roteiro ganhou um 1º evento de 1 px; **invariante e tolerância não mudaram**. `docked-top-hole-hover` conferido no quadro `013776.jpg`: lista "2 minimizadas" aberta |
| A04 (2º run) | runs [45484820746](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45484820746) (PR, `9896de81`) e [45484824806](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45484824806) (manual, 5×) | PR 9/9 `ok`. `source` **15/15 `ok`**; o arrasto pousou em (-199, +119) nas 10 gravações (1 px do 1º evento, como previsto), repouso 0,994–1,001 do inicial. `v42.3.0-beta.15`: arrasto 5/5 `ok` e **buraco 10/10 `ok`, contra `pulsed`/`extra-pulse` esperados**: os cenários do buraco não veem o defeito dela, que depende do auto-recolher, desligado ali; esperado corrigido para `ok`. **Nenhum dos 3 cenários novos reprovou um defeito ainda:** é a A05, com defeito plantado |
| A05 (plano) | sem harness (plano); `frames.csv` das 26 gravações do buraco lidos um a um | 5 de 26 gravações do buraco sem o buraco na tela; correção, defeitos e critério acima |
| A05 (ferramentas) | `node --test "tools/ci/test/*.test.mjs"`; `plant-defect.mjs --root` numa cópia dos 4 `.kt`; `Parser::ParseInput` nos 8 passos `pwsh` | vermelho: guarda de cena `actual: 2, expected: 5` (7 de 46), plantador com stub `actual: 'a = 1
b = 2
', expected: 'a = 9
b = 2
'` (12 de 12); verde 46/46 e 12/12, 208/208 na pasta, em execução independente. 6 defeitos plantados com saída 0. Semente e workflow sem harness: devolvidos 1x (`"$scenario: …"` não fazia parse); 0 erros. Cobertura do diff: `tools/` fora do Kover e do lcov |
| A05 (repouso) | run [45490240256](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/45490240256) (`3d867d37`); `node --test "tools/ci/test/*.test.mjs"`; rejulgamento das gravações guardadas | run 40/50: `hover-stays-open` deu `not-opened` em 10/10 de hover. O repouso era a mediana da gravação inteira e, com o painel preso aberto (160 de 223 quadros), virou o estado aberto (13.651 contra 1.376). Cenário parado com a HUD aberta sairia `ok`. Corrigido: repouso antes do hover, p5 nos parados, teto `open-at-rest`. Vermelho `:398 actual: 'not-opened', expected: 'not-closed'`, `:403 actual: 13000, expected: 1000`, `:419 :426 :433 actual: 2`; verde 54/54, 216/216 na pasta. Rejulgado: defeitos **50/50**; 60 antigas sem mudança; `wrong-scene` acusou exatamente as 5 gravações sem buraco. O commit `48a32e7c` saiu com `defects.json` inválido (1 de 205 testes vermelho, run 45492474236 cancelado); corrigido no seguinte |

## Problemas em aberto e riscos

- Entrada sintética: chega ao Compose (A03).
- `not-opened`, `opened-late` e `not-closed` só têm prova no teste do detector; nenhuma versão do app com esse defeito foi gravada.
- Arrasto: o buraco fica atrás do ponteiro pelo tamanho do 1º evento. Arranque rápido pode mostrar isso; não medido com mouse real.
- Depois de soltar e sair, o painel abre e fecha sozinho: no 1º run abriu 191 ms **depois** da saída do ponteiro, e nas 10 gravações
  do 2º a abertura apareceu 330–592 ms depois de soltar e durou até 748–1.125 ms. Entrada sintética, sem invariante escrita: não foi
  tratado como defeito nem conferido com mouse real.
