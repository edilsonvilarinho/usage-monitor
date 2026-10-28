package com.usagemonitor.terminal

import com.usagemonitor.presentation.viewmodel.teamConnectionFailureOf
import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.entity.serverReferenceNow
import com.usagemonitor.domain.entity.toTeamPresence
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.presentation.terminal.TuiTeam
import com.usagemonitor.presentation.terminal.TuiTeamMember
import com.usagemonitor.presentation.terminal.TuiTeamState
import com.usagemonitor.presentation.viewmodel.isConnectivityFailure
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.prefs.Preferences
import kotlin.time.Instant

/** Uma conta que participa do time: o rótulo dela e o `accountUuid`, a chave de agrupamento. */
internal data class TuiTeamAccount(val label: String, val accountKey: String)

/**
 * A aba 4 (#441): o mesmo `GET /api/v1/team` da janela de time do app, para a conta escolhida,
 * com o corte do dia em BRT. Só leitura: nenhuma batida de presença e nenhum envio, que são do
 * coletor. A presença é calculada no agora do servidor, com o desvio que o coletor mediu e
 * gravou na saúde dele; sem coletor, o desvio é zero.
 *
 * Leitura que falha mantém os números anteriores da mesma conta e publica só a frase.
 */
internal class TuiTeamReader(
    private val settings: () -> TeamIntegrationSettings?,
    private val accounts: () -> List<TuiTeamAccount>,
    private val repository: (TeamIntegrationSettings) -> TeamUsageRepository,
    private val clockOffsetMillis: () -> Long = { 0L }
) {
    private val lastGood = mutableMapOf<String, Pair<TeamUsageSnapshot, Instant>>()

    /** O time existe para a TUI com a integração ligada e ao menos uma conta com login. */
    fun available(): Boolean = settings()?.isActive == true && accounts().isNotEmpty()

    fun load(state: TuiTeamState, now: Instant): TuiTeam? {
        val current = settings()?.takeIf { value -> value.isActive } ?: return null
        val all = accounts().takeIf { list -> list.isNotEmpty() } ?: return null
        val index = state.accountIndex.coerceIn(0, all.lastIndex)
        val account = all[index]
        val referenceNow = serverReferenceNow(now, clockOffsetMillis())
        val cutoff = startOfTodayBrt(referenceNow).toEpochMilliseconds()
        val result = runCatching { runBlocking { repository(current).fetch(account.accountKey, cutoff) } }.getOrElse { error -> Result.failure(error) }
        val fresh = result.getOrNull()
        if (fresh != null) {
            lastGood[account.accountKey] = fresh to now
        }
        val (snapshot, readAt) = lastGood[account.accountKey] ?: (null to null)
        val error = result.exceptionOrNull()
        return TuiTeam(
            accounts = all.map { item -> item.label },
            accountIndex = index,
            members = snapshot?.let { value -> members(value, referenceNow) }.orEmpty(),
            referenceNow = referenceNow,
            failure = error?.let { failed -> failed.message ?: failed::class.simpleName ?: "?" },
            unreachable = error != null && isConnectivityFailure(error),
            connectionFailure = error?.let { failed -> teamConnectionFailureOf(failed) },
            readAt = readAt
        )
    }

    private fun members(snapshot: TeamUsageSnapshot, referenceNow: Instant): List<TuiTeamMember> =
        snapshot.members.toTeamPresence(referenceNow).map { presence ->
            val member = presence.member
            TuiTeamMember(
                alias = member.alias,
                online = presence.isOnline,
                workingNow = presence.isWorkingNow,
                activeSessions = presence.activeSessionCount,
                lastActivityAt = presence.lastActivityAt,
                lastSeenAt = presence.lastSeenAt,
                sessions = member.sessionCount,
                tokens = member.totalTokens,
                costMicros = member.totalCostMicros,
                costIncomplete = !member.isCostComplete
            )
        }

    companion object {
        /**
         * As contas Anthropic marcadas para o time, na ordem das preferências, com o
         * `accountUuid` do `.claude.json` de cada uma (o mesmo que o `team status` lê). Conta
         * sem login fica de fora: sem a chave não há o que pedir ao servidor.
         */
        fun participating(preferences: Preferences, home: File, settings: TeamIntegrationSettings?): List<TuiTeamAccount> {
            val wanted = settings?.participatingProfileIds.orEmpty()
            return AnthropicProfileRegistry.readStored(preferences)
                .filter { record -> !record.hidden && record.id in wanted }
                .mapNotNull { record ->
                    val identity = if (record.id == DEFAULT_ANTHROPIC_PROFILE_ID) File(home, ".claude.json") else File(record.configDirectory, ".claude.json")
                    TerminalTeamRunner.readClaudeIdentity(identity).first?.let { key -> TuiTeamAccount(record.label, key) }
                }
        }
    }
}
