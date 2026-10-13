# Análise da sessão no bot e no app (rodada AB)

## Ponto de situação

**Estado atual:** `Em execução (B00).`

**Direção escolhida (2026-10-13):** o usuário aprovou **todas** as opções AB1 a AB15, mais:

- área de **análises** no menu do bot;
- menu de comandos **organizado por categoria**, sem comandos soltos;
- comandos **e** resultados no **idioma configurado no app**.

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-13 |
| Branch | `feat/408-telegram-features`, a mesma do PR #409 (decisão do usuário) |
| Autor dos commits | identidade temporária `claude`, trailer `Co-Authored-By: Claude Opus 5.5` |

- Galeria da rodada em [`analise-sessao-visual/options.html`](analise-sessao-visual/options.html) (rodada **AB**).
- As alterações pré-existentes em `server/package-lock.json` e `docs/llm/` ficam fora desta entrega.

## Decisões do usuário (2026-10-13)

| # | Pergunta | Resposta |
| --- | --- | --- |
| U1 | `/idioma` por conversa (AA24) contra "respeitar o idioma do app" | **O app manda**: o `/idioma` sai; menu, respostas e alertas sempre no idioma do app |
| U2 | Ação "Análise" no card e no balão da HUD pede valor novo em `CardAction` (enum) | **Exceção aprovada**: `CardAction.ANALYSIS`, como as abas da #399 |
| U3 | Onde nasce o trabalho | **Na `feat/408-telegram-features`**, dentro do PR #409 |

## Diagnóstico (evidência)

| # | Ponto | Evidência | Estado |
| --- | --- | --- | --- |
| D1 | O menu do bot não segue a troca de idioma do app | `TelegramBotService.registerMenu` só roda quando o token muda (`pollLoop`, `settings.botToken != activeToken`); o idioma é lido uma vez ali | confirmado |
| D2 | O menu é uma lista plana de 30 comandos, na ordem de chegada | `TelegramBotMessages.menu` e `help` são listas literais, PT e EN escritas à mão em duas cópias | confirmado |
| D3 | O Telegram não tem seção no menu de comandos | `setMyCommands` aceita só pares nome/descrição | confirmado (API) — a categoria vai na ordem e no prefixo da descrição |
| D4 | Ritmo em pp/h tem fonte | histórico de cotas (`GetUsageHistoryUseCase`, `UsageHistoryPoint.capturedAt/used/total`) | confirmado |
| D5 | Tokens/s por intervalo | `OutputThroughput` é soma de tokens e de tempo (`outputTokens`, `generationMillis`); por diferença de dois recortes `since` dá o intervalo sem média de razões. `getHourlyUsage` não traz tempo de geração | **confirmado na B04**: os dois devolvem somas por modelo "desde" um instante |
| D6 | `CardAction` é o dono das ações do card e do balão | `CardActions.kt`, `cardActionsFor` | confirmado |

## Organização do menu (B01)

Uma tabela só (`TelegramCommandCatalog`), de onde saem o `setMyCommands`, o `/ajuda` e o teste que exige que todo comando do
catálogo seja entendido pelo `parseBotCommand`. Ordem e prefixo da descrição dão a categoria no menu do Telegram:

| Categoria | Comandos |
| --- | --- |
| 📊 Agora | `/status`, `/conta`, `/atualizar`, `/qual`, `/reinicios`, `/quando`, `/ping` |
| 📐 Análises | `/analise`, `/ritmo`, `/vazao`, `/semana`, `/janelas`, `/comparar`, `/pico`, `/grafico`, `/resumo`, `/exportar` |
| 🔔 Alertas | `/alertas`, `/limiar`, `/silencio`, `/foco`, `/avise` |
| 💬 Conversa e PC | `/conversas`, `/sair`, `/teclado`, `/hud`, `/time`, `/api`, `/ajuda` |

## Execução — uma atividade, um commit

