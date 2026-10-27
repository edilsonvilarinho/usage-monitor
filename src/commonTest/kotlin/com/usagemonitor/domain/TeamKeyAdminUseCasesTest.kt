package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.usecase.ClaimTeamKeyForAccountUseCase
import com.usagemonitor.domain.usecase.CreateTeamKeyUseCase
import com.usagemonitor.domain.usecase.RevokeTeamKeyUseCase
import com.usagemonitor.domain.usecase.UpdateTeamKeyUseCase
import com.usagemonitor.domain.usecase.ValidateAdminTokenUseCase
import com.usagemonitor.domain.usecase.VerifyTeamKeyForAccountUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** O que cada caso de uso repassa ao repositório: argumento por argumento, defaults incluídos. */
private class RecordingAdminRepository : TeamAdminRepository {
    val calls = mutableListOf<String>()
    private val key = TeamKeyEntry(id = "k1", label = "time", key = "", keyPrefix = "tk_", maxAccounts = 1)
    private val verification = TeamKeyVerification(authorized = true, claimed = true)

    override suspend fun validateToken(): Result<Unit> = Result.success(Unit).also { calls += "validate" }
    override suspend fun listKeys(): Result<List<TeamKeyEntry>> = Result.success(listOf(key))
    override suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry> =
        Result.success(key).also { calls += "create $label $maxAccounts" }
    override suspend fun updateKey(id: String, label: String?, maxAccounts: Int?): Result<TeamKeyEntry> =
        Result.success(key).also { calls += "update $id $label $maxAccounts" }
    override suspend fun regenerateKey(id: String): Result<TeamKeyEntry> = Result.success(key)
    override suspend fun revokeKey(id: String): Result<TeamKeyEntry> = Result.success(key).also { calls += "revoke $id" }
    override suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry> = Result.success(key)
    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeSession(accountKey: String, deviceId: String, sessionId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> = Result.success(TeamAccountDeletion(0, 0, 0, 0))
    override suspend fun fetchBlockedAccounts(): Result<List<TeamBlockedAccount>> = Result.success(emptyList())
    override suspend fun unblockAccount(accountKey: String): Result<List<TeamBlockedAccount>> = Result.success(emptyList())
    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> = Result.success(emptyList())
    override suspend fun fetchSessionDetail(accountKey: String, deviceId: String, sessionId: String): Result<CliSessionDetail?> =
        Result.success(null)
    override suspend fun verifyKeyForAccount(accountKey: String, accountEmail: String?): Result<TeamKeyVerification> =
        Result.success(verification).also { calls += "verify $accountKey $accountEmail" }
    override suspend fun claimKeyForAccount(accountKey: String, accountEmail: String?): Result<TeamKeyVerification> =
        Result.success(verification).also { calls += "claim $accountKey $accountEmail" }
}

class TeamKeyAdminUseCasesTest {

    @Test
    fun `each use case forwards its arguments and defaults`() = runTest {
        val repository = RecordingAdminRepository()

        ValidateAdminTokenUseCase(repository)()
        CreateTeamKeyUseCase(repository)("time")
        CreateTeamKeyUseCase(repository)("time", maxAccounts = 3)
        UpdateTeamKeyUseCase(repository)("k1", label = "  novo ")
        RevokeTeamKeyUseCase(repository)("k1")
        VerifyTeamKeyForAccountUseCase(repository)("conta-1")
        ClaimTeamKeyForAccountUseCase(repository)("conta-1", "a@b.com")

        assertEquals(
            listOf(
                "validate",
                "create time 1",
                "create time 3",
                "update k1 novo null",
                "revoke k1",
                "verify conta-1 null",
                "claim conta-1 a@b.com"
            ),
            repository.calls
        )
    }
}
