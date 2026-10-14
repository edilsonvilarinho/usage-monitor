# Issue #408 — Modo Telegram: 30 funções novas para o bot

## Ponto de situação

**Estado atual:** `Concluído — A00–A32 executadas; PR aberto para revisão.`

**Direção escolhida (2026-10-13):** o usuário aprovou **todas** as opções, AA1 a AA30 ("pode fazer todos").

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-13 |
| Branch | `feat/408-telegram-features` (criada a partir de `main` em `e61e9a5f`) |
| Autor dos commits | identidade temporária da skill `usage-monitor-commit-push`, trailer `Co-Authored-By: Claude Opus 5.5` |

- As alterações pré-existentes em `server/package-lock.json` e `docs/llm/` ficam fora desta entrega.
- Galeria das 30 opções em [`issue-408-visual/options.html`](issue-408-visual/options.html) (rodada **AA**).

## Diagnóstico (evidência)

| # | Ponto | Evidência | Estado |
| --- | --- | --- | --- |
| D1 | Issue sem especificação além do número de ideias | `gh issue view 408`: "pense em 30 usando nossa skill e caso tenha visual use nossa skill /usage-monitor-visual-options" + print | confirmado |
| D2 | Menu atual tem 10 comandos | `TelegramBotMessages.menu` e print da issue | confirmado |
| D3 | Mesma "Sessão CLI saturada" chegou 3× (06:55, 11:43, 11:43+) para `alem-das-sombras` | print da issue | confirmado (efeito) |
| D4 | Causa da repetição | dedup por `sessionId`, rearma quando a sessão sai do pulso (`UsageAlert.kt`, `evaluateSessionAlerts`) — sessões distintas do mesmo projeto ou rearme | **não confirmada**; por decisão do usuário, sem diagnóstico nesta issue (vira a opção AA1) |
| D5 | `BotButton` é enum | `TelegramBot.kt`: `enum class BotButton` | confirmado — botão novo usa `BotTap` com prefixo |
| D6 | Não existe envio de arquivo | `TelegramBotApi.kt`: `sendPhoto`, sem `sendDocument` | confirmado |
| D7 | `TelegramBotHandlers.handle` é uma cadeia de `if` por comando | `TelegramBotHandlers.kt:63-138` | confirmado |

## Decisões

- **As 30 são inéditas** (decisão do usuário, 2026-10-13): Y3 `/custo`, Y7 `/sessoes`, Y9 botões no alerta e Y10 PDF
  semanal, não escolhidas na #398, ficam fora — regra da skill `usage-monitor-visual-options`.
- Ação remota no PC ou dado de terceiros nasce **desligado** atrás de interruptor (precedente `allowSourceControl`):
  AA16, AA17, AA21, AA29.
- Botão novo: `BotTap` com prefixo curto em `callback_data` (≤ 64 bytes, nunca rótulo nem e-mail); nenhum valor novo em
  `BotButton`.
- Só metadados de uso saem: nunca prompt, resposta, `cwd` ou `session_id`.
- **AA20 — origem do nome verificada**: `LocalCliSessionDataSource.resolveHostName` (`COMPUTERNAME`/`HOSTNAME`,
  `InetAddress` por último) já dá o nome da máquina ao índice CLI; a função sai para um utilitário compartilhado.
- **AA16 — `/time` usa a presença** (`GetTeamPresenceUseCase`, as mesmas contas de `buildSessionPulseTargets`): online,
  trabalhando agora e sessões ativas por apelido. O consumo em dinheiro por pessoa do mockup fica fora — exigiria um recorte
  de `CliSessionRange` que a presença não tem e a regra proíbe criar.
- **AA5 — `/vazao` sai das linhas da comparação** (`BuildModelComparisonUseCase` com 24 h): a vazão por modelo já vem dali,
  ponta a ponta e somada (nunca média de razões).
- **Alerta crítico** (AA8, AA25, AA26) tem um dono só, `isCriticalAlert` no domain: cota em ≥ 90% ou esgotada.
- **Fiação do bot sai do `AppViewModels`** para `TelegramBotWiring.kt` (R3): cada função nova pediria um provedor a mais.
- **Comandos novos** entram por `BotCommand` e saem por despacho em arquivos por assunto, para nenhum arquivo passar de 800
  linhas nem função de 300.

## Rodada AA — opções

