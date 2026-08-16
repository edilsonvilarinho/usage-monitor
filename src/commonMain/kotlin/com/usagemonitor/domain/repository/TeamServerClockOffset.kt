package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.PRESENCE_MAX_CLOCK_OFFSET_MILLIS
import kotlinx.datetime.Instant
import kotlin.concurrent.Volatile
import kotlin.math.absoluteValue

/**
 * Desvio estimado entre o relógio do servidor de time e o desta máquina.
 *
 * Existe porque `last_seen_at` é escrito com o relógio do **servidor** e a
 * classificação de presença acontece no cliente. A falha é assimétrica e a
 * perigosa é silenciosa: uma máquina atrasada vê todo carimbo "no futuro" e
 * declara o time inteiro online para sempre; uma adiantada declara todo mundo
 * offline, que ao menos é visível.
 *
 * Não é um relógio, é uma correção medida — por isso mora fora de `Clock`. A
 * medida vem da resposta do heartbeat, que devolve o agora do servidor, e a
 * latência de ida empurra o resultado na direção conservadora.
 *
 * [NONE] deixa o valor em zero e é o que os testes e o caminho degradado usam:
 * instalação em modo admin puro não bate presença, e servidor anterior à 0.4.0
 * responde pelo ingest, que não devolve relógio nenhum.
 */
interface TeamServerClockOffset {

    /** Quanto somar ao relógio local para chegar ao do servidor. */
    val offsetMillis: Long

    /**
     * Registra uma medida.
     *
     * Descarta o que passar de [PRESENCE_MAX_CLOCK_OFFSET_MILLIS]: corrigir mais
     * de um dia esconderia um erro de configuração em vez de revelá-lo, e a
     * denúncia na tela deixaria de aparecer justamente no caso mais grave.
     */
    fun record(serverNow: Instant, localNow: Instant)

    companion object {
        val NONE: TeamServerClockOffset = object : TeamServerClockOffset {
            override val offsetMillis: Long = 0L
            override fun record(serverNow: Instant, localNow: Instant) = Unit
        }
    }
}

/**
 * Guarda a última medida em memória.
 *
 * Só memória, pelo mesmo raciocínio do marcador de identidade do serviço de
 * envio: o custo de errar é uma classificação degradada nos primeiros 30
 * segundos de execução, e persistir isso seria estado a mais para economizar uma
 * única medição.
 *
 * A última medida vale, e não uma média: um relógio corrigido no meio da
 * execução — por NTP, por suspensão do notebook — precisa ser seguido de
 * imediato, e uma média arrastaria a correção antiga por vários minutos.
 */
class InMemoryTeamServerClockOffset : TeamServerClockOffset {

    @Volatile
    private var measured: Long = 0L

    override val offsetMillis: Long
        get() = measured

    override fun record(serverNow: Instant, localNow: Instant) {
        val candidate = serverNow.toEpochMilliseconds() - localNow.toEpochMilliseconds()
        if (candidate.absoluteValue > PRESENCE_MAX_CLOCK_OFFSET_MILLIS) {
            return
        }
        measured = candidate
    }
}
