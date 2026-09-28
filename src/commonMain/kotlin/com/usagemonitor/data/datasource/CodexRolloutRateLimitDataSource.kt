package com.usagemonitor.data.datasource

import com.usagemonitor.data.parser.CodexRolloutRateLimit

/**
 * Os `rate_limits` mais recentes gravados pelo Codex CLI nos rollouts locais,
 * um por `limit_id` (issue #324). A leitura de arquivo mora em `desktopMain`.
 */
fun interface CodexRolloutRateLimitDataSource {
    suspend fun latestRateLimits(): List<CodexRolloutRateLimit>
}
