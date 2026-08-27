package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta do endpoint privado GET /backend-api/wham/usage.
 *
 * Quando `secondary_window` está presente, a app trata este payload como fonte
 * oficial das janelas 5h + 7d.
 *
 * Se `secondary_window` vier ausente, a coleta é considerada incompleta. O
 * ViewModel mantém a última leitura válida da cache e sinaliza a instabilidade;
 * a janela primária nunca é duplicada para preencher a semanal.
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
    @SerialName("secondary_window") val secondaryWindow: CodexUsageWindowDto?
)

@Serializable
data class CodexUsageWindowDto(
    @SerialName("used_percent") val usedPercent: Long,
    @SerialName("limit_window_seconds") val limitWindowSeconds: Long,
    @SerialName("reset_after_seconds") val resetAfterSeconds: Long,
    @SerialName("reset_at") val resetAt: Long
)
