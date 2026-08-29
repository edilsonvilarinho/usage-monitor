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
    }
}

/**
 * Fontes cujo card mostra atividade observada — requisições contadas de um banco
 * local — em vez de cotas com percentual e reinício.
 *
 * `OPENCODE_GO` deliberadamente **não** entra: ele devolve percentual de três
 * janelas com `resetsAt`, exatamente a forma da Anthropic, e o resumo de
 * atividade observada não desenha barra nem projeção.
 */
fun ApiSource.isObservedActivitySource(): Boolean {
    return this == ApiSource.OPENCODE || this == ApiSource.KILO
}

fun ApiSource.statusBadgeLabel(language: AppLanguage = AppLanguage.PT): String? {
    return null
}

fun ApiSource.statusSupportingText(language: AppLanguage = AppLanguage.PT): String? {
    return null
}
