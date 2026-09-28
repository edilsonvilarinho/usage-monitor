package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Evento `token_count` do rollout do Codex CLI, só com o que os limites por
 * modelo precisam (issue #324). Forma observada nos rollouts locais em
 * 2026-09-27: `payload.rate_limits` com `limit_id` (`codex`, `premium`),
 * `limit_name`, `primary`/`secondary` e `plan_type`. O esquema é interno do CLI
 * e pode mudar sem aviso: todo campo é opcional.
 */
@Serializable
data class CodexRolloutTokenCountLineDto(
    val timestamp: String? = null,
    val type: String? = null,
    val payload: CodexRolloutTokenCountPayloadDto? = null
)

@Serializable
data class CodexRolloutTokenCountPayloadDto(
    val type: String? = null,
    @SerialName("rate_limits") val rateLimits: CodexRolloutRateLimitsDto? = null
)

@Serializable
data class CodexRolloutRateLimitsDto(
    @SerialName("limit_id") val limitId: String? = null,
    @SerialName("limit_name") val limitName: String? = null,
    val primary: CodexRolloutRateLimitWindowDto? = null,
    val secondary: CodexRolloutRateLimitWindowDto? = null,
    @SerialName("plan_type") val planType: String? = null
)

@Serializable
data class CodexRolloutRateLimitWindowDto(
    @SerialName("used_percent") val usedPercent: Double? = null,
    @SerialName("window_minutes") val windowMinutes: Long? = null,
    @SerialName("resets_at") val resetsAt: Long? = null
)
