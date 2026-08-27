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
    @SerialName("rate_limit") val rateLimit: CodexRateLimitDto
)

@Serializable
data class CodexRateLimitDto(
    val allowed: Boolean,
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
