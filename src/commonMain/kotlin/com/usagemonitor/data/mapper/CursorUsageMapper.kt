package com.usagemonitor.data.mapper

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CursorQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.CursorUsageException
import com.usagemonitor.domain.repository.CursorUsageFailureKind
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.floor

/**
 * Converte `GET https://cursor.com/api/usage-summary` em janelas percentuais do ciclo.
 *
 * Duas formas de resposta, as duas registradas pelo Codenotch (`CursorUsage.swift`):
 *
 * - **Pessoal** (free/pro): `individualUsage.plan` com `autoPercentUsed`,
 *   `apiPercentUsed` e `totalPercentUsed`. `used`/`limit` ficam em zero num plano
 *   gratuito mesmo com uso real, porque a franquia chega como `breakdown.bonus`: por
 *   isso só os percentuais contam ali.
 * - **Enterprise/team**: sem `plan`; o teto é `individualUsage.overall` em
 *   `used`/`limit`. A primeira versão exigia `plan` e falhava sempre nessas contas.
 *
 * `totalPercentUsed` **não** vira cota: é a mistura de Auto e API, e como terceira
 * cota disparava o mesmo alerta duas vezes.
 *
 * Percentual acima de 100 é o usuário além da franquia, e é justamente quando ele
 * precisa do card: satura em 100 em vez de derrubar a leitura, como o OpenCode Go.
 */
internal object CursorUsageMapper {

    private const val SCALE = 100L
    private const val FLOAT_TOLERANCE = 1e-9

    /** Ciclo sem fim informado: mesma sentinela do OpenCode Go, com `hasKnownResetAt = false`. */
    internal val CURSOR_UNKNOWN_RESET_AT: Instant = Instant.parse("2100-01-01T00:00:00Z")

    fun toDomain(payload: JsonElement): ApiUsageStats {
        val root = payload as? JsonObject ?: invalidResponse()
        val billingCycleEnd = root.stringAt("billingCycleEnd")
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
        val usage = root.objectAt("individualUsage")
        val plan = usage?.objectAt("plan")
        val team = root.objectAt("teamUsage")

        val windows = buildList {
            plan?.percentAt("autoPercentUsed")?.let { add(CursorQuotaLabels.AUTO to it) }
            plan?.percentAt("apiPercentUsed")?.takeIf { it > 0.0 }?.let { add(CursorQuotaLabels.API to it) }
            usage?.objectAt("onDemand")?.spendPercent()?.let { add(CursorQuotaLabels.ON_DEMAND to it) }
            if (isEmpty()) {
                usage?.objectAt("overall")?.spendPercent()?.let { add(CursorQuotaLabels.INCLUDED to it) }
            }
            team?.objectAt("onDemand")?.spendPercent()?.takeIf { it > 0.0 }
                ?.let { add(CursorQuotaLabels.TEAM_ON_DEMAND to it) }
        }

        if (windows.isEmpty()) {
            // Plano identificado sem nada medido (gratuito sem franquia, ilimitado) é
            // um estado do plano, não consumo zero. Sem nem o tipo de plano, é o
            // contrato que mudou.
            if (root.stringAt("membershipType") != null || root.booleanAt("isUnlimited") == true) {
                throw CursorUsageException(CursorUsageFailureKind.NOTHING_METERED)
            }
            invalidResponse()
        }

        val quotas = windows.map { (label, percent) ->
            QuotaInfo(
                label = label,
                // Truncado, como todo percentual do app: 89,9% não cruzou 90%.
                used = floor(percent + FLOAT_TOLERANCE).toLong().coerceIn(0L, SCALE),
                total = SCALE,
                periodEndAt = billingCycleEnd ?: CURSOR_UNKNOWN_RESET_AT,
                // Sem fim de ciclo, gravar o instante da coleta faria cada poll parecer
                // um período novo — e rearmaria o alerta de limiar a cada 10 minutos.
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

    /** Percentual informado (0–100); valor que não é número finito conta como ausente. */
    private fun JsonObject.percentAt(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf(Double::isFinite)
            ?.coerceAtLeast(0.0)

    /** Balde em dinheiro com teto real: só quando ligado, com limite positivo e uso informado. */
    private fun JsonObject.spendPercent(): Double? {
        if (booleanAt("enabled") != true) return null
        val limit = numberAt("limit")?.takeIf { it > 0.0 } ?: return null
        val used = numberAt("used")?.coerceAtLeast(0.0) ?: return null
        return used / limit * SCALE
    }

    private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.stringAt(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.booleanAt(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.numberAt(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf(Double::isFinite)

    private fun invalidResponse(): Nothing =
        throw IllegalStateException(CURSOR_UNRECOGNIZED_RESPONSE)
}

internal const val CURSOR_UNRECOGNIZED_RESPONSE = "Cursor usage response format is unrecognized"
