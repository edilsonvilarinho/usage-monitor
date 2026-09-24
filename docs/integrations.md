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
- When the payload carries `primary_window` and `secondary_window`, the response itself is the
  source of truth for the `5h` and `7d` quotas.
- Snapshots are only accepted and stored when **both** quotas are present. An incomplete collection
  keeps the last valid reading in cache and flags the source as unstable.

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
