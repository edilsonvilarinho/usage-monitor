package com.usagemonitor.data.parser

import com.usagemonitor.data.dto.CodexRolloutRateLimitsDto
import com.usagemonitor.data.dto.CodexRolloutTokenCountLineDto
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/** Um `rate_limits` do rollout com o instante do evento que o trouxe. */
data class CodexRolloutRateLimit(
    val observedAt: Instant,
    val limits: CodexRolloutRateLimitsDto
)

/**
 * O `rate_limits` mais recente de cada `limit_id` nas linhas dadas (issue #324).
 *
 * Linha que não é `token_count`, que não parseia ou que não tem `limit_id` é
 * ignorada: o arquivo é escrito pelo CLI enquanto é lido, e a última linha pode
 * estar pela metade. O resultado é ordenado por `limit_id` — lista publicada por
 * `StateFlow` precisa de ordem total.
 */
object CodexRolloutRateLimitParser {

    private const val RATE_LIMITS_MARKER = "\"rate_limits\""
    private const val TOKEN_COUNT = "token_count"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun latestByLimitId(lines: Sequence<String>): List<CodexRolloutRateLimit> {
        val latest = linkedMapOf<String, CodexRolloutRateLimit>()
        lines
            .filter { line -> line.contains(RATE_LIMITS_MARKER) }
            .mapNotNull(::parseLine)
            .forEach { candidate ->
                val id = candidate.limits.limitId ?: return@forEach
                val current = latest[id]
                if (current == null || candidate.observedAt >= current.observedAt) {
                    latest[id] = candidate
                }
            }
        return latest.values.sortedBy { rateLimit -> rateLimit.limits.limitId }
    }

    private fun parseLine(line: String): CodexRolloutRateLimit? {
        val parsed = runCatching { json.decodeFromString<CodexRolloutTokenCountLineDto>(line) }.getOrNull() ?: return null
        val payload = parsed.payload ?: return null
        if (payload.type != TOKEN_COUNT) return null
        val limits = payload.rateLimits ?: return null
        val observedAt = parsed.timestamp?.let { value -> runCatching { Instant.parse(value) }.getOrNull() } ?: return null
        return CodexRolloutRateLimit(observedAt, limits)
    }
}
