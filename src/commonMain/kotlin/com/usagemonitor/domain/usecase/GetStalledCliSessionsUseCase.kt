package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.DEFAULT_STALL_THRESHOLD_MILLIS
import com.usagemonitor.domain.entity.STALLED_SESSION_MAX_AGE_MILLIS
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.detectStalledSessions
import com.usagemonitor.domain.repository.CliSessionRepository
import kotlinx.datetime.Clock

/**
 * Sessões desta máquina cujo último pedido não recebeu resposta dentro do limiar.
 *
 * O conjunto candidato sai do índice e é estreito de propósito: só sessões com
 * turno nas últimas [STALLED_SESSION_MAX_AGE_MILLIS] e cujo último turno já passou
 * do limiar. Os dois cortes são seguros porque o pedido pendente é sempre
 * posterior ao último turno — se o pedido cruzou o limiar, o turno anterior a ele
 * também cruzou. Sem esse recorte a leitura abriria a cauda de todo transcript já
 * indexado a cada passada.
 *
 * Não sincroniza o índice, como [GetActiveCliSessionPulsesUseCase]: quem
 * sincroniza é o ViewModel, que serializa a passada com os outros laços que tocam
 * o mesmo banco.
 */
class GetStalledCliSessionsUseCase(
    private val repository: CliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        thresholdMillis: Long = DEFAULT_STALL_THRESHOLD_MILLIS
    ): Result<List<StalledCliSession>> {
        val now = clock.now()
        val nowMillis = now.toEpochMilliseconds()

        val sessionsResult = repository.getSessions(
            profileId = null,
            sinceEpochMillis = nowMillis - STALLED_SESSION_MAX_AGE_MILLIS
        )

        return sessionsResult.mapCatching { sessions ->
            val candidates = sessions.filter { session ->
                session.lastTs.toEpochMilliseconds() <= nowMillis - thresholdMillis
            }
            if (candidates.isEmpty()) {
                return@mapCatching emptyList()
            }

            val tails = repository.getSessionTails(candidates.map { session -> session.sessionId })
                .getOrThrow()
                .associateBy { tail -> tail.sessionId }

            detectStalledSessions(
                sessions = candidates,
                tails = tails,
                now = now,
                thresholdMillis = thresholdMillis
            )
        }
    }
}
