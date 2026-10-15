# Bot do Telegram — teclado fixo como controle (AE4) e barra de quadrados por zona (AE7)

Rodada visual AE (galeria `docs/planos/telegram-bot-ae-visual/options.html`), escolhida pelo usuário em
2026-10-15: **AE4** no menu de comandos e **AE7** nas respostas. Executado por Claude Opus 5.5 no Claude
Code (desktop), branch `claude/distracted-greider-a7583a`.

## Problema (medido no print do usuário)

1. Menu do `setMyCommands`: o emoji repete a **categoria** (📊 em 7 linhas, 📐 em 10) e não distingue o
   comando; 29 linhas sem divisória — a API só aceita nome e descrição.
2. Sparkline `▁…█` (AA13) em `<code>`: 88% vira um retângulo branco no Android, 0% vira um traço sem
   informação — o mesmo defeito que tirou os blocos da janela Análise (`docs/presentation.md`).
3. Barra `▰▱` em `<code>` muda de largura com a fonte; cotas em 0% gastam três linhas cada.

## Decisões

1. **AE7 — barra de quadrados.** Dez emoji (`🟩`/`🟨`/`🟥` cheios, `⬛` vazios), cheios pelo **piso** do
   percentual (a barra nunca passa do número). A cor de cada quadrado é **quantos limiares** o limite
   superior da célula já passou: nenhum verde, um amarelo, dois ou mais vermelho; sem limiar, tudo verde.
   Com os padrões 75/90/100: 80 e 90 amarelos, 100 vermelho — a legenda do mockup ("até 75% · até 90% ·
   acima"). Os limiares são os da conta (`quotaPercentsByTarget`, AA10) ou os gerais. O número vai escrito
   em negrito ao lado (cor nunca informa sozinha) e o `/status` termina com a legenda dos limiares gerais;
   conta com limiar próprio leva a legenda dela.
2. Vale para `/status`, `/conta`, o painel fixado (não compacto) e o alerta de limiar e de reinício
   próximo. O painel compacto (uma linha) e o resumo diário continuam só com o número.
3. **Sparkline sai do `/status`** (decisão comum da rodada): a tendência fica no `/grafico`.
   `TelegramBotUsageMessages.sparkline(s)` e o histórico de 24 h lido no `/status` saem junto.
4. **AE4 — teclado fixo em duas páginas.** Página 1 (2×3): 📊 Cotas, 👤 Conta, 🔄 Coletar, 🔬 Análise,
   🔔 Alertas, ☰ Mais. Página 2: ⏰ Reinícios, ⏳ Quando, 🏁 Qual, 📏 Ritmo, 📉 Gráfico, 📝 Resumo, 🖥 HUD,
   👥 Time, ‹ Voltar, em linhas de três. HUD e Time só aparecem com a permissão ligada (botão que só
   responde "não pode" é promessa falsa). Trocar de página manda uma mensagem curta com o teclado novo —
   o Telegram só troca teclado de resposta junto de uma mensagem.
5. **Nasce ligado**: o pareamento já entrega a página 1. Conversa pareada antes recebe o teclado novo
   uma vez, no primeiro arranque com esta versão, por `TelegramBotPreferences.keyboardLayout` (estado,
   como `announcedUpdateVersion`), gravado **antes** do envio — falha perde o aviso em vez de repeti-lo.
6. Rótulos antigos (📊 Status, 📈 Gráfico) continuam entendidos: o teclado antigo fica no celular até o
   próximo envio.
7. **Menu do Telegram reduzido** a `/status`, `/teclado` e `/ajuda`; o `/ajuda` continua listando os 29
   comandos por categoria, e todos seguem funcionando digitados.
8. Texto novo por `localized()` (catraca `INLINE_LANGUAGE_CHECKS_CEILING`).

### Fora do alcance

- Nenhum teste com bot real: a API do Telegram é falsa nos testes. Largura dos quadrados no celular não
  foi medida em aparelho.

## Execução — uma atividade, um commit

| Item | O que faz |
|---|---|
| T1 | AE7: `telegramZoneBar`/legenda, `/status`, `/conta`, painel e alertas; sparkline removida; testes; docs |
| T2 | AE4: teclado em duas páginas, menu reduzido, pareamento e envio único às conversas antigas; testes; docs; histórico da skill |

## Pontos de situação

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| T1 | `TelegramBotMessages.zoneBar`/`zoneLegend`/`quotaLine`; `UsageAlertSettings.quotaPercentsFor(storageKey)`; `/status`, `/conta`, painel e alerta (avulso e repetido) recebem os limiares de `alertSettingsFlow`; nome e risco em linhas próprias; `quotaValue` com percentual devolve só o número; `bar` e a sparkline (`TelegramBotUsageMessages.sparkline(s)`, leitura de 24 h no `/status`) removidas com os testes delas. Prévia das Configurações com os limiares reais (`telegramBotSectionModel(alertSettings)`). Testes novos: barra pelo piso e por limiares passados, legenda, limiar próprio, alerta com barra | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.Telegram*" --tests "com.usagemonitor.domain.TelegramBot*" --tests "com.usagemonitor.data.*Telegram*" --tests "com.usagemonitor.TelegramBotServiceTest" --tests "com.usagemonitor.architecture.*"`: 127 testes, 0 falhas |
| T2 | `TelegramKeyboardPage`, `BotCommand.KeyboardPage`, `telegramKeyboardRows(language, page, allowPcActions, showTeam)` em linhas de três, rótulos da AA30 mantidos no `parseKeyboardLabel`; `TELEGRAM_KEYBOARD_LAYOUT` e `TelegramBotPreferences.keyboardLayout` (DTO com default 0); pareamento manda a página principal; `TelegramBotService.deliverKeyboard` uma vez por conversa antiga, marca antes do envio; menu do Telegram com `/status`, `/teclado`, `/ajuda`. Testes novos: páginas e permissões, pareamento com teclado, envio único que não repete ao reconectar, menu de três | `gradlew.bat desktopTest` (mesmo filtro da T1): verde depois de ajustar o teste de idioma do menu (`teclado`/`keyboard` em segundo, no lugar de `conta`/`account`); `gradlew.bat allTests`: 2.839 testes, 0 falhas, 0 erros |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| Conversa que tinha tirado o teclado com `/teclado off` o recebe de novo no envio único (o estado do teclado nunca foi gravado). `/teclado off` volta a tirar | aceito |
| Emoji quadrado tem largura própria por fonte; dez cabem num balão de celular no mockup, sem medição em aparelho | aberto |
| Menu com 3 comandos tira os outros 26 do autocompletar do `/` | aceito (escolha AE4) |

## Desvios do plano e achados da execução

- **Regra da zona mudou na execução.** A primeira versão ("até o primeiro limiar verde, até o último amarelo")
  nunca pintava vermelho com os limiares padrão 75/90/100 — o último é 100. Passou a ser a contagem de
  limiares passados, que reproduz a legenda do mockup com os padrões.
- **Mídia da Ajuda e fixtures de tela com a barra antiga.** `HelpMediaRecentTopics.kt` e `TelegramBotSectionTest`
  usam um `statusPreview` literal com `▰▱`; são texto fixo, não saem de `TelegramBotMessages`, e seguem
  passando. Regerar o GIF da ajuda (`generateHelpMedia`) fica fora desta entrega.
