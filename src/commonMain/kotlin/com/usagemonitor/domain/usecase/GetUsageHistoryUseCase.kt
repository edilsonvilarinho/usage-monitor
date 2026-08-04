package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.repository.UsageHistoryRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class GetUsageHistoryUseCase(
    private val repository: UsageHistoryRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        source: ApiSource,
        range: HistoryRange,
        accountKey: UsageAccountKey? = null,
        now: Instant = clock.now()
    ): ApiUsageHistoryReport {
        return repository.getHistoryReport(source, accountKey, range, now)
    }

    suspend fun listAccounts(source: ApiSource) = repository.listAccounts(source)
}
