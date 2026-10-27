package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SourceHealthObservation
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.TeamPresenceResult
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** O que o bot lê do app (#408), sem montar o `AppGraph`. */
class TelegramBotWiringTest {

    private val now = Instant.parse("2026-10-27T18:00:00Z")
    private val fetchedAt = Instant.parse("2026-10-27T17:55:00Z")
    private val work = UsageTargetKey(ApiSource.ANTHROPIC, "work")
    private val workContext = UsageAccountContext(UsageAccountKey(ApiSource.ANTHROPIC, "uuid-1"), "me@example.com")
    private val workStats = ApiUsageStats(
        source = ApiSource.ANTHROPIC, targetKey = work, apiName = "Anthropic", quotas = emptyList(),
        accountContext = workContext, profileLabel = "Trabalho", fetchedAt = fetchedAt
    )
    private val codexStats = ApiUsageStats(source = ApiSource.CODEX, apiName = "Codex", quotas = emptyList())

    private fun presence(deviceId: String, accountKey: String) = TeamMemberPresence(
        member = TeamMemberUsage(deviceId = deviceId, alias = deviceId, lastSeenAt = now, accountKey = accountKey),
        isOnline = true,
        isWorkingNow = false,
        activeSessionCount = 0
    )

    @Test
    fun `source health is unknown before the first reading and empty with no source`() {
        assertNull(sourceHealthOf(UiState.Loading))
        assertEquals(emptyList(), sourceHealthOf(UiState.NoApisEnabled))
    }

    @Test
    fun `a failing source keeps its last reading time and the healthy ones follow`() {
        val state = UiState.Success(
            data = listOf(workStats, codexStats),
            errors = listOf(UiApiError(target = work, message = "HTTP 429", targetLabel = "Anthropic · Trabalho"))
        )

        assertEquals(
            listOf(
                SourceHealthObservation("ANTHROPIC:work", "Anthropic · Trabalho", failure = "HTTP 429", lastReadingAt = fetchedAt),
                SourceHealthObservation("CODEX", "Codex", failure = null)
            ),
            sourceHealthOf(state)
        )
    }

    @Test
    fun `a total failure lists only the failures, named by source when unlabeled`() {
        val state = UiState.Error(errors = listOf(UiApiError(target = UsageTargetKey(ApiSource.CODEX), message = "offline", targetLabel = " ")))

        assertEquals(
            listOf(SourceHealthObservation("CODEX", "Codex", failure = "offline", lastReadingAt = null)),
            sourceHealthOf(state)
        )
    }

    @Test
    fun `team presence asks each account once and merges people`() = runBlocking {
        val asked = mutableListOf<String>()
        val reader = teamPresenceReader(
            presence = { key ->
                asked += key
                Result.success(TeamPresenceResult(entries = listOf(presence("d1", "shared"), presence("d-$key", key))))
            },
            accountKeys = { listOf("a", "b", "a") }
        )

        val result = reader()!!.getOrThrow()

        assertEquals(listOf("a", "b"), asked)
        assertEquals(listOf("shared/d1", "a/d-a", "b/d-b"), result.map { entry -> entry.memberKey })
    }

    @Test
    fun `team presence without accounts is not asked, and fails only when every account fails`() = runBlocking {
        assertNull(teamPresenceReader(presence = { error("não chama") }, accountKeys = { emptyList() })())

        val allFail = teamPresenceReader(
            presence = { key -> Result.failure(IllegalStateException("fora $key")) },
            accountKeys = { listOf("a", "b") }
        )()!!
        assertEquals("fora a", allFail.exceptionOrNull()?.message)

        val someFail = teamPresenceReader(
            presence = { key -> if (key == "a") Result.failure(IllegalStateException()) else Result.success(TeamPresenceResult(listOf(presence("d", key)))) },
            accountKeys = { listOf("a", "b") }
        )()!!
        assertEquals(listOf("b/d"), someFail.getOrThrow().map { entry -> entry.memberKey })
    }

    @Test
    fun `the comparison uses the last collected quotas and hides a failure`() = runBlocking {
        val row = ComparisonRow(
            source = ApiSource.ANTHROPIC, label = "claude-opus", turnCount = 1, totalTokens = 10, costMicros = null,
            cacheRate = null, throughput = null, quotaPercent = null
        )
        var received: Pair<Long, List<ApiUsageStats>>? = null
        val reader = comparisonReader(
            buildComparison = { since, stats ->
                received = since to stats
                Result.success(listOf(row))
            },
            stats = { listOf(workStats) }
        )

        assertEquals(listOf(row), reader(1_000L))
        assertEquals(1_000L to listOf(workStats), received)
        assertNull(comparisonReader(buildComparison = { _, _ -> Result.failure(IllegalStateException()) }, stats = { emptyList() })(0L))
    }

    private class RecordingHistory(
        private val accounts: Map<ApiSource, List<UsageAccountContext>> = emptyMap(),
        private val report: (ApiSource, UsageAccountKey?) -> ApiUsageHistoryReport
    ) : UsageHistoryRepository {
        val asked = mutableListOf<Pair<ApiSource, String?>>()
        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit
        override suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> =
            accounts[source] ?: if (source == ApiSource.MINIMAX) error("SQLite fora") else emptyList()
        override suspend fun getHistoryReport(source: ApiSource, range: HistoryRange, now: Instant) = report(source, null)
        override suspend fun getHistoryReport(source: ApiSource, accountKey: UsageAccountKey?, range: HistoryRange, now: Instant): ApiUsageHistoryReport {
            asked += source to accountKey?.providerAccountId
            return report(source, accountKey)
        }
    }

    private fun emptyReport(source: ApiSource) =
        ApiUsageHistoryReport(source = source, range = HistoryRange.LAST_24_HOURS, lastUpdatedAt = null, series = emptyList())

    private fun reportWithLine(source: ApiSource): ApiUsageHistoryReport {
        val point = UsageHistoryPoint(capturedAt = now - 1.hours, used = 40, total = 100, rawUsed = 40, rawTotal = 100, periodEndAt = now + 1.hours)
        val series = UsageHistorySeries(
            quotaLabel = "Sessão 5h", periodType = PeriodType.INTERVAL, unit = UsageUnit.PERCENTAGE, points = listOf(point),
            currentDisplayUsed = 40, currentDisplayTotal = 100, deltaDisplayUsed = 0, averageDisplayConsumptionPerHour = 0.0,
            currentPeriodEndAt = now + 1.hours, forecast = UsageForecast.InsufficientData, riskSummary = null
        )
        return emptyReport(source).copy(series = listOf(series))
    }

    @Test
    fun `account history is labeled by the snapshot and an unreadable account is left out`() = runBlocking {
        val history = RecordingHistory { source, _ -> if (source == ApiSource.CODEX) error("SQLite fora") else emptyReport(source) }
        val reader = accountHistoryReader(
            GetUsageHistoryUseCase(history),
            stats = { listOf(workStats, codexStats) },
            clock = object : Clock { override fun now() = this@TelegramBotWiringTest.now }
        )

        val result = reader(HistoryRange.LAST_24_HOURS)

        assertEquals(listOf("Anthropic · Trabalho"), result.map { entry -> entry.label })
        assertEquals(listOf(ApiSource.ANTHROPIC to "uuid-1", ApiSource.CODEX to null), history.asked)
    }

    @Test
    fun `the chart numbers the accounts of a source and follows the source order`() = runBlocking {
        val second = UsageAccountContext(UsageAccountKey(ApiSource.ANTHROPIC, "uuid-2"), "other@example.com")
        val history = RecordingHistory(
            accounts = mapOf(ApiSource.ANTHROPIC to listOf(workContext, second))
        ) { source, _ -> reportWithLine(source) }

        val chart = telegramChartOf(
            GetUsageHistoryUseCase(history),
            enabledSources = setOf(ApiSource.CODEX, ApiSource.ANTHROPIC, ApiSource.MINIMAX),
            language = AppLanguage.PT,
            range = HistoryRange.LAST_24_HOURS,
            now = now
        )!!

        val labels = chart.lines.map { line -> line.label }
        assertTrue(labels[0].startsWith("Anthropic 1 · "), labels.toString())
        assertTrue(labels[1].startsWith("Anthropic 2 · "), labels.toString())
        assertTrue(labels.any { label -> label.startsWith("Codex · ") }, labels.toString())
        assertTrue(labels.none { label -> label.contains("@") }, "o nome nunca é o e-mail da conta")
        assertEquals(ApiSource.ANTHROPIC to "uuid-1", history.asked.first())
    }

    @Test
    fun `a chart without any point is no chart`() = runBlocking {
        val history = RecordingHistory { source, _ -> emptyReport(source) }

        assertNull(
            telegramChartOf(GetUsageHistoryUseCase(history), setOf(ApiSource.CODEX), AppLanguage.EN, HistoryRange.LAST_7_DAYS, now)
        )
    }
}
