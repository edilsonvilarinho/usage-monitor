package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.AntigravityUsageDataSource
import com.usagemonitor.data.mapper.ANTIGRAVITY_UNRECOGNIZED_OUTPUT
import com.usagemonitor.data.mapper.AntigravityModelTurnException
import com.usagemonitor.data.mapper.AntigravityUsageMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AntigravityRepository
import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Lê as cotas do Antigravity CLI, com duas travas que o comando externo exige.
 *
 * **TTL.** Cada leitura abre um processo de alguns segundos, e o painel pede
 * coleta também no despertar por reset de **qualquer** fonte. Dentro do TTL a
 * leitura anterior é devolvida; o refresh pedido pelo usuário a invalida antes.
 *
 * **Disjuntor.** Se o envelope não provar que o CLI respondeu `/usage` sozinho, a
 * fonte trava até o app reiniciar: repetir a chamada a cada coleta repetiria o
 * consumo de cota de modelo que a salvaguarda existe para impedir. O disjuntor
 * **não** é desarmado por `invalidateCachedReading` — clicar em atualizar não
 * pode ser o gesto que volta a gastar.
 */
class AntigravityRepositoryImpl(
    private val dataSource: AntigravityUsageDataSource,
    private val clock: Clock = Clock.System,
    private val cacheTtl: Duration = DEFAULT_CACHE_TTL
) : AntigravityRepository {
    private val mutex = Mutex()
    private var cachedStats: ApiUsageStats? = null
    private var cachedAt: Instant? = null
    private var tripped = false

    override suspend fun getUsage(): Result<ApiUsageStats> = mutex.withLock {
        if (tripped) {
            return@withLock Result.failure(IllegalStateException(AntigravityUsageFailureKind.COLLECTION_PAUSED.safeMessage))
        }
        val now = clock.now()
        val cached = cachedStats
        val cachedInstant = cachedAt
        if (cached != null && cachedInstant != null && now - cachedInstant < cacheTtl) {
            return@withLock Result.success(cached)
        }

        try {
            val stats = AntigravityUsageMapper.parse(dataSource.readUsageJson())
            cachedStats = stats
            cachedAt = now
            Result.success(stats)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (turn: AntigravityModelTurnException) {
            tripped = true
            cachedStats = null
            cachedAt = null
            Result.failure(turn)
        } catch (error: IllegalStateException) {
            Result.failure(error)
        } catch (error: java.io.IOException) {
            Result.failure(error)
        } catch (_: Throwable) {
            // Erro nativo ou de parse não carrega a saída do CLI para a mensagem.
            Result.failure(IllegalStateException(ANTIGRAVITY_UNRECOGNIZED_OUTPUT))
        }
    }

    override fun invalidateCachedReading() {
        // Sem lock: a próxima leitura sob o mutex vê o valor nulo; no pior caso
        // uma coleta em andamento grava depois, e o dado dela é o mais recente.
        cachedStats = null
        cachedAt = null
    }

    companion object {
        val DEFAULT_CACHE_TTL: Duration = 5.minutes
    }
}
