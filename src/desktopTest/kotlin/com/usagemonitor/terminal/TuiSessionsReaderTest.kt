package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiSessionPeriod
import com.usagemonitor.presentation.terminal.TuiSessionsLabels
import com.usagemonitor.presentation.terminal.TuiSessionsState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class TuiSessionsReaderTest {

    // Quarta, 21/10/2026, 14:23 BRT.
    private val now = Instant.parse("2026-10-21T17:23:00Z")

    /** O índice como o coletor o deixa: um transcript de ontem e um de hoje. */
    private fun indexed(dir: File): File =
        ClaudeTranscriptFixture.indexed(
            dir,
            mapOf(
                "ontem" to listOf(ClaudeTranscriptFixture.assistantLine("ontem", "m-1", "2026-10-20T15:00:00Z", 1_000, 100)),
                "hoje" to listOf(
                    ClaudeTranscriptFixture.assistantLine("hoje", "m-2", "2026-10-21T16:00:00Z", 2_000, 200),
                    ClaudeTranscriptFixture.assistantLine("hoje", "m-3", "2026-10-21T16:30:00Z", 3_000, 300, model = "modelo-sem-tarifa")
                )
            )
        )

    @Test
    fun `hoje so tem a sessao de hoje, com o custo parcial marcado, e os turnos abrem`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-sessions").toFile()
        try {
            TuiStores(cliIndex = indexed(dir), codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db")).use { stores ->
                val reader = TuiSessionsReader(stores, exportDirectory = dir)
                val today = reader.load(TuiSessionsState(), now)
                val session = today.sessions.single()
                assertEquals("hoje", session.id)
                assertEquals("usage-monitor", session.project)
                assertTrue(session.costIncomplete)
                assertNotNull(session.costMicros)
                assertNull(today.turns)
                assertNull(today.failure)

                val week = reader.load(TuiSessionsState(period = TuiSessionPeriod.LAST_7_DAYS), now)
                assertEquals(listOf("hoje", "ontem"), week.sessions.map { item -> item.id })

                val turns = reader.load(TuiSessionsState(turnsOpen = true), now).turns
                assertEquals(listOf(Instant.parse("2026-10-21T16:30:00Z"), Instant.parse("2026-10-21T16:00:00Z")), turns?.map { turn -> turn.at })
                assertNull(turns?.first()?.costMicros)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `e grava o CSV do app na pasta de onde a TUI abriu`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-sessions").toFile()
        try {
            TuiStores(cliIndex = indexed(dir), codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db")).use { stores ->
                val notice = TuiSessionsReader(stores, exportDirectory = dir).export(TuiSessionsState(), now, TuiSessionsLabels(AppLanguage.PT))
                assertTrue(notice.ok, notice.text)
                val csv = dir.listFiles().orEmpty().single { file -> file.name.endsWith(".csv") }
                assertEquals("usage-monitor-sessions-today-20261021-1423.csv", csv.name)
                assertTrue(csv.readText().startsWith("session_id,project,"))
                assertTrue("hoje" in csv.readText())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `sem nenhum indice a aba diz que a leitura falhou`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-sessions").toFile()
        try {
            TuiStores(cliIndex = File(dir, "a.db"), codexIndex = File(dir, "b.db"), history = File(dir, "c.db")).use { stores ->
                val sessions = TuiSessionsReader(stores, exportDirectory = dir).load(TuiSessionsState(), now - 1.hours)
                assertTrue(sessions.sessions.isEmpty())
                assertNotNull(sessions.failure)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a Codex session keeps the cached input apart, has no cost and opens its turns`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-sessions").toFile()
        try {
            val codex = CodexRolloutFixture.indexed(
                dir, "codex-1", "/workspace/usage-monitor",
                listOf(
                    CodexRolloutFixture.Response("t-1", "2026-10-21T16:00:00Z", "2026-10-21T16:00:04Z", input = 1_000, cached = 400, output = 120),
                    CodexRolloutFixture.Response("t-2", "2026-10-21T16:10:00Z", "2026-10-21T16:10:02Z", input = 500, cached = 100, output = 80)
                )
            )
            TuiStores(cliIndex = File(dir, "sem-indice.db"), codexIndex = codex, history = File(dir, "sem-historico.db")).use { stores ->
                val reader = TuiSessionsReader(stores, exportDirectory = dir)

                val loaded = reader.load(TuiSessionsState(turnsOpen = true), now)

                // O índice do Claude Code ausente não esconde o Codex.
                assertNull(loaded.failure)
                val session = loaded.sessions.single()
                assertEquals(ApiSource.CODEX, session.source)
                assertEquals("usage-monitor", session.project)
                assertEquals(1_500L - 500L, session.inputTokens)
                assertEquals(500L, session.cacheReadTokens)
                assertEquals(200L, session.outputTokens)
                assertNull(session.costMicros)
                assertFalse(session.costIncomplete)
                // Do primeiro ao último registro de uso: o Codex não mede tempo ativo.
                assertEquals(Instant.parse("2026-10-21T16:10:02Z") - Instant.parse("2026-10-21T16:00:04Z"), session.durationMillis.milliseconds)
                assertEquals(listOf(Instant.parse("2026-10-21T16:10:02Z"), Instant.parse("2026-10-21T16:00:04Z")), loaded.turns?.map { turn -> turn.at })
                assertEquals(listOf<Long?>(null, null), loaded.turns?.map { turn -> turn.costMicros })
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the month reaches back 30 days and export says when there is nothing or the index is missing`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-sessions").toFile()
        try {
            val labels = TuiSessionsLabels(AppLanguage.PT)
            TuiStores(cliIndex = indexed(dir), codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db")).use { stores ->
                val reader = TuiSessionsReader(stores, exportDirectory = dir)
                // Vinte dias depois, só o corte de 30 dias ainda vê as duas sessões.
                val later = now + 20.days
                assertEquals(listOf("hoje", "ontem"), reader.load(TuiSessionsState(period = TuiSessionPeriod.LAST_30_DAYS), later).sessions.map { item -> item.id })
                assertTrue(reader.load(TuiSessionsState(period = TuiSessionPeriod.LAST_7_DAYS), later).sessions.isEmpty())

                val nothing = reader.export(TuiSessionsState(), later, labels)
                assertEquals(TuiNotice("Nenhuma sessão do Claude Code neste período para exportar.", ok = false), nothing)

                val month = reader.export(TuiSessionsState(period = TuiSessionPeriod.LAST_30_DAYS), later, labels)
                assertTrue(month.ok, month.text)
                assertTrue(dir.listFiles().orEmpty().any { file -> file.name.startsWith("usage-monitor-sessions-30d-") })
            }
            TuiStores(cliIndex = File(dir, "sem-indice.db"), codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db")).use { stores ->
                val failed = TuiSessionsReader(stores, exportDirectory = dir).export(TuiSessionsState(), now, labels)
                assertFalse(failed.ok)
                assertTrue(failed.text.startsWith("O CSV não foi salvo: "), failed.text)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
