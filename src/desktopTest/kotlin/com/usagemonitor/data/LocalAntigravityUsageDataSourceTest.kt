package com.usagemonitor.data

import com.usagemonitor.data.datasource.AntigravityPtySession
import com.usagemonitor.data.datasource.AntigravityPtySessionFactory
import com.usagemonitor.data.datasource.LocalAntigravityUsageDataSource
import com.usagemonitor.data.datasource.PtyAntigravityUsageCommandRunner
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAntigravityUsageDataSourceTest {
    private val fixture = ">\nModel Quotas\nGemini 3.5 Flash 40% used\nModel: Gemini 3.1 Pro\nRemaining: 400 tokens\nLimit: 1,000 tokens"

    @Test
    fun `PTY runner sends only usage command and always terminates process tree`() = runBlocking {
        val fakeSession = FakeSession(fixture)
        val runner = fakeRunner(fakeSession)

        val output = runner.readUsagePanel()
        val usage = LocalAntigravityUsageDataSource(object : com.usagemonitor.data.datasource.AntigravityUsageCommandRunner {
            override suspend fun readUsagePanel() = output
        }).readUsage()

        assertTrue(fakeSession.commandSent)
        assertTrue(fakeSession.terminated)
        assertEquals("/usage", fakeSession.sentText)
        assertEquals(40.0, usage.first().usedPercent)
        assertEquals(400L, usage.last().remaining)
        assertEquals(UsageUnit.TOKENS, usage.last().unit)
    }

    @Test
    fun `missing CLI reports unavailable without opening a session`() = runBlocking {
        val runner = PtyAntigravityUsageCommandRunner(
            sessionFactory = AntigravityPtySessionFactory { _, _ -> error("session must not start") },
            executableResolver = { null },
            timeoutMillis = 20
        )

        assertFailsWith<IllegalStateException> { runner.readUsagePanel() }
        Unit
    }

    @Test
    fun `authentication screen terminates immediately without submitting credentials`() = runBlocking {
        val fakeSession = FakeSession("Sign in to continue")
        val runner = fakeRunner(fakeSession)

        assertFailsWith<IllegalStateException> { runner.readUsagePanel() }
        assertFalse(fakeSession.commandSent)
        assertTrue(fakeSession.terminated)
    }

    @Test
    fun `timeout terminates the CLI process tree`() = runBlocking {
        val fakeSession = FakeSession("")
        val runner = fakeRunner(fakeSession, timeoutMillis = 20)

        assertFailsWith<IllegalStateException> { runner.readUsagePanel() }
        assertTrue(fakeSession.terminated)
    }

    @Test
    fun ptyRunnerAcceptsTheGroupedModelsAndQuotaPanel() = runBlocking {
        val fakeSession = FakeSession(
            """
                Models & Quota
                >
                GEMINI MODELS
                Models within this group: Gemini Flash, Gemini Pro
                Weekly Limit Remaining
                [████████████████████████████████████] 99.49%
                Refreshes in 167h 58m
            """.trimIndent()
        )
        val runner = fakeRunner(fakeSession)

        val output = runner.readUsagePanel()
        val usage = LocalAntigravityUsageDataSource(object : com.usagemonitor.data.datasource.AntigravityUsageCommandRunner {
            override suspend fun readUsagePanel() = output
        }).readUsage()

        assertTrue(fakeSession.commandSent)
        assertTrue(fakeSession.terminated)
        assertEquals("Gemini models", usage.single().modelName)
        assertEquals(99.49, usage.single().remainingPercent)
    }

    private fun fakeRunner(session: FakeSession, timeoutMillis: Long = 100): PtyAntigravityUsageCommandRunner {
        return PtyAntigravityUsageCommandRunner(
            sessionFactory = AntigravityPtySessionFactory { _, _ -> session },
            executableResolver = { File("agy-test") },
            workingDirectoryProvider = { File(".") },
            timeoutMillis = timeoutMillis,
            startupDelayMillis = 0,
            settleDelayMillis = 0
        )
    }

    private class FakeSession(private val output: String) : AntigravityPtySession {
        var commandSent = false
        var terminated = false
        var sentText = ""

        override fun sendUsageCommand() {
            commandSent = true
            sentText = "/usage"
        }

        override fun readOutput(): String = output
        override fun isAlive(): Boolean = !terminated
        override fun exitCodeOrNull(): Int? = null
        override fun terminateProcessTree() {
            terminated = true
        }
    }
}
