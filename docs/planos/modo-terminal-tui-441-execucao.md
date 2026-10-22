# Modo terminal: modo interativo `usage-monitor tui` — issue #441

## Objetivo

Terceira das três issues do modo terminal (#439, #440, #441). Uma tela cheia por teclado, com a mesma
moldura em todas as abas (título, abas numeradas, atalhos no rodapé): Visão geral (3.1), Sessões (3.2),
Histórico (3.3), Time (3.4), a ajuda (3.5), Configurações (4.2) e a leitura que falhou (6.2), do
protótipo [`prototipo-modo-cli.html`](prototipo-modo-cli.html). Regras e primitivas em
[`docs/design-system/cli/readme.md`](../design-system/cli/readme.md), que vence o protótipo.

## Fatos confirmados (antes de qualquer código)

- **O desenho já é função pura.** As primitivas da #439 (`TerminalLine`, `TerminalSpan`, `TerminalAnsi` nas
  quatro paletas, glifos UTF-8 e ASCII) desenham linhas; a TUI só precisa de moldura, abas e um laço de
  teclado. O texto de cada tela sai de `commonMain`, testável sem terminal.
- **Os bancos não podem ser escritos pela TUI.** `LocalCliSessionDataSource` e `LocalUsageHistoryDataSource`
  abrem pelo `SqliteConnectionManager`, que roda a criação e a migração do schema ao abrir; com o app ou o
  serviço coletando, isso disputaria o arquivo. A #439 resolveu a linha de status com consultas próprias em
  somente leitura (`LocalCliSessionMetricsReader`); a TUI precisa de muito mais (sessões com custo
  recalculado por `ModelPricingTable`, detalhe, histórico por hora).
- **Os view models do app** dependem de repositórios inteiros; a issue decidiu reaproveitá-los. O caminho
  sem escrever é o mesmo repositório e o mesmo caso de uso sobre uma conexão somente leitura.
- **A configuração já tem dono:** a aba 5 muda pelos mesmos `ConfigChange` da #440 (pedido ao coletor vivo,
  ou gravação direta sem ele), então não há regra nova de gravação.
- **A saúde de cada fonte** (para a 6.2) já sai do `collector-health.json` da #440, e a idade da leitura do
  `dashboard-cache.json`.

## Decisões

- **D1 · Biblioteca:** JLine 3, só para o terminal (modo cru, teclas, tamanho e redimensionamento). O
  desenho continua o da #439. Mosaic traria o runtime do Compose para a tela e um segundo jeito de desenhar;
  Lanterna tem o próprio modelo de tela e de cor, que duplicaria as quatro paletas. **Medição (A02):**
  `jline-terminal` 292 KB + `jline-terminal-jni` 51 KB + `jline-native` 190 KB = 533 KB na 3.30.17
  (o pacote `org.jline:jline` inteiro tem 1,53 MB, com leitor de linha e shell que a TUI não usa);
  Lanterna 3.1.3, 577 KB; Mosaic 0.18, 417 KB só o runtime, mais o Compose na tela. O JLine entra só
  como terminal: as teclas são decodificadas pelo `TuiKeyDecoder` (função pura, `ESC [ A` e `ESC O A`,
  a forma que o JLine gera no console do Windows pela terminfo `windows`), sem `jline-reader`.
