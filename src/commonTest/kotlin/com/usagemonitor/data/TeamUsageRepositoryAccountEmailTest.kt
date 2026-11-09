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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EMAIL_ACCOUNT_KEY = "account-uuid-bbb"
private const val EMAIL_SERVER = "http://localhost:3000"
private const val EMAIL_ADDRESS = "ana@empresa.com"
private const val EMAIL_PROFILE = "p1"

private val EMAIL_NOW = Instant.fromEpochMilliseconds(1_800_000_000_000)

private val EMAIL_MEMBER = TeamMemberIdentity(deviceId = "device-1", alias = "ana", hostName = "DESKTOP-A1")

private class EmailFixedClock(private val value: Instant) : Clock {
    override fun now(): Instant = value
}

private fun emailTeamSettings() = TeamIntegrationSettings(
    enabled = true,
    serverUrl = EMAIL_SERVER,
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "ana",
    deviceId = "device-1",
    participatingProfileIds = setOf(EMAIL_PROFILE),
    accountApiKeys = mapOf(EMAIL_PROFILE to "chave-p1")
)

/** Fake do data source que registra o que sairia para a rede. Nenhuma chamada toca a rede. */
private class EmailPresenceDataSource(
    private val presenceResult: Result<TeamPresenceResponseDto>
) : RemoteTeamDataSource(HttpClient()) {
    var lastApiKey: String? = null
    var lastPresence: TeamPresenceRequestDto? = null
    var lastIngest: TeamIngestRequestDto? = null
    var ingestCalls = 0

    override suspend fun touchPresence(
        baseUrl: String,
        apiKey: String,
        request: TeamPresenceRequestDto
    ): TeamPresenceResponseDto {
        lastApiKey = apiKey
        lastPresence = request
        return presenceResult.getOrThrow()
    }

    override suspend fun pushIngest(
        baseUrl: String,
        apiKey: String,
        request: TeamIngestRequestDto
    ): TeamIngestResponseDto {
        ingestCalls += 1
        lastApiKey = apiKey
        lastIngest = request
        return TeamIngestResponseDto()
    }
}

class TeamUsageRepositoryAccountEmailTest {

    @Test
    fun `presenca com e-mail leva o e-mail ao data source`() = runTest {
        val remote = EmailPresenceDataSource(Result.success(TeamPresenceResponseDto()))
        val repository = TeamUsageRepositoryImpl(
            remoteDataSource = remote,
            settingsProvider = { emailTeamSettings() },
            serverClockOffset = InMemoryTeamServerClockOffset(),
            clock = EmailFixedClock(EMAIL_NOW)
        )

        repository.touchPresence(EMAIL_ACCOUNT_KEY, EMAIL_ADDRESS, EMAIL_MEMBER).getOrThrow()

        assertEquals(EMAIL_ADDRESS, remote.lastPresence?.accountEmail)
        assertEquals("chave-de-time-com-tamanho-suficiente", remote.lastApiKey)
    }

    @Test
    fun `presenca com e-mail e perfil usa a chave da conta e leva o e-mail`() = runTest {
        val remote = EmailPresenceDataSource(Result.success(TeamPresenceResponseDto()))
        val repository = TeamUsageRepositoryImpl(
            remoteDataSource = remote,
            settingsProvider = { emailTeamSettings() },
            serverClockOffset = InMemoryTeamServerClockOffset(),
            clock = EmailFixedClock(EMAIL_NOW)
        )

        repository.touchPresence(EMAIL_ACCOUNT_KEY, EMAIL_ADDRESS, EMAIL_MEMBER, EMAIL_PROFILE).getOrThrow()

        assertEquals("chave-p1", remote.lastApiKey)
        assertEquals(EMAIL_ADDRESS, remote.lastPresence?.accountEmail)
    }

    @Test
    fun `404 na presenca com e-mail leva o e-mail ao ingest de compatibilidade`() = runTest {
        val remote = EmailPresenceDataSource(
            Result.failure(TeamServerException(statusCode = 404, message = "Rota nao encontrada."))
        )
        val repository = TeamUsageRepositoryImpl(
            remoteDataSource = remote,
            settingsProvider = { emailTeamSettings() },
            serverClockOffset = InMemoryTeamServerClockOffset(),
            clock = EmailFixedClock(EMAIL_NOW)
        )

        repository.touchPresence(EMAIL_ACCOUNT_KEY, EMAIL_ADDRESS, EMAIL_MEMBER, EMAIL_PROFILE).getOrThrow()

        assertEquals(1, remote.ingestCalls)
        assertEquals(EMAIL_ADDRESS, remote.lastIngest?.accountEmail)
        assertEquals("chave-p1", remote.lastApiKey)
        assertTrue(remote.lastIngest?.turns.orEmpty().isEmpty())
    }
}
