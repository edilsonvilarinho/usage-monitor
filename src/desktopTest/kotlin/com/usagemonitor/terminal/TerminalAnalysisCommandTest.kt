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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
}
