package com.usagemonitor.data.export

import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.MICROS_PER_USD
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Formato de saída da exportação. */
enum class UsageExportFormat(val extension: String) {
    CSV("csv"),
    JSON("json")
}

/**
 * Serializa o que a tela de Sessões CLI mostra.
 *
 * Vive em `data` porque JSON usa `kotlinx.serialization`, que o domain não pode
 * importar. O CSV é montado à mão: uma dependência de biblioteca para gerar
 * quatro colunas seria desproporcional, e o escape está isolado em [csvCell].
 *
 * **Não exporta conteúdo de prompt ou resposta** — só metadados de uso, na mesma
 * regra que a integração com time segue.
 */
object UsageExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun exportSessions(sessions: List<CliSessionSummary>, format: UsageExportFormat): String {
        return when (format) {
            UsageExportFormat.CSV -> sessionsCsv(sessions)
            UsageExportFormat.JSON -> json.encodeToString(sessions.map { it.toDto() })
        }
    }

    fun exportTurns(turns: List<CliSessionTurn>, format: UsageExportFormat): String {
        return when (format) {
            UsageExportFormat.CSV -> turnsCsv(turns)
            UsageExportFormat.JSON -> json.encodeToString(turns.map { it.toDto() })
        }
    }

    fun exportBreakdown(breakdown: CliUsageBreakdown, format: UsageExportFormat): String {
        return when (format) {
            UsageExportFormat.CSV -> breakdownCsv(breakdown)
            UsageExportFormat.JSON -> json.encodeToString(breakdown.toDto())
        }
    }

    private fun sessionsCsv(sessions: List<CliSessionSummary>): String {
        val header = listOf(
            "session_id", "project", "git_branch", "profile_id", "host_name",
            "first_ts", "last_ts", "primary_model", "turn_count",
            "input_tokens", "output_tokens", "cache_read_tokens",
            "cache_write_5m_tokens", "cache_write_1h_tokens",
            "cost_usd", "cost_is_complete", "live_context_tokens"
        )
        val rows = sessions.map { session ->
            listOf(
                session.sessionId,
                session.projectName.orEmpty(),
                session.gitBranch.orEmpty(),
                session.profileId.orEmpty(),
                session.hostName.orEmpty(),
                session.firstTs.toString(),
                session.lastTs.toString(),
                session.primaryModel.orEmpty(),
                session.turnCount.toString(),
                session.inputTokens.toString(),
                session.outputTokens.toString(),
                session.cacheReadTokens.toString(),
                session.cacheWrite5mTokens.toString(),
                session.cacheWrite1hTokens.toString(),
                usdCell(session.costMicros),
                session.isCostComplete.toString(),
                session.liveContextTokens.toString()
            )
        }
        return csvOf(header, rows)
    }

    private fun turnsCsv(turns: List<CliSessionTurn>): String {
        val header = listOf(
            "session_id", "seq", "message_id", "ts", "model", "is_sidechain",
            "input_tokens", "output_tokens", "cache_read_tokens",
            "cache_write_5m_tokens", "cache_write_1h_tokens", "cost_usd"
        )
        val rows = turns.map { turn ->
            listOf(
                turn.sessionId,
                turn.seq.toString(),
                turn.messageId,
                turn.ts.toString(),
                turn.model.orEmpty(),
                turn.isSidechain.toString(),
                turn.inputTokens.toString(),
                turn.outputTokens.toString(),
                turn.cacheReadTokens.toString(),
                turn.cacheWrite5mTokens.toString(),
                turn.cacheWrite1hTokens.toString(),
                // Turno de modelo sem tarifa sai vazio, e não zero: zero afirmaria
                // que não custou nada.
                turn.costMicros?.let { micros -> usdCell(micros) } ?: ""
            )
        }
        return csvOf(header, rows)
    }

    private fun breakdownCsv(breakdown: CliUsageBreakdown): String {
        val header = listOf(
            "axis", "label", "session_count", "turn_count", "total_tokens",
            "cost_usd", "cache_savings_usd", "cache_hit_rate", "cost_is_complete"
        )
        // O eixo vai como coluna: três arquivos seriam pior para quem vai abrir
        // isso numa planilha e filtrar.
        val rows = axisRows("project", breakdown.byProject) +
            axisRows("model", breakdown.byModel) +
            axisRows("branch", breakdown.byBranch) +
            axisRows("total", listOf(breakdown.totals))
        return csvOf(header, rows)
    }

    private fun axisRows(axis: String, buckets: List<CliUsageBucket>): List<List<String>> {
        return buckets.map { bucket ->
            listOf(
                axis,
                bucket.label.orEmpty(),
                bucket.sessionCount.toString(),
                bucket.turnCount.toString(),
                bucket.totalTokens.toString(),
                usdCell(bucket.costMicros),
                usdCell(bucket.cacheSavingsMicros),
                bucket.cacheHitRate.toString(),
                bucket.isCostComplete.toString()
            )
        }
    }

    private fun csvOf(header: List<String>, rows: List<List<String>>): String {
        val builder = StringBuilder()
        builder.append(header.joinToString(",") { cell -> csvCell(cell) })
        builder.append('\n')
        for (row in rows) {
            builder.append(row.joinToString(",") { cell -> csvCell(cell) })
            builder.append('\n')
        }
        return builder.toString()
    }

    /**
     * Escapa uma célula segundo o RFC 4180: aspas duplicadas e o campo entre
     * aspas quando contém vírgula, aspas ou quebra de linha.
     *
     * Nome de projeto e branch são texto livre do usuário e chegam aqui sem
     * verificação: sem o escape, uma vírgula num nome deslocaria todas as
     * colunas seguintes da linha.
     */
    internal fun csvCell(value: String): String {
        val needsQuotes = value.any { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }
        if (!needsQuotes) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

    /** Micros em USD com quatro casas — a mesma precisão que a tela do detalhe usa. */
    private fun usdCell(micros: Long): String {
        val sign = if (micros < 0L) "-" else ""
        val absolute = if (micros < 0L) -micros else micros
        val dollars = absolute / MICROS_PER_USD
        val fraction = (absolute % MICROS_PER_USD) / 100L
        return "$sign$dollars.${fraction.toString().padStart(4, '0')}"
    }
}

