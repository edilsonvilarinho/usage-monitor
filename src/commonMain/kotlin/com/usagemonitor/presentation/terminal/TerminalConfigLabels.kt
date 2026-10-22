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

    val off: String get() = localized(language, "desligado", "off")
    val empty: String get() = localized(language, "(vazio)", "(empty)")

    fun secretSaved(key: String): String = localized(language, "$key salva", "$key saved")

    val secretPrompt: String get() = localized(language, "Digite o valor e tecle enter (não aparece na tela):", "Type the value and press enter (it is not shown):")

    val noSecretGiven: String get() = localized(language, "Nada foi digitado, e nada mudou.", "Nothing was typed, and nothing changed.")

    fun noSettingIn(prefix: String): String = localized(language, "nenhum ajuste começa com '$prefix'", "no setting starts with '$prefix'")

    /** O que um ajuste aceita, pelo tipo do valor (o nome do tipo é o do enum, para o texto não depender do desktop). */
    fun invalidValue(key: String, expected: String): String =
        localized(language, "'$key' não aceita esse valor. Use $expected.", "'$key' does not take that value. Use $expected.")

    fun expected(type: String): String =
        when (type) {
            "BOOLEAN" -> localized(language, "true ou false", "true or false")
            "PERCENTS" -> localized(language, "percentuais de 1 a 100 separados por vírgula, como 75,90", "percentages from 1 to 100 separated by commas, such as 75,90")
            "MINUTES" -> localized(language, "minutos de 1 a 1440", "minutes from 1 to 1440")
            "MINUTES_OR_OFF" -> localized(language, "minutos de 1 a 1440, ou off", "minutes from 1 to 1440, or off")
            "FACTOR" -> localized(language, "um fator de 1.5 a 100, como 2", "a factor from 1.5 to 100, such as 2")
            "QUIET_HOURS" -> localized(language, "uma faixa de horas como 22-08, ou off", "an hour range such as 22-08, or off")
            "HOUR_OR_OFF" -> localized(language, "uma hora de 0 a 23, ou off", "an hour from 0 to 23, or off")
            "USD_OR_OFF" -> localized(language, "um valor em dólares, como 200, ou off", "an amount in dollars, such as 200, or off")
            "LANGUAGE" -> localized(language, "pt ou en", "pt or en")
            "PORT" -> localized(language, "uma porta de 1 a 65535, ou off", "a port from 1 to 65535, or off")
            "CLEAR_ONLY" -> localized(language, "off, que volta ao limiar geral em todas as contas", "off, which goes back to the general threshold in every account")
            else -> localized(language, "um texto", "a text")
        }

    fun keyPrompt(name: String): String =
        localized(
            language,
            "Cole a chave do $name e tecle enter (não aparece na tela nem vai para o histórico do shell):",
            "Paste the $name key and press enter (it is not shown and stays out of the shell history):"
        )

    fun keySaved(name: String): String = localized(language, "chave do $name salva e a fonte ligada", "$name key saved and the source enabled")

    fun keyRemoved(name: String): String = localized(language, "chave do $name removida e a fonte desligada", "$name key removed and the source disabled")

    fun keyRejected(message: String): String = localized(language, "A chave não passou no teste, e nada mudou. $message", "The key did not pass the test, and nothing changed. $message")

    val skipTestHint: String get() = localized(language, "Para gravar sem testar (por exemplo, sem rede agora):", "To save it without testing (for example, with no network now):")

    fun keyTestedWithWarning(message: String): String = localized(language, "Testada, com ressalva: $message", "Tested, with a caveat: $message")

    val keyNotTested: String get() = localized(language, "gravada sem teste", "saved without a test")

    fun noSavedKey(name: String): String = localized(language, "$name não tem chave salva. Salve com:", "$name has no saved key. Save it with:")

    val headerLabel: String get() = localized(language, "APELIDO", "LABEL")
    val headerOrigin: String get() = localized(language, "ORIGEM", "ORIGIN")
    val monitored: String get() = localized(language, "monitorada", "monitored")
    val notMonitored: String get() = localized(language, "desligada", "off")
    val ready: String get() = localized(language, "pronta", "ready")
    val noLogin: String get() = localized(language, "sem login", "not logged in")
    val bySource: String get() = localized(language, "pela fonte", "by the source")

    fun accountAdded(source: String, label: String): String =
        localized(language, "conta $source $label adicionada e monitorada", "$source account $label added and monitored")

    fun accountRenamed(label: String): String = localized(language, "conta renomeada para $label", "account renamed to $label")

    fun accountSwitched(label: String, enabled: Boolean): String =
        if (enabled) localized(language, "$label monitorada", "$label monitored") else localized(language, "$label desligada", "$label off")

    fun accountRemoved(label: String): String = localized(language, "$label removida da lista", "$label removed from the list")

    val accountsRescanned: String get() = localized(language, "contas do Claude Code procuradas de novo", "Claude Code accounts searched again")

    fun unknownAccount(account: String): String = localized(language, "A conta '$account' não existe. Veja as contas com:", "There is no account '$account'. See the accounts with:")

    fun ambiguousAccount(account: String, ids: String): String =
        localized(language, "'$account' é o apelido de mais de uma conta. Use o id: $ids", "'$account' is the label of more than one account. Use the id: $ids")

    val defaultAccount: String
        get() = localized(language, "A conta padrão do Claude Code não sai da lista. Para parar de monitorá-la:", "The default Claude Code account stays on the list. To stop monitoring it:")

    fun accountFailed(detail: String?): String = localized(language, "A conta não foi adicionada", "The account was not added") + (detail?.let { text -> ": $text" } ?: ".")
}
