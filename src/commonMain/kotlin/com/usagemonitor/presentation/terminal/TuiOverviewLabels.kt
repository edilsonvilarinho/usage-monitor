package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/** Os textos da aba 1, Visão geral (3.1), e da leitura que falhou (6.2). */
internal class TuiOverviewLabels(val language: AppLanguage) {
    val headerSources: String get() = localized(language, "FONTES", "SOURCES")
    val keySource: String get() = localized(language, "fonte", "source")
    val keyOpenAccount: String get() = localized(language, "abre a conta", "opens the account")
    val activeSession: String get() = localized(language, "SESSÃO ATIVA", "ACTIVE SESSION")
    val noActiveSession: String get() = localized(language, "nenhuma nos últimos 10 min", "none in the last 10 min")
    val last24h: String get() = localized(language, "24 H", "24 H")
    val noHistory: String get() = localized(language, "sem leitura nas últimas 24 h", "no reading in the last 24 h")
    val noAccounts: String get() = localized(language, "Nenhuma conta coletada ainda.", "No account collected yet.")

    fun resets(at: String, remaining: String): String = localized(language, "reinicia $at BRT · em $remaining", "resets $at BRT · in $remaining")

    /** "A leitura das 14:22 falhou: limite da API." Sem a hora, só "A última leitura falhou". */
    fun failed(at: String?, reason: String?): String {
        val head = if (at != null) localized(language, "A leitura das $at falhou", "The $at reading failed") else localized(language, "A última leitura falhou", "The last reading failed")
        return if (reason.isNullOrBlank()) "$head." else "$head: ${reason.trim().trimEnd('.')}."
    }

    /** "Mostrando os números de 14:12; tenta de novo às 14:27." */
    fun showing(from: String?, retry: String?): String {
        val shown = from?.let { clock -> localized(language, "Mostrando os números de $clock", "Showing the numbers from $clock") }
            ?: localized(language, "Ainda sem números desta conta", "No numbers for this account yet")
        val again = retry?.let { clock -> localized(language, "; tenta de novo às $clock", "; tries again at $clock") }.orEmpty()
        return "$shown$again."
    }
}