| # | Atividade | Opção |
| --- | --- | --- |
| B00 | Plano, galeria AB e rodada no histórico da skill de opções visuais | — |
| B01 | `refactor(telegram)`: catálogo único de comandos por categoria; menu e `/ajuda` saem dele | menu |
| B02 | Idioma do app manda: o menu é re-registrado quando o idioma do app muda; `/idioma` e o idioma por conversa saem | U1 |
| B03 | Auditoria de texto fixo num idioma só nas respostas do bot | U1 |
| B04 | Domain: `SessionAnalysis` (5h e semanal, intervalos, horas, dias, fatias, janela anterior) e leitura dos dados | base |
| B05 | `/analise`: escolha da conta, abas por botão (`ana:`) e o painel de bordo | AB12, AB1 |
| B06 | Sparkline por métrica | AB2 |
| B07 | Intervalos lado a lado | AB3 |
| B08 | Leitura em frases | AB4 |
| B09 | Linha do tempo da janela | AB5 |
| B10 | Placar Anthropic × Codex | AB13 |
| B11 | Antes × agora | AB14 |
| B12 | Velocímetros (PNG) | AB6 |
| B13 | Ritmo por hora 24 h (PNG) | AB7 |
| B14 | Mapa de calor dia × hora (PNG) | AB8 |
| B15 | Curva da janela e ritmo seguro (PNG) | AB9 |
| B16 | Dias da semana (PNG) | AB10 |
| B17 | Cartão de resultado (PNG) | AB11 |
| B18 | Relatório em PDF | AB15 |
| B19 | Botão "📐 Análise" no `/status` e no `/conta` | entrada |
| B20 | App: `CardAction.ANALYSIS` e a janela "Análise" (texto e abas) | entrada, U2 |
| B21 | App: gráficos da janela "Análise" | AB6–AB10 |
| B22 | Fechamento: `gradlew.bat allTests`, docs, descrição do PR #409 | — |

Regras que valem para todas:

- Número que a fonte não informa não é derivado: sem histórico, a linha diz "sem leitura" (`null` ≠ 0).
- Ritmo é diferença de percentual entre leituras ÷ tempo; tokens/s é soma ÷ soma, nunca média de razões.
- Só metadados de uso saem: nada de prompt, resposta, projeto, `cwd` ou `session_id` nas análises.
- Toda resposta e todo rótulo nos dois idiomas, escolhidos pelo idioma do app.

## Pontos de situação

