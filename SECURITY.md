# Security Policy

## Supported versions

Only the latest published release is supported. There are no backports: fixes ship in the next
version. See the [releases page](https://github.com/edilsonvilarinho/usage-monitor/releases).

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:
[**Report a vulnerability**](https://github.com/edilsonvilarinho/usage-monitor/security/advisories/new).
It creates a private advisory visible only to the maintainer.

Please include the app version, the operating system, what an attacker could achieve, and the steps
to reproduce it. A proof of concept helps, but a clear description of the failure is enough to start.

Expect a first response within a few days. This is a single-maintainer project, so there is no
guaranteed SLA and no bug bounty.

## What this app touches

Usage Monitor reads credentials that other tools already store on your machine. Understanding what it
touches is the fastest way to judge whether something you found is a real problem.

### Credentials it reads

| Path | Written by | Access |
|---|---|---|
| `~/.claude/.credentials.json` | Claude Code | read; **written** on OAuth refresh |
| `~/.claude.json` | Claude Code | read only |
| `<CLAUDE_CONFIG_DIR>/.credentials.json` | Claude Code, custom profiles | read; written on refresh |
| macOS Keychain, `Claude Code-credentials` | Claude Code | read only, via `security` |
| `~/.codex/auth.json`, `~/.codex/cap_sid` | Codex | read only |
| `~/.local/share/opencode/opencode.db` | OpenCode | read only |
| `~/.local/share/kilo/kilo.db` | Kilo | read only |

The app never runs a login or logout flow and never deletes a credential file. The only write is the
Anthropic OAuth token refresh, which is atomic, guards against concurrent modification, and preserves
the nodes it does not declare (`mcpOAuth`, `refreshTokenExpiresAt`) instead of round-tripping the
file through its own model.

### Secrets it stores

| Path | Content | Protection |
|---|---|---|
| `~/.usage-monitor/api-keys.json` | MiniMax, DeepSeek, OpenCode Go keys | atomic write, owner-only permissions |
| `~/.usage-monitor/team.json` | team server URL and key | atomic write, owner-only permissions |

Neither lives in the preference store (`Preferences.userRoot()`), which is written in the clear — the
Windows registry, for example. That separation is deliberate: preferences hold settings, not secrets.

API keys are **never** read from environment variables, and are never hardcoded.

### What leaves your machine

- Requests to the vendor APIs listed in [`docs/integrations.md`](docs/integrations.md), carrying the
  credential for that vendor and nothing else.
- If — and only if — you enable the team integration: usage metadata to **the server you configured**.
  That means session id, message id, timestamp, model, token counts, project directory, git branch and
  machine name.

**No prompt content and no response content is ever transmitted**, to any destination, including the
team server. Transcripts are read locally and only token counts and metadata leave the reader.

### Team server

The optional server is self-hosted; there is no managed service and no default endpoint. Its
authentication model — per-person team keys, account claiming, the admin token, and the read-only
report credential — is documented in [`server/README.md`](server/README.md). If you run one, note
that `TEAM_LEGACY_KEY_MODE=open` is the default and keeps the legacy shared key able to read
everything; set it to `off` to enforce per-account isolation.

## Distribution and integrity

- **The Windows installer and the Linux tarball are not code-signed.** SmartScreen or an antivirus may
  block the `.exe`. The macOS DMGs carry no Apple Developer ID and Gatekeeper blocks the first launch.
- Integrity for the auto-updater comes from the **SHA-256 `digest` published by the GitHub API**, over
  TLS — not from the hash printed in the release workflow, which only serves the initial Linux
  installer.
- The updater refuses any artifact whose digest does not match, and on Linux rolls back automatically
  if the promoted version does not confirm that it started within 60 seconds.

If you obtained a build from anywhere other than
[this repository's releases](https://github.com/edilsonvilarinho/usage-monitor/releases), treat it as
untrusted.
