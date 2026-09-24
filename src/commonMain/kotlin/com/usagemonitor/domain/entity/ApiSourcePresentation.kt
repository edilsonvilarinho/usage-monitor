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

/**
 * Fontes cujo card mostra atividade observada localmente — requisições do OpenCode
 * e Kilo, tokens registrados pelo Gemini CLI — em vez de cotas de conta.
 *
 * `OPENCODE_GO` deliberadamente **não** entra: ele devolve percentual de três
 * janelas com `resetsAt`, exatamente a forma da Anthropic, e o resumo de
 * atividade observada não desenha barra nem projeção.
 */
fun ApiSource.isObservedActivitySource(): Boolean {
    return this == ApiSource.OPENCODE || this == ApiSource.KILO || this == ApiSource.GEMINI
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
