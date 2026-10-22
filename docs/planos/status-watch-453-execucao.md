# Modo terminal: `status --watch` — issue #453

## Objetivo

Um usuário pediu um `--attach` para o `status`, "pra ficar na tela sempre": deixar o uso aberto num painel do
tmux, como faz com o `btop`. Hoje o `status` imprime a última coleta e sai, e o `tui` fica aberto mas é a tela
cheia com abas e teclado. O pedido vira `usage-monitor status --watch [--interval <tempo>]`: a mesma tabela do
`status`, redesenhada no lugar até o Ctrl+C. Regras e primitivas em
[`docs/design-system/cli/readme.md`](../design-system/cli/readme.md), que vence o protótipo
[`prototipo-modo-cli.html`](prototipo-modo-cli.html).

## Fatos confirmados (antes de qualquer código)

- **Não há `--attach`, `--watch` nem `--follow` no `status`** (`TerminalCommandLine.kt`): só `--json` e `--max-age`.
  O único `--follow` do projeto é o de `service logs`.
- **A leitura já tem laço pronto**: o `wait` relê o `dashboard-cache.json` a cada 15 s (`TerminalCommands.WAIT_INTERVAL`)
  pelo mesmo `TerminalReader`, e o `tui` também (`TuiRunner.REFRESH_INTERVAL`). Nenhuma requisição: quem muda o número
  é o coletor.
- **A tabela é função pura** (`TerminalStatusView.render`), e o rodapé dela já diz de quando é a leitura ("lido há 38 s
  pelo app aberto"). O aviso "Nada está coletando" também já sai dela.
- **O redesenho sem piscar já está resolvido no `JLineTuiIo.draw`**: cursor no canto, cada linha seguida de "apaga até
  o fim", e "apaga o resto" no fim, sem limpar a tela antes.
- **A detecção de terminal do `tui`** é `System.console() != null` e `TERM` diferente de `dumb` (pelo tipo do JLine).

## Decisões

- **D1 · Nome `--watch`, e não `--attach`.** É o costume dos terminais (`watch`, `kubectl get -w`). `attach` dá a entender
  que o comando se conecta a um processo que já está rodando, como o `tmux attach`.
- **D2 · Intervalo:** padrão 15 s, o do `wait` e da TUI; `--interval <tempo>` com piso de 5 s. O cache só muda a cada
  coleta (60 s com sessão ativa); reler mais rápido só avança a idade e testa a trava de instância sem ganho.
  `--interval` sem `--watch` é erro de uso.
- **D3 · Combinações recusadas:** `--watch` com `--json` ou com `--max-age` é erro de uso. Os dois são contrato de
  quem roda uma vez e lê a saída ou o código; um fluxo de JSON (NDJSON) seria outra decisão de contrato.
- **D4 · Sem JLine, sem tela alternativa e sem modo cru.** O comando não lê tecla: Ctrl+C encerra o processo (código
  130 do JVM). O quadro fica na tela normal, então o último continua visível depois do Ctrl+C. Só o cursor é escondido
  durante o laço e volta pelo shutdown hook, como na TUI.
- **D5 · Sem terminal** (saída num pipe, `TERM=dumb`), o `--watch` diz o que usar e sai com 1, como o `tui` (6.3).
  Redesenhar no lugar num arquivo deixaria lixo de sequências de controle.
- **D6 · Cabeçalho:** uma linha no alto, `usage monitor` em ouro e negrito (o título da moldura da TUI) e, em muted,
  "14:23 BRT · relê a cada 15 s · Ctrl+C sai". Não diz "ao vivo": com nada coletando, a tabela já avisa que os números
  não mudam, e o cabeçalho não pode contradizer.
- **D7 · Textos** por objeto de rótulos (`TerminalLabels`), nunca `== AppLanguage.PT` inline.

## Atividades

| Item | O quê |
| --- | --- |
| A01 | Este plano e a issue #453 com ele em comentário |
| A02 | `status --watch [--interval]`: parser, laço, cabeçalho, ajuda, testes, design system (`cli/readme.md`), protótipo (tela 2.4) e `build-and-release.md`; conferência num tmux real |

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
| --- | --- | --- | --- | --- |
| A01 | 2026-10-22 | `docs(cli): plan status --watch (#453)` | concluída | Worktree `../usage-monitor-watch`, branch `feat/status-watch` a partir da `main` (`ce485d96`). Levantamento: `TerminalCommandLine.kt` (opções do `status`), `TerminalCommands.runWait` (laço de releitura), `JLineTuiIo.draw` (quadro sem piscar), `TuiWiring` (detecção de terminal). Só documentação; nenhum teste rodado |
| A02 | 2026-10-22 | `feat(cli): keep the status open in a pane with status --watch (#453)` | concluída | `TerminalCommand.Status(watch)`, `--watch` e `--interval` (piso 5 s; sozinho, ou `--watch` com `--json`/`--max-age`, é erro de uso); laço em `TerminalStatusWatch` (relê pelo `TerminalReader`, redesenha pelo `TerminalWatchView.frame`, sem JLine); cabeçalho e texto de "sem terminal" em `TerminalLabels`; ajuda PT/EN; design system (`cli/readme.md`: cabeçalho e largura), protótipo 2.4 (a tabela da 2.2 virou `statusTable()`, compartilhada) e `build-and-release.md`. **Achado no tmux real:** num painel de 70×12 as linhas quebravam, o quadro passava da altura e o cabeçalho saía por cima; o laço passou a desligar a quebra automática (DECAWM, `CSI ?7l`) junto com o cursor, e a linha é cortada na borda. Conferido com `createDistributable` no tmux, com os dados desta máquina: 110×24 truecolor (releitura de 5 s, rodapé andando "há 47 s" → "há 57 s"), 70×14 com cabeçalho no lugar, `NO_COLOR=1 LC_ALL=C` em ASCII sem acento; Ctrl+C sai com 130 e devolve cursor (`cursor_flag` 1) e quebra (`wrap_flag` 1); `status --watch \| cat` sai com 1 e a frase; `--watch --json` sai com 2. `bash ./gradlew desktopTest --rerun -Pcoverage -PtestForks=4` + `koverXmlReport` + relatório da skill `usage-monitor-test-report`: 3238 testes, 0 falhas, 17 regras de arquitetura, cobertura Kotlin 83,0% |
