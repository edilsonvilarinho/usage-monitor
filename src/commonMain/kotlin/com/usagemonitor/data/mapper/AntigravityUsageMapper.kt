package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AntigravityQuotaBucketDto
import com.usagemonitor.data.dto.AntigravityUsageEnvelopeDto
import com.usagemonitor.domain.entity.AntigravityQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.math.floor

/** O CLI não passou pelas salvaguardas que provam que `/usage` não abriu turno de modelo. */
class AntigravityModelTurnException(message: String) : IllegalStateException(message)

/**
 * Converte o envelope de `agy --output-format json --print /usage` em cotas percentuais.
 *
 * O encaixe é o mesmo do OpenCode Go — percentual de 0 a 100 com reset —, então a
 * fonte entra no histórico, no limiar, na projeção e no HUD sem valor novo em
 * [UsageUnit] ou [PeriodType]. Nenhuma capacidade é inventada: `rawUsed`/`rawTotal`
 * ficam em zero, porque o CLI informa fração e nunca a grandeza por trás dela.
 */
object AntigravityUsageMapper {

    private const val SCALE = 100L
    private const val FLOAT_TOLERANCE = 1e-9

    /** Sentinela de reset desconhecido, a mesma data do OpenCode Go. */
    internal val ANTIGRAVITY_UNKNOWN_RESET_AT: Instant = Instant.parse("2100-01-01T00:00:00Z")

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    private val authenticationWords = listOf(
        "sign in", "log in", "login", "not logged", "unauthenticated", "authentication", "credential"
    )

    fun parse(rawJson: String): ApiUsageStats {
        val envelope = try {
            json.decodeFromString(AntigravityUsageEnvelopeDto.serializer(), rawJson.trim())
        } catch (_: Exception) {
            // A saída nunca vai para a mensagem: pode conter a conta do usuário.
            throw IllegalStateException(ANTIGRAVITY_UNRECOGNIZED_OUTPUT)
        }
        return toUsageStats(envelope)
    }

    fun toUsageStats(envelope: AntigravityUsageEnvelopeDto): ApiUsageStats {
        if (envelope.status.equals("ERROR", ignoreCase = true)) {
            val error = envelope.error.orEmpty().lowercase()
            if (authenticationWords.any { word -> word in error }) {
                throw IllegalStateException(AntigravityUsageFailureKind.AUTHENTICATION_UNAVAILABLE.safeMessage)
            }
            throw IllegalStateException(ANTIGRAVITY_COMMAND_FAILED)
        }

        // As três provas juntas: sem qualquer uma delas não há como afirmar que o
        // CLI respondeu sozinho, e é justamente o caso em que ele teria mandado
        // `/usage` ao modelo como texto.
        val answeredByCli = envelope.command?.name == "usage" &&
            envelope.numTurns == 0 &&
            envelope.usage?.totalTokens == 0L
        if (!answeredByCli) {
            throw AntigravityModelTurnException(AntigravityUsageFailureKind.COLLECTION_PAUSED.safeMessage)
        }

        val quotas = envelope.command?.data?.groups.orEmpty().flatMap { group ->
            val groupLabel = shortGroupLabel(group.name)
            group.buckets.mapNotNull { bucket -> createQuota(groupLabel, bucket) }
        }

        // Resposta sem nenhuma janela legível é contrato mudado, não conta zerada:
        // falhar preserva o cache em vez de apagá-lo com uma leitura que não mediu nada.
        if (quotas.isEmpty()) {
            throw IllegalStateException(ANTIGRAVITY_NO_QUOTA_WINDOWS)
        }

        return ApiUsageStats(
            source = ApiSource.ANTIGRAVITY,
            apiName = "Antigravity CLI",
            quotas = quotas
        )
    }

    private fun createQuota(groupLabel: String?, bucket: AntigravityQuotaBucketDto): QuotaInfo? {
        val fraction = bucket.remainingFraction ?: return null
        if (!fraction.isFinite() || fraction !in 0.0..1.0) return null
        val periodType = periodTypeOf(bucket.window)
        val label = quotaLabel(groupLabel, periodType, bucket) ?: return null

        // Com a janela intacta (fração 1) o reset é "agora + duração": medido, ele
        // andou de 00:38:35 para 00:45:04 entre chamadas. Tomá-lo como reset real
        // faria cada coleta parecer um período novo para o histórico e para a dedup
        // dos alertas, que toleram só 5 minutos.
        val parsedReset = bucket.resetTime
            ?.takeIf(String::isNotBlank)
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
        val hasKnownResetAt = parsedReset != null && fraction < 1.0

        return QuotaInfo(
            label = label,
            // Truncado, como todo percentual do app: 3,9% usado não é 4%. A folga de
            // 1e-9 é do ponto flutuante — (1 − 0,78) × 100 dá 21,999…, e truncar isso
            // mostraria 21% onde o CLI diz que restam 78%.
            used = floor((1.0 - fraction) * SCALE + FLOAT_TOLERANCE).toLong().coerceIn(0L, SCALE),
            total = SCALE,
            periodEndAt = if (hasKnownResetAt) parsedReset!! else ANTIGRAVITY_UNKNOWN_RESET_AT,
            hasKnownResetAt = hasKnownResetAt,
            periodType = periodType,
            unit = UsageUnit.PERCENTAGE
        )
    }

    private fun periodTypeOf(window: String?): PeriodType {
        val normalized = window.orEmpty().lowercase()
        return when {
            normalized == "weekly" || "week" in normalized -> PeriodType.WEEKLY
            "hour" in normalized || normalized == "5h" -> PeriodType.INTERVAL
            else -> PeriodType.REPORTED
        }
    }

    /**
     * O rótulo é chave da série no histórico, então sai só de campos estáveis: o
     * grupo e a janela. "Gemini Models" → "Antigravity Gemini 7d",
     * "Claude and GPT models" → "Antigravity Claude/GPT 7d".
     */
    private fun quotaLabel(groupLabel: String?, periodType: PeriodType, bucket: AntigravityQuotaBucketDto): String? {
        val group = groupLabel ?: return null
        val window = when (periodType) {
            PeriodType.WEEKLY -> "7d"
            PeriodType.INTERVAL -> "5h"
            PeriodType.MONTHLY, PeriodType.REPORTED -> bucket.id?.takeIf(String::isNotBlank) ?: return null
        }
        return AntigravityQuotaLabels.label(group, window)
    }

    private fun shortGroupLabel(name: String?): String? {
        val trimmed = name?.trim()?.takeIf(String::isNotBlank) ?: return null
        return trimmed
            .replace(Regex("\\s+models$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+and\\s+", RegexOption.IGNORE_CASE), "/")
            .trim()
            .takeIf(String::isNotBlank)
    }
}

const val ANTIGRAVITY_UNRECOGNIZED_OUTPUT = "Antigravity CLI /usage output format is unrecognized"
const val ANTIGRAVITY_COMMAND_FAILED = "Antigravity CLI /usage reported an error"
const val ANTIGRAVITY_NO_QUOTA_WINDOWS = "Antigravity CLI /usage returned no quota windows"
