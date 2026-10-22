# Usage Monitor — modo terminal (CLI)

Design system de tudo o que o Usage Monitor imprime num terminal: comandos de uma tacada, linhas de
status, modo interativo (`usage-monitor tui`), assistente de configuração e serviço sem tela.
Aprovado pelo dono em 2026-10-21 (rodada AG da skill `usage-monitor-visual-options`), a partir de um
pedido de usuário por um modo CLI com o tema do app. Issues: #439 (ver o uso), #440 (configuração e serviço sem tela), #441 (modo interativo).

| Arquivo | O que é |
| --- | --- |
| `cli/readme.md` (este) | Fundamentos, primitivas e regras de texto e comportamento |
| [`tokens/terminal.css`](../tokens/terminal.css) | As quatro paletas de terminal, com os mesmos acentos de `colors.css` |
| [`docs/planos/prototipo-modo-cli.html`](../../planos/prototipo-modo-cli.html) | O mockup de **cada tela**, em sete partes, com terminal claro e sem cor |

## Precedência

A mesma regra do app, aplicada ao terminal:

| Divergência | Quem vence | O que fazer |
| --- | --- | --- |
| Código × este documento | este documento | corrigir o código |
| Código × protótipo (layout de tela) | protótipo | corrigir o código |
| Este documento × protótipo | este documento | corrigir o protótipo, no mesmo commit |
| Este documento tecnicamente errado | o código | corrigir este documento, com a decisão registrada |

O que este documento não redefine vem do [design system do app](../readme.md), seção CONTENT
FUNDAMENTALS: voz impessoal, todo estado com palavra, número explicado, nada de emoji nem
exclamação, números em pt-BR.

**Toda tela nova de terminal entra no protótipo, no mesmo commit:** parte e número próprios
(`N.M`), mais a linha no mapa do topo se for parte nova. Primitiva nova ou contrato alterado entra
aqui, na seção Primitivas.

## Fundamentos

1. **O fundo é do terminal.** Nada pinta fundo, exceto a seleção (`--term-selection`: item
   selecionado, aba ativa, linha em edição) e a barra do tmux, que já tem fundo próprio.
2. **Acento é identidade de fonte**, com os mesmos hexes de `colors.css`. **Estado é palavra mais
   cor semântica.** A cor nunca informa sozinha: sem cor, todas as telas continuam legíveis.
3. **Quatro paletas, decididas pelo terminal, nunca pelo tema do app:** truecolor escuro (padrão),
   truecolor claro (fundo claro detectado por `COLORFGBG` ou OSC 11; na dúvida, escuro), 16 cores
   ANSI (sem `COLORTERM=truecolor`) e sem cor (`NO_COLOR`, `TERM=dumb` ou saída redirecionada).
   **Glifos** são uma decisão separada da cor: saída que não é UTF-8 usa a coluna ASCII da tabela
   de glifos, mesmo com cor.
4. **Sem animação.** Só o cursor do terminal pisca. Espera se diz em texto ("aguardando…"), como o
   progresso da atualização do app.
5. **Toda leitura diz de onde e de quando é:** "lido há 38 s pelo app aberto", "pelo serviço",
   "leitura de 14:12". Leitura velha nunca se passa por atual.
6. **Leitura que falha mantém os números anteriores** e publica só a mensagem. Número que a API não
   informa não é derivado. `null` é "não medido", zero é "medido e sem valor".
7. **Ordem total e determinística:** as fontes e contas saem na ordem do app, nunca reordenadas por
   risco. Duas leituras iguais imprimem a mesma coisa.
8. **Segredo nunca passa por argumento nem aparece na tela.** Chave de API é lida da entrada, sem
   eco. Nenhum tipo com credencial é impresso.
9. **Só metadados de uso.** Nunca prompt, resposta ou conteúdo de sessão.

### Largura

- Modo interativo desenhado para **96 colunas** (94 internas). Abaixo de 96, a coluna de detalhe
  encolhe; abaixo de 80, lista e detalhe se empilham: a lista ocupa a tela e `enter` abre o detalhe.
- Comandos de uma tacada cabem em 100 colunas, e a linha de status também.

## Glifos

