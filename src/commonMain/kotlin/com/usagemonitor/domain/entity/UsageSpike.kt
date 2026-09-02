package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Mínimo de dias completos para haver linha de referência.
 *
 * Abaixo disso a mediana descreveria o acaso: dois dias medidos não dizem qual é
 * o hábito de ninguém, e um aviso tirado dali seria falso positivo com aparência
 * de estatística.
 */
const val MIN_BASELINE_DAYS = 3

/**
 * Fator padrão: o consumo de hoje precisa ser o triplo do habitual.
 *
 * Três porque a variação normal entre dois dias de trabalho já chega ao dobro sem
 * que nada esteja errado — dois transformaria o aviso em rotina, e rotina é o que
 * faz um alerta ser ignorado.
 */
const val DEFAULT_SPIKE_FACTOR = 3.0

/**
 * Piso do fator aceito das preferências.
 *
 * O valor vai em claro para o registro e alguém pode editá-lo à mão; abaixo de
 * 1,5 o aviso sairia em qualquer dia acima da mediana, que é metade dos dias por
 * definição.
 */
const val MIN_SPIKE_FACTOR = 1.5

/**
 * Fração da cota abaixo da qual não há anomalia, qualquer que seja o fator.
 *
 * Um quinto do total. Sem este piso, 0,02% contra 0,005% de mediana é "quatro
 * vezes acima" — verdadeiro na aritmética e inútil como aviso, porque a razão
 * explode justamente onde os números são pequenos demais para significar
 * qualquer coisa.
 */
const val SPIKE_MIN_SHARE_DIVISOR = 5L

/**
 * O consumo de hoje contra o hábito do próprio usuário, na mesma cota.
 *
 * Grandeza **diferente** do limiar de cota e da projeção de risco: aqueles medem
 * distância até o teto, este mede distância até o normal. Um dia três vezes acima
 * do habitual não cruza limiar nenhum enquanto estiver longe do limite, e é
 * exatamente aí que mora o vazamento — laço de agente solto, automação esquecida
 * ligada.
 */
data class UsageDailyBaseline(
    /** Consumo de hoje até a hora local corrente. */
    val todayDelta: Long,
    /** Mediana do consumo dos dias completos, até a **mesma** hora do dia. */
    val baselineDelta: Long,
    /** Quantos dias entraram na mediana. */
    val completeDays: Int
) {
    /**
     * Quantas vezes hoje está acima do habitual.
     *
     * `null` com referência zerada, pela mesma razão de
     * [UsagePeriodComparison.changeRatio]: dividir por zero produziria "infinitas
     * vezes", que não informa nada. Quem consome perto de zero na maioria dos
     * dias fica deliberadamente sem aviso — com mediana zero, o primeiro dia de
     * uso já dispararia.
     */
    val factor: Double?
        get() {
            if (baselineDelta <= 0L) {
                return null
            }
            return todayDelta.toDouble() / baselineDelta.toDouble()
        }
}

/**
 * Uma cota consumindo acima do próprio hábito, hoje.
 *
 * Carrega só o fato; a frase nasce na borda da UI, como em [UsageAlert].
 */
data class UsageSpike(
    val target: UsageTargetKey,
    val targetLabel: String,
    val quotaLabel: String,
    val periodType: PeriodType,
    val unit: UsageUnit,
    val baseline: UsageDailyBaseline,
    /** Quantas vezes acima do habitual; sempre não nulo aqui. */
    val factor: Double,
    /** Total da cota, para a frase poder dizer o consumo em proporção. */
    val quotaTotal: Long,
    /** Dia local do disparo. É ele que rearma o aviso na virada. */
    val localDate: LocalDate
)

