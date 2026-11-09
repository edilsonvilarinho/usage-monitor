package com.usagemonitor

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.repository.TeamServerClockOffset
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import kotlin.time.Instant

/**
 * Id que o gerador nunca produz (o gerado é `team-<UUID>`). Sem time resolvido, o registro o trata
 * como time não configurado: a falha é a de sem configuração, e nenhuma requisição sai.
 */
internal const val UNRESOLVED_TEAM_ID = "team-unresolved"

/** Uso do time que [selectedTeamId] devolve a cada chamada; `null` vai para o id não gerável. */
class RoutedTeamUsageRepository(
    private val registry: TeamRepositoryRegistry,
    private val selectedTeamId: () -> String?
) : TeamUsageRepository {

    private fun current(): TeamUsageRepository = registry.usageRepository(selectedTeamId() ?: UNRESOLVED_TEAM_ID)

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> = current().push(payload)

    override suspend fun push(payload: TeamIngestPayload, profileId: String?): Result<TeamIngestReceipt> =
        current().push(payload, profileId)

    override suspend fun touchPresence(accountKey: String, member: TeamMemberIdentity): Result<TeamPresenceReceipt> =
        current().touchPresence(accountKey, member)

    override suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> = current().touchPresence(accountKey, member, profileId)

    override suspend fun touchPresence(
        accountKey: String,
        accountEmail: String?,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt> = current().touchPresence(accountKey, accountEmail, member)

    override suspend fun touchPresence(
        accountKey: String,
        accountEmail: String?,
        member: TeamMemberIdentity,
        profileId: String?
    ): Result<TeamPresenceReceipt> = current().touchPresence(accountKey, accountEmail, member, profileId)

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> =
        current().fetch(accountKey, cutoffMillis)

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> =
        current().fetchTrend(accountKey, days)

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> = current().fetchSessionDetail(accountKey, deviceId, sessionId)

    override suspend fun checkConnection(): Result<Unit> = current().checkConnection()
}

/** Administração do time que [selectedTeamId] devolve a cada chamada. */
class RoutedTeamAdminRepository(
    private val registry: TeamRepositoryRegistry,
    private val selectedTeamId: () -> String?
) : TeamAdminRepository {

    private fun current(): TeamAdminRepository = registry.adminRepository(selectedTeamId() ?: UNRESOLVED_TEAM_ID)

    override suspend fun validateToken(): Result<Unit> = current().validateToken()

    override suspend fun listKeys(): Result<List<TeamKeyEntry>> = current().listKeys()

    override suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry> =
        current().createKey(label, maxAccounts)

    override suspend fun updateKey(id: String, label: String?, maxAccounts: Int?): Result<TeamKeyEntry> =
        current().updateKey(id, label, maxAccounts)

    override suspend fun regenerateKey(id: String): Result<TeamKeyEntry> = current().regenerateKey(id)

    override suspend fun revokeKey(id: String): Result<TeamKeyEntry> = current().revokeKey(id)

    override suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry> =
        current().unclaimAccount(id, accountKey)

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> =
        current().removeMember(accountKey, deviceId)

    override suspend fun removeSession(accountKey: String, deviceId: String, sessionId: String): Result<Unit> =
        current().removeSession(accountKey, deviceId, sessionId)

    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> =
        current().deleteAccount(accountKey)

    override suspend fun fetchBlockedAccounts(): Result<List<TeamBlockedAccount>> = current().fetchBlockedAccounts()

    override suspend fun unblockAccount(accountKey: String): Result<List<TeamBlockedAccount>> =
        current().unblockAccount(accountKey)

    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> =
        current().fetchOverview(cutoffMillis)

    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> = current().fetchSessionDetail(accountKey, deviceId, sessionId)

    override suspend fun verifyKeyForAccount(accountKey: String, accountEmail: String?): Result<TeamKeyVerification> =
        current().verifyKeyForAccount(accountKey, accountEmail)

    override suspend fun claimKeyForAccount(accountKey: String, accountEmail: String?): Result<TeamKeyVerification> =
        current().claimKeyForAccount(accountKey, accountEmail)

    override suspend fun claimKeyForAccount(
        accountKey: String,
        accountEmail: String?,
        profileId: String?
    ): Result<TeamKeyVerification> = current().claimKeyForAccount(accountKey, accountEmail, profileId)
}

/** Offset de relógio do time que [selectedTeamId] devolve; cada time mede o próprio servidor. */
class RoutedTeamServerClockOffset(
    private val registry: TeamRepositoryRegistry,
    private val selectedTeamId: () -> String?
) : TeamServerClockOffset {

    private fun current(): TeamServerClockOffset = registry.serverClockOffset(selectedTeamId() ?: UNRESOLVED_TEAM_ID)

    override val offsetMillis: Long
        get() = current().offsetMillis

    override fun record(serverNow: Instant, localNow: Instant) {
        current().record(serverNow, localNow)
    }
}
