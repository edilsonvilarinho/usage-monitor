package com.usagemonitor.domain.entity

fun ApiSource.displayName(language: AppLanguage = AppLanguage.PT): String {
    return when (this) {
        ApiSource.ANTHROPIC -> "Anthropic"
        ApiSource.MINIMAX -> "MiniMax"
        ApiSource.CODEX -> "Codex"
        ApiSource.DEEPSEEK -> "DeepSeek"
        ApiSource.OPENCODE -> "OpenCode Zen Free"
        ApiSource.KILO -> if (language == AppLanguage.PT) "Kilo Free" else "Kilo Free"
    }
}

fun ApiSource.isObservedActivitySource(): Boolean {
    return this == ApiSource.OPENCODE || this == ApiSource.KILO
}

fun ApiSource.statusBadgeLabel(language: AppLanguage = AppLanguage.PT): String? {
    return when (this) {
        ApiSource.CODEX -> if (language == AppLanguage.PT) "Instável" else "Unstable"
        else -> null
    }
}

fun ApiSource.statusSupportingText(language: AppLanguage = AppLanguage.PT): String? {
    return when (this) {
        ApiSource.CODEX -> if (language == AppLanguage.PT) {
            "Monitoramento em transição: o contrato de uso mudou e os limites podem oscilar até a fonte estabilizar."
        } else {
            "Usage monitoring is in transition: the usage contract changed and limits may fluctuate until the source stabilizes."
        }
        else -> null
    }
}
