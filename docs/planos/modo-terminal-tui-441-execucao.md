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
