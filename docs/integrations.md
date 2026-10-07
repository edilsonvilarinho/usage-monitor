# Integrations

Reference for every source Usage Monitor reads: endpoint, credentials, and known limits.
For the short version, see the table in the [README](../README.md#supported-integrations).

| Integration | Type | Data source | Local requirement |
|---|---|---|---|
| Anthropic | Remote | `GET https://api.anthropic.com/api/oauth/usage` | `~/.claude/.credentials.json` |
| Codex | Remote | `GET https://chatgpt.com/backend-api/wham/usage` | `~/.codex/auth.json` and `~/.codex/cap_sid` |
| Codex CLI sessions | Local | reads `<CODEX_HOME>/sessions/**/*.jsonl` | local rollouts; no authentication required |
| MiniMax | Remote | `GET https://www.minimax.io/v1/token_plan/remains` | API key entered in **Settings > APIs** |
| DeepSeek | Remote | `GET https://api.deepseek.com/user/balance` | API key entered in **Settings > APIs** |
| OpenCode Zen Free | Local | reads `~/.local/share/opencode/opencode.db` | an existing local OpenCode database |
| OpenCode Go | Remote | `GET https://opencode.ai/zen/go/v1/usage` | API key entered in **Settings > APIs** |
| Kilo Free | Local | reads `~/.local/share/kilo/kilo.db` | an existing local Kilo database |
| OpenRouter | Remote | `GET https://openrouter.ai/api/v1/credits` | API key entered in **Settings > APIs** |
| Gemini CLI | Local | reads `~/.gemini/tmp/*/chats/*.jsonl` | local Gemini CLI session history; token activity only |
| Cursor | Remote | `GET https://cursor.com/api/usage-summary` | existing signed-in Cursor editor session; undocumented personal route |
| Antigravity CLI | Local | `agy --output-format json --print /usage` | Antigravity CLI 1.2.9+ installed and already authenticated |

Usage Monitor **only reads** these files. It never runs a login or logout flow, and it never deletes
a credential file.

## Expected local files

| Path | What it holds |
|---|---|
| `~/.claude/.credentials.json` + `~/.claude.json` | Anthropic default profile: OAuth token and identity |
| `<CLAUDE_CONFIG_DIR>/.credentials.json` + `.claude.json` | Anthropic custom profile |
| macOS Keychain, entry `Claude Code-credentials` | Anthropic on macOS, when the file is absent |
| `~/.codex/auth.json` | Codex bearer token, at `tokens.access_token` |
| `~/.codex/cap_sid` | Codex session cookie |
| `~/.usage-monitor/api-keys.json` | MiniMax, DeepSeek, OpenCode Go and OpenRouter keys — atomic write, owner-only permissions |
| `~/.local/share/opencode/opencode.db` | OpenCode local activity |
| `~/.local/share/kilo/kilo.db` | Kilo local activity |
| `~/.gemini/tmp/<project>/chats/*.jsonl` | Gemini CLI session metadata and token counts; prompt/response content is not retained |
| `%APPDATA%/Cursor/User/globalStorage/state.vscdb` (Windows), `~/Library/Application Support/Cursor/User/globalStorage/state.vscdb` (macOS), or `~/.config/Cursor/User/globalStorage/state.vscdb` (Linux) | Cursor session values read from SQLite in read-only mode |
| `%LOCALAPPDATA%gyingy.exe` (Windows) or `agy` on `PATH` | Antigravity CLI; Usage Monitor runs `/usage` against its existing authenticated session |

Environment variables are never read for API keys.

## Anthropic

- Discovers the default `~/.claude` profile, the `CLAUDE_CONFIG_DIR` inherited at startup, and any
  `~/.claude-*` directory that holds an Anthropic configuration. Extra profiles can also be
  registered by hand in **Settings > Anthropic accounts**.
- Newly detected profiles stay **disabled until you confirm them**. Enabled profiles all show at
  once, one card per account or workspace. Duplicate paths and duplicate identities do not produce
  duplicate collection.
- Tracks the `five_hour` and `seven_day` windows, plus **extra usage credits** as a third quota on
  the card, in the account's real currency — which is not always USD.
- Required headers:
  - `Authorization: Bearer <accessToken>`
  - `anthropic-beta: oauth-2025-04-20`
  - `User-Agent: claude-code/1.0.0`

### Token refresh

When the token is close to expiring, Usage Monitor refreshes it against
`POST https://platform.claude.com/v1/oauth/token`, writing the result atomically and guarding
against concurrent modification of the file.

The request body carries `client_id` and `scope` on top of `grant_type` and `refresh_token`. The
endpoint validates the **shape** before it looks at the grant: without `client_id` it answers
`400 Invalid request format` for any refresh token. The rewrite preserves the nodes the app does not
declare — `mcpOAuth` and `refreshTokenExpiresAt` — instead of round-tripping the file through a DTO.

On Windows, a variable set only with `$env:CLAUDE_CONFIG_DIR` affects the current PowerShell session
and its children. Usage Monitor uses the registered profiles and does not depend on being launched
from the same terminal.

## Codex

- Reads the bearer token from `~/.codex/auth.json` (`tokens.access_token`) and the `cap_sid` cookie
  from `~/.codex/cap_sid`.
- The response is the source of truth for every window it carries. Each window is classified by
  `limit_window_seconds` (5h, 7d, 28–31 days, otherwise "reported"), not by the field it came in.
- **One window is enough** (corrected on 2026-09-27, issue #324 — this section used to say both were
  required, and the code had stopped requiring that): `CodexMapper` maps whatever is present, and
  `isPersistableDashboardStats` only rejects an empty list. A duplicate or unknown window flags the
  source as unstable.
- **Model-level limits come from the local rollout** (issue #324). `wham/usage` returns the account
  limit only; a per-model limit (`GPT-5.3-Codex-Spark` in codenotch#286, with the live read at 0%
  while the model was blocked) appears in the `rate_limits` of each `token_count` event of
  `$CODEX_HOME/sessions/**/rollout-*.jsonl`. Shape observed locally on 2026-09-27: `limit_id`
  (`codex`, `premium`), `limit_name`, `primary`/`secondary` with `used_percent`, `window_minutes` and
  `resets_at` (epoch seconds), `plan_type`. `LocalCodexRolloutRateLimitDataSource` reads the tail
  (256 KB) of the five newest rollouts by date tree, `CodexRolloutRateLimitParser` keeps the latest
  event per `limit_id`, and `CodexMapper.modelLimitQuotas` adds them **after** the live windows,
  labelled `Codex limit <name> (5h|7d)` (`CodexQuotaLabels`, series key — do not rename). Dropped:
  `limit_id = codex` (the live read already has it), a `plan_type` different from the live one (the
  rollout is from another account), windows without percentage or duration, and windows already
  reset. The percentage is truncated, like Anthropic's. A failed rollout read never fails the source.
  Measured on this machine: 215 ms for the first read, only `codex` found, so no extra quota — the
  model-limit path is covered by fixtures shaped like the codenotch report, not by a real account.

- **Extra accounts** (issue #329). The default account (`~/.codex`) keeps the bare target
  `UsageTargetKey(CODEX)` — card order, persisted backoff and the dashboard cache written before
  this version stay valid. Each extra account is a directory used as `CODEX_HOME` by the Codex CLI
  (`auth.json`, `cap_sid` and `sessions/` directly inside it, not under `.codex`), stored by
  `CodexProfileRegistry` in the `codexProfiles` preference node with an id prefixed `codex-` so it
  never collides with an Anthropic `profileId`. `CodexRepositoryImpl.getUsage(profile)` reads that
  directory through `codexProfileSources` and stamps `targetKey`/`profileLabel` on the stats. The app
  never refreshes the token: a directory no CLI uses ends up with an expired session, and the card
  says so. Out of scope by decision: per-account color/emoji, CLI-session filter and team sync.

### Codex CLI local sessions

- This is a separate local source from the remote Codex quota card. It reads rollout files below
  `$CODEX_HOME/sessions`; when the variable is absent, the default is `~/.codex/sessions`.
- Reading does not open `auth.json`, `cap_sid`, API keys, or any login flow. The local index is
  stored separately in `~/.usage-monitor/codex-cli-history.db` and does not alter Claude tables.
- The parser consumes only `session_meta`, `turn_context`, and `token_usage_record`. Prompt,
  response, reasoning text and tool payloads are ignored. `usage` is treated as a per-response
  delta; `turn_token_usage` and `thread_token_usage` are cumulative and are never added as deltas.
- The index is incremental and tolerates incomplete final lines, malformed/unknown records and
  file truncation. Unknown data is counted as a warning instead of aborting the dashboard.
- The rollout schema is internal and may change without notice. `source` is classified from the
  observed metadata and falls back to `UNKNOWN`; it is not inferred from the directory alone.
- The screen offers sliding 5-hour, 7-day and total views and exports the displayed summary as
  CSV/JSON. Export contains metadata and token counters only. There is no USD cost column because
  ChatGPT plan consumption cannot be converted safely to an API price without a verified tariff.
- Usage Monitor never deletes or rewrites the original rollout files. Closing the app does not
  remove the local aggregate index; deleting that database is the manual reset operation.

## MiniMax

- Enabling it in **Settings > APIs** asks for the key in a masked field; the integration only turns
  on after the key is saved.
- Quotas are counted in **requests**, not tokens. Only `MiniMax-M*` models are shown.

## DeepSeek

- Same masked-key flow as MiniMax.
- The dashboard shows the paid balance and, when present, the granted balance. Values are treated as
  USD.
- A prepaid balance does not reset, so it is **not** measured against the time-to-reset ruler used by
  windowed quotas. It uses an absolute runway instead: critical under 7 days, warning under 14.

## OpenCode Zen Free

- Makes no HTTP call. Reads observed activity from the local `~/.local/share/opencode/opencode.db`.
- Counts `assistant` messages from the `opencode` provider, grouped into 5h and 7d windows.
- Watches free models such as `*-free` and `big-pickle`.

## OpenCode Go

A **separate** integration from OpenCode Zen Free: that one reads the local database, this one
queries the paid subscription over HTTP. A machine may have one without the other, and both appear
as distinct rows in **Settings > APIs**.

- Uses the **same OpenCode API key** used for Zen `chat/completions`, entered in a masked field.
- Shows three windows as **percentages** — rolling 5h, weekly and monthly — each with its reset time.
- The API returns **no monetary value** — no spend, no limit. There is no balance line on this card,
  and no token count is inferred from the percentage.
- A valid key on an account **without the Go plan** answers `403 EntitlementError`. That is the
  normal state for someone who only uses paid Zen, so it becomes its own notice — subscribe or turn
  the integration off — rather than a credential error asking you to log in again. There is no
  "retry" action: retrying returns the same 403.
- The endpoint is in production but is **not publicly documented** and carries no version. A missing
  window degrades gracefully; a response with none of the three windows is treated as a failure, so
  the cached reading is preserved instead of being overwritten by a reading that measured nothing.
- The **paid Zen balance is not read**: no endpoint exists for it. `/zen/v1/balance` answers 404
  (upstream issue `anomalyco/opencode#10448`, open).

## Kilo Free

- Makes no HTTP call. Reads observed activity from the local `~/.local/share/kilo/kilo.db`.
- Counts `assistant` messages from the `kilo` provider, grouped into 5h and 7d windows.
- Watches free models such as `kilo-auto/free`, `*/free` and `*:free`.

## OpenRouter

- Same masked-key flow as MiniMax and DeepSeek. The key is the same one used for
  `chat/completions` against OpenRouter's models.
- Reads `GET /api/v1/credits`, which accepts the regular inference key — no separate
  "Provisioning API Key" is required. The dashboard shows the balance as `total_credits -
  total_usage`, matching the "Total Available" figure on OpenRouter's own Credits page.
- Deliberately **does not** use `GET /api/v1/key`: its `limit`/`limit_remaining` fields describe an
  optional per-key spending cap, not the account balance — they stay `null` even on a funded
  account with no cap configured.
- A prepaid balance does not reset, so it is **not** measured against the time-to-reset ruler used
  by windowed quotas. It uses an absolute runway instead, same as DeepSeek: critical under 7 days,
  warning under 14.

## Gemini CLI local usage

- Reads Gemini CLI session files from `~/.gemini/tmp/<project>/chats/*.jsonl` on Windows, macOS and
  Linux. The path and session retention behavior are described in the
  [official session-management guide](https://github.com/google-gemini/gemini-cli/blob/main/docs/cli/session-management.md).
- Session files are append-only JSONL. They contain prompts, responses, tool calls and other private
  content. The parser uses only the session/message IDs, timestamps, model names and token counters;
  it does not persist or log conversation content.
- Reports observed token counts per model over rolling 5-hour and 7-day windows. A token count is not
  an account quota: no limit, percentage or cost is inferred from it. `tokens.total` is used; it
  already includes the cached input, so cached tokens are not added a second time.
- Each call is written twice with the same `id` — without `tokens` when the turn starts and with them
  when the usage arrives. Only the write with tokens counts, and a later tokenless write never erases
  it. `{"$set": …}` patches, which the CLI appends after every message, do not change the count.
  `{"$rewindTo": …}` undoes the conversation, not the bill: the rewound call stays counted.
- Google account quota is a separate source. Gemini CLI documents `/stats model` as an interactive
  view of model token counts and quota information, but this integration has no stable
  machine-readable quota contract. No Google quota card is created until a verifiable source is
  available.
- No `~/.gemini/tmp` directory means Gemini CLI never ran on this machine: the card shows its empty
  state, with no warning. Recent session files that exist but none of which is readable are a
  failure that preserves the last valid reading; a corrupt file among valid ones is skipped.
- Files last modified before the 7-day window are not read, and unchanged files are served from an
  in-memory cache keyed by path, size and modification time.

## Cursor

The official Cursor Admin API is team-scoped. Cursor documents team analytics for Team and
Enterprise customers, while API access to analytics is limited to Enterprise and requires a team
API key. That contract does not cover a personal account's own usage without team administration.
See [Cursor Analytics](https://cursor.com/docs/account/teams/analytics) and the
[Cursor Admin API](https://prod.cursor.com/docs/account/teams/admin-api).

For personal usage, this integration follows the individual-account source referenced by
[Codenotch](https://github.com/vinzdg/codenotch/blob/main/Sources/Providers/CursorLocalProvider.swift):

- Reads the active session from Cursor's local `state.vscdb`, using a read-only SQLite connection.
  Default paths are:
  - Windows: `%APPDATA%/Cursor/User/globalStorage/state.vscdb`
  - macOS: `~/Library/Application Support/Cursor/User/globalStorage/state.vscdb`
  - Linux: `~/.config/Cursor/User/globalStorage/state.vscdb`
- Uses the existing `cursorAuth/accessToken` and account identifier to construct the
  `WorkosCursorSessionToken` cookie for `GET https://cursor.com/api/usage-summary`. The token is
  read for each request, sent only to `cursor.com`, and never written to Usage Monitor storage or
  logs. Usage Monitor does not start a login flow.
- The route is not part of Cursor's documented public API. The response can change without notice;
  unknown or incomplete shapes are source failures and preserve the last valid reading.
- The response comes in two shapes. Personal plans (free/pro) carry `individualUsage.plan` with
  `autoPercentUsed`, `apiPercentUsed` and `totalPercentUsed`; on a free plan `used`/`limit` stay at zero
  even while the allowance is being spent, so only the percentages count there. Enterprise and team
  plans carry no percentages and meter `individualUsage.overall` in `used`/`limit` instead.
- Windows shown, all for the billing cycle: **Auto** (`autoPercentUsed`, the main allowance), **API**
  (only above zero), **On-demand** (when enabled with a positive limit), **Included** (the enterprise
  `overall` ceiling) and **Team on-demand** (only once something was spent). `totalPercentUsed` is a
  blend of Auto and API and is not a row: as a third quota it fired the same alert twice.
- Percentages are truncated like every other percentage in the app. A value above 100 means the
  allowance was exceeded and saturates at 100 instead of failing the card. Without `billingCycleEnd`
  the window has no known reset — the capture instant would make every refresh look like a new cycle.
- Cursor not installed, signed out, a rejected session and a plan with nothing to meter are
  configuration states: a banner without "Retry" and no toast on every refresh. A plan with nothing
  to meter is not reported as zero usage. Network failures keep their type, so a proxy problem shows
  the connectivity banner.
- Collection follows the app's normal 10-minute dashboard refresh. The source is opt-in in
  **Settings > APIs**.

## Antigravity

- Runs `agy --sandbox --print-timeout 30s --output-format json --print /usage` in an empty working
  directory (`~/.usage-monitor/antigravity-work/`). The
  [official headless reference](https://antigravity.google/docs/cli/headless) states that `/usage`
  and `/model` are *"answered by the CLI itself"* and should be run *"as their own `--print`
  invocation"*: they produce a report without a model turn. Measured against agy 1.2.9 on Windows,
  the JSON envelope comes back with `num_turns: 0` and `usage.total_tokens: 0`, and three consecutive
  runs left the remaining fraction unchanged.
- **Guard rails.** The CLI is only called when `agy --version` is 1.2.9 or newer, the version the
  envelope was measured against. The argument reaches `agy` as its own process argument, never
  through a shell: through Git Bash (MSYS), `/usage` is rewritten into a Windows path and reaches the
  model as a prompt, so `.cmd`/`.bat` launchers are refused. Every envelope must carry
  `command.name == "usage"`, `num_turns == 0` and `total_tokens == 0`; if it does not, collection
  pauses until the app restarts instead of repeating a call that could spend model quota.
- Each bucket of `command.data.groups[].buckets[]` becomes a percentage quota:
  used = 100 − `remaining_fraction` × 100 (truncated), with `reset_time` as the reset. The quotas
  take part in history, threshold alerts, forecasts and the HUD like any other windowed quota. A
  window that has not been touched (`remaining_fraction` = 1) reports "now + 7 days" as its reset,
  which moves on every call, so it is shown without a known reset.
- CLI absence, an old version, a signed-out session and a paused collection are configuration
  states: a banner without "Retry" and no toast on every refresh. Timeout, a non-JSON answer or an
  answer with no quota window are failures that keep the last valid reading.
- The CLI is called at most once every 5 minutes; the reset wake-up of another source reuses the
  last reading. A refresh requested by the user always calls it again.
- The collector does not call undocumented IDE RPCs and never stores or logs the CLI output.

## Telegram (bot de alertas e comandos, #387)

Opcional, desligado por padrão. O usuário cria o bot no @BotFather e cola o token em Configurações › Alertas;
token e conversas pareadas ficam em `~/.usage-monitor/telegram.json` (arquivo de segredo, permissão só do dono).

- **Só saída HTTPS** (`https://api.telegram.org/bot<token>/…`): `deleteWebhook` ao conectar (o `getUpdates` não funciona
  com webhook), `getUpdates` em long polling de 25 s (o timeout da requisição é estendido para 35 s; o padrão do app,
  20 s, cortaria toda espera) e `sendMessage`. Nenhuma porta aberta.
- **Começa do agora**: a primeira chamada é `getUpdates(offset = -1)`, que devolve só o último update; o polling segue do
  seguinte. O Telegram guarda updates por 24 h, e sem isso um `/alertas off` antigo seria reaplicado a cada arranque.
- **Pareamento**: "Parear conversa" gera um código de 6 caracteres (sem 0/O/1/I) válido por 10 min; a conversa que mandar
  `/start <código>` entra na lista. Conversa não pareada não recebe resposta nenhuma.
- **Comandos** (`parseBotCommand`, PT e EN): `/status`, `/conta`, `/atualizar`, `/api`, `/alertas on|off`, `/silencio`
  (sem argumento abre o menu de durações) `22-07|off`, `/limiar 75,90`, `/ajuda`.
  Os que mudam algo passam por `applyBotCommand` e gravam nas mesmas preferências de alerta das Configurações.
- **Alertas**: o serviço coleta o mesmo `UsageAlertViewModel.alerts` da bandeja — já deduplicado e respeitando o silêncio —
  e manda o mesmo título e corpo (`usageAlertMessage`) a cada conversa pareada, com 1,1 s entre envios (limite do Telegram
  ~1 mensagem/s por conversa). 429 espera o `retry_after`; 401/404 é token recusado e para até o usuário trocá-lo.
- **Formato (#396, direção W1)**: toda mensagem sai com `parse_mode: HTML` (`<b>`, `<i>`, `<code>`). O `/status` é um
  cartão por conta — risco com emoji **e** palavra, "⚡ em uso", e por cota o reinício (só a hora em menos de 24 h; com o dia
  da semana depois) e uma barra de 10 células em `<code>` cheia pelo piso do percentual. Todo texto variável passa por
  `TelegramBotMessages.escape` (`&`, `<`, `>`): sem ele um rótulo de conta com `<` faria o Telegram recusar a mensagem
  inteira com 400. A prévia das Configurações mostra o mesmo texto sem as marcas (`TelegramBotMessages.plain`).
- **Menu**: ao conectar um token o serviço chama `setMyCommands` com os comandos no idioma do app — o botão "Menu" do
  Telegram os lista sem `/ajuda`. Falha nessa chamada não derruba a conexão.
- **`getMe`** ao conectar dá o `@` do bot: as Configurações dizem "Conectado como @bot" e oferecem "Abrir no Telegram" pelo
  link `https://t.me/<bot>?start=<código>`, que abre a conversa e manda o `/start <código>` sozinho. Sem resposta do
  `getMe`, o link some e o resto segue.
- **Botões do `/status` (#396, direção W5)**: teclado inline de uma linha — 🔄 Atualizar, 🔕 Silenciar 1h, ⚙ Limiares
  (`BotButton`, `callback_data` `refresh`/`snooze`/`thresholds`). O `getUpdates` passa a pedir `callback_query`; toque de
  conversa não pareada não recebe nem o `answerCallbackQuery`, e `callback_data` desconhecido só fecha o "carregando".
  **Atualizar** responde "Coletando…", chama `refreshForBot` (o mesmo `DashboardViewModel.refresh()` do botão do app:
  alvo em backoff de 429 não vai à rede; espera a coleta começar por até 2 s e terminar por até 30 s) e edita a mesma
  mensagem com `editMessageText`; o 400 "message is not modified" é engolido. **Silenciar 1h** grava
  `UsageAlertSettings.snoozedUntilEpochMillis` (campo novo com default, chave `alertsSnoozedUntilMillis`), que silencia
  como o horário de silêncio — adia, não consome —; `/alertas on` o encerra. **Limiares** responde os percentuais atuais
  e o comando para mudar. O toque roda fora do laço de polling: a coleta não atrasa os outros updates.
- **Infra da #398**: `sendMessage` devolve o `message_id` (o painel fixado edita a mesma mensagem) e aceita `silent`
  (`disable_notification`); o teclado inline é uma lista **por linha** (linha vazia é descartada). `pinChatMessage` fixa
  sem notificar, `unpinChatMessage` desafixa; `sendPhoto` manda PNG em multipart com legenda HTML. O 400 é
  `TelegramBadRequestException` com a `description` do Telegram (`isNotModified`, `isMessageGone`). O tratamento de
  comandos e toques saiu do `TelegramBotService` para `TelegramBotHandlers`; o serviço ficou com o polling e o repasse.
- **Uma conta por vez (#398, Y4)**: `/conta` (`/account`) responde "Qual conta?" com um botão por conta, duas por linha,
  emoji do pior risco antes do rótulo; o `/status` ganha as mesmas linhas embaixo das três ações. O toque manda só o
  cartão daquela conta, com a hora da coleta dela. `callback_data` `acc:<ApiSource>:<FNV-1a do rótulo>` (`BotTap`,
  `botAccountKey`): estável entre o envio do teclado e o toque — índice não serve, a ordem pode mudar — e sem rótulo nem
  e-mail. Conta que saiu da leitura responde pedindo `/conta` de novo. `BotButton` (enum) não ganhou valor: toque com
  parâmetro é `BotTap` (sealed).
- **Controle remoto (#398, Y8)**: `/atualizar` (`/refresh`) chama o mesmo `refreshForBot` do botão Atualizar e responde o
  `/status`. `/api` lista as fontes com ✅/⬜; com **"Permitir mudar fontes pelo bot"** (`allowSourceControl` em
  `telegram.json`, **nasce desligado**) vem um botão por fonte (`api:<ApiSource>`), que grava por `persistEnabledApis` — o
  mesmo caminho do interruptor da aba APIs — e recoleta a fonte; a permissão é relida no toque, então um teclado antigo
  deixa de valer quando ela é desligada. `/silencio` sem argumento oferece 1 h, 4 h e "até 08:00" (`snz:60`, `snz:240`,
  `snz:am`; `nextMorningMillis` em BRT, teto de um dia contra dado forjado), gravando o mesmo `snoozedUntilEpochMillis`.
- Nunca trafega prompt, resposta ou caminho de projeto: o `/status` sai do `UsageSnapshot`.
- **Discord** fica para uma segunda fase: bot bidirecional exige Gateway (WebSocket permanente, heartbeat, intents).

## Formato das respostas

Response Anthropic retorna `five_hour`/`seven_day` com `utilization` em **percentual** (0–100) e `resets_at` em ISO 8601 (pode ser nulo), mais `extra_usage`/`spend` com os créditos de uso em unidades menores da moeda da conta.

Response MiniMax retorna `model_remains[]` com cotas em **requests** (não tokens), timestamps em epoch milliseconds.

Response OpenCode Go retorna `usage.{rolling,weekly,monthly}`, cada uma com `status` (`ok` ou `rate-limited`), `percent` (0–100) e `resetsAt` em ISO 8601. **Não devolve valor em dinheiro** — nem gasto, nem limite. O endpoint **não está documentado publicamente** (PR anomalyco/opencode#16513, merged em 2026-08-11) e não declara versão; sem `Authorization` responde `401 AuthError`, e com chave válida sem plano Go responde `403 EntitlementError`. O saldo pago do Zen **não tem endpoint**: `/zen/v1/balance` responde 404.

## Decisões de implementação

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

### Camada data (`commonMain/data/` + `desktopMain/data/`)

- DTOs com `@Serializable` + `@SerialName` para mapear snake_case do JSON.
- `LocalCredentialDataSource` e `LocalApiKeyDataSource` ficam em **desktopMain** (usam `java.io.File`). O primeiro lê `~/.claude/.credentials.json` → `claudeAiOauth.accessToken`; o segundo persiste as chaves MiniMax/DeepSeek em `~/.usage-monitor/api-keys.json`, com escrita atômica e acesso restrito ao usuário. A origem Anthropic sai de `AnthropicCredentialStore`: o ficheiro tem prioridade sempre; **no macOS**, quando ele não existe, cai na entrada `Claude Code-credentials` do Keychain via `security` (só para o perfil padrão — perfis de `CLAUDE_CONFIG_DIR` não têm entrada lá). A gravação pós-refresh volta para a mesma origem.
- **Renovação do token OAuth** (`LocalCredentialDataSource.refreshToken`): o corpo do `POST` precisa de `client_id` e `scope` além de `grant_type`/`refresh_token`. O endpoint valida o **formato** antes de olhar o grant: sem `client_id` responde `HTTP 400 "Invalid request format"` com qualquer refresh token, e era por isso que a renovação nunca acontecia — o app dependia do CLI para renovar e a tela pedia login a cada ~8h (issue #64). `OAUTH_REFRESH_URL` (`platform.claude.com/v1/oauth/token`), o client id e os escopos default são **espelhados da configuração de produção do binário do CLI**; o client id é público, vem embutido no binário distribuído. O `scope` sai dos escopos do próprio ficheiro quando existem — pedir a lista fixa numa conta com menos escopos seria pedir permissão que ela não concedeu.
  - **O status HTTP é checado antes de desserializar.** `TokenRefreshResponse` tem todos os campos opcionais e o `HttpClient` não liga `expectSuccess`: o corpo de erro `{"type":"error",...}` desserializa **sem lançar**, e a falha chegava à tela como "sem access_token", sem status nem motivo. Foi por isso que o episódio de abril de 2026 (commit `198a0a7`, logs depois removidos) não deixou rastro.
  - **A regravação é patch de `JsonObject`, não `encodeToString(CredentialsFileDto)`.** O ficheiro tem nós que o app não declara — `mcpOAuth` (autenticação dos MCP servers) e `refreshTokenExpiresAt` —, e o roundtrip pelo DTO com `ignoreUnknownKeys` os apagava em silêncio. Só `accessToken`/`refreshToken`/`expiresAt` são substituídos. Ao acrescentar campo ao `OAuthCredentialsDto`, pergunte se a escrita ainda preserva o que ele não conhece.
- `RemoteApiDataSource`: Anthropic faz `GET /api/oauth/usage` (headers `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-code/1.0.0` — **obrigatório**: segundo o ai-usagebar, sem o UA do Claude Code o endpoint responde 429 direto; não troque pelo `UsageMonitorDesktop`) e lê a utilização das janelas direto do corpo JSON. MiniMax faz `GET /v1/token_plan/remains`.
- **Sessões locais do Codex CLI** (`LocalCodexCliSessionDataSource` + `CodexCliRolloutParser`): são
  uma fonte separada da quota remota do Codex. A raiz é `$CODEX_HOME/sessions` ou `~/.codex/sessions`,
  sem leitura de `auth.json`, `cap_sid` ou chaves. O índice vive em `~/.usage-monitor/codex-cli-history.db`
  e não usa as tabelas `cli_*` do Claude. Só `session_meta`, `turn_context` e `token_usage_record.usage`
  entram; `response_item`, prompt, resposta, raciocínio textual, ferramentas e os blocos acumulados
  `turn_token_usage`/`thread_token_usage` ficam fora. O schema do rollout é interno: tipos desconhecidos,
  linhas inválidas e truncamento degradam para aviso/contagem, nunca para custo ou conteúdo inventado.
  A tela oferece 5h, 7d, total e exportação CSV/JSON de metadados/contadores; custo USD não é calculado.
- **Créditos de uso (Anthropic)** (`AnthropicCreditsResolution.kt`): `extra_usage` é a fonte primária — `monthly_limit` e `used_credits` vêm em unidades menores da moeda (55000 = R$ 550,00) e `currency` traz a moeda real da conta, que **não é sempre USD**. O rótulo `AnthropicQuotaLabels.EXTRA_CREDITS` é chave da série histórica e não pode ser renomeado.
  - **`spend` é fonte secundária, não só reforço.** Ele descreve o mesmo gasto (`amount_minor`/`currency`/`exponent`) e cobre quando `extra_usage` vem sem `monthly_limit` ou não vem. Em agosto de 2026 a resposta parou de trazer os créditos por cinco dias e a linha sumiu da tela, do cache e do histórico sem deixar rastro — o `return null` único não distinguia "conta sem créditos" de "contrato mudou". O `percent` do `spend` continua fora do cálculo: chega arredondado, e o `utilization` do `extra_usage` não.
  - **`is_enabled` falso continua escondendo a linha em silêncio** — é o estado normal de quem não contratou créditos, e avisar ali viraria alerta permanente. Só `LIMIT_ABSENT` e `UNSUPPORTED_EXPONENT` (`AnthropicCreditsOutcome.signalsFailure`) viram `ApiUsageNotice.EXTRA_CREDITS_UNAVAILABLE`, que a `ApiUsageCard` mostra **também com o card minimizado**: foi o card fechado que escondeu o episódio. O aviso vive no **cabeçalho**, que é composto nos dois estados — ver `CardNoticeHint`.
  - **Expoente monetário diferente de 2 não vira cota.** `formatCents` assume duas casas; aceitar outro expoente mostraria o valor errado por um fator de dez, o que é pior que omitir a linha.
  - **Diagnóstico opt-in** (`USAGE_MONITOR_DEBUG_ANTHROPIC_CREDITS=1` → `~/.usage-monitor/diagnostics/anthropic-credits.jsonl`, mesmo desenho do recorder do Codex): guarda os nós `extra_usage` e `spend` **crus**, porque campo derivado não revela campo renomeado. Com o registro desligado o corpo continua sendo lido uma vez só, pelo `ContentNegotiation`.
- `MiniMaxRepositoryImpl`, `DeepSeekRepositoryImpl` e `OpenCodeGoRepositoryImpl` recebem o leitor da chave por injeção; no desktop ele lê exclusivamente `LocalApiKeyDataSource`. Nunca hardcode credenciais nem leia variáveis de ambiente para essas integrações. O conjunto das fontes que dependem de chave é `API_KEY_DEPENDENT_SOURCES` (`AppPreferenceKeys.kt`), e não um literal repetido: ele já tinha dois donos — o filtro de arranque e `requiresApiKey` das Configurações —, e o terceiro seria onde a fonte seguinte ficaria esquecida.
- Ambos os repos usam `Result.runCatching { }` para encapsular falhas.
- **OpenCode Go** (`OpenCodeGoRepositoryImpl` + `OpenCodeGoMapper`; plano [`opencode-go-execucao.md`](planos/opencode-go-execucao.md), issue #124): a assinatura paga, lida de `GET /zen/go/v1/usage` com a chave da API do OpenCode. É **fonte própria** (`ApiSource.OPENCODE_GO`), ao lado de `OPENCODE`, que é o plano gratuito do Zen lido do SQLite local — exceção declarada à regra de não criar valor em enum existente, pela mesma razão de `AppUpdateSupport`: os cinco `when` exaustivos sobre `ApiSource` são justamente os pontos que a fonte nova precisa preencher, e o erro de compilação garante que nenhum ficou para trás. Reaproveitar `OPENCODE` faria `isObservedActivitySource()` desviar o card para o resumo sem barras, misturaria requisições com percentual, e ignoraria que uma máquina pode ter uma das duas sem a outra.
  - **O acento é reusado, não é um sétimo token.** `accentColorFor` manda `OPENCODE` e `OPENCODE_GO` para `accents.opencode`: o acento identifica o **fornecedor**, o sistema visual fixa seis identidades e diz que elas não mudam, e um sétimo tom teria de passar AA 4,5:1 nas duas superfícies mantendo 20° dos outros seis. Quem separa os cards é o título.
  - **`403 EntitlementError` não é erro de credencial.** É o estado normal de quem só usa o Zen pago, e o repositório o traduz para mensagem própria que `isConfigurationIssue` absorve — sem toast a cada coleta, e com banner que manda assinar ou desligar. O banner **não oferece "Tentar novamente"**: repetir devolve o mesmo 403. O 401 e o 403 sem `EntitlementError`/`subscription required` (proxy corporativo) continuam falha comum, senão o único caso em que revisar a chave resolve ficaria escondido.
  - **Nenhum número é derivado do percentual.** A API não devolve valor gasto nem limite, então `rawUsed`/`rawTotal` ficam em zero — ao contrário da Anthropic, que converte `utilization` numa capacidade porque conhece o teto. Capacidade inventada apareceria na tooltip como tokens que a API nunca informou.
  - **`status: "rate-limited"` não é mapeado**, deliberadamente: `ApiUsageNotice` vive na fonte inteira e não diria **qual** das três janelas está bloqueada, e a janela limitada já chega com o percentual no teto.
  - **Janela ausente degrada, resposta vazia falha.** O endpoint não é documentado nem versionado, então todo campo do DTO é opcional: janela que não vier some do card, `resetsAt` ilegível vira `hasKnownResetAt = false`. Mas resposta **sem nenhuma** das três é contrato mudado e não conta zerada — falhar preserva o cache em vez de apagá-lo com uma leitura que não mediu nada.
  - **O saldo pago do Zen continua fora**: não existe endpoint (`/zen/v1/balance` responde 404; issue upstream anomalyco/opencode#10448 aberta). É por isso que a #124 não fecha com esta entrega.
- **Gemini CLI local** (`LocalGeminiUsageDataSource` + `GeminiSessionLogParser`; issue #267): tokens observados por modelo em 5h/7d, com `total = 0` — atividade observada, na convenção do Kilo e do OpenCode Zen, nunca cota da conta Google.
  - **Cada chamada é gravada duas vezes com o mesmo `id`**, sem tokens ao começar e com tokens ao terminar. Só a gravação com tokens conta, e uma posterior sem tokens não a apaga.
  - **`$set` não mexe na contagem.** O CLI grava `$set lastUpdated` depois de cada mensagem, e a primeira versão limpava tudo a cada patch: a sessão real terminava perto de zero, e o teste passava porque a fixture trazia um `$set` com `messages`, forma que o CLI não grava.
  - **`$rewindTo` desfaz a conversa, não a cobrança**: a chamada desfeita continua contada.
  - **Raiz ausente é card vazio, não banner** — o Gemini CLI nunca rodou na máquina. Só arquivos recentes todos ilegíveis viram `SESSION_HISTORY_UNREADABLE`.
  - Arquivo com mtime anterior à janela de 7d não é lido (o JSONL é append-only), e os demais ficam em cache por caminho + tamanho + mtime.
- **Cursor** (`LocalCursorSessionDataSource` + `CursorUsageMapper` + `CursorQuotaLabels`; issue #267): resumo pessoal por `GET https://cursor.com/api/usage-summary`, **rota não documentada**, com o cookie `WorkosCursorSessionToken=<conta>::<token>` montado da sessão que o editor guarda em `state.vscdb` — lido em read-only, nunca persistido. A conta sai de `cursorAuth/stripeMembershipAuthId` e, na falta, do `sub` do JWT, na mesma ordem do Codenotch.
  - **Duas formas de resposta.** Plano pessoal traz percentuais em `individualUsage.plan`; enterprise/team não traz percentual e mede `individualUsage.overall` em `used`/`limit`. A primeira versão exigia `plan` e falhava sempre nessas contas. No plano gratuito `used`/`limit` ficam em zero com uso real — a franquia chega como `breakdown.bonus` —, então ali só percentual conta.
  - **`totalPercentUsed` não é cota**: é a mistura de Auto e API, e como terceira cota disparava o mesmo alerta duas vezes.
  - **Acima de 100 satura**, em vez de derrubar o card justo quando o usuário estourou a franquia. **Sem `billingCycleEnd`, sentinela 2100 com `hasKnownResetAt = false`**: gravar o instante da coleta fazia cada poll parecer ciclo novo e rearmava o alerta a cada 10 min.
  - Todas as janelas são `MONTHLY`, e o título do card sai do `periodType`: `CursorQuotaLabels.groupOf` devolve o nome da franquia ao título, como `AntigravityQuotaLabels.groupOf` faz com o grupo de modelos.
  - `CursorUsageFailureKind` (não instalado, sem sessão, sessão recusada, plano sem nada a medir) conta como configuração. **Falha de rede passa sem embrulho**: `isConnectivityFailure` classifica pelo tipo, e a primeira versão engolia `ConnectException` numa mensagem genérica.
  - `CursorSessionCredentials` **não é `data class`** — o `toString` gerado imprimiria o token. A leitura do SQLite tem `busy_timeout` e cai para `immutable=1` quando o read-only falha (WAL sem `-shm`).
- **Última leitura mantida em falha, para toda fonte** (`statsRetainedAfterFailure` em `DashboardViewModelSupport.kt`; issue #269): a leitura boa anterior continua no card com `ApiUsageNotice.SOURCE_UNSTABLE`. Antes só Codex e as fontes locais (Gemini, Cursor, Antigravity) a guardavam, e um 429 ou timeout da Anthropic fazia o card sumir da tela **e** do cache em disco. Três casos apagam: `isUnauthorizedIssue`/`isConfigurationIssue` (o número pertence a um acesso que não vale mais, e o banner manda agir), leitura mais velha que `MAX_STALE_READING_AGE` (7 dias — semanas sem coletar não podem parecer o consumo de agora) e Codex que não passa em `isPersistableDashboardStats`. O texto do aviso depende da fonte (`noticeText`): a frase do Codex fala do contrato dele; nas outras é "última leitura válida, coletada há N" (`staleReadingText`).
  - **O instante da coleta não é carimbado no `ApiUsageStats` a cada sucesso.** `DashboardRefreshScheduler.lastSuccessAt` o guarda, e só a leitura mantida e o cache em disco levam `fetchedAt`: um carimbo novo por coleta faria duas leituras iguais serem objetos diferentes e o `StateFlow` reemitiria a tela a cada poll. Cache gravado antes do campo herda o `savedAtEpochMillis` do arquivo.
- **Backoff por 429** (`RateLimitedException` + `RefreshSchedule.kt` + `DashboardRefreshScheduler`; issue #269): `requireSuccess` e a renovação do token lançam o 429 **tipado**, com o `Retry-After` (segundos ou data HTTP, `parseRetryAfter`). A mensagem continua com `HTTP 429`, que é por onde `warningFor` e o toast classificam. A espera é `min(15min, max(60s·2^min(n,4), Retry-After))` — a fórmula do Codenotch — mais 1 s de folga contra a ressonância com o tique. O `Retry-After` só **aumenta** a espera: o endpoint responde `Retry-After: 0`, e obedecê-lo mantinha o poll batendo no limite.
  - **Durante o backoff nada vai à rede**, nem o clique: `refresh(target)` vira toast "aguarde até HH:mm BRT", e o banner troca "Tentar novamente" pela hora (`UiApiError.retryAt`). A leitura fica no card.
  - **O prazo é gravado** (`RefreshSchedulePreferences.kt`, `rateLimitBackoffUntil` em `PreferencesSettings`): reiniciar o app não zera a punição. Alvo desabilitado perde o prazo.
  - **429 do endpoint de token também arma o backoff**, e token vencido nunca chega ao endpoint de uso — lá ele receberia 429 com `Retry-After` de uma hora, e pareceria rate limit.
  - Cada 429 grava na trilha o `Retry-After` recebido, a tentativa e o prazo (`rateLimitBreadcrumb`), sem token nem e-mail: é a medição que decide se a cadência de 60 s se sustenta.
- **Antigravity CLI** (`LocalAntigravityUsageDataSource` + `AntigravityUsageMapper` + `AntigravityQuotaLabels`; issue #267, plano [`integracoes-267-ajustes-execucao.md`](planos/integracoes-267-ajustes-execucao.md)): cotas lidas por `agy --sandbox --print-timeout 30s --output-format json --print /usage`, **sem PTY**. A primeira versão dirigia a TUI interativa por pty4j e nunca abriu o painel: o pty4j usa winpty por default no Windows, `/usage⏎` ia às cegas depois de 900 ms, e o parser procurava cabeçalhos adivinhados.
  - **`--print /usage` não abre turno de modelo**, e isso é documentado, não suposto: a página headless oficial diz que `/usage` e `/model` são *"answered by the CLI itself"* e manda rodá-los *"as its own --print invocation"*. Medido no agy 1.2.9: `num_turns: 0`, `usage.total_tokens: 0`, fração inalterada em três chamadas. A issue vetava `-p` com a premissa de que todo print consome cota — verdade para prompt, falsa para comando de barra.
  - **O argumento chega por lista, nunca por shell.** Pelo Git Bash, `/usage` vira `C:/Program Files/Git/usage` e vai ao modelo como prompt — aconteceu durante a medição. Por isso `.cmd`/`.bat` são recusados, e qualquer teste manual se faz pelo PowerShell.
  - **Três salvaguardas, todas com teste:** portão de versão (≥ 1.2.9, a medida; cache por caminho + tamanho + mtime), disjuntor (`command.name == "usage"`, `num_turns == 0` e `total_tokens == 0`, senão a fonte para até reiniciar o app — **nem o refresh manual o desarma**, porque clicar em atualizar não pode ser o gesto que volta a gastar) e TTL de 5 min no repositório, que só o `refresh(...)` do usuário invalida: o despertar por reset de **qualquer** fonte pede coleta, e sem o TTL cada um abriria um processo.
  - **É cota, não atividade observada.** Cada bucket vira `QuotaInfo(PERCENTAGE)` com o reset do CLI, e a fonte entra em histórico, limiar, projeção e HUD. O canal lateral `ReportedModelQuota` da primeira versão foi apagado — ele deixava o Antigravity fora de tudo isso.
  - **Janela intacta não tem reset conhecido.** Com `remaining_fraction` = 1, `reset_time` é "agora + 7 dias" e anda a cada chamada (medido: 00:38:35 → 00:45:04). Tomado como reset real, cada coleta pareceria período novo para o histórico e para a dedup dos alertas.
  - **Duas cotas do mesmo `periodType` numa fonte só**, um limite semanal por grupo de modelos. O título do card sai do `periodType` e o HUD usa a última palavra do rótulo; `AntigravityQuotaLabels.groupOf` devolve o grupo aos dois, e por isso `hudQuotaShortLabel` deixou de ser a regra burra incondicional.
  - Instalação, versão, login e disjuntor são `AntigravityUsageFailureKind` e contam como configuração (sem toast, banner sem "Tentar novamente"). Timeout e saída ilegível continuam falha comum.
- **Proxy HTTP corporativo** (`ProxySettings.kt` + `ProxyResolution.kt` + `LocalProxySettingsDataSource.kt` + `HttpClientFactory.kt`; issue #174): configuração de proxy para o `HttpClient` único do app, que até então não tinha nenhuma. Precedência resolvida por `resolveEffectiveProxy`: manual explicitamente ligado (`ProxySettings.useEnvironmentProxy = false`) vence sobre `HTTPS_PROXY`/`HTTP_PROXY` do ambiente (`parseProxyEnvironmentValue`, convenção de shell — curl, npm, pip —, não uma API documentada), que vence sobre nenhum proxy. `NO_PROXY` fica fora do escopo.
  - **Só Basic auth.** NTLM exigiria dependência própria que o OkHttp não traz nativamente, e a própria issue trata como caso de borda a validar só com proxy corporativo real. Documentado como limitação na própria aba, não escondido.
  - **A configuração só vale depois de reiniciar o app.** O `httpClient` compartilhado (`AppGraph.kt`, `buildHttpClient`) é montado uma única vez no arranque, com o proxy já resolvido, e é usado por 5+ consumidores com laços próprios (dashboard, sincronização de time, atualização automática) — recriar o engine em runtime arriscaria `ClosedException` numa requisição in-flight de qualquer um deles. Por isso `LocalProxySettingsDataSource` é lido **antes** do bloco do `httpClient`, não depois como os demais data sources de configuração.
  - **"Testar conexão" nunca usa o client compartilhado.** Monta um `HttpClient` efêmero com o valor corrente de `proxySettingsFlow` (já commitado pelos campos da seção, mesmo sem reiniciar) contra um endpoint leve e sem credencial (`https://api.github.com/zen`), e fecha o client depois — é a única forma de dar feedback imediato sem esperar o reinício.
  - **O `Authenticator` do proxy verifica se a requisição já carrega `Proxy-Authorization`** antes de responder ao desafio 407 de novo: sem esse guard, uma senha errada faz o OkHttp reenviar a mesma credencial recusada para sempre. HTTP 407 chega como resposta HTTP normal (`RemoteApiDataSource.requireSuccess` já vira `IllegalStateException`) e cai no mesmo mecanismo de marcador por substring dos demais status (`isProxyAuthIssue`, conta como `isConfigurationIssue` — é credencial errada, só que do proxy).
  - **Falha de conectividade (DNS, timeout de conexão, proxy inalcançável) é classificada por TIPO de exceção, não por substring da mensagem** — texto de `ConnectException`/`SocketTimeoutException` varia por JVM e SO. A checagem mora em `uiApiErrorOf` (`DashboardViewModelSupport.kt`), chamada por `DashboardViewModel.handleTargetFailure`, o funil único de toda falha de coleta, e embute um marcador fixo (`NETWORK_CONNECTIVITY_MARKER`, mesmo desenho de `HTTP_RATE_LIMIT_MARKER`) que `UiApiError.isConnectivityIssue`/`warningFor` consomem por substring — sem precisar de um enum de erro novo. Categoria própria, **fora** de `isConfigurationIssue`: a causa não é credencial errada, e classificar como configuração orientaria a revisar login em vez de proxy.
  - **O banner de conectividade força retry universal** (`DashboardWarning.forcesUniversalRetry`): sem essa marca, só a Anthropic tem botão de retry em `warningActionFor`, e o usuário atrás de proxy corporativo — cujas 8 fontes falham juntas por conectividade — perderia o "Tentar novamente" que o erro genérico já oferecia antes da classificação existir.
