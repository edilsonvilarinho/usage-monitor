package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.PRESENCE_LOOKBACK_MILLIS
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.hasSuspectClockSkew
import com.usagemonitor.domain.entity.toTeamPresence
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.repository.TeamServerClockOffset
import com.usagemonitor.domain.repository.TeamUsageRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Quem está conectado numa conta, agora.
 *
 * O corte vai como `cutoffMillis` cru — **nunca** um valor novo em
 * `CliSessionRange`. Os `when` exaustivos dos chips das duas telas de sessão
 * quebrariam, e esta consulta não é uma janela de quota: é o recorte que decide
 * se alguém está trabalhando neste instante. É o mesmo caminho de
 * [GetActiveTeamSessionPulseUseCase].
 */
class GetTeamPresenceUseCase(
    private val repository: TeamUsageRepository,
    private val serverClockOffset: TeamServerClockOffset = TeamServerClockOffset.NONE,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(accountKey: String): Result<TeamPresenceResult> {
        val referenceNow = referenceNow(clock, serverClockOffset)
        val cutoff = referenceNow.toEpochMilliseconds() - PRESENCE_LOOKBACK_MILLIS

        return repository.fetch(accountKey, cutoff).map { snapshot ->
            snapshot.members.toPresenceResult(referenceNow, serverClockOffset.offsetMillis)
        }
    }
}

/**
 * A mesma leitura, para todas as contas do servidor, via token de administração.
 *
 * A conta e o rótulo são copiados para cada integrante antes da classificação —
 * como a visão global do modal de consumo faz — porque ali não existe "a conta da
 * janela" e é o `accountKey` que separa a mesma máquina logada em duas contas.
 */
class GetAdminTeamPresenceUseCase(
    private val repository: TeamAdminRepository,
    private val serverClockOffset: TeamServerClockOffset = TeamServerClockOffset.NONE,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(): Result<TeamPresenceResult> {
        val referenceNow = referenceNow(clock, serverClockOffset)
        val cutoff = referenceNow.toEpochMilliseconds() - PRESENCE_LOOKBACK_MILLIS

        return repository.fetchOverview(cutoff).map { accounts ->
            val members = accounts.flatMap { account ->
                account.snapshot.members.map { member ->
                    member.copy(
                        accountKey = account.accountKey,
                        accountLabel = account.label,
                        accountEmail = account.accountEmail,
                        accountEmailSource = account.emailSource
                    )
                }
            }
            members.toPresenceResult(referenceNow, serverClockOffset.offsetMillis)
        }
    }
}

/**
 * Resultado da consulta de presença.
 *
 * [clockOffsetMillis] e [clockSkewSuspected] viajam junto porque a tela precisa
 * dizer **por que** os números podem estar errados quando estão: uma coluna de
 * presença silenciosamente incorreta é pior que uma coluna com aviso.
 */
data class TeamPresenceResult(
    val entries: List<TeamMemberPresence> = emptyList(),
    val clockOffsetMillis: Long = 0L,
    val clockSkewSuspected: Boolean = false
)

/** Agora do servidor, estimado a partir do relógio local mais o desvio medido. */
private fun referenceNow(clock: Clock, offset: TeamServerClockOffset): Instant {
    return Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds() + offset.offsetMillis)
}

private fun List<TeamMemberUsage>.toPresenceResult(
    referenceNow: Instant,
    offsetMillis: Long
): TeamPresenceResult {
    return TeamPresenceResult(
        entries = toTeamPresence(referenceNow),
        clockOffsetMillis = offsetMillis,
        clockSkewSuspected = hasSuspectClockSkew(referenceNow)
    )
}
