package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.data.dto.TeamIngestRequestDto
import com.usagemonitor.data.dto.TeamIngestResponseDto
import com.usagemonitor.data.dto.TeamPresenceRequestDto
import com.usagemonitor.data.dto.TeamPresenceResponseDto
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.repository.InMemoryTeamServerClockOffset
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val SERVER_A = "http://localhost:3000"
private const val SERVER_B = "http://localhost:4000"

private val LOCAL_NOW = Instant.fromEpochMilliseconds(1_800_000_000_000)

private val MEMBER = TeamMemberIdentity(
    deviceId = "device-1",
    alias = "edilson",
    hostName = "DESKTOP-A1"
)

private fun settings(serverUrl: String = SERVER_A) = TeamIntegrationSettings(
    enabled = true,
    serverUrl = serverUrl,
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "edilson",
    deviceId = "device-1"
)

private class FixedClock(private val value: Instant) : Clock {
    override fun now(): Instant = value
}

/** Fake do data source: nenhuma das chamadas abaixo toca a rede. */
private class FakePresenceDataSource(
    private var presenceResult: Result<TeamPresenceResponseDto>
) : RemoteTeamDataSource(HttpClient()) {
    var presenceCalls = 0
    var ingestCalls = 0
    val presenceUrls = mutableListOf<String>()
    var lastIngest: TeamIngestRequestDto? = null
    var lastPresence: TeamPresenceRequestDto? = null

    override suspend fun touchPresence(
        baseUrl: String,
        apiKey: String,
        request: TeamPresenceRequestDto
    ): TeamPresenceResponseDto {
        presenceCalls += 1
        presenceUrls += baseUrl
        lastPresence = request
        return presenceResult.getOrThrow()
    }

    override suspend fun pushIngest(
        baseUrl: String,
        apiKey: String,
        request: TeamIngestRequestDto
    ): TeamIngestResponseDto {
        ingestCalls += 1
        lastIngest = request
        return TeamIngestResponseDto()
    }
}

private fun notFound() = Result.failure<TeamPresenceResponseDto>(
    TeamServerException(statusCode = 404, message = "Rota nao encontrada.")
)

class TeamUsageRepositoryPresenceTest {

    @Test
    fun `a rota dedicada e usada quando o servidor a conhece`() = runTest {
        val serverNow = LOCAL_NOW.toEpochMilliseconds() + 4_000
        val remote = FakePresenceDataSource(
            Result.success(TeamPresenceResponseDto(lastSeenAt = serverNow))
        )
        val offset = InMemoryTeamServerClockOffset()
        val repository = TeamUsageRepositoryImpl(
            remoteDataSource = remote,
            settingsProvider = { settings() },
            serverClockOffset = offset,
            clock = FixedClock(LOCAL_NOW)
        )

        val receipt = repository.touchPresence(ACCOUNT_KEY, MEMBER).getOrThrow()

        assertEquals(1, remote.presenceCalls)
        assertEquals(0, remote.ingestCalls)
        assertEquals(Instant.fromEpochMilliseconds(serverNow), receipt.serverTimeAt)
        // A resposta do heartbeat e a unica fonte de medida do desvio.
        assertEquals(4_000L, offset.offsetMillis)
        assertEquals(ACCOUNT_KEY, remote.lastPresence?.accountKey)
        assertEquals("DESKTOP-A1", remote.lastPresence?.member?.hostName)
    }

    @Test
    fun `um 404 cai no ingest so com o membro`() = runTest {
        val remote = FakePresenceDataSource(notFound())
        val repository = TeamUsageRepositoryImpl(remote, { settings() })

        val receipt = repository.touchPresence(ACCOUNT_KEY, MEMBER).getOrThrow()

        assertEquals(1, remote.ingestCalls)
        val sent = remote.lastIngest
        assertEquals(ACCOUNT_KEY, sent?.accountKey)
        assertEquals("device-1", sent?.member?.deviceId)
        assertEquals("edilson", sent?.member?.alias)
        assertTrue(sent?.sessions.orEmpty().isEmpty())
        assertTrue(sent?.turns.orEmpty().isEmpty())
        // O ingest nao devolve relogio: sem medida, e nao com uma medida errada.
        assertNull(receipt.serverTimeAt)
    }

