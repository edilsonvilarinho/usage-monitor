# Reset por cota no painel expandido da barra HUD — plano de execução

Issue [#189](https://github.com/edilsonvilarinho/usage-monitor/issues/189): "Quando o usuario
expandir mostrar os tempos dos resets de cada linha e sessao 5h 7d , caso item tenha".

## Problema

A issue não tem texto além do título — o conteúdo é uma foto da tela em uso real. Nela a barra HUD
está **expandida**, com seis contas, e cada linha traz estado, nome e percentuais
(`● Crítico INFORMATA2 ● 5h 21% ● 7d 13% ↻ 00:31`). Nenhuma diz **quando** a cota reinicia. Com a
janela normal fechada — que é o ponto do modo HUD —, "21% na janela de 5h" não responde à pergunta
que decide se dá para continuar trabalhando agora: 21% faltando dez minutos para o reset é outra
situação que 21% no começo da janela.

O dado já existe, formatado, e **já teve consumidor**. `resetShortLabel`
(`ApiUsageCardFormatting.kt:261`) devolve `22h59` para janela intradiária e `Ter 21h00` para semanal
ou mensal, a partir das mesmas `formatBrtDateTimeParts` que a linha do card usa — não é formato de
data novo. Ela alimentava a versão do HUD em que cada linha era uma **cota**; quando a linha passou a
ser por **conta** (#164, versão 6 do conteúdo), a coluna caiu junto e a função ficou órfã. O `import`
dela em `Main.kt:170` continua no arquivo, sem uso.

O design system, esse, **nunca deixou de descrever a coluna**: `AppHudBar.prompt.md`, § "No new
formats in the row", ainda diz que o reset curto sai das mesmas partes de data do card e que "absent
means 'no reset to show' — the column disappears rather than printing a dash". Pela regra de
precedência do repositório (design system × Compose → corrige o Compose), o desenho certo é o que a
issue pede.

## Decisões

1. **Só no painel expandido.** A pílula parada fica na tela o tempo todo e o retângulo dela captura o
   clique de quem estiver atrás — foi essa queixa que fez a largura virar teto e não valor fixo na
   #164. O reset é detalhe sob demanda: o ponteiro em cima já é o gesto que revela o resto da lista.
2. **Ao lado da cota, dentro do mesmo bloco**, e não em coluna própria à direita da linha. A linha é
   por conta e as cotas são várias; uma coluna única de reset teria de escolher **qual** das cotas
   descrever, que é exatamente a ambiguidade que a versão 4 do conteúdo (uma linha por cota) existia
   para evitar.
3. **Tom secundário (`onSurfaceVariant`), sem separador impresso.** O reset não é consumo, e é a
   diferença de tom que diz isso — a mesma que a contagem regressiva já usa na ponta da primeira
   linha. Um `·` entre percentual e hora gastaria largura para repetir o que o tom já informou.
4. **Nenhum formato novo.** `resetShortLabel` entra como está, inclusive nos casos de borda que ela
   já resolve: `mensal` para os créditos de uso, `reiniciando` para janela vencida, `null` para saldo
   que não expira e para janela sem reset conhecido. `null` não imprime nada — é o "caso item tenha"
   do título.
5. **Teto próprio para o painel expandido.** `HUD_PILL_MAX_WIDTH` foi calibrado para a linha **sem**
   esta coluna, e mantê-lo faria a coluna nova ser paga pelo nome da conta — o mesmo erro que os
   saltos 320 → 420 → 484 já recusaram duas vezes. O painel expandido ganha
   `HUD_PANEL_MAX_WIDTH`; a pílula parada continua com o teto de hoje.
6. **A âncora continua sendo o painel parado.** `hudAnchorSize` já é calculada com `expanded = false`
   e não muda: ancorar no estado expandido faria a janela saltar toda vez que o ponteiro passasse por
   cima.

## O que foi verificado antes de decidir

- **`resetShortLabel` é `internal` em `commonMain`** e visível de `desktopMain` (mesmo módulo), com
  testes próprios já no repositório. Não há por que duplicar formato nem mover a função.
- **A geometria já separa os dois estados**: `hudWindowSize(..., expanded)` mede `sources.take(1)`
  parada e a lista inteira expandida, e `Main.kt` já chama a função duas vezes — uma para a âncora
  (`expanded = false`), outra para o alvo. A coluna nova entra na segunda sem inventar caminho.
- **A largura vai estourar o teto atual, e a conta é a da geometria existente.** Do #185:
  `Anthropic — Padrão` mede 420,9dp com a contagem, contra o teto de 484. Cada reset custa
  `AppSpacing.xs (4) + n × 7,2dp` — ~40dp num `22h59`, ~69dp num `Ter 22h59`. Duas cotas Anthropic
  somam ~110dp; a conta do OpenCode Go tem **três**. Daí a decisão 5.
- **O `HudBarHeightTest` é a costura entre a geometria e o que o Compose dispõe**, e é ele que pega
  divergência entre as duas contas — foi assim que o rodapé antigo nascia 8dp mais curto que o
  conteúdo.
- **O protótipo está atrasado nesta seção** desde a #185: ele desenha uma linha por *cota* com
  reset, desenho que o Kotlin abandonou. A coluna volta, mas na linha por **conta**.

## Riscos declarados

1. **Nome de conta trunca mais no estado expandido.** É o preço da coluna, e a mitigação é a decisão
   5. O valor de `HUD_PANEL_MAX_WIDTH` sai de **medição** com as contas reais em A08, na forma
   `teto da pílula + a maior soma de colunas de reset medida` — nunca um número escolhido por ser
   redondo.
2. **A janela expandida fica mais larga que a âncora.** `hudWindowPosition` já alinha pelo lado mais
   próximo da borda nos dois eixos, então crescer na horizontal é o mesmo mecanismo que já trata o
   crescer na vertical. Confirmado no app real em A08, não deduzido.
3. **A demo `window-modes.gif` da janela de ajuda mostra o HUD** e passa a descrever um desenho
   anterior. Mesma mitigação da #185: regerar e commitar **só** aquele arquivo, restaurando os outros
   onze com `git checkout --` — o gravador dorme em tempo real e cada passada produz bytes
   diferentes.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Plano e comentário-índice na issue | `gh issue comment 189` | plano publicado no comentário `5513144363`; comentário-índice `5513146178`, editado ao fim de cada atividade por `gh api -X PATCH repos/:owner/:repo/issues/comments/5513146178`. A leitura da issue veio da foto, que é o único conteúdo dela: o painel HUD expandido com seis contas, percentuais e nenhum tempo de reset. `resetShortLabel` já existe, já teve consumidor na versão do HUD em que a linha era por cota, e hoje é `import` morto em `Main.kt:170` |
