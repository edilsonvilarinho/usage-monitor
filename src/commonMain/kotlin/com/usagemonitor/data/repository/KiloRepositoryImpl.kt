package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.KiloUsageDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.KiloRepository

class KiloRepositoryImpl(
    private val dataSource: KiloUsageDataSource
) : KiloRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            if (!dataSource.isAvailable()) {
                error("Kilo local database not found")
            }

            val snapshots = dataSource.loadFreeModelUsage()
                .sortedBy { snapshot -> snapshot.modelName.lowercase() }

            if (snapshots.isEmpty()) {
                return@runCatching ApiUsageStats(
                    source = ApiSource.KILO,
                    apiName = KILO_API_NAME,
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
                source = ApiSource.KILO,
                apiName = KILO_API_NAME,
                quotas = quotas
            )
        }
    }

    private companion object {
        const val KILO_API_NAME = "Kilo Free"
    }
}
