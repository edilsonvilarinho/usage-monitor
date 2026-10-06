package com.usagemonitor.data.export

import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.repository.UsageSnapshotEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON do retrato para `GET /api/snapshot` (#388). Os nomes dos campos são
 * contrato da página web servida pelo próprio app — mudar um quebra a página.
 */
object JsonUsageSnapshotEncoder : UsageSnapshotEncoder {

    private val json = Json { encodeDefaults = true }

    override fun encode(snapshot: UsageSnapshot): String {
        val dto = SnapshotDto(
            generatedAt = snapshot.generatedAt.toString(),
            lastCollectedAt = snapshot.lastCollectedAt?.toString(),
            accounts = snapshot.accounts.map { account ->
                AccountDto(
                    source = account.source.name,
                    label = account.label,
                    active = account.active,
                    fetchedAt = account.fetchedAt?.toString(),
                    worstRisk = account.worstRisk?.name,
                    quotas = account.quotas.map { quota ->
                        QuotaDto(
                            label = quota.label,
                            period = quota.periodType.name,
                            unit = quota.unit.name,
                            used = quota.used,
                            total = quota.total,
                            percent = quota.percent,
                            resetsAt = quota.resetsAt?.toString(),
                            currency = quota.currencyCode,
                            risk = quota.risk?.name
                        )
                    }
                )
            }
        )
        return json.encodeToString(SnapshotDto.serializer(), dto)
    }

    @Serializable
    private data class SnapshotDto(
        @SerialName("generated_at") val generatedAt: String,
        @SerialName("last_collected_at") val lastCollectedAt: String?,
        @SerialName("accounts") val accounts: List<AccountDto>
    )

    @Serializable
    private data class AccountDto(
        @SerialName("source") val source: String,
        @SerialName("label") val label: String,
        @SerialName("active") val active: Boolean,
        @SerialName("fetched_at") val fetchedAt: String?,
        @SerialName("worst_risk") val worstRisk: String?,
        @SerialName("quotas") val quotas: List<QuotaDto>
    )

    @Serializable
    private data class QuotaDto(
        @SerialName("label") val label: String,
        @SerialName("period") val period: String,
        @SerialName("unit") val unit: String,
        @SerialName("used") val used: Long,
        @SerialName("total") val total: Long,
        @SerialName("percent") val percent: Int?,
        @SerialName("resets_at") val resetsAt: String?,
        @SerialName("currency") val currency: String,
        @SerialName("risk") val risk: String?
    )
}
