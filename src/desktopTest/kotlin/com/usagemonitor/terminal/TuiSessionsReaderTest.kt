package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TuiSessionPeriod
import com.usagemonitor.presentation.terminal.TuiSessionsLabels
import com.usagemonitor.presentation.terminal.TuiSessionsState
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TuiSessionsReaderTest {

    // Quarta, 21/10/2026, 14:23 BRT.
    private val now = Instant.parse("2026-10-21T17:23:00Z")

    private fun assistantLine(sessionId: String, messageId: String, timestamp: String, input: Long, output: Long, model: String = "claude-opus-5") =
        """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$sessionId","timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":false,"message":{"id":"$messageId","model":"$model","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":$input,"output_tokens":$output,"cache_read_input_tokens":0,"cache_creation_input_tokens":0,""" +
            """"cache_creation":{"ephemeral_5m_input_tokens":0,"ephemeral_1h_input_tokens":0},"service_tier":"standard"}}}"""

    /** O índice como o coletor o deixa: um transcript de ontem e um de hoje. */
    private fun indexed(dir: File): File {
        val root = File(dir, "projects").also { it.mkdirs() }
        val project = File(root, "-workspace-usage-monitor").also { it.mkdirs() }
        File(project, "ontem.jsonl").writeText(assistantLine("ontem", "m-1", "2026-10-20T15:00:00Z", 1_000, 100) + "\n")
        File(project, "hoje.jsonl").writeText(
            listOf(
                assistantLine("hoje", "m-2", "2026-10-21T16:00:00Z", 2_000, 200),
                assistantLine("hoje", "m-3", "2026-10-21T16:30:00Z", 3_000, 300, model = "modelo-sem-tarifa")
            ).joinToString("\n", postfix = "\n")
        )
        val database = File(dir, "usage-history.db")
        LocalCliSessionDataSource(projectRootsProvider = { listOf(CliProjectRoot("default", root.absolutePath)) }, databaseFile = database).use { writer ->
            runBlocking { writer.syncIndex() }
        }
        return database
    }

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
}
