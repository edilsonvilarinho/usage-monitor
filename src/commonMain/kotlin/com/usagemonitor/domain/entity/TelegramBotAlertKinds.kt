package com.usagemonitor.domain.entity

/**
 * Os tipos de alerta que o bot liga e desliga um a um (#408, AA9). Enum novo, e não
 * valor novo num enum existente: cada tipo aponta para um interruptor que já mora em
 * [UsageAlertSettings] — o bot e as Configurações gravam o mesmo campo.
 */
enum class AlertKind(val data: String) {
    QUOTA("quota"),
    RESET("reset"),
    SESSION("session"),
    STALLED("stalled"),
    SPIKE("spike")
}

fun UsageAlertSettings.isOn(kind: AlertKind): Boolean = when (kind) {
    AlertKind.QUOTA -> quotaAlertsEnabled
    AlertKind.RESET -> quotaResetAlertsEnabled
    AlertKind.SESSION -> sessionAlertsEnabled
    AlertKind.STALLED -> stalledSessionAlertsEnabled
    AlertKind.SPIKE -> spikeAlertsEnabled
}

/** O mesmo campo das Configurações, invertido. Função pura, como [applyBotCommand]. */
fun UsageAlertSettings.toggled(kind: AlertKind): UsageAlertSettings = when (kind) {
    AlertKind.QUOTA -> copy(quotaAlertsEnabled = !quotaAlertsEnabled)
    AlertKind.RESET -> copy(quotaResetAlertsEnabled = !quotaResetAlertsEnabled)
    AlertKind.SESSION -> copy(sessionAlertsEnabled = !sessionAlertsEnabled)
    AlertKind.STALLED -> copy(stalledSessionAlertsEnabled = !stalledSessionAlertsEnabled)
    AlertKind.SPIKE -> copy(spikeAlertsEnabled = !spikeAlertsEnabled)
}

/**
 * O que faz dois alertas serem "o mesmo" para o bot agrupar (#408, AA1): tipo e assunto —
 * projeto da sessão, cota da conta (e o limiar, que é outro aviso). A sessão sem projeto
 * cai no próprio id, que nunca sai do processo. `null` não agrupa: o crítico
 * ([isCriticalAlert]) sempre abre mensagem nova, porque edição não toca no celular.
 */
fun alertGroupKey(alert: UsageAlert): String? {
    if (isCriticalAlert(alert)) return null
    return when (alert) {
        is UsageAlert.QuotaThreshold -> "quota|${alert.target.storageKey}|${alert.quotaLabel}|${alert.thresholdPercent}"
        is UsageAlert.QuotaReset -> "reset|${alert.target.storageKey}|${alert.quotaLabel}"
        is UsageAlert.SessionSaturated -> "session|${alert.projectName ?: alert.sessionId}"
        is UsageAlert.SessionStalled -> "stalled|${alert.projectName ?: alert.sessionId}"
        is UsageAlert.SpendSpike -> "spike|${alert.target.storageKey}|${alert.quotaLabel}"
    }
}

/** Mensagem que agrupa as repetições de um alerta numa conversa, no dia local [day] (ISO). */
data class TelegramAlertGroup(
    val messageId: Long,
    val count: Int,
    val firstAtMillis: Long,
    val day: String
)
