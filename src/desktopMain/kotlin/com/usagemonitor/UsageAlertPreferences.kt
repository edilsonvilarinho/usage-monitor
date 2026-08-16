package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.DEFAULT_QUOTA_ALERT_PERCENTS
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.UsageAlertSettings

private const val QUOTA_ALERTS_ENABLED_KEY = "alertsQuotaEnabled"
private const val QUOTA_ALERT_PERCENTS_KEY = "alertsQuotaPercents"
private const val SESSION_ALERTS_ENABLED_KEY = "alertsSessionEnabled"
private const val QUIET_HOURS_KEY = "alertsQuietHours"

/**
 * Preferências de alerta no mesmo armazenamento das demais (registro/plist via
 * `PreferencesSettings`).
 *
 * Não vão para `~/.usage-monitor/`: ali moram os segredos da integração de time,
 * e limiar de alerta não é segredo. Também não passam por `UserPreferences` —
 * aquela classe não é lida por ninguém.
 */
internal fun readPersistedAlertSettings(settings: PreferencesSettings): UsageAlertSettings {
    return UsageAlertSettings(
        quotaAlertsEnabled = settings.getBoolean(QUOTA_ALERTS_ENABLED_KEY, true),
        quotaPercents = decodeQuotaPercents(settings.getStringOrNull(QUOTA_ALERT_PERCENTS_KEY)),
        sessionAlertsEnabled = settings.getBoolean(SESSION_ALERTS_ENABLED_KEY, true),
        quietHours = decodeQuietHours(settings.getStringOrNull(QUIET_HOURS_KEY))
    )
}

internal fun persistAlertSettings(settings: PreferencesSettings, value: UsageAlertSettings) {
    settings.putBoolean(QUOTA_ALERTS_ENABLED_KEY, value.quotaAlertsEnabled)
    settings.putString(QUOTA_ALERT_PERCENTS_KEY, encodeQuotaPercents(value.quotaPercents))
    settings.putBoolean(SESSION_ALERTS_ENABLED_KEY, value.sessionAlertsEnabled)
    settings.putString(QUIET_HOURS_KEY, encodeQuietHours(value.quietHours))
}

/**
 * Valor ausente cai no default; valor presente mas ilegível vira lista vazia.
 *
 * A distinção importa: lista vazia é uma escolha válida ("não quero alerta de
 * quota") e não pode ser confundida com "nunca configurei".
 */
internal fun decodeQuotaPercents(stored: String?): List<Int> {
    if (stored == null) {
        return DEFAULT_QUOTA_ALERT_PERCENTS
    }
    return stored.split(',')
        .mapNotNull { part -> part.trim().toIntOrNull() }
        .filter { percent -> percent in 1..100 }
        .distinct()
        .sorted()
}

internal fun encodeQuotaPercents(percents: List<Int>): String {
    return percents
        .filter { percent -> percent in 1..100 }
        .distinct()
        .sorted()
        .joinToString(",")
}

/** `"22-8"` significa das 22h às 8h do dia seguinte. Vazio ou inválido = sem silêncio. */
internal fun decodeQuietHours(stored: String?): QuietHours? {
    val raw = stored?.trim().orEmpty()
    if (raw.isEmpty()) {
        return null
    }
    val parts = raw.split('-')
    if (parts.size != 2) {
        return null
    }
    val start = parts[0].trim().toIntOrNull() ?: return null
    val end = parts[1].trim().toIntOrNull() ?: return null
    return runCatching { QuietHours(start, end) }.getOrNull()
}

internal fun encodeQuietHours(quietHours: QuietHours?): String {
    if (quietHours == null) {
        return ""
    }
    return "${quietHours.startHour}-${quietHours.endHour}"
}
