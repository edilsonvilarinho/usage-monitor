package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta do endpoint privado GET /backend-api/codex/usage.
 *
 * O payload contém metadados da conta e as duas janelas de consumo do Codex:
 * - primary_window: janela de 5 horas
 * - secondary_window: janela semanal
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
    @SerialName("primary_window") val primaryWindow: CodexUsageWindowDto,
    @SerialName("secondary_window") val secondaryWindow: CodexUsageWindowDto
)

@Serializable
data class CodexUsageWindowDto(
    @SerialName("used_percent") val usedPercent: Long,
    @SerialName("limit_window_seconds") val limitWindowSeconds: Long,
    @SerialName("reset_after_seconds") val resetAfterSeconds: Long,
    @SerialName("reset_at") val resetAt: Long
)
