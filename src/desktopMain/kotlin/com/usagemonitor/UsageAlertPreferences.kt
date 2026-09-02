package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.DEFAULT_QUOTA_ALERT_PERCENTS
import com.usagemonitor.domain.entity.DEFAULT_SPIKE_FACTOR
import com.usagemonitor.domain.entity.DEFAULT_STALL_THRESHOLD_MILLIS
import com.usagemonitor.domain.entity.MIN_SPIKE_FACTOR
import com.usagemonitor.domain.entity.MIN_STALL_THRESHOLD_MILLIS
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.UsageAlertSettings
import kotlin.math.roundToInt

private const val QUOTA_ALERTS_ENABLED_KEY = "alertsQuotaEnabled"
private const val QUOTA_ALERT_PERCENTS_KEY = "alertsQuotaPercents"
private const val SESSION_ALERTS_ENABLED_KEY = "alertsSessionEnabled"
private const val STALLED_ALERTS_ENABLED_KEY = "alertsStalledEnabled"
private const val STALL_THRESHOLD_MINUTES_KEY = "alertsStallThresholdMinutes"
private const val SPIKE_ALERTS_ENABLED_KEY = "alertsSpikeEnabled"
private const val SPIKE_FACTOR_TENTHS_KEY = "alertsSpikeFactorTenths"
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
        stalledSessionAlertsEnabled = settings.getBoolean(STALLED_ALERTS_ENABLED_KEY, true),
        stallThresholdMillis = decodeStallThresholdMillis(settings.getIntOrNull(STALL_THRESHOLD_MINUTES_KEY)),
        spikeAlertsEnabled = settings.getBoolean(SPIKE_ALERTS_ENABLED_KEY, true),
        spikeFactor = decodeSpikeFactor(settings.getIntOrNull(SPIKE_FACTOR_TENTHS_KEY)),
        quietHours = decodeQuietHours(settings.getStringOrNull(QUIET_HOURS_KEY))
    )
}

internal fun persistAlertSettings(settings: PreferencesSettings, value: UsageAlertSettings) {
    settings.putBoolean(QUOTA_ALERTS_ENABLED_KEY, value.quotaAlertsEnabled)
    settings.putString(QUOTA_ALERT_PERCENTS_KEY, encodeQuotaPercents(value.quotaPercents))
    settings.putBoolean(SESSION_ALERTS_ENABLED_KEY, value.sessionAlertsEnabled)
    settings.putBoolean(STALLED_ALERTS_ENABLED_KEY, value.stalledSessionAlertsEnabled)
    settings.putInt(STALL_THRESHOLD_MINUTES_KEY, (value.effectiveStallThresholdMillis / 60_000L).toInt())
    settings.putBoolean(SPIKE_ALERTS_ENABLED_KEY, value.spikeAlertsEnabled)
    settings.putInt(SPIKE_FACTOR_TENTHS_KEY, (value.effectiveSpikeFactor * 10.0).roundToInt())
    settings.putString(QUIET_HOURS_KEY, encodeQuietHours(value.quietHours))
}

/**
 * Fator gravado em **décimos inteiros**, com o piso do domain aplicado na leitura.
 *
 * Inteiro e não `Double` porque o valor vai em claro para o registro, e é lá que
 * alguém pode editá-lo à mão: `30` é legível e não tem separador decimal para
 * depender do idioma do sistema. Ausente ou abaixo do piso cai no default — o
 * armazenamento não é fonte confiável de faixa válida.
 */
internal fun decodeSpikeFactor(storedTenths: Int?): Double {
    if (storedTenths == null) {
        return DEFAULT_SPIKE_FACTOR
    }
    val factor = storedTenths.toDouble() / 10.0
    if (factor < MIN_SPIKE_FACTOR) {
        return DEFAULT_SPIKE_FACTOR
    }
    return factor
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

/**
 * Limiar gravado em minutos, com o piso do domain aplicado na leitura.
 *
 * Minutos e não millis porque o valor vai em claro para o registro e é lá que
 * alguém pode editá-lo à mão. Ausente ou abaixo do piso cai no default — o
 * armazenamento não é fonte confiável de faixa válida.
 */
internal fun decodeStallThresholdMillis(storedMinutes: Int?): Long {
    if (storedMinutes == null) {
        return DEFAULT_STALL_THRESHOLD_MILLIS
    }
    val millis = storedMinutes.toLong() * 60_000L
    if (millis < MIN_STALL_THRESHOLD_MILLIS) {
        return DEFAULT_STALL_THRESHOLD_MILLIS
    }
    return millis
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