| Uso | Glifo | Sem cor ou ASCII |
| --- | --- | --- |
| Molduras e caixas | `╭ ╮ ╰ ╯ │ ─ ├ ┤ ┬ ┴` (cantos arredondados, como o raio do app) | `+ - \|` |
| Barra de cota | `█` cheio, `▏▎▍▌▋▊▉` em oitavos, `░` vazio | `[####----]` |
| Fonte | `●` na cor da fonte | `*` só na linha em estado crítico |
| Seleção | `▸` em ouro | `>` |
| Aviso | `▲` em `--term-warn` | `!` |
| Feito, pulado | `✓` verde, `–` muted | `ok`, `-` |
| Reinício de janela | `┆` muted | `\|` |
| Anel na linha de status | `○ ◔ ◑ ◕ ●` (quartos de uso) | o número basta |
| Valor ajustável | `‹ 70 ›` | `< 70 >` |
| Legenda de série | `■` na cor da série | inicial da série |

Sem emoji, em nenhuma tela. Glifo fora desta tabela precisa entrar aqui antes de ser usado.

## Primitivas

Cada primitiva é uma função pura de dados para linhas (texto mais estilos por trecho), testável sem
terminal. O número entre parênteses é a tela do protótipo onde ela aparece.

**Barra de cota** (2.2, 3.1). Células `█` mais uma parcial em oitavos, na cor da fonte; o resto em
`░` com `--term-dim`, sempre visível. Larguras fixas: 12 na linha de status, 20 em tabelas e
comandos, 40 no detalhe. Pelo **piso**: a barra nunca passa do número escrito ao lado, nem de 100%,
e nunca cresce por efeito. No ASCII, `[####----]` com a mesma largura total.

**Palavra de estado** (2.2). `em dia`, `atenção`, `crítico`, em `--term-ok`, `--term-warn` e
`--term-crit`, pelos **limiares de alerta da conta** (`UsageAlertSettings.quotaPercentsFor`: o
limiar próprio da conta ou o geral, padrão 75, 90 e 100). A palavra conta quantos limiares o
percentual já alcançou: nenhum, `em dia`; um, `atenção`; dois ou mais, `crítico`. É a mesma regra
de zonas da barra do Telegram (AE7), então o bot e o terminal nunca discordam. Sem limiar nenhum,
não há palavra: só o número. Cota sem percentual (saldo, contagem) também não leva palavra. Na
linha de status e na barra do tmux, `em dia` não se escreve: só o que pede atenção aparece.

