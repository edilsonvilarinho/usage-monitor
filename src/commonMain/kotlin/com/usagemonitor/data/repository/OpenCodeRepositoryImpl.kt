package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.OpenCodeUsageDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.OpenCodeRepository

class OpenCodeRepositoryImpl(
    private val dataSource: OpenCodeUsageDataSource
) : OpenCodeRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            val snapshots = dataSource.loadFreeModelUsage()
                .sortedBy { snapshot -> snapshot.modelName.lowercase() }

            if (snapshots.isEmpty()) {
                return@runCatching ApiUsageStats(
                    source = ApiSource.OPENCODE,
                    apiName = OPEN_CODE_API_NAME,
                    quotas = emptyList()
                )
            }

            val quotas = buildList {
                snapshots.forEach { snapshot ->
                    add(
                        QuotaInfo(
                            label = "${snapshot.modelName} 5h",
                            used = snapshot.requestsLastFiveHours,
                            total = 0L,
                            periodEndAt = snapshot.capturedAt,
                            hasKnownResetAt = false,
                            periodType = PeriodType.INTERVAL,
                            unit = UsageUnit.REQUESTS
                        )
                    )
                    add(
                        QuotaInfo(
                            label = "${snapshot.modelName} 7d",
                            used = snapshot.requestsLastSevenDays,
                            total = 0L,
                            periodEndAt = snapshot.capturedAt,
                            hasKnownResetAt = false,
                            periodType = PeriodType.WEEKLY,
                            unit = UsageUnit.REQUESTS
                        )
                    )
                }
            }

            ApiUsageStats(
                source = ApiSource.OPENCODE,
                apiName = OPEN_CODE_API_NAME,
                quotas = quotas
            )
        }
    }

    private companion object {
        const val OPEN_CODE_API_NAME = "OpenCode Zen Free"
    }
}
