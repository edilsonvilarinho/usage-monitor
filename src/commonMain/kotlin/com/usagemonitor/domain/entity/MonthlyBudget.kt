package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Créditos de uso da conta, na moeda **real** dela.
 *
 * Existe separado do custo estimado do índice CLI porque as duas grandezas não
 * somam: o `extra_usage` da Anthropic vem na moeda da conta — que nem sempre é
 * USD — e o custo do índice é sempre USD. Juntá-los sem taxa de câmbio
 * produziria um número inventado.
 */
data class AccountCreditUsage(
    val usedMinorUnits: Long,
    val limitMinorUnits: Long,
    val currencyCode: String
) {
    val share: Double
        get() {
            if (limitMinorUnits <= 0L) {
                return 0.0
            }
            return (usedMinorUnits.toDouble() / limitMinorUnits.toDouble()).coerceIn(0.0, 1.0)
        }
}

/**
 * Situação do orçamento mensal contra o custo estimado do índice CLI.
 *
 * Só cobre o gasto **em USD** que o app calcula a partir dos transcripts. Os
 * créditos da conta viajam ao lado, em [AccountCreditUsage], e a tela mostra os
 * dois com a moeda explícita.
 */
data class MonthlyBudgetStatus(
    val limitMicros: Long,
    val spentMicros: Long,
    /** Dias já decorridos do mês, contando o dia corrente como um dia inteiro. */
    val daysElapsed: Int,
    val daysInMonth: Int,
    /** `true` quando algum turno do mês ficou sem preço: o gasto é piso. */
    val isSpendComplete: Boolean = true
) {
    val share: Double
        get() {
            if (limitMicros <= 0L) {
                return 0.0
            }
            return spentMicros.toDouble() / limitMicros.toDouble()
        }

    val isExceeded: Boolean
        get() = limitMicros > 0L && spentMicros >= limitMicros

    /**
     * Fechamento do mês mantido o ritmo diário.
     *
     * O ritmo é o gasto dividido pelos dias decorridos, e não uma média móvel:
     * a média móvel exigiria histórico por dia, que o resumo não carrega, e a
     * projeção deixaria de ser derivável do mesmo número que a tela já mostra.
     */
    val projectedMicros: Long
        get() {
            if (daysElapsed <= 0) {
                return spentMicros
            }
            return spentMicros * daysInMonth / daysElapsed
        }

    val willExceed: Boolean
        get() = limitMicros > 0L && projectedMicros > limitMicros
}

/** Início do mês corrente no fuso da apresentação, para recortar o índice. */
fun startOfMonthMillis(now: Instant, timeZone: TimeZone): Long {
    val today = now.toLocalDateTime(timeZone).date
    return LocalDate(today.year, today.monthNumber, 1).atStartOfDayIn(timeZone).toEpochMilliseconds()
}

/**
 * Monta a situação do orçamento. `null` quando não há teto configurado — sem
 * teto a barra não teria contra o que medir.
 */
fun monthlyBudgetStatusOf(
    limitMicros: Long,
    totals: CliUsageBucket,
    now: Instant,
    timeZone: TimeZone
): MonthlyBudgetStatus? {
    if (limitMicros <= 0L) {
        return null
    }
    val today: LocalDateTime = now.toLocalDateTime(timeZone)
    return MonthlyBudgetStatus(
        limitMicros = limitMicros,
        spentMicros = totals.costMicros,
        daysElapsed = today.date.dayOfMonth,
        daysInMonth = daysInMonth(today.date.year, today.date.monthNumber),
        isSpendComplete = totals.isCostComplete
    )
}

internal fun daysInMonth(year: Int, monthNumber: Int): Int {
    return when (monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
