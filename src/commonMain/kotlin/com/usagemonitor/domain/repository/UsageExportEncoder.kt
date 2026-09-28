package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.UsageExportFormat
import kotlin.time.Instant

/**
 * Serializa em texto (CSV ou JSON) o que as telas exportam.
 *
 * Porta do domain, implementada em `data` (`DefaultUsageExportEncoder`): o JSON
 * usa `kotlinx.serialization`, que o domain não importa, e a apresentação não
 * pode depender de `data`. Quem monta o grafo injeta a implementação.
 */
interface UsageExportEncoder {
    fun encodeSessions(sessions: List<CliSessionSummary>, format: UsageExportFormat): String

    fun encodeBreakdown(breakdown: CliUsageBreakdown, format: UsageExportFormat): String

    fun encodeCodexCliSessions(sessions: List<CodexCliSessionSummary>, format: UsageExportFormat): String

    /** Retrato do Dashboard; sempre CSV. */
    fun encodeDashboardSnapshot(stats: List<ApiUsageStats>, capturedAt: Instant): String
}
