package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta de GET /api/oauth/usage — endpoint dedicado de uso do Claude.ai OAuth.
 * Requer header `anthropic-beta: oauth-2025-04-20`.
 *
 * `utilization` é uma fração (0.0 a 1.0) — não temos contagem bruta de tokens.
 * `resets_at` é ISO 8601 com timezone.
 */
@Serializable
data class AnthropicUsageResponse(
    @SerialName("five_hour") val fiveHour: AnthropicUsageWindow,
    @SerialName("seven_day") val sevenDay: AnthropicUsageWindow,
    @SerialName("seven_day_sonnet") val sevenDaySonnet: AnthropicUsageWindow? = null,
    @SerialName("extra_usage") val extraUsage: AnthropicExtraUsage? = null,
)

@Serializable
data class AnthropicUsageWindow(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String,
)

@Serializable
data class AnthropicExtraUsage(
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("monthly_limit") val monthlyLimit: Int,
    @SerialName("used_credits") val usedCredits: Double,
)
