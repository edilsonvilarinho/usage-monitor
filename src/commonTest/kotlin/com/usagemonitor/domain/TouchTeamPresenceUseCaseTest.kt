package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.domain.usecase.TouchTeamPresenceUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val MEMBER = TeamMemberIdentity(deviceId = "device-1", alias = "Ana", hostName = "DESKTOP-A1")

private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException("nao usado no teste"))

/**
 * Implementa só as abstratas: as formas com perfil ou e-mail caem nos defaults da interface,
 * e o que chega a `calls` é o que sobrou na forma de 2 argumentos.
 */
private open class BaseOnlyUsageRepository : TeamUsageRepository {
    val calls = mutableListOf<String>()

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        calls += "push ${payload.accountKey}"
        return Result.success(TeamIngestReceipt())
    }

    override suspend fun touchPresence(accountKey: String, member: TeamMemberIdentity): Result<TeamPresenceReceipt> {
        calls += "presence $accountKey"
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> = unsupported()

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> = unsupported()

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> = unsupported()

    override suspend fun checkConnection(): Result<Unit> = Result.success(Unit)
}

/** Sobrescreve as formas com perfil ou e-mail, para registrar exatamente o que o caso de uso repassou. */
private class RecordingPresenceRepository : BaseOnlyUsageRepository() {
    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> {
        calls += "member $accountKey perfil=$profileId"
        return Result.success(TeamPresenceReceipt())
    }

    override suspend fun touchPresence(
        accountKey: String,
        accountEmail: String?,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> {
        calls += "email $accountKey $accountEmail perfil=$profileId"
        return Result.success(TeamPresenceReceipt())
    }
}

class TouchTeamPresenceUseCaseTest {

    @Test
    fun `sem e-mail o perfil chega ao repositorio`() = runTest {
        val repository = RecordingPresenceRepository()

        TouchTeamPresenceUseCase(repository)("conta-1", MEMBER, "p1")

        assertEquals(listOf("member conta-1 perfil=p1"), repository.calls)
    }

    @Test
    fun `com e-mail o e-mail e o perfil chegam ao repositorio`() = runTest {
        val repository = RecordingPresenceRepository()

        TouchTeamPresenceUseCase(repository)("conta-1", "ana@empresa.com", MEMBER, "p1")

        assertEquals(listOf("email conta-1 ana@empresa.com perfil=p1"), repository.calls)
    }

    @Test
    fun `sem perfil o caso de uso repassa null`() = runTest {
        val repository = RecordingPresenceRepository()

        TouchTeamPresenceUseCase(repository)("conta-1", "ana@empresa.com", MEMBER)
        TouchTeamPresenceUseCase(repository)("conta-1", MEMBER)

        assertEquals(
            listOf("email conta-1 ana@empresa.com perfil=null", "member conta-1 perfil=null"),
            repository.calls
        )
    }

    @Test
    fun `default de touchPresence com perfil descarta o perfil e cai na forma de 2 argumentos`() = runTest {
        val repository = BaseOnlyUsageRepository()

        repository.touchPresence("conta-1", MEMBER, "p1")

        assertEquals(listOf("presence conta-1"), repository.calls)
    }

    @Test
    fun `default de touchPresence com e-mail descarta o e-mail`() = runTest {
        val repository = BaseOnlyUsageRepository()

        repository.touchPresence("conta-1", "ana@empresa.com", MEMBER)

        assertEquals(listOf("presence conta-1"), repository.calls)
    }

    @Test
    fun `default de touchPresence com e-mail e perfil descarta os dois`() = runTest {
        val repository = BaseOnlyUsageRepository()

        repository.touchPresence("conta-1", "ana@empresa.com", MEMBER, "p1")

        assertEquals(listOf("presence conta-1"), repository.calls)
    }
}