- **D2 · Dados em somente leitura:** `SqliteConnectionManager` ganha o modo somente leitura (sem schema, sem
  criar arquivo), e a TUI monta os repositórios e casos de uso do app sobre ele. Nenhuma requisição, exceto
  a aba Time (o mesmo `GET` da janela de time do app) e as mudanças da aba 5 (pelo canal da #440).
- **D3 · Tela:** redesenho completo a cada mudança, na tela alternativa do terminal, sem animação; releitura
  a cada 15 s (a do `wait`) e com `r`. Abaixo de 80 colunas, lista e detalhe se empilham.
- **D4 · Textos** por objeto de rótulos, nunca `== AppLanguage.PT` inline.
- **D5 · Casos de uso, e não os view models (A03).** A issue pedia os view models de
  `presentation/viewmodel/`; eles não importam Compose, mas sincronizam o índice (escrita) e têm laços
  próprios de releitura (60 s, 30 s do time), que disputariam o da TUI. A TUI usa a camada logo abaixo:
  os mesmos casos de uso (`GetCliSessionsUseCase`, `GetUsageHistoryUseCase`…), repositórios e data
  sources, com as mesmas regras de custo e de janela. Com o data source em somente leitura, o
  `syncIndex` é inerte e devolve um relatório vazio, e o caso de uso segue para a leitura.
- **D6 · Time sem cota por pessoa (A07).** O protótipo mostrava "CLAUDE 5h / 7d" por pessoa, mas o
  servidor do time não guarda cota (só sessões e turnos), e número que a fonte não informa não é
  derivado. As colunas são PESSOA, AGORA (trabalhando, app aberto ou ausente, sempre escrito),
  SESSÕES, TOKENS e HOJE (custo pela `ModelPricingTable`, com `+` para modelo sem tarifa); as cotas
  da conta estão na aba 1. A TUI não bate presença nem envia uso: lê o desvio do relógio do servidor
  que o coletor mediu, gravado na saúde dele (`clock_offset_ms`, campo novo com default), e sem
  coletor usa zero. `t` anda pelas contas do time (não há "times", há contas que participam).

## Atividades

| Item | O que faz |
| --- | --- |
| A01 | Este plano e a worktree; plano publicado como comentário na #441 |
| A02 | Moldura, abas e rodapé em função pura (80/96 colunas, empilhamento), a camada de terminal com JLine (modo cru, teclas, tamanho, tela alternativa), `usage-monitor tui` e a ajuda (3.5). Medição da biblioteca |
| A03 | Bancos em somente leitura (`SqliteConnectionManager`) e o leitor da TUI sobre os repositórios do app |
| A04 | Aba 1 Visão geral (3.1) e a leitura que falhou (6.2) |
| A05 | Aba 2 Sessões (3.2) |
| A06 | Aba 3 Histórico (3.3) |
| A07 | Aba 4 Time (3.4), que some sem time configurado |
| A08 | Aba 5 Configurações (4.2) pelo canal da #440 |
| A09 | Ajuda da linha de comando, README, docs, conferência com o protótipo nas quatro paletas e em 80/96 colunas, prints, prova pelo SSH contra o serviço e PR |

## Riscos

- **R1 · Windows:** o console do Windows não foi medido aqui (#342); o JLine usa JNA ali, que o app já traz.
- **R2 · Schema:** a TUI de uma versão lê o banco escrito por outra; coluna nova que a TUI espera e o banco
  não tem vira falha de leitura daquela aba, nunca escrita.

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
| --- | --- | --- | --- | --- |
| A01 | 2026-10-22 | `docs(cli): plan the interactive terminal mode (#441)` | concluída | Worktree `../usage-monitor-441`, branch `feat/issue-441-terminal-tui` a partir da `main` (`7c11e48c`, com a #440). Levantamento: telas 3.1–3.5, 4.2 e 6.2 do protótipo; `SqliteConnectionManager` roda o schema ao abrir (`onOpen`), por isso D2; `collector-health.json` e `dashboard-cache.json` já dão a 6.2. Só documentação; nenhum teste rodado |
| A02 | 2026-10-22 | `feat(cli): open the interactive mode with its frame, tabs and help (#441)` | concluída | Moldura, abas, rodapé, caixa e lista/detalhe em `TuiFrame` (função pura, altura e largura exatas, 96 no máximo, empilhado abaixo de 80); estado e teclas em `TuiReducer`; teclas em `TuiKeyDecoder`; terminal em `JLineTuiIo` (modo cru, tela alternativa, cursor escondido, restauração também no shutdown hook). `gradlew desktopTest --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.*CommandLine*"`: 224 testes, verdes depois de encurtar a linha do `tui` na ajuda (84 colunas no EN). `createDistributable` + tmux 96×26: moldura, `?`/`esc`, `3`, Ctrl+C devolvem o terminal. **Achado:** o runtime do jlink não tinha `jdk.unsupported` (`sun.misc.Signal`), e o SIGWINCH não chegava: o `tmux resize-window` deixava a tela velha. Entrou o módulo, e o laço também compara o tamanho a cada 200 ms. `tui < /dev/null \| cat` sai com 1 e a frase (6.3, novo no protótipo). O `pure virtual method called` impresso ao sair aparece também no `--version` da mesma distribuição local (runtime JDK 21 do jpackage local; a instalada, com 17, não imprime): não é da TUI |
| A03 | 2026-10-22 | `feat(cli): read the app databases without writing for the interactive mode (#441)` | concluída | `SqliteConnectionManager(readOnly = true)`: `SQLiteConfig.setReadOnly`, `busy_timeout` de 5 s, sem `mkdirs`, sem `onOpen` (schema) e falha se o banco não existe. `LocalCliSessionDataSource`, `LocalCodexCliSessionDataSource` e `LocalUsageHistoryDataSource` ganham `readOnly`; o `syncIndex` dos dois índices fica inerte. `TuiStores` monta os casos de uso do app sobre eles (D5). `gradlew desktopTest --tests "com.usagemonitor.data.ReadOnlyDatabaseTest" --tests "com.usagemonitor.data.LocalUsageHistoryDataSourceTest" --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest"`: 73 testes; os dois de "não muda nada" falharam na primeira volta porque o SQLite cria `-wal` (vazio) e `-shm` para ler um banco em WAL mesmo em somente leitura. Decisão: é o mecanismo do WAL, e o arquivo principal não muda; o teste compara o hash dos `.db` e exige `-wal` vazio, e os sidecars saem restritos ao dono, como os do coletor. Verde depois disso; a leitura com o gravador ainda aberto vê o que ele já gravou |
| A04 | 2026-10-22 | `feat(cli): show the overview tab and the failed reading in the interactive mode (#441)` | concluída | `TuiOverviewView`: contas à esquerda (apelido, ou a fonte; maior percentual, ou o saldo no formato curto do card, `$7.82`), detalhe com uma linha de cota por cota (barra de até 40, percentual, palavra de estado, reinício), a sessão ativa (a mais recente com atividade em 10 min, custo da sessão inteira pelo índice; Codex sem tarifa, sem custo) e as últimas 24 h (`TuiSparkline`, 48 meias horas, `┆` no reinício). Empilhado abaixo de 80. A 6.2 vem do `collector-health.json`, que ganha `target` e `failure` (campos novos com default; coletor anterior cai no rótulo). **Divergências corrigidas no protótipo:** os créditos são uma linha de cota como as outras; o custo sai `$9.40`, como a linha de status; o coletor não grava a hora da tentativa, então a faixa diz "A última leitura falhou: …". **Achado:** `LocalTeamSettingsDataSource.load()` grava (cria o `deviceId`, move arquivo ilegível); a TUI lê pelo `peek()` novo, sem efeito. Em tmux, distribuição local com os dados desta máquina: 96×26, 70×20 empilhado (`enter`/`esc`), e `NO_COLOR=1 LC_ALL=C` em ASCII sem acento. O primeiro ASCII saiu `?`: o perfil lia `terminal.encoding()` (UTF-8, padrão do JDK) e o JLine escreve em `stdoutEncoding()`; corrigido. `gradlew desktopTest --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.data.*"`: verde |
| A05 | 2026-10-22 | `feat(cli): show the sessions tab in the interactive mode (#441)` | concluída | `TuiSessionsView` + `TuiSessionsReader`: sessões do Claude Code (todas as contas) e do Codex no corte, ordem por última atividade e id; custo do índice com `+` e nota do modelo sem tarifa; Codex com `—` (o app não tem tarifa dele); composição de tokens e contexto da selecionada; `enter` abre os turnos (do mais recente), `esc` volta; `e` grava o CSV do app (`UsageExporter`, só o Claude Code, como a janela de Sessões) na pasta de onde a TUI abriu, com aviso `✓` até a próxima tecla. Cada aba lê só o que é dela, e trocar de aba pede releitura. **Divergências corrigidas no protótipo:** "ontem" saiu dos cortes (o índice só corta por início, `sinceEpochMillis`; fim exigiria consulta nova) e o corte vira `hoje · 7 dias · 30 dias`; custo `$9.40`, modelo pelo id, como o app. **Achados:** o `abbreviate` do app formata pelo locale da JVM (`4,8M` num computador, `4.8M` noutro): a TUI usa `TuiText.tokens`, pelo idioma do app; e as iniciais das séries colidiam sem cor (`c`/`c`), agora `e s l g` (`i o r w` em inglês), registrado no readme. Em tmux com os dados desta máquina: lista, `→` 7 dias, `enter` turnos, `e` CSV (arquivo conferido: só metadados; apagado). `gradlew desktopTest --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.architecture.*"`: verde |
| A06 | 2026-10-22 | `feat(cli): show the history tab in the interactive mode (#441)` | concluída | `TuiHistoryView`: uma trilha por cota com percentual, na ordem do painel, pelo `GetUsageHistoryUseCase` sobre o histórico em somente leitura; 24 h (48 células de meia hora), 7 dias (56 de 3 h) e 30 dias (60 de 12 h); eixo em BRT (3 h cheias, virada do dia, a cada 5 dias); uso de agora e palavra de estado no fim; o `enter` da aba 1 abre aqui na trilha da conta. **Achado pelos dados desta máquina:** marcar todo fim de janela que muda (a regra do gráfico do app) enchia o Go 5h, janela corrida, de `┆`; e com "janela anterior acabou" sozinho o Claude 5h perdia os reinícios, porque entre duas janelas vem uma leitura ociosa sem reinício conhecido e com fim fictício. A regra ficou: o fim mudou e ficou para trás, comparado com a última leitura que conhecia o fim (`TuiSparkline`, testes dos três casos; readme). **Divergência corrigida no protótipo:** saiu a frase "o pico de hoje foi entre…", que seria interpretação derivada sem fonte no app; rótulos com o apelido e o rótulo do app. Em tmux 96 e 80 colunas, nas três janelas. `gradlew desktopTest --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*"`: 230 testes, verdes |
| A02 (correção) | 2026-10-22 | `fix(cli): write the arrow keys as ASCII when the terminal is not UTF-8 (#441)` | concluída | Achado na conferência da A07 em `NO_COLOR=1 LC_ALL=C`, 80 colunas: o rodapé de toda aba e a ajuda escreviam `??` no lugar de `↑↓`/`←→`, porque `withoutAccents` só trocava a pontuação tipográfica. As setas viram `^ v < >` (mesma largura), registradas na tabela de glifos do readme. `gradlew desktopTest --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.terminal.*"`: verde |
| A07 | 2026-10-22 | `feat(cli): show the team tab in the interactive mode (#441)` | concluída | `TuiTeamView` + `TuiTeamReader`: o `GET /api/v1/team` da janela de time do app pelo `TeamUsageRepositoryImpl`, com o corte do dia em BRT no agora do servidor; presença e ordem pelo `toTeamPresence`; aba só com a integração ativa (`isActive`) e ao menos uma conta participando com login; `HttpClient` montado só na primeira leitura, com o proxy efetivo do app. Leitura que falha mantém os números anteriores da mesma conta e diz de quando são; falha de conexão classificada pelo tipo (`isConnectivityFailure`) vira "o servidor do time não respondeu", em vez da mensagem do socket; sem leitura nenhuma a contagem de pessoas não aparece (seria "0 pessoas" sem medir). Glifos de presença `● ◑ ○` / `* + -` no readme; decisão D6; protótipo 3.4 refeito. **Prova de ponta a ponta:** servidor de `server/` local (chave legada, banco no scratchpad), raiz `--user-data-dir` temporária, `team join --accounts default` (`team status`: conexão ok), uso de exemplo por `POST /v1/ingest` e `/v1/presence` (só contagens) e um integrante envelhecido no SQLite do servidor: tmux 96×20 truecolor e 80×20 `NO_COLOR=1 LC_ALL=C` mostraram trabalhando/app aberto/ausente, custo com `+` nos modelos sem tarifa e `$0.11` no Haiku 4.5; com o servidor derrubado, `r` manteve os números com a frase; religado, voltou. **Achados na conferência:** a legenda numa linha passava de 96 colunas (virou duas); em 80 colunas o apelido tinha 9 células (a coluna AGORA agora guarda 30 e o resto vai ao apelido); e as setas do rodapé saíam `??` no ASCII em toda aba (commit próprio, linha "A02 (correção)"). `gradlew desktopTest --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*" --tests "com.usagemonitor.*Collector*"`: 242 testes, verdes |
