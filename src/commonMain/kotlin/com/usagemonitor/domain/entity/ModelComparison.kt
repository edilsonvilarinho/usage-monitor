package com.usagemonitor.domain.entity

/**
 * Uso de um modelo do Codex num recorte, somado resposta a resposta (#386).
 * O Codex não tem tarifa: não há custo aqui, e não há zero no lugar dele.
 */
data class CodexCliModelUsage(
    val model: String,
    val responseCount: Int,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val throughput: OutputThroughput?
)

/** Métricas da comparação (#386, direção P7). Ordem = ordem das colunas. */
enum class ComparisonMetric { TOKENS, COST, CACHE, THROUGHPUT, QUOTA }

/**
 * Uma linha da comparação entre modelos e fontes.
 *
 * Cada métrica é `null` quando a fonte não a mede — MiniMax não informa tokens,
 * Codex não tem tarifa, fonte sem CLI não tem vazão. Nulo é "não medido" e a
 * tela escreve isso; zero seria afirmar uma medida que não existe.
 */
data class ComparisonRow(
    val source: ApiSource,
    /** Modelo, ou o nome da fonte quando a linha é da fonte inteira. */
    val label: String,
    val turnCount: Int?,
    val totalTokens: Long?,
    val costMicros: Long?,
    /** Turnos sem tarifa conhecida: o custo da linha é piso, exibido com "+". */
    val unpricedTurnCount: Int = 0,
    val cacheRate: Double?,
    val throughput: OutputThroughput?,
    /** Maior percentual entre as cotas da fonte agora (0–100); é da conta, não do modelo. */
    val quotaPercent: Int?
) {
    /** Valor numérico da métrica, para a escala do mapa de calor. */
    fun valueOf(metric: ComparisonMetric): Double? = when (metric) {
        ComparisonMetric.TOKENS -> totalTokens?.toDouble()
        ComparisonMetric.COST -> costMicros?.toDouble()
        ComparisonMetric.CACHE -> cacheRate
        ComparisonMetric.THROUGHPUT -> throughput?.tokensPerSecond
        ComparisonMetric.QUOTA -> quotaPercent?.toDouble()
    }
}

/**
 * Intensidade da célula no mapa de calor: valor sobre o maior da coluna (0–1).
 * `null` sem valor; coluna sem nenhum valor positivo dá zero em todas — a cor é
 * apoio, o número está sempre escrito na célula.
 */
fun heatIntensity(rows: List<ComparisonRow>, row: ComparisonRow, metric: ComparisonMetric): Double? {
    val value = row.valueOf(metric) ?: return null
    val max = rows.mapNotNull { candidate -> candidate.valueOf(metric) }.maxOrNull() ?: return null
    if (max <= 0.0) {
        return 0.0
    }
    return (value / max).coerceIn(0.0, 1.0)
}

/**
 * Monta as linhas: modelos do Claude CLI, modelos do Codex e uma linha por fonte
 * sem CLI com a cota corrente. Ordem total e determinística — fonte (ordem do
 * enum), tokens decrescentes, rótulo — para a lista publicada não recompor à toa.
 */
fun buildComparisonRows(
    claudeModels: List<CliUsageBucket>,
    claudeThroughputs: Map<String, OutputThroughput>,
    codexModels: List<CodexCliModelUsage>,
    stats: List<ApiUsageStats>
): List<ComparisonRow> {
    val quotaBySource = stats
        .groupBy { item -> item.source }
        .mapValues { (_, items) -> items.flatMap { item -> item.quotas }.mapNotNull(::quotaPercentOf).maxOrNull() }

    val claudeRows = claudeModels.map { bucket ->
        ComparisonRow(
            source = ApiSource.ANTHROPIC,
            label = bucket.label ?: "—",
            turnCount = bucket.turnCount,
            totalTokens = bucket.totalTokens,
            costMicros = bucket.costMicros,
            unpricedTurnCount = bucket.unpricedTurnCount,
            cacheRate = bucket.cacheHitRate,
            throughput = bucket.label?.let { model -> claudeThroughputs[model] },
            quotaPercent = quotaBySource[ApiSource.ANTHROPIC]
        )
    }
    val codexRows = codexModels.map { usage ->
        ComparisonRow(
            source = ApiSource.CODEX,
            label = usage.model,
            turnCount = usage.responseCount,
            totalTokens = usage.totalTokens,
            costMicros = null,
            cacheRate = if (usage.inputTokens > 0L) usage.cachedInputTokens.toDouble() / usage.inputTokens else null,
            throughput = usage.throughput,
            quotaPercent = quotaBySource[ApiSource.CODEX]
        )
    }
    val sourceRows = stats
        .filter { item -> item.source != ApiSource.ANTHROPIC && item.source != ApiSource.CODEX }
        .map { item ->
            ComparisonRow(
                source = item.source,
                label = item.profileLabel?.let { label -> "${item.apiName} · $label" } ?: item.apiName,
                turnCount = null,
                totalTokens = null,
                costMicros = null,
                cacheRate = null,
                throughput = null,
                quotaPercent = item.quotas.mapNotNull(::quotaPercentOf).maxOrNull()
            )
        }

    return (claudeRows + codexRows + sourceRows).sortedWith(
        compareBy<ComparisonRow> { row -> row.source.ordinal }
            .thenByDescending { row -> row.totalTokens ?: -1L }
            .thenBy { row -> row.label }
    )
}

/** Percentual de uma cota com janela; saldo e atividade sem limite não têm percentual. */
private fun quotaPercentOf(quota: QuotaInfo): Int? {
    if (!quota.hasKnownResetAt || quota.unit == UsageUnit.CURRENCY_USD || quota.total <= 0L) {
        return null
    }
    return (quota.percentageUsed * 100f).toInt()
}
