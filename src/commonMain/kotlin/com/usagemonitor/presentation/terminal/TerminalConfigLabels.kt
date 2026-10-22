package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.presentation.ui.localized

/** Os textos de configurar pelo terminal (#440), nos dois idiomas do app. */
internal class TerminalConfigLabels(val language: AppLanguage) {

    /** A consequência de uma mudança aplicada: quem já a usa, ou quando passa a valer. */
    fun appliedBy(collector: CollectorKind?): String =
        when (collector) {
            CollectorKind.APP -> localized(language, "o app já está usando", "the app is already using it")
            CollectorKind.SERVICE -> localized(language, "o serviço já está usando", "the service is already using it")
            null -> localized(language, "vale quando o app ou o serviço abrir", "applies when the app or the service starts")
        }

    fun restartNeeded(collector: CollectorKind?): String =
        when (collector) {
            CollectorKind.APP -> localized(language, "vale depois de reiniciar o app", "applies after restarting the app")
            CollectorKind.SERVICE -> localized(language, "vale depois de usage-monitor service restart", "applies after usage-monitor service restart")
            null -> localized(language, "vale quando o app ou o serviço abrir", "applies when the app or the service starts")
        }

    fun notAnswered(collector: CollectorKind): String =
        when (collector) {
            CollectorKind.APP -> localized(
                language,
                "O app aberto não respondeu em 10 s, e nada mudou. Ele pode ser de uma versão anterior: atualize-o, ou feche-o e tente de novo.",
                "The open app did not answer within 10 s, and nothing changed. It may be an older version: update it, or close it and try again."
            )
            CollectorKind.SERVICE -> localized(
                language,
                "O serviço não respondeu em 10 s, e nada mudou. Veja se ele está de pé com usage-monitor status.",
                "The service did not answer within 10 s, and nothing changed. Check that it is up with usage-monitor status."
            )
        }

    fun failed(detail: String?): String =
        localized(language, "Não foi possível gravar", "Could not save") + (detail?.let { text -> ": $text" } ?: ".")

    fun sourceSwitched(name: String, enabled: Boolean): String =
        if (enabled) localized(language, "$name ligada", "$name enabled") else localized(language, "$name desligada", "$name disabled")

    fun keyRequired(name: String): String =
        localized(language, "$name precisa de uma chave antes de ligar. Salve com:", "$name needs a key before it can be enabled. Save it with:")

    fun unknownSource(id: String): String = localized(language, "a fonte '$id' não existe", "there is no source '$id'")

    val headerSource: String get() = localized(language, "FONTE", "SOURCE")
    val headerId: String get() = "ID"
    val headerState: String get() = localized(language, "ESTADO", "STATE")
    val headerKey: String get() = localized(language, "CHAVE", "KEY")
    val enabled: String get() = localized(language, "ligada", "enabled")
    val disabled: String get() = localized(language, "desligada", "disabled")
    val keySaved: String get() = localized(language, "salva", "saved")
    val keyMissing: String get() = localized(language, "sem chave", "no key")
}
