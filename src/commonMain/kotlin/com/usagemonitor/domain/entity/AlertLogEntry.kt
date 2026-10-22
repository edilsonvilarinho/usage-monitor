package com.usagemonitor.domain.entity

/**
 * Um aviso que saiu (#440, 5.2 do protótipo): o registro do que foi avisado, quando e
 * para onde, que o `usage-monitor alerts` mostra. Sem tela não há bandeja, e este
 * registro é o que fica. O silêncio adia o aviso, e ele entra aqui quando sai.
 *
 * [title] é a primeira linha da notificação, no idioma de quem coletou; nunca prompt ou
 * resposta, só metadados de uso.
 */
data class AlertLogEntry(
    val atEpochMillis: Long,
    val kind: AlertLogKind,
    val title: String,
    /** O percentual do momento, quando o aviso é de cota; `null` nos outros. */
    val percent: Int? = null,
    val destinations: List<AlertDestination> = emptyList()
)

/** O tipo do aviso, pela gravidade que o terminal escreve. */
enum class AlertLogKind {
    CRITICAL,
    WARNING,
    RESET,
    REMINDER,
    SESSION,
    SPIKE
}

/** Para onde o aviso foi entregue. */
enum class AlertDestination {
    TRAY,
    TELEGRAM
}

/** O tipo do registro de um aviso: crítico pela régua única de [isCriticalAlert]. */
fun alertLogKindOf(alert: UsageAlert): AlertLogKind =
    when (alert) {
        is UsageAlert.QuotaThreshold -> if (isCriticalAlert(alert)) AlertLogKind.CRITICAL else AlertLogKind.WARNING
        is UsageAlert.QuotaReset -> AlertLogKind.RESET
        is UsageAlert.ResetSoon -> AlertLogKind.REMINDER
        is UsageAlert.SessionSaturated, is UsageAlert.SessionStalled -> AlertLogKind.SESSION
        is UsageAlert.SpendSpike -> AlertLogKind.SPIKE
    }

/** O percentual do aviso de cota. */
fun alertLogPercentOf(alert: UsageAlert): Int? =
    when (alert) {
        is UsageAlert.QuotaThreshold -> alert.actualPercent
        is UsageAlert.ResetSoon -> alert.actualPercent
        is UsageAlert.QuotaReset, is UsageAlert.SessionSaturated, is UsageAlert.SessionStalled, is UsageAlert.SpendSpike -> null
    }

/**
 * Para onde um aviso vai: a bandeja só existe no app; o Telegram, com o bot ligado, com
 * token e com alguma conversa pareada — a mesma condição em que o bot repassa o aviso.
 */
fun alertDestinationsFor(collector: CollectorKind, telegram: TelegramBotSettings): List<AlertDestination> =
    buildList {
        when (collector) {
            CollectorKind.APP -> add(AlertDestination.TRAY)
            CollectorKind.SERVICE -> Unit
        }
        if (telegram.enabled && telegram.botToken.isNotBlank() && telegram.authorizedChats.isNotEmpty()) {
            add(AlertDestination.TELEGRAM)
        }
    }
