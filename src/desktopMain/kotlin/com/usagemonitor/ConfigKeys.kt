package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.MIN_SPIKE_FACTOR
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.TelegramBotPreferences
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.parseThresholdPercents
import kotlinx.coroutines.flow.update

/** O que os ajustes leem: as preferências e os dois arquivos de configuração que têm ajuste simples. */
internal class ConfigReadSource(
    val settings: PreferencesSettings,
    val alerts: UsageAlertSettings,
    val proxy: ProxySettings,
    val telegram: TelegramBotSettings
) {
    companion object {
        fun of(store: AppSettingsStore): ConfigReadSource =
            ConfigReadSource(store.settings, store.alertSettingsFlow.value, store.proxySettingsFlow.value, store.telegramSettingsFlow.value)
    }
}

/** O tipo do valor de um ajuste: o que ele aceita e como se escreve. */
internal enum class ConfigValueType {
    BOOLEAN,
    PERCENTS,
    MINUTES,
    MINUTES_OR_OFF,
    FACTOR,
    QUIET_HOURS,
    HOUR_OR_OFF,
    USD_OR_OFF,
    LANGUAGE,
    TEXT,
    PORT,
    SECRET,
    CLEAR_ONLY
}

/**
 * Um ajuste com nome (#440, 4.3 do protótipo): `alerts.quota.thresholds`. O nome é
 * contrato de script — inglês, pontos, estável entre versões. [read] devolve o valor no
 * formato de máquina (`true`, `75,90`, `22-08`), ou `null` para desligado; [write]
 * recebe o valor já normalizado por [ConfigValues.normalize].
 */
internal class ConfigKey(
    val name: String,
    val type: ConfigValueType,
    val read: (ConfigReadSource) -> String?,
    val write: (AppSettingsStore, String?) -> Unit,
    /** Só vale depois de reiniciar quem coleta: o proxy (a regra do app, #174). */
    val restartNeeded: Boolean = false
) {
    val secret: Boolean get() = type == ConfigValueType.SECRET
}

/**
 * Os ajustes das Configurações que não têm comando próprio. Fontes, chaves, contas,
 * pareamento do bot, time e acesso web têm (`sources`, `keys`, `accounts`, `telegram`,
 * `team`, `web`); tema, opacidade, escala, movimento, anel da bandeja e dicas de início
 * não se aplicam ao terminal (4.4 do protótipo).
 */
internal object ConfigKeys {