| Atividade | Commit | Evidência | Estado |
| --- | --- | --- | --- |
| B00 | `docs: plan the session analysis round` | galeria aberta no painel do navegador: 15 opções + referência + entrada, console sem erro, sem rolagem horizontal a 375 px; publicada para o usuário, que escolheu AB1–AB15 e respondeu U1–U3 | concluída |
| B01 | `refactor(telegram): build the command menu and help from one catalog by category` | `gradlew.bat desktopTest --tests "*Telegram*"`: 111 testes, 0 falhas (novos: todo nome do catálogo, PT e EN, é comando que o bot entende; categorias contíguas no menu com o emoji da categoria abrindo a descrição; nomes só `a-z0-9_`; um cabeçalho por categoria no `/ajuda` de cada idioma). `TelegramBotMessages` caiu de 353 para 277 linhas | concluída |
| B02 | `feat(telegram): follow the app language everywhere and drop /idioma` | `gradlew.bat desktopTest --tests "*Telegram*"`: 111 testes, 0 falhas (novo: trocar o idioma do app registra o menu de novo — `conta` → `account` — e o alerta seguinte sai em inglês nas duas conversas; `/idioma en` passou a ser comando desconhecido). `TelegramChat.language`, `languageOf`, `BotCommand.ChatLanguage` e `languageSet` removidos | concluída |
| B03 | `fix(telegram): show Portuguese series labels in the app language` | varredura de literais acentuados fora de ramo de idioma nos `Telegram*Messages`, renderizadores, serviço e `UsageAlertMessages`: todas as mensagens já tinham EN; vazavam as chaves de série PT e o "Token do bot recusado" das Configurações. `gradlew.bat desktopTest --tests "*Telegram*" --tests "*UsageAlert*"`: 181 testes, 0 falhas (novo: `Saldo` → `Balance`, `Go semanal` → `Go weekly`, rótulo neutro igual, PT intacto, `/status` em inglês sem "Saldo") | concluída |
| B04 | `feat(analysis): build the session analysis from quota history and CLI throughput` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*SessionAnalysis*"`: 112 + 4 testes, 0 falhas (novos, cenário 12:30 BRT com a 5h em 62%: ritmo agora 21 pp/h, médio 17,7, seguro 25,3, ≈93% no reset; intervalos e fatias com 50 tok/s exatos por diferença de recortes; anterior no mesmo ponto 71%; dias locais e mapa 7×24; sem índice a vazão fica nula). D5 confirmado: Claude `getModelThroughputs(perfil, since)` e Codex `getModelUsage(since)` devolvem somas | concluída |
| B05 | `feat(telegram): open the session analysis with tabs from /analise` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*SessionAnalysis*"`: 120 testes, 0 falhas (novos: parse com fonte, placar e vista em qualquer ordem, `5h img` → curva, palavra desconhecida inválida, só leitura; toques `ana:`/`anv:` vão e voltam com a chave que tem `:`; painel PT e EN com vírgula/ponto, folga e vazão por modelo; aba atual marcada e `callback_data` ≤ 64 bytes; no serviço, `/analise` com uma conta analisável abre o painel e a aba edita a mesma mensagem). `TelegramAnalysisHandler.kt` separado do `TelegramBotHandlers` (479 linhas) | concluída |
| B06 | `feat(telegram): add the trend view to the session analysis` | `gradlew.bat desktopTest --tests "*TelegramAnalysis*"`: 3 testes, 0 falhas (novo: uma linha por métrica — 5h % das fatias da janela, pp/h e tok/s por hora, semanal por dia —, valor de agora e seta; não medido vira espaço na linha; PT e EN) | concluída |
| B07 | `feat(telegram): add the pace by interval view to the session analysis` | `gradlew.bat desktopTest --tests "*TelegramAnalysis*"`: 4 testes, 0 falhas (novo: 1 h a 7 d com pp/h da 5h, tok/s e razão contra 7 d — `1 h 14,0 48 ↑ 1,9×`; 7 d sem razão consigo; PT e EN) | concluída |
| B08 | `feat(telegram): add the plain reading view to the session analysis` | `gradlew.bat desktopTest --tests "*TelegramAnalysis*"`: 5 testes, 0 falhas (novo: "A 5h está em 62% e chega a ~83% no reinício das 14:00 — não estoura", "13% abaixo da janela anterior no mesmo ponto", hora mais pesada 15h (17 pp), semanal com o ritmo que cabe, vazão por modelo; sem janela anterior nem vazão, as frases somem em vez de virar zero) | concluída |
| B09 | `feat(telegram): add the 5h window timeline to the session analysis` | `gradlew.bat desktopTest --tests "*TelegramAnalysis*"`: 6 testes, 0 falhas (novo: fatias de 30 min desde 09:00 com barra sobre a maior fatia, pontos, ritmo da fatia e vazão — `09:00 ▰▰▰▱▱ 6 pp 12/h 50 t/s`; rodapé com total e reinício; sem janela aberta, a frase diz isso) | concluída |

## Problemas em aberto e riscos

| # | Risco | Estado |
| --- | --- | --- |
| R1 | Nada é conferido contra o Telegram real (edição com teclado, `sendPhoto`, `sendDocument`) | aberto |
| R2 | Tokens/s por intervalo pode não existir para o Codex (D5) | fechado na B04: `CodexCliModelUsage.throughput`. O Codex mede a máquina toda, não a conta |
| R3 | O PR #409 cresce com esta entrega (decisão U3) | aceito |

## Desvios do plano e achados da execução
- B03: o **app** em inglês também mostra "Saldo"/"Go semanal" (`expandedQuotaTitle` devolve o rótulo cru em cota de moeda) —
  fora do escopo do bot; virou tarefa separada (reaproveitar `displayQuotaLabel` nas telas).
