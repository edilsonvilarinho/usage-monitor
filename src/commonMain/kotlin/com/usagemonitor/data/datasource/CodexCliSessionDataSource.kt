package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary

interface CodexCliSessionDataSource {
    suspend fun syncIndex(): CodexCliSessionIndexReport
    suspend fun readSessions(sinceEpochMillis: Long? = null): List<CodexCliSessionSummary>
    suspend fun readSession(sessionId: String): CodexCliSessionDetail?
}
