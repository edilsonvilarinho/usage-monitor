# Pedido do terminal sem resposta (#460) e job `test-report` no rerun (#461)

## Objetivo

Duas falhas vistas no mesmo run do CI do PR #458 ([40026216169](https://github.com/edilsonvilarinho/usage-monitor/actions/runs/40026216169)),
tratadas numa branch só (`fix/terminal-config-and-test-report-460-461`):

- **#460:** `TerminalAccountsCommandTest` falhou uma vez no Windows: o `disable` pelo "serviço" do
  `TerminalConfigHarness` não teve resposta. O mesmo defeito, se for do canal, alcança a produção: um pedido do
  terminal engolido pelo app ou pelo serviço, e a saída dizendo que nada mudou.
- **#461:** o job `test-report` (a) some quando um portão bloqueia, justamente quando o HTML explicaria o motivo, e
  (b) num `rerun --failed` lê o artefato da tentativa anterior e bloqueia por um teste que passou.

## Fatos confirmados (antes de qualquer código)

- **O teste durou 6,5 s com `timeoutMillis = 3_000`:** duas esperas do `TerminalConfigClient.apply` (antes e depois
  do `withdraw` que devolveu `false`). O pedido saiu de `requests/` e a resposta nunca chegou.
- **O `system-err` do XML da tentativa 1 está vazio** (artefato `12533316472`, baixado nesta análise). Uma exceção
  que derrubasse a thread do coletor do harness imprimiria a pilha ali. Isso enfraquece a hipótese 2 da issue
  (thread morta) e deixa a 1 (pedido tirado e descartado em silêncio) como a mais provável, sem prova direta: não há
  Windows nesta máquina para repetir o teste em laço.
- **`LocalConfigRequestChannel.takePending`** renomeia para `processing/`, lê com `runCatching` e, se a leitura
  falhar, apaga o arquivo e devolve nada: o pedido some sem `reply`. No Windows, antivírus e indexador seguram por
  instantes um arquivo recém-movido (*sharing violation*).
- **`withdraw` é um `File.delete()`:** com o arquivo preso no Windows ele devolve `false` com o pedido ainda em
  `requests/`, e o cliente conclui que o coletor o tirou. Mesma saída de 2 × 3 s.
- **`serveConfigRequests` não protege nada:** exceção no `apply` ou no `reply` sobe. No harness derruba a thread; no
  `CollectorEngine` encerra a corrotina dos pedidos, e todo pedido seguinte fica sem resposta até reiniciar.
- **O texto diz "10 s"** (`TerminalConfigLabels.notAnswered`), fixo, e o limite real é o `timeoutMillis` do cliente.
- **#461:** o passo "Build report" roda em `bash -e`; `set -uo pipefail` não desliga o `-e`, e o gerador saindo com 1
  encerra o passo antes do resumo. "Upload report" não tem `if: always()`. O `download-artifact` por nome trouxe o
  `test-reports` da tentativa 1 (dois artefatos com o mesmo nome no run).
- **As ações fixadas já servem para baixar por id:** `upload-artifact` (`ea165f8`) tem a saída `artifact-id`, e o
  `download-artifact` (`d3f86a1` = v4.3.0) aceita `artifact-ids`. `kover-report` não é lido pelo `test-report` (o
  `report.xml` do Kover vai dentro do `test-reports`).

## Decisões

- **D1 · Pedido tirado é respondido, sempre.** `takePending` tenta a leitura algumas vezes (falha passageira do
  Windows); se ainda falhar, devolve o pedido sem conteúdo, e o coletor responde `ConfigRefusals.FAILED`
  ("unreadable request", o mesmo caminho do pedido de versão vizinha). Devolver à fila foi recusado: o arquivo já
  está preso, e um pedido que volta pode ser aplicado depois que o terminal desistiu.
- **D2 · `withdraw` e `reply` também tentam de novo** em falha passageira. `withdraw` só desiste quando o arquivo
  sumiu (o coletor o tirou); `reply` que falha depois da mudança aplicada faria o terminal dizer "nada mudou" sobre
  algo que mudou.
