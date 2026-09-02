package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ACTIVE_SESSION_WINDOW_MILLIS
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.HudSessionSummary
import com.usagemonitor.domain.repository.CliSessionRepository
import kotlinx.datetime.Clock

/**
 * O consumo da janela de 5h e quantas sessões estão vivas agora, para o rodapé
 * da barra HUD (issue #164).
 *
 * O HUD mostrava cota de API e nada do que a máquina gastou de fato — "o que
 * está apresentado não mostra os dados", nas palavras de quem usa. Cota é o teto
 * do fornecedor; isto aqui é o que o CLI queimou.
 *
 * **Não sincroniza o índice.** `GetCliSessionsUseCase`, que responde a mesma
 * pergunta para a tela de Sessões CLI, começa por `repository.syncIndex()` — mas
 * ali a leitura é sob demanda do usuário. Aqui o laço é de fundo e convive com o
 * do `SessionPulseViewModel`, que já sincroniza a cada 30s: sincronizar de novo
 * na mesma cadência dobraria a varredura dos `projects/` sem tornar nada mais
 * fresco.
 *
 * **A janela é a mesma `LAST_5H` da tela de Sessões CLI**, ancorada no fim da
 * quota quando ele é conhecido. Um corte próprio aqui faria o rodapé do HUD e o
 * cabeçalho daquela tela discordarem sobre o mesmo gasto, e o usuário não teria
 * como saber qual dos dois olhar.
 *
 * **"Ativa" é o corte de 5 min do semáforo** (`ACTIVE_SESSION_WINDOW_MILLIS`),
 * não um terceiro valor: a mesma pergunta — "isto está rodando agora?" — já tem
 * resposta neste app, e dois cortes dariam duas respostas.
 */
class GetHudSessionSummaryUseCase(
    private val repository: CliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        windows: CliQuotaWindows = CliQuotaWindows()
    ): Result<HudSessionSummary> {
        val now = clock.now()
        val window = CliSessionRange.LAST_5H.resolve(now, windows)
        val activeCutoffMillis = now.toEpochMilliseconds() - ACTIVE_SESSION_WINDOW_MILLIS

        return repository.getSessions(
            profileId = null,
            sinceEpochMillis = window.cutoffMillis
        ).map { sessions ->
            HudSessionSummary(
                activeSessionCount = sessions.count { session ->
                    session.lastTs.toEpochMilliseconds() >= activeCutoffMillis
                },
                windowSessionCount = sessions.size,
                costMicros = sessions.sumOf { session -> session.costMicros },
                totalTokens = sessions.sumOf { session -> session.totalTokens },
                unpricedTurnCount = sessions.sumOf { session -> session.unpricedTurnCount }
            )
        }
    }
}
