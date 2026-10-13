# Issue #408 — Modo Telegram: 30 funções novas para o bot

## Ponto de situação

**Estado atual:** `Execução das 30 opções (A00–A32).`

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

## Problemas em aberto e riscos

| # | Risco | Estado |
| --- | --- | --- |
| R1 | Nada é conferido contra o Telegram real pelo agente (limites de edição, `sendDocument`, `ReplyKeyboardMarkup`) | aberto |
| R2 | AA20: dois PCs com o mesmo token fazendo `getUpdates` recebem 409 do Telegram — exige decisão de quem faz polling | aberto, só se AA20 for escolhida |
| R3 | `AppViewModels.kt` (454 linhas) cresce com a fiação; acima de ~700, extrair | medido |

## Desvios do plano e achados da execução

- (preenchido a cada atividade)