| # | Nome | Base no código | Custo |
| --- | --- | --- | --- |
| AA1 | Alerta repetido vira contador (edita a mensagem anterior) | `broadcast` + `editMessageText` | médio |
| AA2 | `/quando` — quando cada cota estoura | projeção de `worstQuotaRisk` | baixo |
| AA3 | `/reinicios` — linha do tempo | `resetsAt` do snapshot | baixo |
| AA4 | Lembrete N min antes do reinício | subtipo novo de `UsageAlert` | médio |
| AA5 | `/vazao` — tokens/s por modelo | `OutputThroughput` | baixo |
| AA6 | `/comparar` — mapa de calor em PNG | `BuildModelComparisonUseCase` + render Skia | médio |
| AA7 | `/semana` — semana × anterior | histórico | médio |
| AA8 | Modo foco (só crítico) | campo novo em `UsageAlertSettings` | médio |
| AA9 | Tipos de alerta no bot | interruptores já existentes em `UsageAlertSettings` | baixo |
| AA10 | Limiar por conta | mapa por `UsageTargetKey` | alto |
| AA11 | `/pico` | `UsageSpike` | baixo |
| AA12 | `/janelas 5h` | lista de janelas (#392) | médio |
| AA13 | Sparkline no `/status` | histórico | baixo |
| AA14 | `/status` em imagem com anéis | portar o anel para Skia puro | alto |
| AA15 | Painel fixado em uma linha | `TelegramBotSummaryMessages.panel` | baixo |
| AA16 | `/time` | servidor do time | alto |
| AA17 | Atualizar pelo bot | `AppUpdate*` | alto |
| AA18 | Aviso de fonte quebrada | `statsRetainedAfterFailure` | médio |
| AA19 | `/ping` | versão + horários de coleta | baixo |
| AA20 | Nome da máquina nas mensagens | origem do nome **a verificar** | médio |
| AA21 | Papéis por conversa | campo em `TelegramChat` | médio |
| AA22 | `/conversas` e `/sair` | `authorizedChats` | baixo |
| AA23 | `/exportar` CSV/JSON | `UsageExportEncoder` + `sendDocument` novo | médio |
| AA24 | `/idioma` por conversa | idioma em `TelegramChat` | médio |
| AA25 | Alertas em lote (15 min) | buffer no serviço | médio |
| AA26 | Som só no crítico | `silent` já existe | baixo |
| AA27 | `/ritmo` (estimativa) | cálculo de apresentação | baixo |
| AA28 | `/qual` — conta com mais folga | snapshot + risco | baixo |
| AA29 | `/hud` — traz a HUD à frente | `focus.request` | baixo |
| AA30 | Teclado fixo de atalhos | `ReplyKeyboardMarkup` | baixo |

## Execução — uma atividade, um commit

| # | Atividade | Opção |
| --- | --- | --- |
| A00 | Plano, galeria AA e rodada no histórico da skill de opções visuais | — |
| A01 | `refactor(telegram)`: despacho por comando, provedores do bot num só tipo, fiação em `TelegramBotWiring.kt` | — |
| A02 | `/reinicios` | AA3 |
| A03 | `/quando` (projeção no `UsageSnapshotQuota`) | AA2 |
| A04 | `/qual` | AA28 |
| A05 | `/ritmo` | AA27 |
| A06 | `/ping` | AA19 |
| A07 | `/pico` | AA11 |
| A08 | `/vazao` | AA5 |
| A09 | `/semana` | AA7 |
| A10 | `/janelas` | AA12 |
| A11 | Sparkline no `/status` | AA13 |
| A12 | `/comparar` em imagem | AA6 |
| A13 | `/status img` com anéis | AA14 |
| A14 | `/conversas` e `/sair` | AA22 |
| A15 | Papéis por conversa | AA21 |
| A16 | `/idioma` por conversa | AA24 |
| A17 | Teclado fixo | AA30 |
| A18 | `/hud` | AA29 |
| A19 | Nome da máquina | AA20 |
| A20 | `/exportar` (`sendDocument`) | AA23 |
| A21 | `/time` | AA16 |
| A22 | Atualização pelo bot | AA17 |
| A23 | Tipos de alerta no bot | AA9 |
| A24 | Som só no crítico | AA26 |
| A25 | Alerta repetido vira contador | AA1 |
| A26 | Alertas em lote | AA25 |
| A27 | Painel fixado em uma linha | AA15 |
| A28 | Modo foco | AA8 |
| A29 | Lembrete antes do reinício | AA4 |
| A30 | Fonte quebrada | AA18 |
| A31 | Limiar por conta | AA10 |
| A32 | Fechamento: `allTests`, docs, PR | — |

Molde por tipo de opção:

- **Comando**: subtipo em `BotCommand` + `parseBotCommand` (PT/EN) + texto em `TelegramBot*Messages.kt` + tratador +
  `menu()` e `help()` + testes (`TelegramBotCommandTest`, `TelegramBotMessagesTest`, `TelegramBotServiceTest`).
- **Toque**: `BotTap` com prefixo novo.
- **Opção nas Configurações**: campo em `TelegramBotSettings` (copy/equals/hashCode à mão) + DTO de
  `LocalTelegramSettingsDataSource` com default + `TelegramBotSection`/`TelegramBotOptions` + protótipo + design system,
  no mesmo commit.
- **Imagem**: modelo puro em `commonMain` (padrão `TelegramChartModel`) + render em `desktopMain`.
- Decisão registrada em `docs/integrations.md`, seção Telegram.
- Com 3 ou mais comandos novos, a primeira atividade é `refactor(telegram)`: `handle` vira despacho por comando, sem
  mudança de comportamento.

## Pontos de situação

| Atividade | Commit | Evidência | Estado |
| --- | --- | --- | --- |
| A00 | `docs: plan issue 408 telegram bot features` | galeria aberta no painel do navegador: 30 cards + referência, console sem erro, sem rolagem horizontal a 375 px; publicada para o usuário, que escolheu AA1–AA30 | concluída |
| A01 | `refactor(telegram): dispatch commands and move the bot wiring out of AppViewModels` | `gradlew.bat desktopTest --tests "*Telegram*"`: 58 testes, 0 falhas (comportamento igual; `handle` virou `when` exaustivo e a fiação foi para `TelegramBotWiring.kt`, `AppViewModels` 454 → 394 linhas) | concluída |
| A02 | `feat(telegram): list the next quota resets with /reinicios` | `gradlew.bat desktopTest --tests "*Telegram*"`: 60 testes, 0 falhas (novos: ordem do relógio com empate por conta e cota, reinício vencido fora, sem reinício conhecido; o teste do menu passou a exigir que todo item seja comando conhecido, em vez da lista fixa) | concluída |
| A03 | `feat(telegram): show when each quota runs out with /quando` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*UsageSnapshotTest*"`: 61 + 3 testes, 0 falhas (novos: estoura antes do reinício com a antecedência, não estoura, sem projeção, saldo sem reinício; `exhaustsAt` no retrato com o filtro de cota vencida) | concluída |
| A04 | `feat(telegram): pick the account with the most room with /qual` | `gradlew.bat desktopTest --tests "*Telegram*"`: 62 testes, 0 falhas (novo: menor risco, depois menor percentual, por fonte; saldo fora; única conta) | concluída |
| A05 | `feat(telegram): suggest a daily pace for long windows with /ritmo` | `gradlew.bat desktopTest --tests "*Telegram*"`: 63 testes, 0 falhas (novo: 39 pp em 3,2 dias → ~12 pp por dia; janela de menos de um dia fora) | concluída |
| A06 | `feat(telegram): answer /ping with version, uptime and last collection` | `gradlew.bat desktopTest --tests "*Telegram*"`: 64 testes, 0 falhas (novo: versão, 6 h 12 min aberto, próxima coleta e hora por conta; `TelegramBotReadings` criado para as leituras da #408) | concluída |
| A07 | `feat(telegram): explain today's usage against your habit with /pico` | `gradlew.bat desktopTest --tests "*Telegram*"`: 65 testes, 0 falhas (novo: mais forte primeiro em pontos da cota; sem anomalia, o fator das Configurações) | concluída |
| A08 | `feat(telegram): report output throughput per model with /vazao` | `gradlew.bat desktopTest --tests "*Telegram*"`: 66 testes, 0 falhas (novo: mais rápido primeiro, modelo sem vazão fora, índice ilegível) | concluída |
| A09 | `feat(telegram): compare this week with the previous one with /semana` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*UsageSnapshotTest*"`: 67 + 3 testes, 0 falhas (novo: pontos somados com seta, sem semana anterior fora; `snapshotLabel` passou a ser o dono do rótulo da conta) | concluída |
| A10 | `feat(telegram): list the latest quota windows with /janelas` | `gradlew.bat desktopTest --tests "*Telegram*"`: 68 testes, 0 falhas (novos: mais nova primeiro com pico, esgotou e faixa ativa; sem janelas semanais; argumento inválido) | concluída |
| A11 | `feat(telegram): add a 6 hour sparkline under each quota in /status` | `gradlew.bat desktopTest --tests "*Telegram*"`: 70 testes, 0 falhas (novos: blocos de 30 min com a última leitura levada adiante, menos de duas leituras sem linha, cartão com a linha só na cota com histórico) | concluída |
| A12 | `feat(telegram): send the model comparison heat map as an image with /comparar` | `gradlew.bat desktopTest --tests "*Telegram*"`: 73 testes, 0 falhas (novos: células da comparação com intensidade sobre o maior da coluna, teto de 12 linhas sem mudar a escala, PNG com altura por linha e fundo; parse 24h/7d/30d) | concluída |
| A13 | `feat(telegram): draw the account rings as an image with /status img` | `gradlew.bat desktopTest --tests "*Telegram*"`: 76 testes, 0 falhas (novos: janela mais longa por fora com teto de três arcos, maior percentual no centro, conta só com saldo fora, PNG de quatro anéis por linha com a cor do arco crítico). PNG do anel e do mapa de calor (A12) conferidos a olho numa captura temporária descartada; o núcleo cresceu (raio 64) para o percentual não cobrir o arco interno | concluída |
| A14 | `feat(telegram): list paired chats with /conversas and unpair with /sair` | `gradlew.bat desktopTest --tests "*Telegram*"`: 77 testes, 0 falhas (novos: lista com a conversa atual marcada e escape; `/sair` tira só esta conversa, esquece e desafixa o painel dela e responde por último) | concluída |
| A15 | `feat(telegram): give each paired chat a control or read-only role` | `gradlew.bat desktopTest --tests "*Telegram*"`: 79 testes, 0 falhas (novos: conversa só de leitura recebe a recusa no `/alertas off` e no toque de silenciar e continua lendo; interruptor Controle por conversa; `changesApp` exaustivo). Captura `build/issue396-screenshots/telegram-section.png` conferida: Controle e o interruptor ao lado do nome | concluída |
| A16 | `feat(telegram): choose the language of each chat with /idioma` | `gradlew.bat desktopTest --tests "*Telegram*"`: 80 testes, 0 falhas (novo: `/idioma en` numa conversa faz o alerta sair em inglês nela e em português na outra; parse pt/en/app e sem argumento) | concluída |
| A17 | `feat(telegram): offer a persistent shortcut keyboard with /teclado` | `gradlew.bat desktopTest --tests "*Telegram*"`: 82 testes, 0 falhas (novos: JSON do teclado persistente e do `remove_keyboard`; `/teclado on` manda as duas linhas, o rótulo tocado roda o comando e `/teclado off` tira) | concluída |
| A18 | `feat(telegram): bring the HUD to the front with /hud behind a PC actions switch` | `gradlew.bat desktopTest --tests "*Telegram*"`: 84 testes, 0 falhas (novos: `/hud` recusado sem a permissão e pedido com ela; interruptor nasce desligado e manda o conjunto de opções). Primeira rodada: 1 falha no teste de tela (`performScrollTo` fora de rolagem, armadilha conhecida), corrigida no teste | concluída |
| A19 | `feat(telegram): sign messages with this PC's name when asked` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CliSession*"`: 86 + 236 testes, 0 falhas (novos: assinatura só com nome conhecido e escapada; desligada nada muda, ligada alerta e resposta terminam com o nome). `resolveLocalHostName` saiu do `LocalCliSessionDataSource` (que estava na faixa 750–800 e encolheu) | concluída |
| A20 | `feat(telegram): send the CLI usage as a csv or json file with /exportar` | `gradlew.bat desktopTest --tests "*Telegram*"`: 88 testes, 0 falhas (novos: `sendDocument` multipart; `/exportar json 30d` manda o arquivo com nome de data e recorte; parse em qualquer ordem e formato inválido) | concluída |
| A21 | `feat(telegram): show who from the team is online with /time behind a switch` | `gradlew.bat desktopTest --tests "*Telegram*"`: 89 testes, 0 falhas (novo: trabalhando, online e fora em ordem total, só apelido — nome da máquina não aparece; desligado, sem time e servidor fora do ar) | concluída |
| A22 | `feat(telegram): announce a ready update once and restart it from the bot` | `gradlew.bat desktopTest --tests "*Telegram*"`: 90 testes, 0 falhas (novo: sem permissão nada sai; com ela, um aviso por versão com o botão `upd:<versão>`, toque de versão antiga não reinicia, o da pronta chama `applyUpdate`). Primeira rodada: 1 falha no teste "sample reply" — o exemplo expandido saiu da altura padrão da cena com a opção nova (armadilha "altura da cena"); a cena do teste passou a 2000 px | concluída |
| A23 | `feat(telegram): turn each alert kind on or off from /alertas` | `gradlew.bat desktopTest --tests "*Telegram*"`: 91 testes, 0 falhas (novos: `/alertas` sem argumento abre um botão por tipo, o toque inverte só aquele campo e redesenha o teclado; ida e volta do `alt:<tipo>` e da inversão para todos os tipos) | concluída |
| A24 | `feat(telegram): ring only for critical alerts` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*" --tests "*UsageAlert*"`: 93 + 2 + 64 testes, 0 falhas (novos: `isCriticalAlert` — 89% não, 90% e 100% sim, sessão e reinício nunca; com a opção padrão a cota em 75% sai sem som e a de 92% com som, desligada tudo toca; interruptor do card nasce ligado). Primeira rodada: 3 falhas por fixture — `UsageTargetKey(ANTHROPIC)` exige perfil; os testes passaram a usar Codex | concluída |
| A25 | `feat(telegram): edit a repeated alert into a counter instead of a new message` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*"`: 97 + 2 testes, 0 falhas (novos: `alertGroupKey` junta sessões do mesmo projeto e separa projeto, limiar e sessão sem projeto, crítico nunca agrupa; texto `2×`/`3× desde` com `última:` em BRT; a segunda saturada do mesmo projeto edita a mensagem 801 em vez de enviar, desligado abre outra; interruptor nasce ligado) | concluída |
| A26 | `feat(telegram): batch non critical alerts every 15 minutes` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*"`: 99 + 2 testes, 0 falhas (novos: `batchAlertItems` um item por grupo na ordem da chegada com o alerta mais recente; 75% + duas saturadas do mesmo projeto + 92% → o crítico sai na hora e o lote sai depois como `🔔 3 alertas`, com `(2×)` e sem som; interruptor nasce ligado). Os testes da A24/A25 seguiram verdes com o lote ligado (lote de um alerta sai como avulso) | concluída |
| A27 | `feat(telegram): fit the pinned panel in one line` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*"`: 100 + 2 testes, 0 falhas (novos: `compactPanel` em duas linhas — risco, conta escapada e pior percentual, saldo com o valor, hora da coleta embaixo e texto igual com só o `generatedAt` mudando; painel do serviço nasce em uma linha; interruptor nasce ligado). `fullestQuota` passou a ser o dono da "cota mais cheia" do painel e do resumo | concluída |
| A28 | `feat(telegram): let only critical alerts through for a while with /foco` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*" --tests "*UsageAlert*"`: 169 testes, 0 falhas (novos: no foco 80% espera, 92% sai com 75 e 90, a saturada sai só depois do fim; parse `/foco 2h`, `/focus 3`, `off`, 13 h e sem argumento inválidos, `changesApp`; `/foco 2h` grava o prazo e responde "Foco até 11:00"). Primeira rodada: 1 falha de inicialização — o teste novo foi anexado depois das funções de topo do `UsageAlertTest`, fora da classe; movido para dentro | concluída |
| A29 | `feat(alerts): remind before a high quota resets, with /avise in the bot` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*" --tests "*UsageAlert*" --tests "*AlertSettings*"`: 185 testes, 0 falhas (novos: 80% a 20 min não lembra, a 14 min lembra uma vez com 14 min, de novo na mesma janela não, 40% nunca, desligado nunca; `/avise 30`, `/remind 15min`, `off`, 2 e sem argumento inválidos; persistência ausente = 15, `0` = desligado, 999 → 120). `UsageAlert.ResetSoon` é subtipo do `sealed`, não valor de enum; o kit `Settings.jsx` não desenha a seção de alertas, nada a atualizar ali | concluída |
| A30 | `feat(telegram): warn when a source keeps failing and when it is back` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*" --tests "*UsageAlert*" --tests "*AlertSettings*"`: 188 testes, 0 falhas (novos: `evaluateSourceHealth` — aviso uma vez aos 15 min com a hora da falha e dos números, nada aos 20, "voltou" só depois de avisar, silêncio adia, falha curta calada, fonte desligada sai; serviço manda o motivo escapado e "Cursor voltou — coleta 09:00"; interruptor nasce ligado) | concluída |
| A31 | `feat(alerts): set quota thresholds per account from /limiar` | `gradlew.bat desktopTest --tests "*Telegram*" --tests "*CriticalAlert*" --tests "*UsageAlert*" --tests "*AlertSettings*" --tests "*UsageSnapshot*"`: 195 testes, 0 falhas (novos: conta com 50,80 avisa em 60% e as outras não; `quotaPercentsFor` volta ao geral sem limiar próprio; `/limiar` sem números vira o menu e o toque `thr:` vai e volta; persistência com tabulação no rótulo e linha ilegível descartada; no serviço, `/limiar` → toque → `50,80` grava só a conta, `geral` sem pedido aberto não muda nada e com pedido devolve ao geral). Primeira rodada: compilação falhou — os botões do menu eram uma lista plana e o teclado pede linhas | concluída |
| A32 | `docs: close the issue 408 plan` | `gradlew.bat allTests`: 2673 testes, 0 falhas, 0 ignorados (inclui `ArchitectureRulesTest`: nenhum arquivo de produção acima de 800 linhas — o maior tocado, `UsageAlert.kt`, com 637). Branch publicada e PR aberto contra `main` | concluída |

## Problemas em aberto e riscos

| # | Risco | Estado |
| --- | --- | --- |
| R1 | Nada é conferido contra o Telegram real pelo agente (limites de edição, `sendDocument`, `ReplyKeyboardMarkup`) | aberto |
| R2 | AA20: dois PCs com o mesmo token fazendo `getUpdates` recebem 409 do Telegram — exige decisão de quem faz polling | aberto, só se AA20 for escolhida |
| R3 | `AppViewModels.kt` (454 linhas) cresce com a fiação; acima de ~700, extrair | fechado na A01: fiação em `TelegramBotWiring.kt`, `AppViewModels` com 394 linhas |

## Desvios do plano e achados da execução
- Pós-A32 (CI do PR #409, run 38461985034): `machine name signs alerts…` falhou com `ConcurrentModificationException` no
  `waitUntil` — o `FakeTelegramApi` usava `Collections.synchronizedList` e o teste a percorria sem o lock enquanto o serviço
  gravava. Listas trocadas por `CopyOnWriteArrayList`; `gradlew.bat desktopTest --tests "*TelegramBotService*"`: 33 testes, 0 falhas.
- A03: a linha do `/reinicios` no `/ajuda` não tinha entrado na A02 (a substituição não casou e passou calada); entrou aqui junto com a do `/quando`. Daqui em diante as edições falham quando o trecho não existe.
- A12/A13: na conferência a olho, com três arcos o percentual do centro cobria o arco interno; raio e passo do anel foram ajustados antes do commit. O custo do mapa de calor sai no formato da janela de comparação (`formatMicrosUsd`, quatro casas), reaproveitado sem mudança.
- A18: as opções novas da #408 passaram a morar em `TelegramBotPreferences` (data class sem segredo) em vez de campos soltos no `TelegramBotSettings`, para não reescrever o `copy`/`equals`/`hashCode` à mão a cada função.
- A24: "Só alerta crítico toca" nasce **ligado** (o mockup AA26 aprovado mostra o interruptor ligado; não age no PC nem expõe
  dado). Quem já tinha o bot passa a receber os alertas não críticos sem som. Teste e respostas a comandos seguem com som.
- A25: o agrupamento é só em memória (reiniciar o app abre mensagem nova) e o crítico nunca agrupa, para não perder o som
  da A24. A causa das repetições (D4) continua sem diagnóstico, como decidido.
- A24–A26: as três opções de entrega nascem **ligadas**, como os interruptores do mockup aprovado. Efeito para quem já
  usa o bot: alerta não crítico chega sem som, até 15 min depois e, repetido no dia, como contador. Cada uma desliga no card.
- A29: o lembrete nasce ligado em 15 min (mockup AA4 com o interruptor ligado) e vale para a bandeja, porque mora na decisão
  pura. No registro, ausente é o padrão e `0` é desligado — sem isso, quem já tinha o app nunca o receberia.
- A30: "3 coletas seguidas" do mockup AA18 virou **15 min seguidos** falhando. O `UiState` é `StateFlow` (duas falhas
  iguais não emitem), e contar coletas pediria um evento no `DashboardViewModel`, que está em 751 linhas (faixa que não
  cresce). 15 min = três coletas no ritmo sem sessão.
- A21: o `/time` mostra presença (online, trabalhando, sessões ativas) e não o consumo em dinheiro por pessoa do mockup AA16 — a presença não traz custo, e somar sessões do time num recorte pediria um `CliSessionRange` novo (proibido).
