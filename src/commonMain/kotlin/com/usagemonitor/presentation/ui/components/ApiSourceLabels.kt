package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage

/**
 * Selo de origem das fontes lidas localmente (Gemini CLI, Cursor, Antigravity), ou
 * `null` para as demais. Texto de tela: saiu do domain, que só classifica.
 *
 * `when` exaustivo: fonte nova não compila sem decidir se tem selo.
 */
internal fun ApiSource.statusBadgeLabel(language: AppLanguage): String? {
    val pt = language == AppLanguage.PT
    return when (this) {
        ApiSource.GEMINI -> if (pt) "Uso local" else "Local usage"
        ApiSource.CURSOR -> if (pt) "Sessão local" else "Local session"
        ApiSource.ANTIGRAVITY -> if (pt) "CLI local" else "Local CLI"
        ApiSource.ANTHROPIC,
        ApiSource.MINIMAX,
        ApiSource.CODEX,
        ApiSource.DEEPSEEK,
        ApiSource.OPENCODE,
        ApiSource.OPENCODE_GO,
        ApiSource.KILO,
        ApiSource.OPENROUTER -> null
    }
}

/** A linha de apoio das mesmas fontes de [statusBadgeLabel], ou `null`. */
internal fun ApiSource.statusSupportingText(language: AppLanguage): String? {
    val pt = language == AppLanguage.PT
    return when (this) {
        ApiSource.GEMINI -> if (pt) "Tokens observados no Gemini CLI" else "Tokens observed in Gemini CLI"
        ApiSource.CURSOR -> if (pt) "Lê o uso pela sessão já conectada no Cursor" else "Reads usage through your existing Cursor session"
        ApiSource.ANTIGRAVITY -> if (pt) "Consulta /usage no Antigravity CLI já autenticado" else "Reads /usage from your already authenticated Antigravity CLI"
        ApiSource.ANTHROPIC,
        ApiSource.MINIMAX,
        ApiSource.CODEX,
        ApiSource.DEEPSEEK,
        ApiSource.OPENCODE,
        ApiSource.OPENCODE_GO,
        ApiSource.KILO,
        ApiSource.OPENROUTER -> null
    }
}
