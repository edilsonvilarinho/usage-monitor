package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.terminal.TerminalAccount
import com.usagemonitor.presentation.terminal.TerminalAnalysisLabels
import com.usagemonitor.presentation.terminal.TerminalAnalysisView
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TerminalAnalysisCommandTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val work = TerminalAccount(ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", now, listOf(70, 90), emptyList())
    private val codex = TerminalAccount(ApiSource.CODEX, "CODEX", "Codex", null, now, listOf(70, 90), emptyList())
    private val fiveHour = AnalysisWindowView("Claude 5h", PeriodType.INTERVAL, 64, now + 9.minutes - 5.hours, now + 9.minutes, 20.0, 13.2, 240.0, 67)
    private val analysis = SessionAnalysis("Anthropic · trabalho", ApiSource.ANTHROPIC, now, fiveHour, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList())
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)

    @Test
    fun `analysis takes an optional account and --json`() {
        assertEquals(TerminalCommandParse.Parsed(TerminalCommand.Analysis()), parseTerminalCommand("analysis", emptyList()))
        assertEquals(TerminalCommandParse.Parsed(TerminalCommand.Analysis("codex", json = true)), parseTerminalCommand("analysis", listOf("codex", "--json")))
        assertTrue(parseTerminalCommand("analysis", listOf("--watch")) is TerminalCommandParse.Invalid)
        assertTrue(parseTerminalCommand("analysis", listOf("claude", "codex")) is TerminalCommandParse.Invalid)
        assertTrue("analysis" in TerminalCommandNames.ALL)
    }

    @Test
    fun `the filter picks the source or the account alias`() {
        assertTrue(TerminalAnalysisCommand.matches(work, null))
        assertTrue(TerminalAnalysisCommand.matches(work, "claude"))
        assertTrue(TerminalAnalysisCommand.matches(work, "Trabalho"))
        assertFalse(TerminalAnalysisCommand.matches(work, "codex"))
        assertTrue(TerminalAnalysisCommand.matches(codex, "codex"))
    }

    @Test
    fun `the json contract fields are stable, in English and snake case`() {
        val root = Json.parseToJsonElement(TerminalAnalysisJson.encode(listOf(work to analysis, codex to null), now)).jsonObject
        assertEquals(setOf("version", "generated_at", "accounts"), root.keys)
        val accounts = root.getValue("accounts").jsonArray
        assertEquals(setOf("source", "source_name", "account", "windows"), accounts[0].jsonObject.keys)
        val window = accounts[0].jsonObject.getValue("windows").jsonArray.single().jsonObject
        assertEquals(
            setOf(
                "label", "period", "percent", "starts_at", "resets_at", "time_elapsed_percent", "pace_last_hour", "pace_average", "safe_pace",
                "projected_at_reset", "verdict", "exhausts_at_last_hour_pace", "exhausts_at_average_pace"
            ),
            window.keys
        )
        assertEquals("fits", window.getValue("verdict").jsonPrimitive.content)
        assertEquals("97", window.getValue("time_elapsed_percent").jsonPrimitive.content)
        // 36 pontos a 20 %/h: 1 h 48 min depois de agora, depois do reinício.
        assertEquals((now + 108.minutes).toString(), window.getValue("exhausts_at_last_hour_pace").jsonPrimitive.content)
        assertEquals(0, accounts[1].jsonObject.getValue("windows").jsonArray.size)
    }

    @Test
    fun `the text output has the race per account, and nothing to show exits with 3`() {
        val labels = TerminalAnalysisLabels(AppLanguage.PT)
        val output = TerminalAnalysisView.render(listOf(work to analysis), now, CollectorKind.APP, 38.seconds, labels, TerminalLabels(AppLanguage.PT), color)
        val text = output.lines.map { line -> line.plainText }
        assertEquals(0, output.exitCode)
        assertEquals("● Anthropic · trabalho", text[0])
        assertTrue(text[1].startsWith("5h · 64% usada · ✓ cabe até o reinício"), text[1])
        assertTrue(text.all { line -> line.length <= TerminalAnalysisView.WIDTH }, text.joinToString("\n"))
        assertEquals("lido há 38 s pelo app aberto", text.last())
        val empty = TerminalAnalysisView.render(emptyList(), now, CollectorKind.APP, 38.seconds, labels, TerminalLabels(AppLanguage.PT), color, filter = "pessoal")
        assertEquals(3, empty.exitCode)
        assertEquals("▲ Nenhuma conta com análise corresponde a 'pessoal'.", empty.lines.single().plainText)
    }

    /** Uma leitura de verdade: o cache do painel numa pasta temporária, sem índice nem histórico. */
    private fun runCommand(command: TerminalCommand.Analysis, unicode: Boolean = true, onStores: () -> Unit = {}): Pair<Int, String> {
        val dir = Files.createTempDirectory("usage-monitor-analysis").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val cache = File(dir, "dashboard-cache.json")
        val stats = ApiUsageStats(
            source = ApiSource.ANTHROPIC, targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "work"), apiName = "Anthropic", profileLabel = "trabalho",
            quotas = listOf(QuotaInfo(label = "Claude 5h", used = 64, total = 100, periodEndAt = now + 9.minutes, unit = UsageUnit.PERCENTAGE)),
            fetchedAt = now - 38.seconds
        )
        runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats), now - 38.seconds) }
        val clock = object : Clock {
            override fun now(): Instant = now
        }
        val buffer = ByteArrayOutputStream()
        val console = TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = unicode), PrintStream(buffer, true, "UTF-8"))
        try {
            val code = TerminalAnalysisCommand.run(command, AppLanguage.PT, TerminalReader(cache, File(dir, "app.lock"), node, clock), console) {
                onStores()
                TuiStores(cliIndex = File(dir, "sem-indice.db"), codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db"))
            }
            return code to buffer.toString(Charsets.UTF_8).replace("\r\n", "\n")
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `run reads the cached accounts and prints the json with one entry per account`() {
        val (code, output) = runCommand(TerminalCommand.Analysis(json = true))

        assertEquals(0, code, output)
        val accounts = Json.parseToJsonElement(output).jsonObject.getValue("accounts").jsonArray
        assertEquals("anthropic", accounts.single().jsonObject.getValue("source").jsonPrimitive.content)
        assertEquals("trabalho", accounts.single().jsonObject.getValue("account").jsonPrimitive.content)
    }

    @Test
    fun `run with a filter that matches nothing opens no database and exits with 3`() {
        var opened = false

        val (jsonCode, json) = runCommand(TerminalCommand.Analysis("codex", json = true)) { opened = true }
        val (textCode, text) = runCommand(TerminalCommand.Analysis("codex")) { opened = true }

        assertEquals(3, jsonCode)
        assertEquals(0, Json.parseToJsonElement(json).jsonObject.getValue("accounts").jsonArray.size)
        assertEquals(3, textCode)
        assertEquals("▲ Nenhuma conta com análise corresponde a 'codex'.\n", text)
        assertFalse(opened)
    }

    @Test
    fun `run prints the race as text, and the json escapes accents on an ascii console`() {
        val (code, text) = runCommand(TerminalCommand.Analysis("trabalho"))
        assertEquals(0, code, text)
        assertTrue(text.startsWith("● Anthropic · trabalho\n"), text)

        val ascii = TerminalAnalysisJson.encode(listOf(work.copy(name = "Anthropic · trabalho") to analysis), now, asciiOnly = true)
        assertTrue(ascii.all { char -> char.code < 128 }, ascii)
        assertTrue(ascii.contains("Anthropic \\u00b7 trabalho"), ascii)
    }
}
