package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.GeminiMessageUsage
import com.usagemonitor.data.datasource.GeminiUsageDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.GeminiRepository
import com.usagemonitor.domain.repository.GeminiUsageException
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

class GeminiRepositoryImpl(
    private val dataSource: GeminiUsageDataSource,
    private val nowProvider: () -> Instant = { Clock.System.now() }
) : GeminiRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return try {
            val now = nowProvider()
            val sessions = dataSource.loadSessions()
            val messagesBySessionAndId = linkedMapOf<Pair<String, String>, GeminiMessageUsage>()
            sessions.forEach { session ->
                session.messages.forEach { message ->
                    messagesBySessionAndId[session.sessionId to message.messageId] = message
                }
            }
            val messages = messagesBySessionAndId.entries.map { entry ->
                entry.key.first to entry.value
            }
            val fiveHourStart = now.minus(5, kotlinx.datetime.DateTimeUnit.HOUR, TimeZone.UTC)
            val sevenDayStart = now.minus(7, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            val fiveHourMessages = messages.filter { (_, message) -> message.capturedAt in fiveHourStart..now }
            val sevenDayMessages = messages.filter { (_, message) -> message.capturedAt in sevenDayStart..now }
            val modelNames = (fiveHourMessages + sevenDayMessages)
                .map { (_, message) -> message.modelName }
                .distinct()
                .sortedBy(String::lowercase)

            val quotas = buildList {
                modelNames.forEach { modelName ->
                    add(observedQuota(
                        label = "$modelName 5h",
                        amount = sumTokens(fiveHourMessages, modelName),
                        unit = UsageUnit.TOKENS,
                        periodType = PeriodType.INTERVAL,
                        capturedAt = now
                    ))
                    add(observedQuota(
                        label = "$modelName 7d",
                        amount = sumTokens(sevenDayMessages, modelName),
                        unit = UsageUnit.TOKENS,
                        periodType = PeriodType.WEEKLY,
                        capturedAt = now
                    ))
                }
                if (fiveHourMessages.isNotEmpty() || sevenDayMessages.isNotEmpty()) {
                    add(observedQuota(
                        label = "Gemini CLI sessions 5h",
                        amount = fiveHourMessages.map { item -> item.first }.toSet().size.toLong(),
                        unit = UsageUnit.REQUESTS,
                        periodType = PeriodType.INTERVAL,
                        capturedAt = now
                    ))
                    add(observedQuota(
                        label = "Gemini CLI sessions 7d",
                        amount = sevenDayMessages.map { item -> item.first }.toSet().size.toLong(),
                        unit = UsageUnit.REQUESTS,
                        periodType = PeriodType.WEEKLY,
                        capturedAt = now
                    ))
                }
            }

            Result.success(ApiUsageStats(
                source = ApiSource.GEMINI,
                apiName = GEMINI_API_NAME,
                quotas = quotas
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: GeminiUsageException) {
            Result.failure(failure)
        } catch (_: Throwable) {
            // A mensagem bruta da origem pode expor caminho ou detalhe local.
            Result.failure(IllegalStateException("Gemini CLI local usage is unavailable"))
        }
    }

    private fun sumTokens(
        messages: List<Pair<String, GeminiMessageUsage>>,
        modelName: String
    ): Long = messages.asSequence()
        .filter { (_, message) -> message.modelName == modelName }
        .fold(0L) { total, (_, message) -> saturatingAdd(total, message.totalTokens) }

    private fun observedQuota(
        label: String,
        amount: Long,
        unit: UsageUnit,
        periodType: PeriodType,
        capturedAt: Instant
    ) = QuotaInfo(
        label = label,
        used = amount,
        total = 0L,
        periodEndAt = capturedAt,
        hasKnownResetAt = false,
        periodType = periodType,
        unit = unit
    )

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private companion object {
        const val GEMINI_API_NAME = "Gemini CLI"
    }
}
