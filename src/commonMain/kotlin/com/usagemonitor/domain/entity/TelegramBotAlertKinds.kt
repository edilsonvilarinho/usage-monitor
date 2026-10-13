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