    @Test
    fun `depois do 404 a rota nova nao e tentada de novo`() = runTest {
        val remote = FakePresenceDataSource(notFound())
        val repository = TeamUsageRepositoryImpl(remote, { settings() })

        repository.touchPresence(ACCOUNT_KEY, MEMBER)
        repository.touchPresence(ACCOUNT_KEY, MEMBER)
        repository.touchPresence(ACCOUNT_KEY, MEMBER)

        // Sem o marcador seriam tres 404 — e um a cada 30s, para sempre.
        assertEquals(1, remote.presenceCalls)
        assertEquals(3, remote.ingestCalls)
    }

    @Test
    fun `trocar de servidor re-testa a rota`() = runTest {
        val remote = FakePresenceDataSource(notFound())
        var current = settings(SERVER_A)
        val repository = TeamUsageRepositoryImpl(remote, { current })

        repository.touchPresence(ACCOUNT_KEY, MEMBER)
        current = settings(SERVER_B)
        repository.touchPresence(ACCOUNT_KEY, MEMBER)

        assertEquals(2, remote.presenceCalls)
        assertEquals(listOf(SERVER_A, SERVER_B), remote.presenceUrls)
    }

    @Test
    fun `um 500 e falha de verdade e nao cai no ingest`() = runTest {
        val remote = FakePresenceDataSource(
            Result.failure(TeamServerException(statusCode = 500, message = "Erro interno."))
        )
        val repository = TeamUsageRepositoryImpl(remote, { settings() })

        val result = repository.touchPresence(ACCOUNT_KEY, MEMBER)

        assertTrue(result.isFailure)
        assertEquals(0, remote.ingestCalls)
    }

    @Test
    fun `um 401 e falha de verdade e nao cai no ingest`() = runTest {
        // Cair no caminho de compatibilidade aqui esconderia chave errada atras
        // de uma batida que parece ter funcionado.
        val remote = FakePresenceDataSource(
            Result.failure(TeamServerException(statusCode = 401, message = "Chave invalida."))
        )
        val repository = TeamUsageRepositoryImpl(remote, { settings() })

        val result = repository.touchPresence(ACCOUNT_KEY, MEMBER)

        assertTrue(result.isFailure)
        assertEquals(0, remote.ingestCalls)
    }

    @Test
    fun `falha de rede nao marca a rota como ausente`() = runTest {
        val remote = FakePresenceDataSource(Result.failure(IllegalStateException("sem rede")))
        val repository = TeamUsageRepositoryImpl(remote, { settings() })

        repository.touchPresence(ACCOUNT_KEY, MEMBER)
        repository.touchPresence(ACCOUNT_KEY, MEMBER)

        assertEquals(2, remote.presenceCalls)
        assertEquals(0, remote.ingestCalls)
    }

    @Test
    fun `integracao desligada falha sem tocar a rede`() = runTest {
        val remote = FakePresenceDataSource(Result.success(TeamPresenceResponseDto()))
        val repository = TeamUsageRepositoryImpl(remote, { TeamIntegrationSettings() })

        val result = repository.touchPresence(ACCOUNT_KEY, MEMBER)

        assertTrue(result.isFailure)
        assertEquals(0, remote.presenceCalls)
        assertEquals(0, remote.ingestCalls)
    }

    @Test
    fun `resposta sem relogio nao vira epoca zero`() = runTest {
        val remote = FakePresenceDataSource(Result.success(TeamPresenceResponseDto(lastSeenAt = 0L)))
        val offset = InMemoryTeamServerClockOffset()
        val repository = TeamUsageRepositoryImpl(
            remoteDataSource = remote,
            settingsProvider = { settings() },
            serverClockOffset = offset,
            clock = FixedClock(LOCAL_NOW)
        )

        val receipt = repository.touchPresence(ACCOUNT_KEY, MEMBER).getOrThrow()

        assertNull(receipt.serverTimeAt)
        assertEquals(0L, offset.offsetMillis)
    }
}