**Linha de cota** (2.2). `● Fonte  conta  cota  barra  NN%  estado  HH:MM BRT · em X`. A coluna de
cota traz o **rótulo do app** ("Claude 5h", "Go semanal", "Cursor Included"), e não uma janela
abreviada: os rótulos são chave de série e variam por fonte, e só o rótulo diz o mesmo que a HUD e
o bot (decisão da A05 da #439, que corrigiu o "JAN." do protótipo). A fonte e a conta só na primeira
cota da conta; a porcentagem em negrito; o "em X" em muted. O reinício segue o bot até 6 dias
("15:10", "qui 21:00"); mais longe, a data ("05/11 15:47"). Janela já vencida diz "reiniciando".
Cota sem percentual (saldo) mostra o valor no lugar da barra.

**Rodapé de leitura** (2.2). Origem e idade da leitura, seguidas da contagem por estado, com cada
estado na sua cor: "lido há 38 s pelo app aberto · 9 cotas: 1 crítica, 2 em atenção, 6 em dia".

**Linha de status** (1.1, 1.2). `◑ Conta · 5h NN% barra HH:MM · 7d NN% · sessão US$ · tok/s ·
modelo`. A conta é a que aquele CLI está usando. Acima do limiar: estado escrito e "reinicia em".

**Segmento do tmux** (1.3). Sigla de duas letras na cor da fonte e número em negrito, sobre o fundo
da barra. Estado abreviado (`crít`, `atn`) só acima do limiar.

**Moldura do modo interativo** (3.1). Borda arredondada em `--term-border`. Na borda de cima, à
esquerda, `usage monitor` em ouro e negrito; à direita, o status ("14:23 BRT · ao vivo", ou
"leitura de 14:12" em `--term-warn` quando a última falhou). Logo abaixo, a linha de abas, um
separador e o corpo; no fim, outro separador e o rodapé de atalhos.

**Abas** (3.1). Numeradas e fixas: `1 Visão geral`, `2 Sessões`, `3 Histórico`, `4 Time`,
`5 Configurações`. A ativa vai em ouro e negrito sobre `--term-selection`, as outras em muted. Sem
time configurado, a aba 4 não aparece e as outras **mantêm o número**: atalho não muda de tecla.

**Rodapé de atalhos** (3.1). A tecla em negrito, o rótulo em muted, dois espaços entre pares. Os
atalhos da aba vêm primeiro, os comuns depois (`1–5 aba`, `r atualizar`, `? ajuda`, `q sair`).

**Lista e detalhe** (3.1, 4.2). Coluna de 21 células com cabeçalho em maiúsculas muted. O item
selecionado leva `▸` em ouro e fundo de seleção. Um divisor `│` e, à direita, o detalhe.

**Tabela** (3.2, 3.4). Cabeçalho em maiúsculas, muted e negrito. Números alinhados à direita e
custo em negrito. A linha selecionada leva `▸` e fundo de seleção. Modelo sem tarifa leva `+` no
custo, com nota de rodapé.

**Sparkline** (3.3). Uma célula por meia hora em `▁▂▃▄▅▆▇█`, na cor da fonte, com `┆` muted no
reinício da janela. O eixo é em BRT, e a última coluna repete o número e o estado.

**Composição de tokens** (3.2). Segmentos `█` nas cores da série (`--term-input`, `--term-output`,
`--term-cread`, `--term-cwrite`), com legenda `■` e a porcentagem de cada série.

**Caixa** (3.5). Borda arredondada e título opcional na cor de acento. Usada para a ajuda e para
qualquer sobreposição. Nunca há caixa dentro de caixa.

**Campo de configuração** (4.2). `[x]` em verde e `[ ]` em muted, valor ajustável `‹ 70 ›` com as
setas em ouro, campo de texto `[ valor ]`. A linha em edição leva fundo de seleção. No alto da
seção, à direita: "muda na hora, sem reiniciar o serviço", ou o aviso de reinício quando o ajuste
exigir (proxy).

**Faixa de aviso** (6.2). `▲` em `--term-warn` e a frase do que houve; abaixo, em muted, o que
está sendo mostrado e quando tenta de novo.

**Passo do assistente** (4.1). `N/5` mais o título em negrito; perguntas com o padrão entre
colchetes, em muted (`[S/n]`); resultados com `✓` verde ou `–` muted, mais uma consequência em
muted.

**Resultado de comando** (4.3, 5.1). `✓ ação` mais uma consequência em muted ("o serviço já está
usando"). Aviso com `▲` e, se houver o que rodar, o comando na linha de baixo, indentado.

## Texto

- **Idioma: o do app.** Decidido pelo dono na #439 (2026-10-21): toda saída de terminal, inclusive
  `--help`, `--version` e `--diagnostics`, segue a `LanguageSelection` (preferência gravada ou
  `--locale`). Isso revoga a regra "inglês/ASCII" do #418, cujo motivo continua valendo e é
  tratado de outro jeito: **console que não é UTF-8** (o do Windows sem página 65001) recebe os
  glifos da coluna ASCII e o texto **sem acento** ("critico", "atencao"). Nomes de comandos,
  opções, ajustes e campos do `--json` são sempre em inglês.
- Hora sempre em BRT: "15:10 BRT", "em 47 min", "em 1d06h", "qui 21:00".
- Comandos no formato `usage-monitor <verbo> [objeto]`. Ajustes com nome em inglês e pontos
  (`alerts.quota.thresholds`), estáveis entre versões, porque viram contrato de script.
- `check` sai com código pelo pior estado: 0 em dia, 1 atenção, 2 crítico, 3 sem leitura.
- `--json` tem campos estáveis em inglês e nunca carrega segredo.

## Comportamento que o visual pressupõe

- **Um só coletor por pasta de dados**, pela mesma trava de instância única do app: no desktop o
  dono é o app, no servidor é `usage-monitor service`. O resto só lê.
- **A leitura pronta é o `dashboard-cache.json`** que o app já grava, de forma atômica, a cada
  coleta (o mesmo que reidrata a HUD no arranque). Não existe um segundo arquivo de retrato: um
  dono só para "a última leitura de cada conta".
- **Configurar pelo terminal muda os mesmos arquivos que o app usa.** O coletor aplica na hora,
  exceto o proxy, que vale depois de reiniciar (a mesma regra do app).
- Sem tela não há bandeja: avisos vão para o Telegram e para o registro de `alerts`.
- Tema, HUD, escala da interface, cor e emoji por conta não se aplicam ao terminal.
