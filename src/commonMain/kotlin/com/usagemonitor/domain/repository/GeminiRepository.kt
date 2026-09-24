package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface GeminiRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

/**
 * Diretório ausente **não** está aqui: sem `~/.gemini/tmp` o Gemini CLI nunca rodou
 * na máquina, e isso é card vazio, não falha.
 */
enum class GeminiUsageFailureKind(val safeMessage: String) {
    SESSION_HISTORY_UNREADABLE("Gemini CLI local session history contains no recognized usage records")
}

class GeminiUsageException(
    val kind: GeminiUsageFailureKind
) : IllegalStateException(kind.safeMessage)
