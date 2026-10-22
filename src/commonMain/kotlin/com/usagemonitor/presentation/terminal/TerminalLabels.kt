package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.presentation.ui.localized
import kotlin.time.Duration

/**
 * Os textos do modo terminal nos dois idiomas do app (decisão D1 da #439). Objeto de
 * rótulos, e não `== AppLanguage.PT` espalhado: a catraca de verificação inline não sobe.
 */
internal class TerminalLabels(val language: AppLanguage) {

    fun zone(zone: QuotaZone): String =
        when (zone) {
            QuotaZone.ON_TRACK -> localized(language, "em dia", "on track")
            QuotaZone.ATTENTION -> localized(language, "atenção", "warning")
            QuotaZone.CRITICAL -> localized(language, "crítico", "critical")
        }

    /** A palavra mais longa entre as três, para a coluna de estado não dançar entre linhas. */
    val zoneColumnWidth: Int
        get() = QuotaZone.entries.maxOf { zone -> zone(zone).length }

    fun inDuration(duration: Duration): String = localized(language, "em ", "in ") + countdown(duration)

    /** "há 38 s", "há 18 min", "há 2 h": a idade de uma leitura. */
    fun ago(duration: Duration): String {
        val seconds = duration.inWholeSeconds.coerceAtLeast(0L)
        val amount = when {
            seconds < 60L -> "$seconds s"
            seconds < 3_600L -> "${seconds / 60L} min"
            else -> "${seconds / 3_600L} h"
        }
        return localized(language, "há $amount", "$amount ago")
    }

    fun readByApp(age: Duration): String = localized(language, "lido ${ago(age)} pelo app aberto", "read ${ago(age)} by the open app")

    fun quotaCount(count: Int): String = localized(language, "$count cotas", if (count == 1) "1 quota" else "$count quotas")

    fun zoneCount(zone: QuotaZone, count: Int): String =
        when (zone) {
            QuotaZone.CRITICAL -> localized(language, if (count == 1) "1 crítica" else "$count críticas", "$count critical")
            QuotaZone.ATTENTION -> localized(language, "$count em atenção", "$count warning")
            QuotaZone.ON_TRACK -> localized(language, "$count em dia", "$count on track")
        }

    companion object {
        /** "47 min", "2h17", "4d18h": quanto falta, sem arredondar para cima. */
        fun countdown(duration: Duration): String {
            val minutes = duration.inWholeMinutes.coerceAtLeast(0L)
            return when {
                minutes < 60L -> "$minutes min"
                minutes < 1_440L -> "${minutes / 60L}h${(minutes % 60L).toString().padStart(2, '0')}"
                else -> "${minutes / 1_440L}d${(minutes % 1_440L / 60L).toString().padStart(2, '0')}h"
            }
        }
    }
}
