package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.data.dto.TeamAccountDeletionDto
import com.usagemonitor.data.dto.TeamSessionDetailResponseDto
import com.usagemonitor.data.dto.TeamSessionRowDto
import com.usagemonitor.data.dto.TeamVerificationDto
import com.usagemonitor.data.repository.TeamAdminRepositoryImpl
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val CONFIGURED = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://localhost:3000",
    apiKey = "chave-de-time-com-tamanho-suficiente",
    alias = "edilson",
    deviceId = "device-1"
)

private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val ACCOUNT_EMAIL = "pessoa@empresa.com"

/** Mesmas configurações, mais o token que liga o modo administrador. */
private val ADMIN = CONFIGURED.copy(adminToken = "token-de-admin-com-tamanho-suficiente")

/** Fake do data source: nenhuma das chamadas abaixo toca a rede. */
private class FakeRemoteTeamDataSource(
    private val claimResult: Result<TeamVerificationDto>,
    private val verifyResult: Result<TeamVerificationDto>,
    private val deleteMemberResult: Result<Unit> =
        Result.failure(IllegalStateException("nao deveria chamar")),
    private val deleteSessionResult: Result<Unit> =
        Result.failure(IllegalStateException("nao deveria chamar")),
    private val sessionDetailResult: Result<TeamSessionDetailResponseDto> =
        Result.failure(IllegalStateException("nao deveria chamar")),
    private val deleteAccountResult: Result<TeamAccountDeletionDto> =
        Result.failure(IllegalStateException("nao deveria chamar"))
) : RemoteTeamDataSource(HttpClient()) {
    var claimCalls = 0
    var verifyCalls = 0
    var lastClaimEmail: String? = null
    var lastVerifyEmail: String? = null
    var deleteMemberCalls = 0
    var deleteSessionCalls = 0
    var deleteAccountCalls = 0
    var sessionDetailCalls = 0
    var lastDeleteMemberCredential: TeamCredential? = null
    var lastSessionDetailCredential: TeamCredential? = null
    var lastDeleteSessionAdminToken: String? = null
    var lastDeleteSessionTarget: Triple<String, String, String>? = null

    override suspend fun deleteMember(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        deviceId: String
    ) {
        deleteMemberCalls += 1
        lastDeleteMemberCredential = credential
        deleteMemberResult.getOrThrow()
    }

    override suspend fun deleteAccount(
        baseUrl: String,
        adminToken: String,
        accountKey: String
    ): TeamAccountDeletionDto {
        deleteAccountCalls += 1
        return deleteAccountResult.getOrThrow()
    }

    override suspend fun deleteSession(
        baseUrl: String,
        adminToken: String,
        accountKey: String,
        deviceId: String,
        sessionId: String
    ) {
        deleteSessionCalls += 1
        lastDeleteSessionAdminToken = adminToken
        lastDeleteSessionTarget = Triple(accountKey, deviceId, sessionId)
        deleteSessionResult.getOrThrow()
    }

    override suspend fun fetchSessionDetail(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): TeamSessionDetailResponseDto {
        sessionDetailCalls += 1
        lastSessionDetailCredential = credential
        return sessionDetailResult.getOrThrow()
    }

    override suspend fun claimKey(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        accountEmail: String?
    ): TeamVerificationDto {
        claimCalls += 1
        lastClaimEmail = accountEmail
        return claimResult.getOrThrow()
    }

    override suspend fun verifyKey(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        accountEmail: String?
    ): TeamVerificationDto {
        verifyCalls += 1
        lastVerifyEmail = accountEmail
        return verifyResult.getOrThrow()
    }
}

class TeamAdminRepositoryImplTest {

    @Test
    fun `vincula quando o servidor tem a rota`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.success(TeamVerificationDto(authorized = true, claimed = true)),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { CONFIGURED }

        val result = repository.claimKeyForAccount(ACCOUNT_KEY, ACCOUNT_EMAIL)

