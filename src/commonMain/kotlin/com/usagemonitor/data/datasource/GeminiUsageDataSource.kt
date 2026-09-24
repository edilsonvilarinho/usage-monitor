package com.usagemonitor.data.datasource

import kotlinx.datetime.Instant

/** Fonte local de mensagens com uso registrado pelo Gemini CLI. */
interface GeminiUsageDataSource {
    suspend fun loadSessions(): List<GeminiSessionUsage>
}

data class GeminiSessionUsage(
    val sessionId: String,
    val messages: List<GeminiMessageUsage>
)

data class GeminiMessageUsage(
    val messageId: String,
    val capturedAt: Instant,
    val modelName: String,
    val totalTokens: Long
)
