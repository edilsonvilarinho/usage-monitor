# Integrations

Reference for every source Usage Monitor reads: endpoint, credentials, and known limits.
For the short version, see the table in the [README](../README.md#supported-integrations).

| Integration | Type | Data source | Local requirement |
|---|---|---|---|
| Anthropic | Remote | `GET https://api.anthropic.com/api/oauth/usage` | `~/.claude/.credentials.json` |
| Codex | Remote | `GET https://chatgpt.com/backend-api/wham/usage` | `~/.codex/auth.json` and `~/.codex/cap_sid` |
| MiniMax | Remote | `GET https://www.minimax.io/v1/token_plan/remains` | API key entered in **Settings > APIs** |
| DeepSeek | Remote | `GET https://api.deepseek.com/user/balance` | API key entered in **Settings > APIs** |
| OpenCode Zen Free | Local | reads `~/.local/share/opencode/opencode.db` | an existing local OpenCode database |
| OpenCode Go | Remote | `GET https://opencode.ai/zen/go/v1/usage` | API key entered in **Settings > APIs** |
| Kilo Free | Local | reads `~/.local/share/kilo/kilo.db` | an existing local Kilo database |
| OpenRouter | Remote | `GET https://openrouter.ai/api/v1/credits` | API key entered in **Settings > APIs** |

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