/**
 * A linha de referência desta série, ou `null` quando não dá para afirmar nada.
 *
 * **Os dias anteriores são recortados até a mesma hora do dia que [now]**, e não
 * medidos inteiros: comparar um dia parcial com dias completos só produziria
 * aviso à noite, quando ele já não serve para interromper nada.
 *
 * O consumo de cada dia sai de [positiveDeltaOf], então reset de janela contribui
 * zero em vez de delta negativo — é isso que permite somar um dia inteiro de uma
 * cota de 5h, que reinicia quatro ou cinco vezes nesse intervalo.
 *
 * O intervalo entre a última leitura de um dia e a primeira do seguinte não é
 * contado em nenhum dos dois. A perda é de uma coleta por virada de dia, é a
 * mesma em todos os dias, e portanto não desloca a razão — que é o que a função
 * existe para calcular.
 */
fun UsageHistorySeries.dailyBaseline(now: Instant, timeZone: TimeZone): UsageDailyBaseline? {
    // Mesma recusa que a média por hora e a previsão já aplicam: janela reportada
    // não tem semântica local confiável, e sem total conhecido não há régua — o
    // piso de proporção não teria contra o que medir.
    if (periodType == PeriodType.REPORTED || currentDisplayTotal <= 0L) {
        return null
    }

    val nowLocal = now.toLocalDateTime(timeZone)
    val cutoffSecondOfDay = nowLocal.time.toSecondOfDay()

    val byDay = LinkedHashMap<LocalDate, MutableList<UsageHistoryPoint>>()
    for (point in points) {
        val local = point.capturedAt.toLocalDateTime(timeZone)
        if (local.time.toSecondOfDay() > cutoffSecondOfDay) {
            continue
        }
        byDay.getOrPut(local.date) { mutableListOf() } += point
    }

    val todayPoints = byDay[nowLocal.date] ?: return null
    val previousDeltas = byDay
        .filterKeys { date -> date != nowLocal.date }
        // Um dia com uma leitura só não tem intervalo para medir, e entrar na
        // mediana como zero afirmaria que naquele dia não se consumiu nada.
        .filterValues { dayPoints -> dayPoints.size >= 2 }
        .values
        .map { dayPoints -> positiveDeltaOf(dayPoints, unit) }

    if (previousDeltas.size < MIN_BASELINE_DAYS) {
        return null
    }

    return UsageDailyBaseline(
        todayDelta = positiveDeltaOf(todayPoints, unit),
        baselineDelta = medianOf(previousDeltas),
        completeDays = previousDeltas.size
    )
}

/**
 * O veredito, quando há um.
 *
 * `null` sem referência, com fator abaixo do pedido, ou com consumo de hoje
 * pequeno demais para significar alguma coisa.
 */
fun UsageHistorySeries.detectSpike(
    target: UsageTargetKey,
    targetLabel: String,
    now: Instant,
    timeZone: TimeZone,
    minFactor: Double = DEFAULT_SPIKE_FACTOR
): UsageSpike? {
    val baseline = dailyBaseline(now, timeZone) ?: return null
    val factor = baseline.factor ?: return null

    if (factor < minFactor) {
        return null
    }
    if (baseline.todayDelta < currentDisplayTotal / SPIKE_MIN_SHARE_DIVISOR) {
        return null
    }

    return UsageSpike(
        target = target,
        targetLabel = targetLabel,
        quotaLabel = quotaLabel,
        periodType = periodType,
        unit = unit,
        baseline = baseline,
        factor = factor,
        quotaTotal = currentDisplayTotal,
        localDate = now.toLocalDateTime(timeZone).date
    )
}

/**
 * Mediana, e não média.
 *
 * Com três a seis amostras a média é dominada por um único incidente anterior: o
 * dia em que o laço de agente rodou solto levantaria a referência e esconderia o
 * próximo, que é justamente o que a detecção existe para pegar.
 */
internal fun medianOf(values: List<Long>): Long {
    if (values.isEmpty()) {
        return 0L
    }
    val sorted = values.sorted()
    val middle = sorted.size / 2
    if (sorted.size % 2 == 1) {
        return sorted[middle]
    }
    // Média dos dois centrais, em inteiro: a unidade da série é inteira e devolver
    // fração aqui só criaria arredondamento na frase.
    return (sorted[middle - 1] + sorted[middle]) / 2L
}
