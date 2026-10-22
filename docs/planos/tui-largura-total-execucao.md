# Modo interativo: a moldura ocupa o terminal inteiro

## Objetivo

Relato do usuário (2026-10-22), com foto de um monitor ultrawide: no `usage-monitor tui` a janela "não ficou
otimizada para o tamanho, ficou pequena". A moldura ficava presa em 96 colunas num canto do terminal, e o
divisor `│` entre a lista e o detalhe parava onde o conteúdo acabava.

## Fatos confirmados (antes de qualquer código)

- **A largura era decisão, não defeito de leitura do tamanho.** `TuiSize.frameWidth` fazia
  `columns.coerceAtMost(96)`, e `cli/readme.md` ("Largura") dizia "mais largo que 96, a moldura não cresce";
  o `TuiFrameTest` cobrava. A altura já era a do terminal.
- **As abas já se adaptam à largura.** Visão geral e Configurações usam `detailWidth`; Sessões divide o resto
  entre projeto e modelo; Histórico corta a trilha pelo espaço e tem teto no número de células; Time tem teto
  no apelido. Barras de cota têm largura fixa (40 no detalhe), pela regra das primitivas.
- **O divisor curto** vem de `TuiFrame.listDetail`, que monta `max(esquerda, direita)` linhas, não a altura
  do corpo.

## Decisões

- **D1 · Largura:** a moldura acompanha o terminal nas duas direções. 96 colunas continua sendo a largura
  de desenho (o protótipo mostra as telas nela); acima disso, a coluna de detalhe e as tabelas ganham o
  espaço, e as larguras fixas das primitivas (barras, trilhas) não mudam.
- **D2 · Divisor:** lista e detalhe ocupam a altura toda do corpo, para o `│` ir até o separador do rodapé.

## Atividades

| Id | Atividade |
| --- | --- |
| A01 | `frameWidth` = largura do terminal; testes de largura exata em 200 colunas em todas as abas; regra no `cli/readme.md`, nos READMEs e no texto da parte 3 do protótipo |
| A02 | `listDetail` preenche a altura do corpo (divisor até o rodapé), com teste |

## Pontos de situação

| Id | Data | Commit | Situação | Comando e resultado |
| --- | --- | --- | --- | --- |
| A01 | 2026-10-22 | `fix(cli): let the interactive frame fill wide terminals` | concluída | `gradlew desktopTest --tests "com.usagemonitor.presentation.terminal.*" --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*"`: 279 testes, 0 falhas. Prova: `createDistributable` e `tui` num tmux 200×30 (Fedora, Linux): as 30 linhas com 200 colunas, abas 1, 2, 3 e 5 com a moldura até a borda e o detalhe e a tabela de sessões ocupando o espaço |
