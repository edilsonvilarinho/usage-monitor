package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class HistoryRange {
    LAST_24_HOURS,
    LAST_7_DAYS,
    LAST_30_DAYS,
    TOTAL;

    fun windowStart(now: Instant): Instant {
        return when (this) {
            LAST_24_HOURS -> now.minus(24, kotlinx.datetime.DateTimeUnit.HOUR, TimeZone.UTC)
            LAST_7_DAYS -> now.minus(7, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            LAST_30_DAYS -> now.minus(30, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            TOTAL -> TOTAL_WINDOW_START
        }
    }

    /**
     * Início da janela **anterior**, de mesma duração, para o comparativo.
     *
     * `null` em [TOTAL]: "tudo" não tem período anterior contra o que comparar,
     * e inventar um daria um número sem significado.
     */
    fun previousWindowStart(now: Instant): Instant? {
        return when (this) {
            LAST_24_HOURS -> now.minus(48, kotlinx.datetime.DateTimeUnit.HOUR, TimeZone.UTC)
            LAST_7_DAYS -> now.minus(14, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            LAST_30_DAYS -> now.minus(60, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            TOTAL -> null
        }
    }

    private companion object {
        val TOTAL_WINDOW_START: Instant = Instant.fromEpochMilliseconds(Long.MIN_VALUE)
    }
}

/**
 * Consumo desta janela contra o da janela anterior de mesma duração.
 *
 * Só compara o **delta** — o quanto foi consumido dentro de cada janela — e não
 * o valor acumulado da cota: o acumulado zera no reset e a comparação viraria
 * uma função de quando o reset caiu, não de quanto se usou.
 */
data class UsagePeriodComparison(
    val currentDelta: Long,
    val previousDelta: Long
) {
    /**
     * Variação relativa. `null` quando a janela anterior não teve consumo —
     * dividir por zero produziria "infinito por cento", que não informa nada.
     */
    val changeRatio: Double?
        get() {
            if (previousDelta <= 0L) {
                return null
            }
            return (currentDelta - previousDelta).toDouble() / previousDelta.toDouble()
        }

    val isIncrease: Boolean
        get() = currentDelta > previousDelta
}

private const val RESET_DETECTION_TOLERANCE_MS = 300_000L

/**
 * O `resets_at` devolvido pelas APIs de uso (ex.: Anthropic) sofre jitter
 * de até ~1s entre polls dentro da MESMA janela, sem reset real. Como a
 * menor janela real (5h) é muito maior que esse jitter, só tratamos como
 * mudança de período diferenças acima de [RESET_DETECTION_TOLERANCE_MS].
 */
fun isSamePeriod(a: Instant, b: Instant): Boolean {
    return kotlin.math.abs(a.toEpochMilliseconds() - b.toEpochMilliseconds()) <= RESET_DETECTION_TOLERANCE_MS
}

data class UsageHistoryPoint(
    val capturedAt: Instant,
    val used: Long,
    val total: Long,
    val rawUsed: Long,
    val rawTotal: Long,
    val periodEndAt: Instant,
    /** Ver `UsageSnapshotRecord.hasKnownResetAt`. `true` no default pelo mesmo motivo. */
    val hasKnownResetAt: Boolean = true
) {
    val displayUsed: Long
        get() = if (rawUsed > 0L) rawUsed else used

    val displayTotal: Long
        get() = if (rawTotal > 0L) rawTotal else total

    val normalizedUsage: Float
        get() = if (displayTotal > 0L) {
            (displayUsed.toFloat() / displayTotal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Consumo observado ao longo de uma sequência de pontos.
 *
 * **Só as variações no sentido do gasto entram.** Para [UsageUnit.CURRENCY_USD] o
 * ponto guarda o saldo, então gastar é o saldo **cair** e a função soma as quedas;
 * para as demais unidades o ponto guarda o acumulado e gastar é subir. É por isso
 * que o reset de uma janela — que derruba o acumulado a zero — contribui zero em
 * vez de um delta negativo que apagaria o consumo anterior.
 *
 * Mora no domain, e não em quem lê o histórico, porque tem dois consumidores: o
 * relatório do histórico e a linha de referência diária de [UsageDailyBaseline].
 * Duas cópias divergiriam justamente no tratamento do reset, que é a parte
 * sutil.
 */
fun positiveDeltaOf(points: List<UsageHistoryPoint>, unit: UsageUnit): Long {
    var delta = 0L
    for (index in 1 until points.size) {
        val current = points[index]
        val previous = points[index - 1]
        val diff = current.displayUsed - previous.displayUsed
        if (unit == UsageUnit.CURRENCY_USD) {
            if (diff < 0L) {
                delta += -diff
            }
        } else {
            if (diff > 0L) {
                delta += diff
            }
        }
    }
    return delta
}

sealed interface UsageForecast {
    data object InsufficientData : UsageForecast
    data object NoGrowth : UsageForecast
    data object ResetsBeforeExhaustion : UsageForecast
    data class EstimatedExhaustionAt(val instant: Instant) : UsageForecast
}

/** Nível de risco de a cota estourar antes do reset da sessão, com base na projeção de consumo. */
enum class UsageRiskLevel { ON_TRACK, AT_RISK, WILL_EXCEED }

data class QuotaRiskSummary(
    val level: UsageRiskLevel,
    /**
     * Quando a cota deve esgotar no ritmo observado.
     *
     * Com reset conhecido ele é não nulo apenas quando `level != ON_TRACK`: ali a
     * ausência de data **é** a informação, porque significa que o reset chega
     * primeiro. Sem reset conhecido ele é a resposta principal e vem preenchido
     * em qualquer nível — inclusive `ON_TRACK`, que ali quer dizer só "ainda tem
     * folga", não "não vai acabar".
     */
    val estimatedExhaustionAt: Instant?,
    /**
     * Se a cota tem reset. Falso é saldo pré-pago, e muda o que a tela pode
     * afirmar: sem reset não existe "esgotar antes do reset" (issue #109).
     */
    val hasKnownResetAt: Boolean = true
)

/**
 * Autonomia restante abaixo da qual um saldo sem reset é crítico.
 *
 * Saldo pré-pago não reseta: o que existe é a data em que ele acaba. Sete dias é
 * o prazo em que a recarga precisa acontecer; catorze é onde ela entra no radar.
 * Os dois valores são a decisão travada na issue #109, e são o **único** lugar em
 * que ela mora.
 */
const val BALANCE_CRITICAL_RUNWAY_MILLIS = 7L * 24 * 3_600_000
const val BALANCE_WARNING_RUNWAY_MILLIS = 14L * 24 * 3_600_000

/**
 * Deriva o nível de risco a partir do forecast. `referenceAt` deve ser o mesmo instante-base
 * usado para projetar [UsageForecast.EstimatedExhaustionAt.instant] (o `capturedAt` do último
 * ponto), não o relógio atual — mantém a proporção tempo-até-estourar/tempo-até-reset consistente
 * com o cálculo original.
 *
 * [hasKnownResetAt] falso troca a régua inteira, e não é um detalhe (issue #109):
 * a comparação padrão é "quanto do tempo até o reset a cota aguenta", e sem reset
 * não há denominador. O saldo do DeepSeek grava `Instant.DISTANT_FUTURE`, o que
 * fazia a razão dar ~0,002 e **qualquer** consumo maior que zero virar
 * `WILL_EXCEED` — para escapar disso a previsão teria de estar a dezenas de
 * milhares de anos de distância. O card ficava em Crítico permanente e o ponto de
 * risco do ícone da bandeja, aceso para sempre.
 *
 * Sem reset a pergunta é outra e é absoluta: **quanto tempo o saldo ainda dura.**
 */
fun UsageForecast.riskSummary(
    referenceAt: Instant,
    periodEndAt: Instant,
    hasKnownResetAt: Boolean = true
): QuotaRiskSummary? {
    return when (this) {
        UsageForecast.ResetsBeforeExhaustion ->
            QuotaRiskSummary(
                level = UsageRiskLevel.ON_TRACK,
                estimatedExhaustionAt = null,
                hasKnownResetAt = hasKnownResetAt
            )
        is UsageForecast.EstimatedExhaustionAt -> {
            val msToExhaustion = (instant.toEpochMilliseconds() - referenceAt.toEpochMilliseconds()).coerceAtLeast(0L)
            val level = if (hasKnownResetAt) {
                val msToReset = (periodEndAt.toEpochMilliseconds() - referenceAt.toEpochMilliseconds()).coerceAtLeast(1L)
                val ratio = msToExhaustion.toDouble() / msToReset.toDouble()
                if (ratio < 0.5) UsageRiskLevel.WILL_EXCEED else UsageRiskLevel.AT_RISK
            } else {
                when {
                    msToExhaustion < BALANCE_CRITICAL_RUNWAY_MILLIS -> UsageRiskLevel.WILL_EXCEED
                    msToExhaustion < BALANCE_WARNING_RUNWAY_MILLIS -> UsageRiskLevel.AT_RISK
                    else -> UsageRiskLevel.ON_TRACK
                }
            }
            // Sem reset e com folga o nível é `ON_TRACK`, mas a data continua
            // sendo o que a cota tem a dizer: ela é a resposta a "quando acaba",
            // não um aviso. Guardá-la aqui é o que permite ao card mostrar a
            // previsão sem acender cor nenhuma.
            QuotaRiskSummary(
                level = level,
                estimatedExhaustionAt = instant,
                hasKnownResetAt = hasKnownResetAt
            )
        }
        UsageForecast.NoGrowth, UsageForecast.InsufficientData -> null
    }
}

/** Chave estável para correlacionar uma [QuotaInfo] com sua [UsageHistorySeries] correspondente. */
data class QuotaSeriesKey(val label: String, val periodType: PeriodType)

val QuotaInfo.seriesKey: QuotaSeriesKey
    get() = QuotaSeriesKey(label = label, periodType = periodType)

data class UsageHistorySeries(
    val quotaLabel: String,
    val periodType: PeriodType,
    val unit: UsageUnit,
    val points: List<UsageHistoryPoint>,
    val currentDisplayUsed: Long,
    val currentDisplayTotal: Long,
    val deltaDisplayUsed: Long,
    val averageDisplayConsumptionPerHour: Double,
    val currentPeriodEndAt: Instant,
    val forecast: UsageForecast,
    val riskSummary: QuotaRiskSummary?,
    /** Esta janela contra a anterior; `null` em "Total" ou sem dado anterior. */
    val comparison: UsagePeriodComparison? = null,
    /**
     * Pontos crus da janela **anterior**, de mesma duração (issue #215).
     *
     * Vazio em "Total" e sempre que não há dado anterior — mesma condição de
     * [comparison] ser `null`, e pela mesma razão: a leitura já ia até
     * `previousWindowStart` para calcular o delta, e até esta entrada esses
     * pontos eram descartados depois de servir só a esse número. O gráfico os
     * usa para desenhar a linha de referência tracejada; nenhuma consulta
     * nova ao banco.
     */
    val previousWindowPoints: List<UsageHistoryPoint> = emptyList()
) {
    val seriesKey: QuotaSeriesKey
        get() = QuotaSeriesKey(label = quotaLabel, periodType = periodType)
}

data class ApiUsageHistoryReport(
    val source: ApiSource,
    val range: HistoryRange,
    val lastUpdatedAt: Instant?,
    val series: List<UsageHistorySeries>,
    val accountContext: UsageAccountContext? = null
) {
    val isEmpty: Boolean
        get() = series.isEmpty()
}
