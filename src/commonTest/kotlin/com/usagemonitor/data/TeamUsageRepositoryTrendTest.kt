package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.data.dto.TeamMemberDto
import com.usagemonitor.data.dto.TeamTrendResponseDto
import com.usagemonitor.data.dto.TeamTrendRowDto
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.repository.InMemoryTeamServerClockOffset
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val SERVER_A = "http://localhost:3000"
private const val SERVER_B = "http://localhost:4000"

private fun trendSettings(serverUrl: String = SERVER_A) = TeamIntegrationSettings(
    enabled = true,
    serverUrl = serverUrl,
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "edilson",
    deviceId = "device-1"
)

private class FakeTrendDataSource(
    var result: Result<TeamTrendResponseDto>
) : RemoteTeamDataSource(HttpClient()) {

    var calls = 0
    var lastDays: Int? = null

    override suspend fun fetchTeamTrend(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        days: Int
    ): TeamTrendResponseDto {
        calls++
        lastDays = days
        return result.getOrThrow()
    }
}

class TeamUsageRepositoryTrendTest {

    @Test
    fun `a successful read maps the rows`() = runTest {
        val dataSource = FakeTrendDataSource(
            Result.success(
                TeamTrendResponseDto(
                    members = listOf(TeamMemberDto(deviceId = "device-1", alias = "edilson")),
                    rows = listOf(
                        TeamTrendRowDto(
                            deviceId = "device-1",
                            dayStartMillis = 1_800_000_000_000,
                            model = "claude-opus-5",
                            turnCount = 3,
                            inputTokens = 10
                        )
                    )
                )
            )
        )
        val repository = buildRepository(dataSource)

        val data = repository.fetchTrend(ACCOUNT_KEY, days = 30).getOrThrow()

        assertNotNull(data)
        assertEquals(30, dataSource.lastDays)
        assertEquals("edilson", data.members.single().alias)
        assertEquals(3, data.rows.single().turnCount)
    }

    /**
     * `404` significa "este servidor não conhece a rota" e não pode virar erro:
     * nem todo time atualiza servidor e app juntos.
     */
    @Test
    fun `a 404 becomes an unavailable trend`() = runTest {
        val dataSource = FakeTrendDataSource(
            Result.failure(TeamServerException(statusCode = 404, message = "não encontrado"))
        )
        val repository = buildRepository(dataSource)

        assertNull(repository.fetchTrend(ACCOUNT_KEY, days = 30).getOrThrow())
    }

    /** Sem lembrar a ausência, um servidor antigo pagaria 404 a cada abertura. */
    @Test
    fun `the missing route is remembered per url`() = runTest {
        val dataSource = FakeTrendDataSource(
            Result.failure(TeamServerException(statusCode = 404, message = "não encontrado"))
        )
        var settings = trendSettings(SERVER_A)
        val repository = TeamUsageRepositoryImpl(
            remoteDataSource = dataSource,
            settingsProvider = { settings },
            serverClockOffset = InMemoryTeamServerClockOffset()
        )

        repository.fetchTrend(ACCOUNT_KEY, days = 30)
        repository.fetchTrend(ACCOUNT_KEY, days = 30)
        assertEquals(1, dataSource.calls)

        // Servidor diferente é outra instalação: a ausência não se transfere.
        settings = trendSettings(SERVER_B)
        repository.fetchTrend(ACCOUNT_KEY, days = 30)
        assertEquals(2, dataSource.calls)
    }

    /** Chave errada não pode virar "servidor antigo". */
    @Test
    fun `a 401 stays a failure`() = runTest {
        val dataSource = FakeTrendDataSource(
            Result.failure(TeamServerException(statusCode = 401, message = "chave inválida"))
        )
        val repository = buildRepository(dataSource)

        val result = repository.fetchTrend(ACCOUNT_KEY, days = 30)

        assertTrue(result.isFailure)
        // E a rota não é marcada como ausente: a próxima leitura tenta de novo.
        repository.fetchTrend(ACCOUNT_KEY, days = 30)
        assertEquals(2, dataSource.calls)
    }

    private fun buildRepository(dataSource: FakeTrendDataSource): TeamUsageRepositoryImpl {
        return TeamUsageRepositoryImpl(
            remoteDataSource = dataSource,
            settingsProvider = { trendSettings() },
            serverClockOffset = InMemoryTeamServerClockOffset()
        )
    }
}