    val ALL: List<ConfigKey> = listOf(
        alert("alerts.quota.enabled", ConfigValueType.BOOLEAN, { alerts -> alerts.quotaAlertsEnabled.toString() }) { alerts, value -> alerts.copy(quotaAlertsEnabled = (value == "true")) },
        alert("alerts.quota.thresholds", ConfigValueType.PERCENTS, { alerts -> alerts.quotaPercents.joinToString(",") }) { alerts, value ->
            alerts.copy(quotaPercents = parseThresholdPercents(value.orEmpty()).orEmpty())
        },
        // Gravado pelo `/limiar` do bot (#408, AA10); as Configurações só mostram e desfazem.
        alert("alerts.quota.account_thresholds", ConfigValueType.CLEAR_ONLY, { alerts ->
            alerts.quotaPercentsByTarget.values.sortedBy { override -> override.label }
                .joinToString("; ") { override -> override.label + " " + override.percents.joinToString(",") }
                .ifEmpty { null }
        }) { alerts, _ -> alerts.copy(quotaPercentsByTarget = emptyMap()) },
        alert("alerts.reset.enabled", ConfigValueType.BOOLEAN, { alerts -> alerts.quotaResetAlertsEnabled.toString() }) { alerts, value -> alerts.copy(quotaResetAlertsEnabled = (value == "true")) },
        alert("alerts.reminder.minutes", ConfigValueType.MINUTES_OR_OFF, { alerts -> alerts.resetReminderMinutes?.toString() }) { alerts, value -> alerts.copy(resetReminderMinutes = value?.toInt()) },
        alert("alerts.session.enabled", ConfigValueType.BOOLEAN, { alerts -> alerts.sessionAlertsEnabled.toString() }) { alerts, value -> alerts.copy(sessionAlertsEnabled = (value == "true")) },
        alert("alerts.stalled.enabled", ConfigValueType.BOOLEAN, { alerts -> alerts.stalledSessionAlertsEnabled.toString() }) { alerts, value ->
            alerts.copy(stalledSessionAlertsEnabled = (value == "true"))
        },
        alert("alerts.stalled.minutes", ConfigValueType.MINUTES, { alerts -> (alerts.stallThresholdMillis / 60_000L).toString() }) { alerts, value ->
            alerts.copy(stallThresholdMillis = value!!.toLong() * 60_000L)
        },
        alert("alerts.spike.enabled", ConfigValueType.BOOLEAN, { alerts -> alerts.spikeAlertsEnabled.toString() }) { alerts, value -> alerts.copy(spikeAlertsEnabled = (value == "true")) },
        alert("alerts.spike.factor", ConfigValueType.FACTOR, { alerts -> alerts.spikeFactor.toString() }) { alerts, value -> alerts.copy(spikeFactor = value!!.toDouble()) },
        alert("alerts.quiet", ConfigValueType.QUIET_HOURS, { alerts -> alerts.quietHours?.let(ConfigValues::quietHoursText) }) { alerts, value ->
            alerts.copy(quietHours = value?.let(ConfigValues::quietHoursOf))
        },
        ConfigKey(
            "budget.monthly_usd", ConfigValueType.USD_OR_OFF,
            read = { source -> readPersistedBudgetMicros(source.settings).takeIf { micros -> micros > 0L }?.let(::formatBudgetUsd) },
            write = { store, value -> persistBudgetMicros(store.settings, value?.let(::parseBudgetUsd) ?: 0L) }
        ),
        ConfigKey(
            "language", ConfigValueType.LANGUAGE,
            read = { source -> storedLanguage(source.settings).name.lowercase() },
            write = { store, value -> store.languageSelection.choose(AppLanguage.valueOf(value!!.uppercase())) }
        ),
        ConfigKey(
            "updates.auto", ConfigValueType.BOOLEAN,
            read = { source -> readPersistedAutoUpdateEnabled(source.settings).toString() },
            write = { store, value -> persistAutoUpdateEnabled(store.settings, (value == "true")) }
        ),
        ConfigKey(
            "updates.beta", ConfigValueType.BOOLEAN,
            read = { source -> readPersistedReceiveBetaUpdates(source.settings).toString() },
            write = { store, value -> persistReceiveBetaUpdates(store.settings, (value == "true")) }
        ),
        ConfigKey(
            "system.autostart", ConfigValueType.BOOLEAN,
            read = { source -> source.settings.getBoolean(AUTO_START_KEY, false).toString() },
            write = { store, value -> writeAutoStart(store, (value == "true")) }
        ),
        telegram("telegram.source_control", ConfigValueType.BOOLEAN, { telegram -> telegram.allowSourceControl.toString() }) { telegram, value -> telegram.copy(allowSourceControl = (value == "true")) },
        // Trocar a hora religa o resumo de hoje, como nas Configurações.
        telegram("telegram.daily_summary_hour", ConfigValueType.HOUR_OR_OFF, { telegram -> telegram.dailySummaryHour?.toString() }) { telegram, value ->
            telegram.copy(dailySummaryHour = value?.toInt(), lastSummaryDate = null)
        },
        telegram("telegram.live_panel", ConfigValueType.BOOLEAN, { telegram -> telegram.livePanelEnabled.toString() }) { telegram, value -> telegram.copy(livePanelEnabled = (value == "true")) },
        botOption("telegram.pc_actions", { preferences -> preferences.allowPcActions }) { preferences, on -> preferences.copy(allowPcActions = on) },
        botOption("telegram.machine_name", { preferences -> preferences.showMachineName }) { preferences, on -> preferences.copy(showMachineName = on) },
        botOption("telegram.team", { preferences -> preferences.showTeam }) { preferences, on -> preferences.copy(showTeam = on) },
        botOption("telegram.remote_update", { preferences -> preferences.allowRemoteUpdate }) { preferences, on -> preferences.copy(allowRemoteUpdate = on) },
        botOption("telegram.sound_only_critical", { preferences -> preferences.soundOnlyOnCritical }) { preferences, on -> preferences.copy(soundOnlyOnCritical = on) },
        botOption("telegram.group_repeated", { preferences -> preferences.groupRepeatedAlerts }) { preferences, on -> preferences.copy(groupRepeatedAlerts = on) },
        botOption("telegram.batch_alerts", { preferences -> preferences.batchAlerts }) { preferences, on -> preferences.copy(batchAlerts = on) },
        botOption("telegram.compact_panel", { preferences -> preferences.compactPanel }) { preferences, on -> preferences.copy(compactPanel = on) },
        botOption("telegram.source_health_alerts", { preferences -> preferences.sourceHealthAlerts }) { preferences, on -> preferences.copy(sourceHealthAlerts = on) },
        proxy("proxy.use_environment", ConfigValueType.BOOLEAN, { proxy -> proxy.useEnvironmentProxy.toString() }) { proxy, value -> proxy.copy(useEnvironmentProxy = (value == "true")) },
        proxy("proxy.host", ConfigValueType.TEXT, { proxy -> proxy.host.ifEmpty { null } }) { proxy, value -> proxy.copy(host = value.orEmpty()) },
        proxy("proxy.port", ConfigValueType.PORT, { proxy -> proxy.port.takeIf { port -> port > 0 }?.toString() }) { proxy, value -> proxy.copy(port = value?.toInt() ?: 0) },
        proxy("proxy.username", ConfigValueType.TEXT, { proxy -> proxy.username.ifEmpty { null } }) { proxy, value -> proxy.copy(username = value.orEmpty()) },
        proxy("proxy.password", ConfigValueType.SECRET, { proxy -> proxy.password.ifEmpty { null } }) { proxy, value -> proxy.copy(password = value.orEmpty()) }
    )

