package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface GeminiRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

enum class GeminiUsageFailureKind(val safeMessage: String) {
    SESSION_DIRECTORY_MISSING("Gemini CLI local session directory is missing for this profile"),
    SESSION_HISTORY_UNREADABLE("Gemini CLI local session history contains no recognized usage records")
}

class GeminiUsageException(
    val kind: GeminiUsageFailureKind
) : IllegalStateException(kind.safeMessage)
