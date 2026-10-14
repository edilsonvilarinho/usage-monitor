package com.usagemonitor.domain.entity

/**
 * Fontes cuja coleta exige uma chave em `~/.usage-monitor/api-keys.json`.
 *
 * Dono único do conjunto: o filtro de arranque (`API_KEY_DEPENDENT_SOURCES`), as
 * Configurações, o selo da linha da fonte e o `LocalApiKeyDataSource` derivam
 * daqui. Antes eram seis literais, e o selo "Chave necessária" já tinha esquecido
 * a OpenRouter.
 *
 * `when` exaustivo e sem `else`: fonte nova não compila sem decidir se tem chave local.
 */
fun ApiSource.requiresLocalApiKey(): Boolean {
    return when (this) {
        ApiSource.MINIMAX,
        ApiSource.DEEPSEEK,
        ApiSource.OPENCODE_GO,
        ApiSource.OPENROUTER -> true
        ApiSource.ANTHROPIC,
        ApiSource.CODEX,
        ApiSource.OPENCODE,
        ApiSource.KILO,
        ApiSource.GEMINI,
        ApiSource.CURSOR,
        ApiSource.ANTIGRAVITY -> false
    }
}
