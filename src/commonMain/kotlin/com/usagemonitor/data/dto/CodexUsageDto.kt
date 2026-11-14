package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta do endpoint privado GET /backend-api/wham/usage.
 *
 * A fonte pode variar as janelas conforme o plano. A classificação é feita pelo
 * período informado em `limit_window_seconds`, não pela posição do campo.
 *
 * `primary_window` e `secondary_window` são opcionais para aceitar respostas
 * parciais ou mudanças de plano sem quebrar o card.
 */
@Serializable
data class CodexUsageResponse(
    @SerialName("plan_type") val planType: String,
    @SerialName("rate_limit") val rateLimit: CodexRateLimitDto,
    // Ausente em respostas sem o bloco: a leitura fica com `null` (não informado).
    @SerialName("rate_limit_reset_credits") val resetCredits: CodexResetCreditsDto? = null
)

/**
 * Bloco `rate_limit_reset_credits` (issue #571). `applicable_available_count` não é lido:
 * a semântica não está documentada e a amostra real não basta para nomeá-la.
 */
@Serializable
data class CodexResetCreditsDto(
    @SerialName("available_count") val availableCount: Int? = null
)

@Serializable
data class CodexRateLimitDto(
    @SerialName("allowed") val allowed: Boolean,
    @SerialName("limit_reached") val limitReached: Boolean,
    @SerialName("primary_window") val primaryWindow: CodexUsageWindowDto? = null,
    @SerialName("secondary_window") val secondaryWindow: CodexUsageWindowDto? = null
)

@Serializable
data class CodexUsageWindowDto(
    @SerialName("used_percent") val usedPercent: Long,
    @SerialName("limit_window_seconds") val limitWindowSeconds: Long,
    @SerialName("reset_after_seconds") val resetAfterSeconds: Long,
    @SerialName("reset_at") val resetAt: Long
)
