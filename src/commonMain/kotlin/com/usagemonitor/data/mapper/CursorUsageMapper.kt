package com.usagemonitor.data.mapper

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/** Mapeia apenas percentuais explícitos da rota individual, sem reconstruir uso ou custo. */
internal object CursorUsageMapper {
    fun toDomain(payload: JsonElement, capturedAt: Instant): ApiUsageStats {
        val root = payload as? JsonObject ?: invalidResponse()
        val individualUsage = root.objectAt("individualUsage") ?: invalidResponse()
        val plan = individualUsage.objectAt("plan") ?: invalidResponse()
        val billingCycleEnd = root.stringAt("billingCycleEnd")
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }

        val percentages = listOf(
            "Auto" to plan.doubleAt("autoPercentUsed"),
            "API" to plan.doubleAt("apiPercentUsed"),
            "Included total" to plan.doubleAt("totalPercentUsed")
        ).mapNotNull { (label, value) ->
            value?.let { percentage -> label to percentage }
        }
        if (percentages.isEmpty()) invalidResponse()

        val quotas = percentages.map { (label, percentage) ->
            if (!percentage.isFinite() || percentage !in 0.0..100.0) invalidResponse()
            QuotaInfo(
                label = "Cursor $label",
                used = percentage.roundToInt().toLong(),
                total = 100L,
                periodEndAt = billingCycleEnd ?: capturedAt,
                hasKnownResetAt = billingCycleEnd != null,
                periodType = PeriodType.MONTHLY,
                unit = UsageUnit.PERCENTAGE
            )
        }

        return ApiUsageStats(
            source = ApiSource.CURSOR,
            apiName = "Cursor",
            quotas = quotas
        )
    }

    private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.stringAt(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.doubleAt(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun invalidResponse(): Nothing =
        throw IllegalStateException("Cursor usage response format is unrecognized")
}
