package com.usagemonitor

import com.usagemonitor.domain.entity.AnalysisModelSpeed
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexCliModelUsage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.analysisThroughputCuts
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * O leitor da análise da sessão (bot e janela): qual card, qual índice e qual
 * carimbo de sincronização entram na análise.
 */
class SessionAnalysisReaderTest {

    private val now = Instant.parse("2026-10-28T15:30:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val account = UsageAccountContext(UsageAccountKey(ApiSource.ANTHROPIC, "account-a"), "a@example.com")
    private val claude = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "work"),
        apiName = "Anthropic",
        quotas = emptyList(),
        accountContext = account,
        profileLabel = "Trabalho"
    )
    private val codex = ApiUsageStats(source = ApiSource.CODEX, apiName = "Codex", quotas = emptyList())
    private val minimax = ApiUsageStats(source = ApiSource.MINIMAX, apiName = "MiniMax", quotas = emptyList())

    private val historyCalls = mutableListOf<Pair<ApiSource, UsageAccountKey?>>()
    private val claudeCalls = mutableListOf<Pair<String?, Long>>()
    private val codexCalls = mutableListOf<Long>()

    private fun reader(
        stats: List<ApiUsageStats> = listOf(claude, codex, minimax),
        historyFails: Boolean = false,
        claudeIndex: Result<Map<String, OutputThroughput>> = Result.success(mapOf("opus" to OutputThroughput(3_000, 60_000))),
        codexIndex: Result<List<CodexCliModelUsage>> = Result.success(emptyList()),
        codexIndexedAt: Instant? = null
    ) = sessionAnalysisReader(
        currentStats = { stats },
        history = { source, accountKey, at ->
            historyCalls += source to accountKey
            if (historyFails) error("banco fechado")
            ApiUsageHistoryReport(source = source, range = HistoryRange.LAST_7_DAYS, lastUpdatedAt = at, series = emptyList())
        },
        claudeThroughputs = { profileId, since ->
            claudeCalls += profileId to since
            claudeIndex
        },
        codexUsage = { since ->
            codexCalls += since
            codexIndex
        },
        codexIndexedAt = { codexIndexedAt },
        clock = clock
    )

    @Test
    fun `claude reads seven days of the account history and the index of the card profile at every cut`() {
        val analysis = runBlocking { reader()(claude.targetKey.storageKey) }

        assertNotNull(analysis)
        assertEquals("Anthropic · Trabalho", analysis.accountLabel)
        assertEquals(listOf<Pair<ApiSource, UsageAccountKey?>>(ApiSource.ANTHROPIC to account.key), historyCalls)
        assertEquals(analysisThroughputCuts(emptyList(), now), claudeCalls.map { (_, since) -> since }.toSet())
        assertEquals(setOf("work"), claudeCalls.map { (profileId, _) -> profileId }.toSet())
        assertEquals(emptyList<Long>(), codexCalls)
        assertEquals(listOf(AnalysisModelSpeed("opus", 50.0)), analysis.modelSpeeds)
        // O índice do Claude acompanha a coleta: sem carimbo próprio.
        assertNull(analysis.throughputIndexedAt)
    }

    @Test
    fun `codex reads the machine index, drops models without measured time and carries the index time`() {
        val indexedAt = Instant.parse("2026-10-28T15:20:00Z")
        val codexIndex = Result.success(
            listOf(
                CodexCliModelUsage("gpt-5", 3, 10, 0, 1_200, 1_210, OutputThroughput(1_200, 30_000)),
                CodexCliModelUsage("gpt-5-mini", 1, 10, 0, 50, 60, null)
            )
        )

        val analysis = runBlocking { reader(codexIndex = codexIndex, codexIndexedAt = indexedAt)(codex.targetKey.storageKey) }

        assertNotNull(analysis)
        assertEquals(listOf(AnalysisModelSpeed("gpt-5", 40.0)), analysis.modelSpeeds)
        assertEquals(indexedAt, analysis.throughputIndexedAt)
        assertEquals(emptyList<Pair<String?, Long>>(), claudeCalls)
        assertEquals(analysisThroughputCuts(emptyList(), now), codexCalls.toSet())
    }

    @Test
    fun `an index that fails leaves the speeds empty but keeps the analysis`() {
        val analysis = runBlocking { reader(claudeIndex = Result.failure(IllegalStateException("índice travado")))(claude.targetKey.storageKey) }

        assertNotNull(analysis)
        assertEquals(emptyList<AnalysisModelSpeed>(), analysis.modelSpeeds)
    }

    @Test
    fun `no analysis for an unknown card, a source without analysis or an unreadable history`() {
        assertNull(runBlocking { reader()("anthropic:outro") })
        assertNull(runBlocking { reader()(minimax.targetKey.storageKey) })
        assertEquals(emptyList<Pair<ApiSource, UsageAccountKey?>>(), historyCalls, "fonte sem análise não lê o histórico")
        assertNull(runBlocking { reader(historyFails = true)(claude.targetKey.storageKey) })
        assertEquals(emptyList<Pair<String?, Long>>(), claudeCalls, "sem histórico não há recorte para o índice")
        assertNull(runBlocking { reader(stats = emptyList())(claude.targetKey.storageKey) })
    }
}
