package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ACTIVE_SESSION_WINDOW_MILLIS
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.mergeSessionPulses
import com.usagemonitor.domain.entity.toSessionPulse
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.repository.TeamUsageRepository
import kotlinx.datetime.Clock

/**
 * Sessões desta máquina com interação nos últimos minutos, por conta Anthropic.
 *
 * Uma consulta só para todas as contas: o índice já devolve o `profileId` de cada
 * linha, e uma leitura por card multiplicaria o mesmo `SELECT` pelo número de
 * contas configuradas.
 *
 * Não sincroniza o índice — ao contrário de [GetCliSessionsUseCase]. Quem
 * sincroniza é o ViewModel, que precisa serializar a passada com os outros laços
 * que tocam o mesmo banco.
 */
class GetActiveCliSessionPulsesUseCase(
    private val repository: CliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(): Result<Map<String, SessionPulse>> {
        val now = clock.now()
        val cutoffMillis = now.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS

        return repository.getSessions(profileId = null, sinceEpochMillis = cutoffMillis).map { sessions ->
            sessions
                .groupBy { session -> session.profileId ?: DEFAULT_ANTHROPIC_PROFILE_ID }
                .mapValues { (_, profileSessions) -> profileSessions.toSessionPulse(now) }
                .filterValues { pulse -> pulse.isPulsing }
        }
    }
}

/**
 * Sessões de todo o time com interação nos últimos minutos, numa conta.
 *
 * Inclui as sessões desta máquina: o botão do time mostra a conta inteira, e
 * esconder a própria linha faria o número da tela discordar do semáforo.
 */
class GetActiveTeamSessionPulseUseCase(
    private val repository: TeamUsageRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(accountKey: String): Result<SessionPulse> {
        val now = clock.now()
        val cutoffMillis = now.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS

        return repository.fetch(accountKey, cutoffMillis).map { snapshot ->
            snapshot.members
                .map { member ->
                    member.sessions.toSessionPulse(
                        now = now,
                        memberAlias = member.alias,
                        machineLabel = member.machineLabel
                    )
                }
                .mergeSessionPulses()
        }
    }
}
