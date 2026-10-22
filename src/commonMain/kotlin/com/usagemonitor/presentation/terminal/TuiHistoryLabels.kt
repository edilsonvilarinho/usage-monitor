package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.presentation.ui.localized

/** Os textos da aba 3, Histórico (3.3). */
internal class TuiHistoryLabels(val language: AppLanguage) {
    val window: String get() = localized(language, "janela", "window")

    fun range(range: HistoryRange): String =
        when (range) {
            HistoryRange.LAST_7_DAYS -> localized(language, "7 dias", "7 days")
            HistoryRange.LAST_30_DAYS -> localized(language, "30 dias", "30 days")
            else -> "24 h"
        }

    fun cell(range: HistoryRange): String =
        when (range) {
            HistoryRange.LAST_7_DAYS -> localized(language, "uma célula = 3 horas", "one cell = 3 hours")
            HistoryRange.LAST_30_DAYS -> localized(language, "uma célula = 12 horas", "one cell = 12 hours")
            else -> localized(language, "uma célula = meia hora", "one cell = half an hour")
        }

    val legend: String get() = localized(language, "reinício da janela · altura = uso da janela · cor = fonte", "window reset · height = window usage · color = source")
    val noReading: String get() = localized(language, "sem leitura nesta janela", "no reading in this window")
    val noQuotas: String get() = localized(language, "Nenhuma cota com percentual para mostrar.", "No quota with a percentage to show.")
    val keyWindow: String get() = localized(language, "janela", "window")
    val keyQuota: String get() = localized(language, "cota", "quota")

    /** Dias da semana abreviados, de segunda (1) a domingo (7), como o eixo de 7 dias usa. */
    fun weekday(isoDay: Int): String =
        localized(language, listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[isoDay - 1], listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")[isoDay - 1])

    /** Dia e mês na ordem do app: `05/11` em português, `11/05` em inglês. */
    fun dayMonth(day: Int, month: Int): String {
        val d = day.toString().padStart(2, '0')
        val m = month.toString().padStart(2, '0')
        return localized(language, "$d/$m", "$m/$d")
    }
}
