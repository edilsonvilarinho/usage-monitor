package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class CountingTeamRepository : TeamUsageRepository {
    val pushed = mutableListOf<TeamIngestPayload>()

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        pushed += payload
        return Result.success(TeamIngestReceipt())
    }

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        return Result.success(TeamUsageSnapshot())
    }

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> {
        return Result.success(null)
    }

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> {
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> {
        return Result.success(null)
    }
}

private val EMPTY_PAYLOAD = TeamIngestPayload(
    accountKey = "account-uuid-aaa",
    member = TeamMemberIdentity(deviceId = "device-1", alias = "edilson"),
    sessions = emptyList(),
    turns = emptyList()
)

class PushTeamUsageUseCaseTest {

    @Test
    fun `lote sem turnos nao gera requisicao`() = runTest {
        val repository = CountingTeamRepository()

        val result = PushTeamUsageUseCase(repository)(EMPTY_PAYLOAD)

        assertTrue(result.isSuccess)
        assertTrue(repository.pushed.isEmpty())
    }

    @Test
    fun `force envia o lote vazio para gravar so a identidade`() = runTest {
        val repository = CountingTeamRepository()

        // O servidor grava o apelido dentro do ingest: sem esta requisição, um
        // apelido novo só chegaria ao time junto do próximo lote de turnos.
        val result = PushTeamUsageUseCase(repository)(EMPTY_PAYLOAD, force = true)

        assertTrue(result.isSuccess)
        assertEquals("edilson", repository.pushed.single().member.alias)
    }
}
