package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Um turno do assistente dentro de uma sessão do Claude Code.
 *
 * Os valores são o **delta real daquele turno**, lidos do `message.usage` da linha
 * `assistant` do transcript — não o acumulado da sessão. `messageId` é a chave de
 * deduplicação: a mesma mensagem pode aparecer repetida no `.jsonl`.
 */
data class CliSessionTurn(
    val sessionId: String,
    val seq: Int,
    val messageId: String,
    val ts: Instant,
    val model: String?,
    val isSidechain: Boolean = false,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
) {
    val cacheWriteTokens: Long
        get() = cacheWrite5mTokens + cacheWrite1hTokens

    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens

    val pricing: ModelPricing?
        get() = ModelPricingTable.forModel(model)

    /** Custo do turno em micros de USD, ou `null` quando o modelo não tem preço conhecido. */
    val costMicros: Long?
        get() = pricing?.costMicros(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )

    /**
     * Economia gerada pelo cache neste turno: o que os tokens lidos do cache
     * teriam custado como input menos o que custaram como cache read.
     */
    val cacheSavingsMicros: Long?
        get() {
            val modelPricing = pricing ?: return null
            val delta = modelPricing.inputMicrosPerMillion - modelPricing.cacheReadMicrosPerMillion
            return cacheReadTokens * delta / MICROS_PER_USD
        }
}

/**
 * Raiz de transcripts de uma conta Anthropic.
 *
 * Os transcripts não carregam identidade de conta. A atribuição vem do
 * caminho: cada perfil tem seu próprio config dir do Claude Code
 * (`~/.claude`, `~/.claude-conta2`, ...) e cada um tem seu `projects/`.
 */
data class CliProjectRoot(
    val profileId: String,
    val directoryPath: String
)

/**
 * Agregados de uma sessão, servidos direto do índice SQLite.
 *
 * Quando a leitura usa uma janela temporal, todos os valores são os da janela —
 * não os totais históricos da sessão.
 */
data class CliSessionSummary(
    val sessionId: String,
    val filePath: String,
    /** Perfil Anthropic dono do transcript; `null` em linhas ainda não reatribuídas. */
    val profileId: String? = null,
    val cwd: String? = null,
    val gitBranch: String? = null,
    /**
     * Máquina onde o transcript foi indexado. Os transcripts do Claude Code não
     * carregam identidade de máquina, então o valor é o hostname de quem leu o
     * arquivo — na prática a máquina onde a sessão rodou, já que a leitura é do
     * filesystem local.
     */
    val hostName: String? = null,
    val firstTs: Instant,
    val lastTs: Instant,
    val primaryModel: String? = null,
    val turnCount: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L,
    val costMicros: Long = 0L,
    /** Turnos cujo modelo não está na tabela de preços: o custo exibido está incompleto. */
    val unpricedTurnCount: Int = 0,
    /** O `.jsonl` de origem não existe mais (retenção do CLI); só o resumo sobrevive. */
    val stale: Boolean = false
) {
    val cacheWriteTokens: Long
        get() = cacheWrite5mTokens + cacheWrite1hTokens

    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens

    /** `cacheRead / (cacheRead + cacheWrite)`. Zero quando não houve atividade de cache. */
    val cacheHitRate: Double
        get() {
            val denominator = cacheReadTokens + cacheWriteTokens
            if (denominator <= 0L) {
                return 0.0
            }
            return cacheReadTokens.toDouble() / denominator.toDouble()
        }

    /** O custo cobre todos os turnos apenas quando nenhum modelo ficou sem preço. */
    val isCostComplete: Boolean
        get() = unpricedTurnCount == 0

    /** Último segmento do `cwd`, usado como nome do projeto na lista. */
    val projectName: String?
        get() = cwd
            ?.trimEnd('/', '\\')
            ?.split('/', '\\')
            ?.lastOrNull()
            ?.takeIf { it.isNotBlank() }
}