- **D3 · Um pedido que falha não derruba o laço.** `serveConfigRequests` isola cada pedido. O `apply` já não
  lança (o `ConfigApplier` converte a exceção em `FAILED`, e o `flush` é protegido; conferido na A03); quem lança é
  o `reply`, e essa falha vai para `onFailure` enquanto o próximo pedido segue. O `CollectorEngine` a anota pelo
  `recordFailure` (trilha `ERROR`, que vai ao `app.log`, #416, com a mensagem saneada); o harness imprime a pilha,
  que cai no `system-err` do XML do CI.
- **D4 · O texto do tempo esgotado vem do `timeoutMillis`.** `ConfigDelivery.NotAnswered` passa a levar o limite, e
  o rótulo diz os segundos arredondados para cima (nunca um número menor do que o esperado).
- **D5 · #461: o portão vira texto, não interrupção.** `status` capturado com `|| status=$?`; o passo só falha se o
  gerador não saiu com 0 nem 1 (uso errado ou quebra), e "Upload report" roda com `if: always()`. O job continua
  informativo, fora do `ci-ok`.
- **D6 · #461: download pelo id da tentativa**, e não por nome com sufixo. O nome `test-reports` é o que as pessoas
  baixam (`gh run download -n test-reports`, citado em vários planos) e o que o `release-linux.yml` também usa.
  `desktop-windows` e `server` expõem o `artifact-id` como saída do job; o `test-report` baixa por `artifact-ids`, e
  só quando o id existe (vazio baixaria **todos** os artefatos do run). Job que não roda de novo no rerun mantém a
  saída da tentativa em que rodou, que é a do artefato certo.

## Atividades

| Item | O quê |
| --- | --- |
| A01 | Este plano, em comentário nas issues #460 e #461 |
| A02 | #460, D1 e D2: `LocalConfigRequestChannel` com novas tentativas e pedido ilegível respondido; testes no `ConfigChannelTest` |
| A03 | #460, D3: `serveConfigRequests` isola cada pedido; `onFailure` na trilha do `CollectorEngine`; testes |
| A04 | #460, D4: texto do tempo esgotado pelo `timeoutMillis`; testes; conferir `cli/readme.md` e `build-and-release.md` |
| A05 | #461, D5 e D6: `ci.yml` (Build report, Upload report, download por id) e `build-and-release.md` |

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
| --- | --- | --- | --- | --- |
| A01 | 2026-10-23 | `docs: plan the terminal config channel and test-report fixes (#460, #461)` | concluída | Worktree `../usage-monitor-460-461` a partir da `main` (`8e284706`). `gh api …/artifacts/12533316472/zip`: XML de `TerminalAccountsCommandTest` com 1 falha, `system-err` vazio. `gh api` nos `action.yml` fixados: `artifact-id` (upload) e `artifact-ids` (download v4.3.0). Só documentação; nenhum teste rodado |
| A02 | 2026-10-23 | `fix(cli): answer every terminal request the collector takes (#460)` | concluída | `LocalConfigRequestChannel`: ler, apagar (`withdraw` e o pedido tirado) e responder tentam 5 vezes a 100 ms (`retrying`); `withdraw` só desiste quando o arquivo sumiu; `takePending` devolve `null` no conteúdo em vez de descartar, e `serveConfigRequests` responde `FAILED` ("unreadable request"). Funções de arquivo e pausa como parâmetro, só para o teste. `JAVA_HOME=~/.jdks/ms-21.0.12.1 bash ./gradlew desktopTest -Dorg.gradle.java.home=$JAVA_HOME --tests "com.usagemonitor.ConfigChannelTest" --tests "com.usagemonitor.terminal.*"`: 135 testes, 0 falhas (`ConfigChannelTest` 12, 4 novos: pedido ilegível respondido, leitura na 3ª tentativa, `withdraw` com arquivo preso e sem nova tentativa quando já tirado, resposta na 3ª tentativa e falha que sobe). Windows: só o CI |
| A03 | 2026-10-23 | `fix(cli): keep serving terminal requests after a reply fails (#460)` | concluída | `serveConfigRequests(onFailure)`: `reply` que lança não encerra o laço, e a mudança aplicada ainda chega ao `onApplied`. **Achado:** o `try` planejado em volta do `apply` seria código morto — `ConfigApplier.apply` já faz `runCatching` e `AppSettingsStore.flush` também; ficou só o do `reply`. `CollectorEngine` anota por `recordFailure("atender pedido do terminal")`; o `TerminalConfigHarness` imprime a pilha. `bash ./gradlew desktopTest -Dorg.gradle.java.home=$JAVA_HOME --tests "com.usagemonitor.ConfigChannelTest" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*"`: 154 testes, 0 falhas (2 novos: recusa seguida do próximo pedido; resposta que não grava, avisada, com as duas mudanças anunciadas) |
| A04 | 2026-10-23 | `fix(cli): say the reply timeout the terminal actually waited (#460)` | concluída | `ConfigDelivery.NotAnswered(collector, timeoutMillis)`; `TerminalConfigLabels.notAnswered` diz os segundos arredondados para cima. `cli/readme.md` (D1) e `build-and-release.md` dizem 10 s, que continua sendo o `REPLY_TIMEOUT_MILLIS` da produção: sem mudança. `bash ./gradlew desktopTest -Dorg.gradle.java.home=$JAVA_HOME --tests "com.usagemonitor.ConfigChannelTest" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.architecture.*"`: 297 testes, 0 falhas (`TerminalConfigLabelsTest` novo: 10 s, 3 s e 200 ms → 1 s) |
| A05 | 2026-10-23 | `ci: keep the test report when a gate blocks and read the current attempt (#461)` | concluída | `ci.yml`: `id: upload-test-reports` e saída `test-reports-id` em `desktop-windows` e `server`; `test-report` baixa por `artifact-ids` só com id não vazio; "Build report" com `\|\| status=$?` e saída vermelha só para código > 1; "Upload report" com `if: always()`. `build-and-release.md` ("Test report"). `actionlint -shellcheck= .github/workflows/ci.yml` (1.7.12, checksum conferido): sem achado. Passo simulado com `bash -e` e um gerador falso: código 0 e 1 → resumo escrito e passo 0; código 2 → resumo escrito e passo 2. O passo antigo com código 1: passo 1 e resumo vazio (o defeito da issue). O download por id e o rerun só se provam no CI deste PR |
