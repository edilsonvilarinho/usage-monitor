package com.usagemonitor.data

import com.usagemonitor.data.datasource.AntigravityUsageDataSource
import com.usagemonitor.data.repository.AntigravityRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AntigravityRepositoryImplTest {

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class CountingDataSource(var payload: String = VALID) : AntigravityUsageDataSource {
        var calls = 0
        override suspend fun readUsageJson(): String {
            calls += 1
            return payload
        }
    }

    private val start = Instant.parse("2026-09-24T12:00:00Z")

    @Test
    fun `maps the envelope into normalized quotas`() = runTest {
        val stats = AntigravityRepositoryImpl(CountingDataSource()).getUsage().getOrThrow()

        assertEquals(ApiSource.ANTIGRAVITY, stats.source)
        assertEquals(listOf("Antigravity Gemini 7d"), stats.quotas.map { it.label })
    }

    @Test
    fun `readings inside the TTL reuse the previous CLI call`() = runTest {
        val clock = MutableClock(start)
        val source = CountingDataSource()
        val repository = AntigravityRepositoryImpl(source, clock)

        repository.getUsage().getOrThrow()
        clock.instant = start + 4.minutes
        repository.getUsage().getOrThrow()
        assertEquals(1, source.calls)

        clock.instant = start + 6.minutes
        repository.getUsage().getOrThrow()
        assertEquals(2, source.calls)
    }

    @Test
    fun `an explicit refresh invalidates the reading`() = runTest {
        val source = CountingDataSource()
        val repository = AntigravityRepositoryImpl(source, MutableClock(start))

        repository.getUsage().getOrThrow()
        repository.invalidateCachedReading()
        repository.getUsage().getOrThrow()

        assertEquals(2, source.calls)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val source = CountingDataSource(payload = "{not json")
        val repository = AntigravityRepositoryImpl(source, MutableClock(start))

        assertTrue(repository.getUsage().isFailure)
        source.payload = VALID
        assertTrue(repository.getUsage().isSuccess)
        assertEquals(2, source.calls)
    }

    /**
     * Uma vez aberto, o disjuntor não chama mais o CLI — nem com o refresh do
     * usuário: clicar em atualizar não pode ser o gesto que volta a gastar.
     */
    @Test
    fun `a model turn trips the breaker until restart`() = runTest {
        val source = CountingDataSource(payload = """{"status":"SUCCESS","num_turns":1,"usage":{"total_tokens":90}}""")
        val repository = AntigravityRepositoryImpl(source, MutableClock(start))

        val first = repository.getUsage().exceptionOrNull()
        assertEquals(AntigravityUsageFailureKind.COLLECTION_PAUSED.safeMessage, first?.message)

        source.payload = VALID
        repository.invalidateCachedReading()
        val second = repository.getUsage().exceptionOrNull()
        assertEquals(AntigravityUsageFailureKind.COLLECTION_PAUSED.safeMessage, second?.message)
        assertEquals(1, source.calls)
    }

    @Test
    fun `native errors do not leak their text`() = runTest {
        val repository = AntigravityRepositoryImpl(object : AntigravityUsageDataSource {
            override suspend fun readUsageJson(): String = throw IllegalArgumentException("raw output with secrets")
        })

        val error = repository.getUsage().exceptionOrNull()
        assertTrue(error?.message?.contains("secrets") == false)
    }

    @Test
    fun `cancellation is propagated`() = runTest {
        val repository = AntigravityRepositoryImpl(object : AntigravityUsageDataSource {
            override suspend fun readUsageJson(): String = throw CancellationException("cancel")
        })

        assertFailsWith<CancellationException> { repository.getUsage() }
    }

    private companion object {
        const val VALID = """{"status":"SUCCESS","num_turns":0,"usage":{"total_tokens":0},
            "command":{"name":"usage","data":{"groups":[{"name":"Gemini Models","buckets":[
            {"id":"gemini-weekly","window":"weekly","remaining_fraction":0.5,"reset_time":"2026-09-30T21:57:08Z"}]}]}}}"""
    }
}
