package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.presentation.terminal.terminalQuotaId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `usage-monitor status --json` (tela 2.3). **Os nomes dos campos são contrato** de quem
 * escreve script em cima deles: campo novo pode entrar, campo existente não muda de nome,
 * de tipo nem de significado sem subir [VERSION]. Inglês, `snake_case` como o
 * `/api/snapshot` da web local, e só metadados de uso: nenhum segredo, caminho ou e-mail.
 *
 * Uma lista plana de cotas, e não contas com cotas dentro, porque é o que um `jq` filtra
 * sem precisar achatar (`.quotas[] | select(.percent >= 70)`).
 */
internal object TerminalJson {
    const val VERSION = 1

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = true
    }

    /**
     * [asciiOnly] para saída que não é UTF-8: o que não é ASCII vira `\uXXXX`, que continua
     * JSON válido. Trocar "Padrão" por "Padr?o", como o texto da tabela faz, corromperia o dado.
     */
    fun encode(reading: TerminalReading, asciiOnly: Boolean = false): String {
        val text = encodeUtf8(reading)
        return if (asciiOnly) escapeNonAscii(text) else text
    }

    private fun escapeNonAscii(text: String): String =
        buildString(text.length) {
            for (char in text) {
                if (char.code < 128) append(char) else append("\\u").append(char.code.toString(16).padStart(4, '0'))
            }
        }

    private fun encodeUtf8(reading: TerminalReading): String {
        val quotas = reading.accounts.flatMap { account ->
            account.quotas.map { quota ->
                QuotaDto(
                    id = terminalQuotaId(account, quota),
                    source = account.source.name.lowercase(),
                    sourceName = account.name,
                    account = account.profile,
                    label = quota.label,
                    period = quota.periodType.name.lowercase(),
                    unit = quota.unit.name.lowercase(),
                    used = quota.used,
                    total = quota.total,
                    percent = quota.percent,
                    state = account.zoneOf(quota)?.let(::stateName),
                    thresholds = account.thresholds,
                    resetsAt = quota.resetsAt?.toString(),
                    currency = quota.currencyCode,
                    fetchedAt = account.fetchedAt?.toString()
                )
            }
        }
        val dto = StatusDto(
            version = VERSION,
            generatedAt = reading.now.toString(),
            lastCollectedAt = reading.lastCollectedAt?.toString(),
            collectorRunning = reading.collectorRunning,
            quotas = quotas
        )
        return json.encodeToString(StatusDto.serializer(), dto)
    }

    /** Nomes fixos em inglês, independentes do idioma e do enum interno. */
    private fun stateName(zone: QuotaZone): String =
        when (zone) {
            QuotaZone.ON_TRACK -> "on_track"
            QuotaZone.ATTENTION -> "warning"
            QuotaZone.CRITICAL -> "critical"
        }

    @Serializable
    private data class StatusDto(
        @SerialName("version") val version: Int,
        @SerialName("generated_at") val generatedAt: String,
        @SerialName("last_collected_at") val lastCollectedAt: String?,
        @SerialName("collector_running") val collectorRunning: Boolean,
        @SerialName("quotas") val quotas: List<QuotaDto>
    )

    @Serializable
    private data class QuotaDto(
        @SerialName("id") val id: String,
        @SerialName("source") val source: String,
        @SerialName("source_name") val sourceName: String,
        @SerialName("account") val account: String?,
        @SerialName("label") val label: String,
        @SerialName("period") val period: String,
        @SerialName("unit") val unit: String,
        @SerialName("used") val used: Long,
        @SerialName("total") val total: Long,
        @SerialName("percent") val percent: Int?,
        @SerialName("state") val state: String?,
        @SerialName("thresholds") val thresholds: List<Int>,
        @SerialName("resets_at") val resetsAt: String?,
        @SerialName("currency") val currency: String,
        @SerialName("fetched_at") val fetchedAt: String?
    )
}
