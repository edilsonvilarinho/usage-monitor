package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val SERVER_A = "http://localhost:3000"
private const val TEAM_KEY = "chave-de-time-com-tamanho-suficiente"

/** Sem chave de time: só as contas participantes têm chave própria. */
private fun semChaveDeTime(participantes: Map<String, String>) = TeamIntegrationSettings(
    enabled = true,
    serverUrl = SERVER_A,
    apiKey = "",
    alias = "edilson",
    deviceId = "device-1",
    participatingProfileIds = participantes.keys,
    accountApiKeys = participantes
)

/** Com chave de time e as mesmas contas participantes com chave própria. */
private fun comChaveDeTime(participantes: Map<String, String>) = semChaveDeTime(participantes).copy(apiKey = TEAM_KEY)

/** Fake do data source: registra a credencial da leitura e falha de propósito, sem tocar a rede. */
private class FakeReadDataSource : RemoteTeamDataSource(HttpClient()) {
    val credentials = mutableListOf<TeamCredential>()

    override suspend fun fetchTeam(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        sinceEpochMillis: Long?
    ): TeamSnapshotDto {
        credentials += credential
        throw IllegalStateException("fake: so registra a credencial")
    }
}

class TeamUsageRepositoryReadCredentialTest {

    @Test
    fun `sem chave de time a leitura usa a chave propria da conta participante`() = runTest {
        val remote = FakeReadDataSource()
        val repository = TeamUsageRepositoryImpl(remote, { semChaveDeTime(mapOf("p1" to "chave-p1")) })

        repository.fetch(ACCOUNT_KEY, cutoffMillis = null)

        assertEquals(TeamCredential.TeamKey("chave-p1"), remote.credentials.single())
    }

    @Test
    fun `sem chave de time a leitura usa a primeira chave propria em ordem alfabetica`() = runTest {
        val remote = FakeReadDataSource()
        val settings = semChaveDeTime(mapOf("p2" to "chave-p2", "p1" to "chave-p1"))
        val repository = TeamUsageRepositoryImpl(remote, { settings })

        repository.fetch(ACCOUNT_KEY, cutoffMillis = null)

        assertEquals(TeamCredential.TeamKey("chave-p1"), remote.credentials.single())
    }

    @Test
    fun `com chave de time a leitura usa a chave de time mesmo com chave propria da conta`() = runTest {
        val remote = FakeReadDataSource()
        val repository = TeamUsageRepositoryImpl(remote, { comChaveDeTime(mapOf("p1" to "chave-p1")) })

        repository.fetch(ACCOUNT_KEY, cutoffMillis = null)

        assertEquals(TeamCredential.TeamKey(TEAM_KEY), remote.credentials.single())
    }
}
