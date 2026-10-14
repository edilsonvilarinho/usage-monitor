package com.usagemonitor.domain.entity

fun ApiSource.displayName(language: AppLanguage = AppLanguage.PT): String {
    return when (this) {
        ApiSource.ANTHROPIC -> "Anthropic"
        ApiSource.MINIMAX -> "MiniMax"
        ApiSource.CODEX -> "Codex"
        ApiSource.DEEPSEEK -> "DeepSeek"
        ApiSource.OPENCODE -> "OpenCode Zen Free"
        ApiSource.OPENCODE_GO -> "OpenCode Go"
        ApiSource.KILO -> if (language == AppLanguage.PT) "Kilo Free" else "Kilo Free"
        ApiSource.OPENROUTER -> "OpenRouter"
        ApiSource.GEMINI -> "Gemini CLI"
        ApiSource.CURSOR -> "Cursor"
        ApiSource.ANTIGRAVITY -> "Antigravity CLI"
    }
}

fun ApiSource.statusBadgeLabel(language: AppLanguage = AppLanguage.PT): String? {
    return if (this == ApiSource.GEMINI) {
        if (language == AppLanguage.PT) "Uso local" else "Local usage"
    } else if (this == ApiSource.CURSOR) {
        if (language == AppLanguage.PT) "Sessão local" else "Local session"
    } else if (this == ApiSource.ANTIGRAVITY) {
        if (language == AppLanguage.PT) "CLI local" else "Local CLI"
    } else {
        null
    }
}

fun ApiSource.statusSupportingText(language: AppLanguage = AppLanguage.PT): String? {
    return if (this == ApiSource.GEMINI) {
        if (language == AppLanguage.PT) "Tokens observados no Gemini CLI" else "Tokens observed in Gemini CLI"
    } else if (this == ApiSource.CURSOR) {
        if (language == AppLanguage.PT) "Lê o uso pela sessão já conectada no Cursor" else "Reads usage through your existing Cursor session"
    } else if (this == ApiSource.ANTIGRAVITY) {
        if (language == AppLanguage.PT) "Consulta /usage no Antigravity CLI já autenticado" else "Reads /usage from your already authenticated Antigravity CLI"
    } else {
        null
    }
}
