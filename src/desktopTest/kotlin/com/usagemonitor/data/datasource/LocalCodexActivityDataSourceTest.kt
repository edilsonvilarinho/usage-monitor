package com.usagemonitor.data.datasource

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A execução do Codex, lida do estado do app desktop e dos rollouts.
 *
 * O caso que motivou: um turno do app desktop rodando havia sete minutos, com o
 * rollout parado desde o início do turno. A tabela diz "em andamento"; o arquivo,
 * sozinho, diria "parado".
 */
class LocalCodexActivityDataSourceTest {

    private val home: File = Files.createTempDirectory("codex-activity").toFile()
    private val now = 1_790_283_790_000L

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    @Test
    fun `turno em andamento com item recente esta vivo mesmo com o rollout parado`() {
        threadHistory(startedAtSeconds = (now - 7 * 60_000) / 1_000, lastItemMillis = now - 3 * 60_000)
        rollout(ageMillis = 7 * 60_000)

        assertTrue(source().isActive(now))
    }

    /** Turno "em andamento" que ficou assim depois de uma queda do app não acende nada. */
    @Test
    fun `turno em andamento sem item ha mais de dez minutos nao esta vivo`() {
        threadHistory(startedAtSeconds = (now - 60 * 60_000) / 1_000, lastItemMillis = now - 30 * 60_000)

        assertFalse(source().isActive(now))
    }

    @Test
    fun `sem o banco do app o rollout recente do cli basta`() {
        rollout(ageMillis = 60_000)

        assertTrue(source().isActive(now))
    }

    @Test
    fun `rollout antigo e nenhum turno nao e execucao`() {
        rollout(ageMillis = 20 * 60_000)

        assertFalse(source().isActive(now))
    }

    @Test
    fun `sem nada do codex na maquina nao e execucao`() {
        assertFalse(source().isActive(now))
    }

    @Test
    fun `turno recem aberto sem item ainda esta vivo`() {
        assertTrue(isCodexTurnLive(startedAt = (now - 30_000) / 1_000, lastItemMillis = null, nowMillis = now))
        // Segundos ou milissegundos: o tamanho do número diz qual.
        assertTrue(isCodexTurnLive(startedAt = now - 30_000, lastItemMillis = null, nowMillis = now))
        assertFalse(isCodexTurnLive(startedAt = (now - 5 * 60_000) / 1_000, lastItemMillis = null, nowMillis = now + 6 * 60_000))
    }

    private fun source() = LocalCodexActivityDataSource(home, ZoneOffset.UTC)

    private fun threadHistory(startedAtSeconds: Long, lastItemMillis: Long) {
        val database = File(home, "thread_history_1.sqlite")
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE thread_turns (thread_id TEXT, status TEXT, started_at INTEGER, completed_at INTEGER)")
                statement.execute("CREATE TABLE thread_items (thread_id TEXT, created_at_ms INTEGER, item_type TEXT)")
                statement.execute("INSERT INTO thread_turns VALUES ('t1', 'completed', ${startedAtSeconds - 3_600}, ${startedAtSeconds - 3_000})")
                statement.execute("INSERT INTO thread_turns VALUES ('t2', 'inProgress', $startedAtSeconds, NULL)")
                statement.execute("INSERT INTO thread_items VALUES ('t2', $lastItemMillis, 'reasoning')")
            }
        }
    }

    private fun rollout(ageMillis: Long) {
        val day = java.time.Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val directory = File(home, "sessions/%04d/%02d/%02d".format(day.year, day.monthValue, day.dayOfMonth))
        directory.mkdirs()
        val file = File(directory, "rollout-2026-09-24T17-55-18-abc.jsonl")
        file.writeText("{}\n")
        file.setLastModified(now - ageMillis)
    }
}
