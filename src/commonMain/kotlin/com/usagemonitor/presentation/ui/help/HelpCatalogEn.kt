package com.usagemonitor.presentation.ui.help

import com.usagemonitor.presentation.ui.UPDATE_RESTART_ACTION_EN
import com.usagemonitor.presentation.ui.components.BETA_UPDATES_LABEL_EN

/** Entradas da ajuda em inglês; mesma ordem e mesmo desenho de [portugueseHelpEntry]. */
internal fun englishHelpEntry(topic: HelpTopic): HelpEntry = when (topic) {
    HelpTopic.DASHBOARD -> HelpEntry(
        title = "Dashboard and integrations",
        summary = "One card per account or integration, with quotas and the risk semaphore.",
        description = "The main window shows one card per source, refreshed every ten minutes " +
            "on its own and with a per-card refresh button. Each quota shows how much was " +
            "consumed, when the window resets, and a risk dot that weighs the burn rate against " +
            "the time left before the reset. If one source fails the others keep going: the " +
            "failing card states the reason, and its retry button reloads only that target.",
        steps = listOf(
            "Open Settings from the footer gear and go to the \"APIs\" tab.",
            "Enable the integrations in use. MiniMax, DeepSeek, OpenCode Go and OpenRouter ask " +
                "for the API key right there; it is stored in ~/.usage-monitor/api-keys.json. " +
                "Gemini CLI reads local sessions; Cursor uses its existing local session to fetch " +
                "the personal summary; Antigravity reads its quotas through the already authenticated CLI's /usage.",
            "In the same dialog, \"Test key\" runs the real collection and states why it failed — " +
                "key rejected, missing plan, proxy, or no connection.",
            "Anthropic and Codex read the credential the CLI already wrote on this machine — " +
                "there is nothing to fill in.",
            "Behind a corporate proxy, set it up in the \"Network\" tab and restart Usage Monitor " +
                "(not your computer): the HTTP client is built once, at startup.",
            "Drag a card to reorder the grid; \"Minimize card\" collapses it to its title row."
        ),
        mediaId = "dashboard"
    )

    HelpTopic.HISTORY -> HelpEntry(
        title = "History and forecast",
        summary = "Usage over time, with a projection of when the quota runs out.",
        description = "Every collection is stored in a local SQLite database. The history screen " +
            "plots the chosen range with window resets marked, the hourly average, the forecast " +
            "of when the quota runs out and a comparison with the previous period. The " +
            "comparison uses each window's own consumption, never the running total — the total " +
            "resets with the window, which would make the number depend on when the reset fell.",
        steps = listOf(
            "On the source card, click \"Open history\".",
            "Pick the range in the top bar; with no data point in the previous period there is " +
                "no comparison, because zero there would mean \"consumed nothing\".",
            "The summary precedes the chart. Expand \"Quota summary\" and \"Windows and hourly distribution\" to read the details.",
            "Open \"Account\" to select another account. Click \"PDF\" to save the selected source, account, range and quotas. Zoom and collapsed sections do not remove report data.",
            "Nothing is sent anywhere: the history lives in ~/.usage-monitor/usage-history.db."
        ),
        mediaId = "history"
    )

    HelpTopic.CLI_SESSIONS -> HelpEntry(
        title = "Claude Code sessions",
        summary = "One row per session, with estimated cost and a health verdict.",
        description = "The app indexes the transcripts Claude Code writes on this machine and " +
            "builds one row per session in the window: tokens, cost estimated from the " +
            "per-model price table, active time and a health verdict that says whether the " +
            "session is saturated or needs attention. The list refreshes itself while the window " +
            "is open. Only usage metadata is read — no prompt or reply text.",
        steps = listOf(
            "On an Anthropic account card, click \"CLI sessions for this account\".",
            "Use the window selector (5h, 24h, 7 days, 30 days) to change the range.",
            "Click a session for the detail: context growth turn by turn, token composition and " +
                "cache savings.",
            "The copy button hands you the \"claude --resume\" command with the full session " +
                "identifier, which is what resumes the conversation directly."
        ),
        mediaId = "cli-sessions"
    )

    HelpTopic.BREAKDOWN -> HelpEntry(
        title = "Breakdown by axis",
        summary = "The same window sliced by project, model, branch or tool.",
        description = "The breakdown sums the very same turns as the session list, grouped by " +
            "the chosen axis, and adds the burn rate in dollars and tokens per hour plus an " +
            "activity grid by weekday and hour. The lists describe the same turns: adding " +
            "buckets from different axes would count the same spend more than once. Cost is " +
            "recomputed from tokens, never split between models.",
        steps = listOf(
            "On the CLI sessions screen, switch to the \"Breakdown\" tab.",
            "Pick the axis — project, model, branch, tool or activity — in the tab strip.",
            "Filter by a fragment of the name, change the sort and page through the controls row.",
            "The window chosen at the top applies to both tabs."
        ),
        mediaId = "breakdown"
    )

    HelpTopic.BUDGET -> HelpEntry(
        title = "Monthly budget",
        summary = "A cap in dollars against the estimated cost of the current month.",
        description = "The budget compares the configured cap with the estimated cost of this " +
            "month's sessions, in the presentation time zone. It is independent of the window " +
            "chosen on screen: a budget is monthly, and tying it to the last five hours would " +
            "produce a meaningless number. Anthropic usage credits appear on a separate line " +
            "with the currency spelled out, never added in — they may come in another currency.",
        steps = listOf(
            "Open Settings → \"Alerts\".",
            "Fill in \"Monthly cap in USD (empty disables)\".",
            "The tracking shows up on the \"Breakdown\" tab of the CLI sessions screen."
        ),
        mediaId = "budget"
    )

    HelpTopic.ALERTS -> HelpEntry(
        title = "Alerts and tray",
        summary = "A native notification when a quota crosses a threshold or a session stalls.",
        description = "The tray icon carries a risk dot, and the app fires a native notification " +
            "when a quota crosses one of the thresholds (75, 90 and 100% by default), when a CLI " +
            "session saturates, or when a session's last request goes unanswered for longer than " +
            "the chosen limit. The threshold is a floor: 89.9% has not crossed 90%. There is " +
            "one more warning of a different nature: the ones above measure the distance to the " +
            "quota ceiling, and this one measures the distance to your own habit — it goes out " +
            "when the day's usage passes a multiple of the median of the last few days, at the " +
            "same time of day. With fewer than three measured days, or with usual consumption " +
            "near zero, it is not sent. During a muted period the warning is postponed, not " +
            "lost — it goes out once the silence ends. The quota threshold does not reach " +
            "prepaid balance sources (DeepSeek, OpenRouter), which have no ceiling to measure a " +
            "percentage against, nor observed activity ones (Gemini CLI, Kilo Free, OpenCode Zen " +
            "Free), which report no limit: on those, no threshold is ever evaluated. The other " +
            "three warnings still apply.",
        steps = listOf(
            "Open Settings → \"Alerts\".",
            "Turn on \"Warn when a quota crosses a threshold\" and adjust the thresholds.",
            "Turn on \"Warn when a CLI session saturates\" and \"Warn when a CLI session goes " +
                "unanswered\", with the waiting time next to it.",
            "Turn on \"Warn when the day's usage departs from the usual\" and pick the " +
                "multiple (2×, 3× or 5×) next to it.",
            "Use \"Mute during a time range\" for the part of the day when no notification " +
                "should go out."
        ),
        mediaId = "alerts"
    )

    HelpTopic.EXPORT -> HelpEntry(
        title = "Export and PDF report",
        summary = "CSV, JSON and a PDF report of what is on screen.",
        description = "CSV and JSON export the open tab — sessions or breakdown — for the chosen " +
            "window. The PDF report does not follow the tab: it is the whole window, with " +
            "totals, axes and sessions together. A turn with no known price exports an empty " +
            "cell, never a zero: a zero would claim it cost nothing. Only usage metadata leaves " +
            "here, never prompt or reply content.",
        steps = listOf(
            "Open the CLI sessions screen or the team sessions modal.",
            "Choose the window and the tab to export.",
            "Use the CSV, JSON or PDF buttons in the top bar and pick where to save."
        ),
        mediaId = "export"
    )

    HelpTopic.TEAM -> HelpEntry(
        title = "Team view",
        summary = "The same account's usage added up across the team's machines.",
        description = "Optional and off by default. A Node.js server the company hosts receives " +
            "the indexed turns from each machine and returns the aggregated view per account: " +
            "usage per member, each one's sessions, the breakdown by axis and a thirty-day " +
            "trend. Each person uses the key the administrator issued for them. Only usage " +
            "metadata travels — no prompt or reply content.",
        steps = listOf(
            "Open Settings → \"Team\" and turn the integration on.",
            "Fill in \"Server\", \"Team key\" and \"Your alias\".",
            "Tick the accounts under \"Accounts in the team\".",
            "Click \"Test connection\": that is what binds the account to the key.",
            "The account card gains the \"Team sessions in this account\" button."
        ),
        mediaId = "team"
    )

    HelpTopic.PRESENCE -> HelpEntry(
        title = "Live presence",
        summary = "Who has the app open, and who is actually running the CLI right now.",
        description = "Presence shows two separate states, not one scale: \"online\" means the " +
            "app is open, with a heartbeat every thirty seconds; \"working now\" means a turn was " +
            "produced in the last five minutes. Collapsing them would hide exactly the case the " +
            "screen exists for — someone with the app open and idle. The timestamps come from " +
            "the server clock, so a machine with a slow clock does not see the whole team online " +
            "forever.",
        steps = listOf(
            "With the team integration on, click \"Who is connected now\" on the account card.",
            "Whoever administers the server has the same button in the main window footer, and " +
                "there the list covers every account."
        ),
        mediaId = "presence"
    )

    HelpTopic.WINDOW_MODES -> HelpEntry(
        title = "HUD strip",
        summary = "The whole app in a floating, always-on-top notch.",
        description = "The app lives in a notch docked to a " +
            "screen edge, always above other windows, with one ring per account — one arc per " +
            "quota —, each window's percentage (7d, 5h) and the status word. Hovering a ring opens that " +
            "account's balloon: each quota with its bar, how much is used and left and when " +
            "it resets, the plan and where the reading came from, and the card's own buttons. " +
            "Clicking a ring refreshes that account. At the ends of the notch sit the hand, " +
            "which moves it, and the gear, which opens version, update, export, Settings and " +
            "Help — including how long is left until the next automatic collection.",
        steps = listOf(
            "Hover the gear at the end of the notch to open \"Settings\", \"Help\", " +
                "\"Refresh now\" and the export.",
            "The tray icon menu has \"Open\", which brings the notch to the front when another " +
                "window covers it.",
            "Drag the hand at the end of the notch (only the hand moves it) and " +
                "drop it near any edge of any monitor — top, bottom or the sides: it docks to " +
                "the nearest one and stays there next time.",
            "To read the rings: the outer one is the longest window (the weekly one) and the " +
                "inner one the shortest (the 5h one). In the balloon, the glyph next to each " +
                "quota lights its ring.",
            "To tell two Claude accounts apart: in Settings → \"Accounts\", click \"Edit\" on " +
                "the profile and pick the \"Color\" and the \"Emoji\" — the emoji sits at the " +
                "corner of the ring and next to the name in the balloon and on the card.",
            "Claude Code sessions with a growing or saturated context, or with no reply since " +
                "the last request, show up in the \"CLI sessions\" section of the account's balloon.",
            "With no API enabled the notch says \"No APIs\" and Settings open by themselves " +
                "at startup."
        ),
        mediaId = "window-modes"
    )

    HelpTopic.APPEARANCE -> HelpEntry(
        title = "Appearance and window",
        summary = "Theme, language, interface scale, opacity and start with the system.",
        description = "Every theme is drawn from the same tokens, in both modes. The scale " +
            "changes the density of the composition, not just the text size: icons, spacing and " +
            "click targets grow along with it, and the proportions of the screen stay put. The " +
            "main window remembers its size and position between runs.",
        steps = listOf(
            "Open Settings → \"Appearance\".",
            "Pick the theme and the \"Language\". The \"HUD bar preview\" at the top of the tab " +
                "shows the bar with the chosen theme, size and opacity.",
            "\"Interface size\" ranges from 80% to 150%; the window is resized along with it. " +
                "\"Window opacity\" is on the same tab.",
            "\"System Startup\" lives under Settings → \"System\"."
        ),
        mediaId = "appearance"
    )

    HelpTopic.UPDATES -> HelpEntry(
        title = "Automatic updates",
        summary = "The new version downloaded in the background and swapped in on exit.",
        description = "When enabled, the update downloads the new version in the background, " +
            "checks the file's SHA-256 against what the GitHub API publishes, and swaps it in " +
            "when the app closes. On Windows this covers the .exe installer setup; on Linux, the " +
            "script install in a managed tree; on macOS the mechanism does not exist and the " +
            "switch says why. After a version change, the what's new window lists what changed.",
        steps = listOf(
            "Open Settings → \"System\" and turn on \"Automatic updates\".",
            "If your install cannot be swapped, the switch shows up disabled with the reason " +
                "written next to it.",
            "Once the version is downloaded, the strip at the top of the dashboard — or, in the HUD " +
                "bar, the gear balloon, marked with a colored dot — offers the " +
                "\"$UPDATE_RESTART_ACTION_EN\" button; without clicking it, the swap happens the next " +
                "time the app closes.",
            "To try versions before the stable release, turn on \"$BETA_UPDATES_LABEL_EN\" right " +
                "below. The strip, the HUD balloon, what's new and the footer say \"beta\"; turning it " +
                "off does not roll back — the app stays on the beta until a newer stable comes out."
        ),
        mediaId = "updates"
    )
}
