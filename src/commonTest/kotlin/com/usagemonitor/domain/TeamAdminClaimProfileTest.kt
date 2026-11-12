package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.usecase.ClaimTeamKeyForAccountUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException("nao usado no teste"))

/**
 * Implementa só as abstratas. Quem não sobrescreve a forma com perfil cai no default
 * da interface, que descarta o perfil e chama a forma de 2 argumentos.
 */
private open class BaseOnlyAdminRepository : TeamAdminRepository {
    val calls = mutableListOf<String>()

    override suspend fun validateToken(): Result<Unit> = unsupported()

    override suspend fun listKeys(): Result<List<TeamKeyEntry>> = unsupported()

    override suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry> = unsupported()

    override suspend fun updateKey(id: String, label: String?, maxAccounts: Int?): Result<TeamKeyEntry> = unsupported()

    override suspend fun regenerateKey(id: String): Result<TeamKeyEntry> = unsupported()

    override suspend fun revokeKey(id: String): Result<TeamKeyEntry> = unsupported()

    override suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry> = unsupported()

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> = unsupported()

    override suspend fun removeSession(accountKey: String, deviceId: String, sessionId: String): Result<Unit> =
        unsupported()

    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> = unsupported()

    override suspend fun fetchBlockedAccounts(): Result<List<TeamBlockedAccount>> = unsupported()

    override suspend fun unblockAccount(accountKey: String): Result<List<TeamBlockedAccount>> = unsupported()

    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> = unsupported()

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> = unsupported()

    override suspend fun verifyKeyForAccount(
        accountKey: String,
        accountEmail: String?
    ): Result<TeamKeyVerification> = unsupported()

    override suspend fun claimKeyForAccount(
        accountKey: String,
        accountEmail: String?
    ): Result<TeamKeyVerification> {
        calls += "claim2 $accountKey $accountEmail"
        return unsupported()
    }
}

/** Sobrescreve a forma com perfil para registrar o que o caso de uso repassou. */
private class RecordingClaimAdminRepository : BaseOnlyAdminRepository() {
    override suspend fun claimKeyForAccount(
        accountKey: String,
        accountEmail: String?,
        profileId: String?
    ): Result<TeamKeyVerification> {
        calls += "claim3 $accountKey $accountEmail perfil=$profileId"
        return unsupported()
    }
}

class TeamAdminClaimProfileTest {

    @Test
    fun `caso de uso de claim repassa e-mail e perfil ao repositorio`() = runTest {
        val repository = RecordingClaimAdminRepository()

        ClaimTeamKeyForAccountUseCase(repository)("conta-1", "ana@empresa.com", "p1")

        assertEquals(listOf("claim3 conta-1 ana@empresa.com perfil=p1"), repository.calls)
    }

    @Test
    fun `caso de uso de claim sem perfil repassa null como perfil`() = runTest {
        val repository = RecordingClaimAdminRepository()

        ClaimTeamKeyForAccountUseCase(repository)("conta-1")

        assertEquals(listOf("claim3 conta-1 null perfil=null"), repository.calls)
    }

    @Test
    fun `default de claim com perfil descarta o perfil e cai na forma de 2 argumentos`() = runTest {
        val repository = BaseOnlyAdminRepository()

        repository.claimKeyForAccount("conta-1", "ana@empresa.com", "p1")

        assertEquals(listOf("claim2 conta-1 ana@empresa.com"), repository.calls)
    }
}
