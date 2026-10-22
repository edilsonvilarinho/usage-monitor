package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.domain.entity.CollectorTeamHealth
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.presentation.terminal.TuiTeamState
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Só o `GET`: qualquer escrita no servidor derruba o teste. */
private class ReadOnlyTeamRepository(var next: Result<TeamUsageSnapshot>) : TeamUsageRepository {
    val fetched = mutableListOf<Pair<String, Long?>>()

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        fetched += accountKey to cutoffMillis
        return next
    }

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> = error("a TUI não envia uso")
    override suspend fun touchPresence(accountKey: String, member: TeamMemberIdentity): Result<TeamPresenceReceipt> = error("a TUI não bate presença")
    override suspend fun fetchSessionDetail(accountKey: String, deviceId: String, sessionId: String): Result<CliSessionDetail?> = Result.success(null)
    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)
    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> = Result.success(null)
}

class TuiTeamReaderTest {

    // Quarta, 21/10/2026, 14:23 BRT.
    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val active = TeamIntegrationSettings(enabled = true, serverUrl = "https://time.exemplo.com", apiKey = "chave", alias = "edi", deviceId = "d-0")

    private fun session(id: String, lastTs: Instant) = CliSessionSummary(sessionId = id, filePath = "", firstTs = lastTs - 30.minutes, lastTs = lastTs, inputTokens = 1_000, costMicros = 2_000_000)

    private val snapshot = TeamUsageSnapshot(
        listOf(
            TeamMemberUsage(deviceId = "d-2", alias = "Bruno", lastSeenAt = now - 30.minutes, sessions = listOf(session("b", now - 3.hoursAgo()))),
            TeamMemberUsage(deviceId = "d-1", alias = "Ana", lastSeenAt = now - 10.seconds, sessions = listOf(session("a", now - 1.minutes)))
        )
    )

    private fun Int.hoursAgo() = (this * 60).minutes

    @Test
    fun `le o dia da conta escolhida e classifica a presenca no agora do servidor`() {
        val repository = ReadOnlyTeamRepository(Result.success(snapshot))
        val reader = TuiTeamReader({ active }, { listOf(TuiTeamAccount("trabalho", "uuid-1"), TuiTeamAccount("pessoal", "uuid-2")) }, { _ -> repository }, clockOffsetMillis = { 0L })
        val team = reader.load(TuiTeamState(accountIndex = 1), now)!!
        assertEquals(listOf("trabalho", "pessoal"), team.accounts)
        assertEquals("uuid-2" to startOfTodayBrt(now).toEpochMilliseconds(), repository.fetched.single())
        val (ana, bruno) = team.members
        assertEquals("Ana", ana.alias)
        assertTrue(ana.workingNow && ana.online)
        assertFalse(bruno.online)
        assertEquals(2_000_000, ana.costMicros)
        assertNull(team.failure)
        assertEquals(now, team.readAt)
    }

    @Test
    fun `leitura que falha mantem os numeros anteriores da mesma conta`() {
        val repository = ReadOnlyTeamRepository(Result.success(snapshot))
        val reader = TuiTeamReader({ active }, { listOf(TuiTeamAccount("trabalho", "uuid-1")) }, { _ -> repository })
        reader.load(TuiTeamState(), now)
        repository.next = Result.failure(IllegalStateException("servidor fora do ar"))
        val team = reader.load(TuiTeamState(), now + 15.seconds)!!
        assertEquals("servidor fora do ar", team.failure)
        assertFalse(team.unreachable)
        assertEquals(2, team.members.size)
        assertEquals(now, team.readAt)
    }

    @Test
    fun `falha de conexao e classificada pelo tipo, nao pela mensagem do socket`() {
        val repository = ReadOnlyTeamRepository(Result.failure(java.net.ConnectException("Failed to connect to localhost/[0:0:0:0:0:0:0:1]:3917")))
        val team = TuiTeamReader({ active }, { listOf(TuiTeamAccount("trabalho", "uuid-1")) }, { _ -> repository }).load(TuiTeamState(), now)!!
        assertTrue(team.unreachable)
        assertNull(team.readAt)
    }

    @Test
    fun `sem integracao ligada ou sem conta com login, a aba 4 nao existe`() {
        val repository = ReadOnlyTeamRepository(Result.success(snapshot))
        assertFalse(TuiTeamReader({ active.copy(enabled = false) }, { listOf(TuiTeamAccount("trabalho", "uuid-1")) }, { _ -> repository }).available())
        assertFalse(TuiTeamReader({ active }, { emptyList() }, { _ -> repository }).available())
        assertFalse(TuiTeamReader({ null }, { emptyList() }, { _ -> repository }).available())
        assertTrue(TuiTeamReader({ active }, { listOf(TuiTeamAccount("trabalho", "uuid-1")) }, { _ -> repository }).available())
    }

    @Test
    fun `olhar o team json nao cria o arquivo nem o deviceId`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-team").toFile()
        try {
            val file = File(dir, "team.json")
            assertNull(LocalTeamSettingsDataSource(file).peek())
            assertFalse(file.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `o desvio do relogio vem da saude do coletor e muda a presenca`() {
        val dir = Files.createTempDirectory("usage-monitor-tui-team").toFile()
        try {
            val health = LocalCollectorHealthDataSource(File(dir, "collector-health.json"))
            // O servidor está 10 min à frente: o "agora" dele passa da janela de 90 s da Ana.
            health.write(CollectorHealth(now.toEpochMilliseconds(), team = CollectorTeamHealth(active = true, clockOffsetMillis = 10.minutes.inWholeMilliseconds)))
            assertEquals(10.minutes.inWholeMilliseconds, health.read()?.team?.clockOffsetMillis)
            val repository = ReadOnlyTeamRepository(Result.success(snapshot))
            val reader = TuiTeamReader({ active }, { listOf(TuiTeamAccount("trabalho", "uuid-1")) }, { _ -> repository }, clockOffsetMillis = { health.read()?.team?.clockOffsetMillis ?: 0L })
            val team = reader.load(TuiTeamState(), now)!!
            assertEquals(now + 10.minutes, team.referenceNow)
            assertTrue(team.members.none { member -> member.online })
        } finally {
            dir.deleteRecursively()
        }
    }
}
