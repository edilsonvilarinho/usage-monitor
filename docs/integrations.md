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
| Gemini CLI | Local | reads `~/.gemini/tmp/*/chats/session-*.jsonl` | local Gemini CLI session history; token activity only |
| Cursor | Remote | `GET https://cursor.com/api/usage-summary` | existing signed-in Cursor editor session; undocumented personal route |
| Antigravity CLI | Local | interactive `agy` PTY and `/usage` command | Antigravity CLI on `PATH`, already authenticated |

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
| `~/.gemini/tmp/<project_hash>/chats/session-*.jsonl` | Gemini CLI session metadata and token counts; prompt/response content is not retained |
| `%APPDATA%/Cursor/User/globalStorage/state.vscdb` (Windows), `~/Library/Application Support/Cursor/User/globalStorage/state.vscdb` (macOS), or `~/.config/Cursor/User/globalStorage/state.vscdb` (Linux) | Cursor session values read from SQLite in read-only mode |
| `agy` executable on `PATH` | Antigravity CLI; Usage Monitor opens its existing authenticated session interactively |

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

- Reads Gemini CLI session files from `~/.gemini/tmp/<project_hash>/chats/` on Windows, macOS and
  Linux. The path and session retention behavior are described in the
  [official session-management guide](https://github.com/google-gemini/gemini-cli/blob/main/docs/cli/session-management.md).
- Current session files are JSONL. They contain prompts, responses, tool calls and other private
  content. The parser uses only the session/message IDs, timestamps, model names and token counters;
  it does not persist or log conversation content.
- Reports observed token counts over rolling 5-hour and 7-day windows. A token count is not an
  account quota: no limit, percentage or cost is inferred from it. `total` is used when present;
  cached-token counts are not added a second time.
- Google account quota is a separate source. Gemini CLI documents `/stats model` as an interactive
  view of model token counts and quota information, but this integration has no stable
  machine-readable quota contract. No Google quota card is created until a verifiable source is
  available.
- Session rewinds remove the superseded messages from the aggregate. Repeated message IDs replace
  their earlier record. Messages without token metadata are ignored.
- Collection follows the app's normal 10-minute dashboard refresh. A missing or unreadable local
  source is reported as unavailable; it is never presented as zero usage.

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
- Only percentages and billing-cycle reset dates present in the response are shown. Token counts,
  money and limits are not derived from percentages. A plan with no recognized metered value is
  unavailable rather than zero.
- Collection follows the app's normal 10-minute dashboard refresh. The source is opt-in in
  **Settings > APIs**.

## Antigravity

- Uses the official Antigravity CLI command `/usage` (alias `/quota`). It refreshes model quota data
  from the backend and opens an interactive terminal panel; see the
  [official command reference](https://antigravity.google/docs/cli/commands/usage).
- The collector runs the CLI in a pseudo-terminal, sends only `/usage`, reads the displayed quota
  fields, then terminates the process tree. It never uses `agy -p`, which is a model-prompt mode,
  and does not call undocumented IDE RPCs.
- CLI absence, missing authentication, exit before the panel appears, timeout or an unrecognized
  output format make the source unavailable. They are not converted to zero usage, and the last
  valid reading is preserved.
- Only values explicitly reported by the CLI are shown: model, percentages, used/remaining counts,
  limits and reset descriptions when the panel provides them. The collector does not infer a
  percentage or reset date from another metric. Since these values remain separate from normalized
  quotas, they do not trigger quota threshold alerts or forecasts.
- Collection follows the app's normal 10-minute dashboard refresh. The source is opt-in in
  **Settings > APIs** and uses the account already configured in Antigravity CLI.
