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

/**
 * Fontes cujo card mostra atividade observada localmente — requisições do OpenCode
 * e Kilo, tokens registrados pelo Gemini CLI — em vez de cotas de conta.
 *
 * `OPENCODE_GO` deliberadamente **não** entra: ele devolve percentual de três
 * janelas com `resetsAt`, exatamente a forma da Anthropic, e o resumo de
 * atividade observada não desenha barra nem projeção.
 *
 * O ramo `OBSERVED_ACTIVITY` de [quotaThresholdGap] tem de dar o mesmo conjunto;
 * `ApiSourceClassificationTest` compara os dois.
 */
fun ApiSource.isObservedActivitySource(): Boolean {
    return when (this) {
        ApiSource.OPENCODE,
        ApiSource.KILO,
        ApiSource.GEMINI -> true
        ApiSource.ANTHROPIC,
        ApiSource.CODEX,
        ApiSource.MINIMAX,
        ApiSource.DEEPSEEK,
        ApiSource.OPENCODE_GO,
        ApiSource.OPENROUTER,
        ApiSource.CURSOR,
        ApiSource.ANTIGRAVITY -> false
    }
}

/**
 * Fontes com índice local de sessões CLI por modelo: Claude CLI (Anthropic) e Codex.
 *
 * Coincide hoje com [requiresUsageAccount], mas é outro fato — conta de uso não
 * implica índice de sessões —, e por isso tem dono próprio.
 */
fun ApiSource.hasCliSessionIndex(): Boolean {
    return when (this) {
        ApiSource.ANTHROPIC,
        ApiSource.CODEX -> true
        ApiSource.MINIMAX,
        ApiSource.DEEPSEEK,
        ApiSource.OPENCODE,
        ApiSource.OPENCODE_GO,
        ApiSource.KILO,
        ApiSource.OPENROUTER,
        ApiSource.GEMINI,
        ApiSource.CURSOR,
        ApiSource.ANTIGRAVITY -> false
    }
}