/** Sessão com os turnos carregados, entrada do cálculo de analytics. */
data class CliSessionDetail(
    val summary: CliSessionSummary,
    val turns: List<CliSessionTurn>
)

/** Resultado de uma passada de indexação incremental sobre `~/.claude/projects`. */
data class CliSessionIndexReport(
    val scannedFiles: Int = 0,
    val updatedFiles: Int = 0,
    /** Linhas que falharam o parse e foram ignoradas — nunca derrubam a sessão. */
    val skippedLines: Int = 0
)

/** Distribuição do custo da sessão por componente, em micros de USD. */
data class CliSessionCostBreakdown(
    val inputMicros: Long = 0L,
    val outputMicros: Long = 0L,
    val cacheReadMicros: Long = 0L,
    val cacheWriteMicros: Long = 0L
) {
    val totalMicros: Long
        get() = inputMicros + outputMicros + cacheReadMicros + cacheWriteMicros

    fun fractionOf(component: Long): Double {
        val total = totalMicros
        if (total <= 0L) {
            return 0.0
        }
        return component.toDouble() / total.toDouble()
    }
}

/**
 * Métricas derivadas de uma sessão.
 *
 * As séries e as métricas de contexto usam apenas turnos da thread principal
 * (`isSidechain == false`): um subagente tem contexto próprio e misturá-lo
 * distorceria o gráfico. Os valores monetários somam **todos** os turnos,
 * porque o gasto do subagente é real.
 */
data class CliSessionAnalytics(
    val cacheHitRate: Double = 0.0,
    val cacheSavingsMicros: Long = 0L,
    val averageContextPerTurn: Long = 0L,
    val liveContextTokens: Long = 0L,
    val nextInteractionCostMicros: Long = 0L,
    val contextSaturation: Double? = null,
    val costBreakdown: CliSessionCostBreakdown = CliSessionCostBreakdown(),
    val mainTurnCount: Int = 0,
    val sidechainTurnCount: Int = 0,
    val unpricedTurnCount: Int = 0,
    val contextPerTurn: List<Long> = emptyList(),
    val cacheWrite5mPerTurn: List<Long> = emptyList(),
    val cacheWrite1hPerTurn: List<Long> = emptyList(),
    val cumulativeCostMicros: List<Long> = emptyList(),
    val cumulativeSavingsMicros: List<Long> = emptyList()
) {
    /**
     * Status da sessão: quanto da janela de contexto já está ocupado e quanto
     * custa continuar. As duas dimensões são avaliadas em paralelo — numa
     * janela pequena a fração pode ser modesta e a mensagem ainda sair cara.
     */
    val health: CliSessionHealth
        get() {
            val saturation = contextSaturation ?: 0.0
            return when {
                saturation >= CliSessionHealthThresholds.SATURATED_WINDOW_FRACTION ||
                    nextInteractionCostMicros >= CliSessionHealthThresholds.SATURATED_NEXT_COST_MICROS ->
                    CliSessionHealth.SATURATED

                saturation >= CliSessionHealthThresholds.ATTENTION_WINDOW_FRACTION ||
                    nextInteractionCostMicros >= CliSessionHealthThresholds.ATTENTION_NEXT_COST_MICROS ->
                    CliSessionHealth.ATTENTION

                else -> CliSessionHealth.HEALTHY
            }
        }

    val isSaturated: Boolean
        get() = health == CliSessionHealth.SATURATED

    val isCostComplete: Boolean
        get() = unpricedTurnCount == 0
}

/** Recomendação sobre continuar a sessão ou recomeçar com o contexto enxuto. */
enum class CliSessionHealth {
    /** Contexto pequeno para a janela do modelo e mensagem barata. */
    HEALTHY,

    /** Contexto crescendo: compactar já reduz o custo por mensagem. */
    ATTENTION,

    /** Continuar sai caro e a janela está perto do limite. */
    SATURATED
}