@Serializable
private data class ExportedSessionDto(
    @SerialName("session_id") val sessionId: String,
    val project: String? = null,
    @SerialName("git_branch") val gitBranch: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("host_name") val hostName: String? = null,
    @SerialName("first_ts") val firstTs: String,
    @SerialName("last_ts") val lastTs: String,
    @SerialName("primary_model") val primaryModel: String? = null,
    @SerialName("turn_count") val turnCount: Int,
    @SerialName("input_tokens") val inputTokens: Long,
    @SerialName("output_tokens") val outputTokens: Long,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long,
    @SerialName("cache_write_5m_tokens") val cacheWrite5mTokens: Long,
    @SerialName("cache_write_1h_tokens") val cacheWrite1hTokens: Long,
    @SerialName("cost_micros_usd") val costMicros: Long,
    @SerialName("cost_is_complete") val costIsComplete: Boolean,
    @SerialName("live_context_tokens") val liveContextTokens: Long
)

private fun CliSessionSummary.toDto(): ExportedSessionDto {
    return ExportedSessionDto(
        sessionId = sessionId,
        project = projectName,
        gitBranch = gitBranch,
        profileId = profileId,
        hostName = hostName,
        firstTs = firstTs.toString(),
        lastTs = lastTs.toString(),
        primaryModel = primaryModel,
        turnCount = turnCount,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWrite5mTokens = cacheWrite5mTokens,
        cacheWrite1hTokens = cacheWrite1hTokens,
        costMicros = costMicros,
        costIsComplete = isCostComplete,
        liveContextTokens = liveContextTokens
    )
}

@Serializable
private data class ExportedTurnDto(
    @SerialName("session_id") val sessionId: String,
    val seq: Int,
    @SerialName("message_id") val messageId: String,
    val ts: String,
    val model: String? = null,
    @SerialName("is_sidechain") val isSidechain: Boolean,
    @SerialName("input_tokens") val inputTokens: Long,
    @SerialName("output_tokens") val outputTokens: Long,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long,
    @SerialName("cache_write_5m_tokens") val cacheWrite5mTokens: Long,
    @SerialName("cache_write_1h_tokens") val cacheWrite1hTokens: Long,
    /** Nulo quando o modelo não tem tarifa conhecida — zero afirmaria custo zero. */
    @SerialName("cost_micros_usd") val costMicros: Long? = null
)

private fun CliSessionTurn.toDto(): ExportedTurnDto {
    return ExportedTurnDto(
        sessionId = sessionId,
        seq = seq,
        messageId = messageId,
        ts = ts.toString(),
        model = model,
        isSidechain = isSidechain,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWrite5mTokens = cacheWrite5mTokens,
        cacheWrite1hTokens = cacheWrite1hTokens,
        costMicros = costMicros
    )
}

@Serializable
private data class ExportedBucketDto(
    val label: String? = null,
    @SerialName("session_count") val sessionCount: Int,
    @SerialName("turn_count") val turnCount: Int,
    @SerialName("total_tokens") val totalTokens: Long,
    @SerialName("cost_micros_usd") val costMicros: Long,
    @SerialName("cache_savings_micros_usd") val cacheSavingsMicros: Long,
    @SerialName("cache_hit_rate") val cacheHitRate: Double,
    @SerialName("cost_is_complete") val costIsComplete: Boolean
)

@Serializable
private data class ExportedBreakdownDto(
    @SerialName("by_project") val byProject: List<ExportedBucketDto>,
    @SerialName("by_model") val byModel: List<ExportedBucketDto>,
    @SerialName("by_branch") val byBranch: List<ExportedBucketDto>,
    val totals: ExportedBucketDto
)

private fun CliUsageBucket.toDto(): ExportedBucketDto {
    return ExportedBucketDto(
        label = label,
        sessionCount = sessionCount,
        turnCount = turnCount,
        totalTokens = totalTokens,
        costMicros = costMicros,
        cacheSavingsMicros = cacheSavingsMicros,
        cacheHitRate = cacheHitRate,
        costIsComplete = isCostComplete
    )
}

private fun CliUsageBreakdown.toDto(): ExportedBreakdownDto {
    return ExportedBreakdownDto(
        byProject = byProject.map { bucket -> bucket.toDto() },
        byModel = byModel.map { bucket -> bucket.toDto() },
        byBranch = byBranch.map { bucket -> bucket.toDto() },
        totals = totals.toDto()
    )
}
