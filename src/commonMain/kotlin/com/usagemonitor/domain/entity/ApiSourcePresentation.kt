package com.usagemonitor.domain.entity

/**
 * Nome de marca da fonte. Não é texto traduzível: é o mesmo nos dois idiomas, e por
 * isso mora no domain e não recebe idioma. Rótulos de estado traduzidos ficam em
 * `presentation/ui/components/ApiSourceLabels.kt`.
 */
fun ApiSource.displayName(): String {
    return when (this) {
        ApiSource.ANTHROPIC -> "Anthropic"
        ApiSource.MINIMAX -> "MiniMax"
        ApiSource.CODEX -> "Codex"
        ApiSource.DEEPSEEK -> "DeepSeek"
        ApiSource.OPENCODE -> "OpenCode Zen Free"
        ApiSource.OPENCODE_GO -> "OpenCode Go"
        ApiSource.KILO -> "Kilo Free"
        ApiSource.OPENROUTER -> "OpenRouter"
        ApiSource.GEMINI -> "Gemini CLI"
        ApiSource.CURSOR -> "Cursor"
        ApiSource.ANTIGRAVITY -> "Antigravity CLI"
    }
}