        assertEquals(true, result.getOrThrow().claimed)
        assertEquals(1, remote.claimCalls)
        assertEquals(0, remote.verifyCalls)
        // O e-mail viaja no vínculo: o servidor confere a conta contra o rótulo
        // da chave, e sem ele o botão aprovaria uma conta que o envio recusa.
        assertEquals(ACCOUNT_EMAIL, remote.lastClaimEmail)
    }

    @Test
    fun `cai na verificacao contra servidor sem a rota de vinculo`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(TeamServerException(404, "rota inexistente")),
            verifyResult = Result.success(TeamVerificationDto(authorized = true, claimed = false))
        )
        val repository = TeamAdminRepositoryImpl(remote) { CONFIGURED }

        val result = repository.claimKeyForAccount(ACCOUNT_KEY, ACCOUNT_EMAIL)

        // Servidor 0.3.0 não conhece o vínculo explícito; informar ainda é melhor
        // que reprovar uma configuração correta.
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().claimed)
        assertEquals(1, remote.verifyCalls)
        // O e-mail acompanha a queda para a verificação: perdê-lo no caminho
        // faria a consulta responder por outra pergunta que não a do vínculo.
        assertEquals(ACCOUNT_EMAIL, remote.lastVerifyEmail)
    }

    @Test
    fun `servidor sem a rota de verificacao nao reprova a configuracao`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(TeamServerException(404, "rota inexistente")),
            verifyResult = Result.failure(TeamServerException(404, "rota inexistente"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { CONFIGURED }

        val result = repository.claimKeyForAccount(ACCOUNT_KEY)

        // Servidor 0.2.x: o app não pode declarar inválida uma configuração
        // correta só porque o servidor da empresa ficou para trás.
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow().authorized)
    }

    @Test
    fun `403 continua sendo falha`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(TeamServerException(403, "conta de outra chave")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { CONFIGURED }

        val result = repository.claimKeyForAccount(ACCOUNT_KEY)

        assertTrue(result.isFailure)
        assertEquals(0, remote.verifyCalls)
    }

    @Test
    fun `administrador remove integrante com token de admin mesmo tendo chave de time`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar")),
            deleteMemberResult = Result.success(Unit)
        )
        val repository = TeamAdminRepositoryImpl(remote) { ADMIN }

        val result = repository.removeMember(ACCOUNT_KEY, "device-1")

        assertTrue(result.isSuccess)
        assertEquals(1, remote.deleteMemberCalls)
        assertEquals(
            TeamCredential.AdminToken(ADMIN.adminToken),
            remote.lastDeleteMemberCredential
        )
    }

    @Test
    fun `administrador exclui sessao com token de admin e alvo completo`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar")),
            deleteSessionResult = Result.success(Unit)
        )
        val repository = TeamAdminRepositoryImpl(remote) { ADMIN }

        val result = repository.removeSession(ACCOUNT_KEY, "device-1", "session-1")

        assertTrue(result.isSuccess)
        assertEquals(1, remote.deleteSessionCalls)
        assertEquals(ADMIN.adminToken, remote.lastDeleteSessionAdminToken)
        assertEquals(
            Triple(ACCOUNT_KEY, "device-1", "session-1"),
            remote.lastDeleteSessionTarget
        )
    }

    @Test
    fun `administrador le detalhe com token de admin mesmo tendo chave de time`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar")),
            sessionDetailResult = Result.success(
                TeamSessionDetailResponseDto(
                    session = TeamSessionRowDto(
                        deviceId = "device-1",
                        sessionId = "session-1"
                    )
                )
            )
        )
        val repository = TeamAdminRepositoryImpl(remote) { ADMIN }

        val result = repository.fetchSessionDetail(ACCOUNT_KEY, "device-1", "session-1")

        assertTrue(result.isSuccess)
        assertEquals(1, remote.sessionDetailCalls)
        assertEquals(
            TeamCredential.AdminToken(ADMIN.adminToken),
            remote.lastSessionDetailCredential
        )
    }

    @Test
    fun `404 ao excluir sessao exige servidor 0_8_0`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar")),
            deleteSessionResult = Result.failure(TeamServerException(404, "rota inexistente"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { ADMIN }

        val result = repository.removeSession(ACCOUNT_KEY, "device-1", "session-1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("0.8.0"))
    }

    @Test
    fun `excluir sessao exige modo administrador`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { CONFIGURED }

        val result = repository.removeSession(ACCOUNT_KEY, "device-1", "session-1")

        assertTrue(result.isFailure)
        assertEquals(0, remote.deleteSessionCalls)
    }

    @Test
    fun `apaga a conta e devolve o recibo`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar")),
            deleteAccountResult = Result.success(
                TeamAccountDeletionDto(
                    deletedTurns = 1240,
                    deletedSessions = 8,
                    deletedMembers = 3,
                    unlinkedKeys = 1
                )
            )
        )
        val repository = TeamAdminRepositoryImpl(remote) { ADMIN }

        val report = repository.deleteAccount(ACCOUNT_KEY).getOrThrow()

        assertEquals(1240, report.deletedTurns)
        assertEquals(3, report.deletedMembers)
        assertEquals(1, report.unlinkedKeys)
    }

    @Test
    fun `404 ao apagar conta vira pedido de atualizar o servidor`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar")),
            deleteAccountResult = Result.failure(TeamServerException(404, "rota inexistente"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { ADMIN }

        val result = repository.deleteAccount(ACCOUNT_KEY)

        // A rota é idempotente: 404 só pode ser servidor anterior a ela. E aqui
        // não há fallback — nenhuma outra rota apaga uma conta.
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("0.5.0"))
    }

    @Test
    fun `apagar conta exige modo administrador`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { CONFIGURED }

        val result = repository.deleteAccount(ACCOUNT_KEY)

        assertTrue(result.isFailure)
        assertEquals(0, remote.deleteAccountCalls)
    }

    @Test
    fun `sem servidor configurado nem tenta a rede`() = runTest {
        val remote = FakeRemoteTeamDataSource(
            claimResult = Result.failure(IllegalStateException("nao deveria chamar")),
            verifyResult = Result.failure(IllegalStateException("nao deveria chamar"))
        )
        val repository = TeamAdminRepositoryImpl(remote) { TeamIntegrationSettings() }

        val result = repository.claimKeyForAccount(ACCOUNT_KEY)

        assertTrue(result.isFailure)
        assertEquals(0, remote.claimCalls)
    }
}
