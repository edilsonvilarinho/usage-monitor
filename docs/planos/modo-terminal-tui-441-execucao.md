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
  Lanterna tem o próprio modelo de tela e de cor, que duplicaria as quatro paletas. Medição na A02.
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
