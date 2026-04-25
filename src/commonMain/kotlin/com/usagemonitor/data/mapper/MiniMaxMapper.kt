package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

/**
 * Mapper: converte o DTO da MiniMax para a entidade do domain.
 *
 * RESPONSABILIDADE ÚNICA: só transforma dados, sem lógica de negócio.
 *
 * `object` em Kotlin é um singleton — equivalente a exportar funções
 * estáticas em um módulo JavaScript. Não precisa instanciar.
 */
object MiniMaxMapper {

    fun toUsageStats(response: MiniMaxTokenPlanResponse): ApiUsageStats {
        val quotas = response.modelRemains
            .filter { it.modelName == "MiniMax-M*" }
            .flatMap { dto ->
            val periodEnd = Instant.fromEpochMilliseconds(dto.endTime)

            val intervalQuota = QuotaInfo(
                label = dto.modelName,
                used = dto.currentIntervalUsageCount,
                total = dto.currentIntervalTotalCount,
                periodEndAt = periodEnd,
                periodType = PeriodType.INTERVAL,
                unit = UsageUnit.REQUESTS
            )

            if (dto.currentWeeklyTotalCount > 0L) {
                val weeklyEnd = Instant.fromEpochMilliseconds(dto.weeklyEndTime)
                val weeklyQuota = QuotaInfo(
                    label = dto.modelName,
                    used = dto.currentWeeklyUsageCount,
                    total = dto.currentWeeklyTotalCount,
                    periodEndAt = weeklyEnd,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.REQUESTS
                )
                listOf(intervalQuota, weeklyQuota)
            } else {
                listOf(intervalQuota)
            }
        }

        return ApiUsageStats(
            apiName = "MiniMax",
            quotas = quotas
        )
    }
}