    fun named(name: String): ConfigKey? = ALL.firstOrNull { key -> key.name == name.trim().lowercase() }

    private fun alert(
        name: String,
        type: ConfigValueType,
        read: (UsageAlertSettings) -> String?,
        transform: (UsageAlertSettings, String?) -> UsageAlertSettings
    ) = ConfigKey(
        name, type,
        read = { source -> read(source.alerts) },
        write = { store, value ->
            // Atômico: o bot (`/silenciar`, `/limiar`) muda o mesmo flow de outra thread.
            store.alertSettingsFlow.update { current -> transform(current, value) }
            persistAlertSettings(store.settings, store.alertSettingsFlow.value)
        }
    )

    private fun telegram(
        name: String,
        type: ConfigValueType,
        read: (TelegramBotSettings) -> String?,
        transform: (TelegramBotSettings, String?) -> TelegramBotSettings
    ) = ConfigKey(
        name, type,
        read = { source -> read(source.telegram) },
        write = { store, value -> updateTelegramSettings(store) { current -> transform(current, value) } }
    )

    private fun botOption(name: String, read: (TelegramBotPreferences) -> Boolean, transform: (TelegramBotPreferences, Boolean) -> TelegramBotPreferences) =
        telegram(name, ConfigValueType.BOOLEAN, { telegram -> read(telegram.preferences).toString() }) { telegram, value ->
            telegram.copy(preferences = transform(telegram.preferences, (value == "true")))
        }

    private fun proxy(name: String, type: ConfigValueType, read: (ProxySettings) -> String?, transform: (ProxySettings, String?) -> ProxySettings) =
        ConfigKey(
            name, type,
            read = { source -> read(source.proxy) },
            write = { store, value ->
                updateProxySettings(store.proxySettingsFlow, store.proxySettingsDataSource) { current -> transform(current, value) }
            },
            restartNeeded = true
        )

