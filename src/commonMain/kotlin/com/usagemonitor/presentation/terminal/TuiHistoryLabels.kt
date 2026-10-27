package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.presentation.ui.localized

/** Os textos da aba 3, Histórico (3.3): a tabela de janelas (AI4) e o gráfico alto (AI1, #463). */
internal class TuiHistoryLabels(val language: AppLanguage) {
    val period: String get() = localized(language, "período", "period")

    fun range(range: HistoryRange): String =
        when (range) {
            HistoryRange.LAST_7_DAYS -> localized(language, "7 dias", "7 days")
            HistoryRange.LAST_30_DAYS -> localized(language, "30 dias", "30 days")
            else -> "24 h"
        }

    val oneLinePerWindow: String get() = localized(language, "uma linha = uma janela", "one line = one window")
    val columnStart: String get() = localized(language, "INÍCIO", "START")
    val columnActive: String get() = localized(language, "ATIVA", "ACTIVE")
    val columnPeak: String get() = localized(language, "PICO", "PEAK")
    val columnEnd: String get() = localized(language, "COMO TERMINOU", "HOW IT ENDED")

    fun windowCount(count: Int): String =
        localized(language, if (count == 1) "1 janela no período" else "$count janelas no período", if (count == 1) "1 window in the period" else "$count windows in the period")

    fun exhaustedCount(count: Int): String = localized(language, if (count == 1) "1 esgotou" else "$count esgotaram", "$count ran out")

    val noWindow: String get() = localized(language, "sem janela no período", "no window in the period")

    fun olderWindows(count: Int): String =
        localized(language, if (count == 1) "+1 janela mais antiga" else "+$count janelas mais antigas", if (count == 1) "+1 older window" else "+$count older windows")

    fun open(reset: String): String = localized(language, "aberta · reinicia $reset", "open · resets $reset")
    fun leftover(percent: Int): String = localized(language, "sobrou $percent%", "$percent% left")
    fun exhaustedAfter(duration: String): String = localized(language, "esgotou após $duration", "ran out after $duration")
    fun idleFor(duration: String): String = localized(language, "parado $duration", "idle $duration")

    /** O gráfico alto (AI1): de quando a quando, e quanto tempo cabe numa coluna. */
    fun chartTitle(from: String, to: String, column: String): String =
        localized(language, "janela de $from a $to · uma coluna = $column", "window from $from to $to · one column = $column")

    fun usedWithElapsed(percent: Int, elapsed: Int): String =
        localized(language, "$percent% usada com $elapsed% da janela já passada", "$percent% used with $elapsed% of the window gone")

    fun used(percent: Int): String = localized(language, "$percent% usada", "$percent% used")
    fun peak(percent: Int): String = localized(language, "pico $percent%", "peak $percent%")
    fun active(span: String): String = localized(language, "ativa $span", "active $span")
    fun averagePace(pace: String): String = localized(language, "ritmo médio $pace", "average pace $pace")

    val noReading: String get() = localized(language, "sem leitura nesta janela", "no reading in this window")
    val noQuotas: String get() = localized(language, "Nenhuma cota com percentual para mostrar.", "No quota with a percentage to show.")
    val keyWindow: String get() = localized(language, "janela", "window")
    val keyChart: String get() = localized(language, "gráfico", "chart")
    val keyPeriod: String get() = localized(language, "período", "period")
    val keyBack: String get() = localized(language, "lista", "list")

    /** Dia e mês na ordem do app: `05/11` em português, `11/05` em inglês. */
    fun dayMonth(day: Int, month: Int): String {
        val d = day.toString().padStart(2, '0')
        val m = month.toString().padStart(2, '0')
        return localized(language, "$d/$m", "$m/$d")
    }
}
