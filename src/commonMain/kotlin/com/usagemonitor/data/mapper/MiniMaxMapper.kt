package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import com.usagemonitor.domain.entity.ApiUsageStats
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
        // Converte cada modelo (ModelRemainDto) para uma QuotaInfo
        val quotas = response.modelRemains.map { dto ->
            // Epoch milissegundos → Instant (tipo do kotlinx-datetime)
            val periodEnd = Instant.fromEpochMilliseconds(dto.endTime)
            val weeklyEnd = Instant.fromEpochMilliseconds(dto.weeklyEndTime)

            QuotaInfo(
                label = dto.modelName,
                used = dto.currentIntervalUsageCount,
                total = dto.currentIntervalTotalCount,
                periodEndAt = periodEnd,
                weeklyUsed = dto.currentWeeklyUsageCount,
                weeklyTotal = dto.currentWeeklyTotalCount,
                weeklyEndAt = weeklyEnd,
                unit = UsageUnit.REQUESTS
            )
        }

        return ApiUsageStats(
            apiName = "MiniMax",
            quotas = quotas
        )
    }
}