    /**
     * Iniciar com o sistema é do app desktop: grava a entrada do sistema e a preferência,
     * como o interruptor das Configurações. Fora da raiz padrão (#419), não grava nada.
     */
    private fun writeAutoStart(store: AppSettingsStore, enabled: Boolean) {
        check(AppDataDirectory.isDefault) { "autostart is off with --user-data-dir" }
        val result = AutoStartManager.setAutoStart(enabled)
        check(result.isSuccess) { (result as? AutoStartResult.Failure)?.reason ?: "autostart" }
        store.settings.putBoolean(AUTO_START_KEY, enabled)
    }
}

/** Muda e grava o `telegram.json` de forma atômica: o bot mexe no mesmo flow ao vivo. */
internal fun updateTelegramSettings(store: AppSettingsStore, transform: (TelegramBotSettings) -> TelegramBotSettings) {
    store.telegramSettingsFlow.update(transform)
    store.telegramSettingsDataSource.save(store.telegramSettingsFlow.value)
}

/**
 * O que cada tipo aceita, em função pura. `normalize` devolve o valor no formato de
 * máquina, [OFF] para desligar, ou `null` quando o texto não serve.
 */
internal object ConfigValues {
    const val OFF = "off"

    private val OFF_WORDS = setOf("off", "none", "0", "desligado", "")
    private val TRUE_WORDS = setOf("true", "on", "yes", "1", "sim", "ligado")
    private val FALSE_WORDS = setOf("false", "off", "no", "0", "nao", "não", "desligado")

    fun normalize(type: ConfigValueType, text: String): String? {
        val value = text.trim().lowercase()
        return when (type) {
            ConfigValueType.BOOLEAN -> when (value) {
                in TRUE_WORDS -> "true"
                in FALSE_WORDS -> "false"
                else -> null
            }
            ConfigValueType.PERCENTS -> parseThresholdPercents(value)?.joinToString(",")
            ConfigValueType.MINUTES -> value.toIntOrNull()?.takeIf { minutes -> minutes in 1..1_440 }?.toString()
            ConfigValueType.MINUTES_OR_OFF -> if (value in OFF_WORDS) OFF else value.toIntOrNull()?.takeIf { minutes -> minutes in 1..1_440 }?.toString()
            ConfigValueType.FACTOR -> value.replace(',', '.').toDoubleOrNull()?.takeIf { factor -> factor >= MIN_SPIKE_FACTOR && factor <= 100.0 }?.toString()
            ConfigValueType.QUIET_HOURS -> if (value in OFF_WORDS) OFF else quietHoursOf(value)?.let(::quietHoursText)
            ConfigValueType.HOUR_OR_OFF -> if (value in OFF_WORDS) OFF else value.toIntOrNull()?.takeIf { hour -> hour in 0..23 }?.toString()
            ConfigValueType.USD_OR_OFF -> if (value in OFF_WORDS) OFF else parseBudgetUsd(value)?.takeIf { micros -> micros > 0L }?.let(::formatBudgetUsd)
            ConfigValueType.LANGUAGE -> value.takeIf { language -> language == "pt" || language == "en" }
            ConfigValueType.TEXT -> text.trim().ifEmpty { OFF }
            ConfigValueType.PORT -> if (value in OFF_WORDS) OFF else value.toIntOrNull()?.takeIf { port -> port in 1..65_535 }?.toString()
            ConfigValueType.SECRET -> text.trim().ifEmpty { OFF }
            ConfigValueType.CLEAR_ONLY -> if (value in OFF_WORDS) OFF else null
        }
    }

    /** "22-08", "22:00-08:00" ou "22h-8h": a faixa do silêncio, que atravessa a meia-noite. */
    fun quietHoursOf(text: String): QuietHours? {
        val hours = text.split('-').map { part -> part.trim().substringBefore(':').removeSuffix("h").toIntOrNull() }
        if (hours.size != 2 || hours.any { hour -> hour == null || hour !in 0..23 }) {
            return null
        }
        return QuietHours(hours[0]!!, hours[1]!!)
    }

    fun quietHoursText(hours: QuietHours): String =
        hours.startHour.toString().padStart(2, '0') + "-" + hours.endHour.toString().padStart(2, '0')
}
