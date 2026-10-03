package com.usagemonitor.data.export

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.domain.repository.UsageExportEncoder
import kotlin.time.Instant

/** A porta de exportação sobre [UsageExporter] e [CodexCliUsageExporter]. */
object DefaultUsageExportEncoder : UsageExportEncoder {
    override fun encodeSessions(sessions: List<CliSessionSummary>, format: UsageExportFormat): String =
        UsageExporter.exportSessions(sessions, format)

    override fun encodeBreakdown(breakdown: CliUsageBreakdown, format: UsageExportFormat): String =
        UsageExporter.exportBreakdown(breakdown, format)

    override fun encodeCodexCliSessions(sessions: List<CodexCliSessionSummary>, format: UsageExportFormat): String =
        CodexCliUsageExporter.exportSessions(sessions, format)

    override fun encodeDashboardSnapshot(stats: List<ApiUsageStats>, capturedAt: Instant): String =
        UsageExporter.exportDashboardSnapshot(stats, capturedAt)
}
