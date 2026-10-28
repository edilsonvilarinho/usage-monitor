package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileRecord
import com.usagemonitor.ConfigKeys
import com.usagemonitor.ConfigReadSource
import com.usagemonitor.ConfigValues
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.WebAccessSettings
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.terminal.TerminalAccountRow
import com.usagemonitor.presentation.terminal.TerminalGlyphs
import com.usagemonitor.presentation.terminal.TerminalSourceRow
import com.usagemonitor.presentation.terminal.TuiSettingAction
import com.usagemonitor.presentation.terminal.TuiSettingEdit
import com.usagemonitor.presentation.terminal.TuiSettingField
import com.usagemonitor.presentation.terminal.TuiSettings
import com.usagemonitor.presentation.terminal.TuiSettingsLabels
import com.usagemonitor.presentation.terminal.TuiSettingsSection

/** O que a aba 5 lê, pelas mesmas leituras dos comandos de listar da #440. */
internal class TuiSettingsInputs(
    val sources: () -> List<TerminalSourceRow>,
    val accounts: () -> List<TerminalAccountRow>,
    val values: () -> ConfigReadSource,
    val telegram: () -> TelegramBotSettings,
    val team: () -> TeamIntegrationSettings?,
    /** Os apelidos das contas marcadas para o time. */
    val teamAccounts: (TeamIntegrationSettings?) -> List<String>,
    val web: () -> WebAccessSettings,
    /** Fora da raiz padrão (#419), iniciar com o sistema não existe. */
    val autostartAvailable: Boolean
)

/** Os apelidos das contas marcadas para o time, sem as escondidas, na ordem gravada. */
internal fun tuiTeamAccountLabels(records: List<AnthropicProfileRecord>, settings: TeamIntegrationSettings?): List<String> {
    val wanted = settings?.participatingProfileIds.orEmpty()
    return records.filter { record -> !record.hidden && record.id in wanted }.map { record -> record.label }
}

/**
 * A aba 5 (#441, A08): as seções das Configurações com os valores gravados agora. Cada campo
 * leva o id do comando da 4.3 que o muda ([TuiSettingsCommands]); a aba não tem regra própria.
 * O segredo nunca sai daqui: o campo só sabe se há um.
 */
internal class TuiSettingsReader(private val inputs: TuiSettingsInputs) {
    /** O "desligado" dos `‹ ›` da leitura em curso, no idioma dela. */
    private var offText: String = ConfigValues.OFF

    fun load(language: AppLanguage, glyphs: TerminalGlyphs): TuiSettings {
        val labels = TuiSettingsLabels(language)
        offText = labels.offValue
        val values = inputs.values()
        fun value(key: String): String? = ConfigKeys.named(key)?.read(values)
        val team = inputs.team()
        return TuiSettings(
            listOf(
                TuiSettingsSection(labels.sectionSources, sources(labels, glyphs)),
                TuiSettingsSection(labels.sectionAccounts, accounts(labels), hint = labels.accountsHint),
                TuiSettingsSection(labels.sectionAlerts, alerts(labels, ::value)),
                TuiSettingsSection(labels.sectionTelegram, telegram(labels, glyphs, ::value)),
                TuiSettingsSection(labels.sectionTeam, team(team, labels, glyphs), hint = labels.teamHint),
                TuiSettingsSection(labels.sectionNetwork, network(labels, glyphs, ::value), restartNeeded = true),
                TuiSettingsSection(labels.sectionWeb, web(labels), hint = labels.webHint),
                TuiSettingsSection(labels.sectionSystem, system(labels, ::value), hint = labels.systemHint)
            )
        )
    }

    private fun sources(labels: TuiSettingsLabels, glyphs: TerminalGlyphs): List<TuiSettingField> =
        inputs.sources().flatMap { row ->
            val name = row.source.displayName()
            buildList {
                add(
                    TuiSettingField.Toggle(
                        name, row.enabled, TuiSettingsCommands.source(row.source, !row.enabled),
                        note = if (row.needsKey) (if (row.hasKey) labels.keySaved else labels.keyMissing) else null
                    )
                )
                if (row.needsKey) {
                    add(TuiSettingField.Text(labels.apiKey(name), glyphs.secretMask.takeIf { row.hasKey }, TuiSettingEdit(TuiSettingsCommands.key(row.source), secret = true), labels.keyTested))
                    if (row.hasKey) {
                        add(TuiSettingField.Action(labels.removeKey(name), TuiSettingAction(TuiSettingsCommands.keyRemove(row.source)), confirm = true))
                    }
                }
            }
        }

    private fun accounts(labels: TuiSettingsLabels): List<TuiSettingField> =
        inputs.accounts().map { row ->
            val note = listOfNotNull(
                row.origin,
                labels.noLogin.takeIf { row.ready == false },
                labels.bySource.takeIf { row.bySource },
                labels.renameNote.takeIf { !row.bySource }
            ).joinToString(" · ")
            TuiSettingField.Toggle(
                "${row.source.displayName()} · ${row.label}", row.monitored, TuiSettingAction(TuiSettingsCommands.account(row.id), (!row.monitored).toString()),
                note = note,
                rename = TuiSettingEdit(TuiSettingsCommands.accountLabel(row.id), row.label).takeIf { !row.bySource }
            )
        } + TuiSettingField.Action(labels.rescan, TuiSettingAction(TuiSettingsCommands.ACCOUNTS_RESCAN), labels.rescanNote)

    private fun alerts(labels: TuiSettingsLabels, value: (String) -> String?): List<TuiSettingField> = buildList {
        add(toggle(labels.quotaAlerts, "alerts.quota.enabled", value))
        add(text(labels.thresholds, "alerts.quota.thresholds", value, note = null))
        value("alerts.quota.account_thresholds")?.let { overrides ->
            add(TuiSettingField.Action(labels.accountThresholds, TuiSettingsCommands.set("alerts.quota.account_thresholds", ConfigValues.OFF), overrides))
        }
        add(toggle(labels.resetAlerts, "alerts.reset.enabled", value))
        add(stepper(labels.reminder, "alerts.reminder.minutes", value, listOf(ConfigValues.OFF, "5", "10", "15", "30", "60")) { minutes ->
            labels.minutesBefore(minutes.toInt())
        })
        add(toggle(labels.sessionAlerts, "alerts.session.enabled", value))
        add(toggle(labels.stalledAlerts, "alerts.stalled.enabled", value))
        add(stepper("    ${labels.stalledAfter}", "alerts.stalled.minutes", value, listOf("30", "60", "120", "240")) { minutes ->
            // Como o segmentado do app: `30min`, `1h`.
            val total = minutes.toInt()
            if (total < 60) "${total}min" else "${total / 60}h"
        })
        add(toggle(labels.spikeAlerts, "alerts.spike.enabled", value))
        add(stepper("    ${labels.spikeFactor}", "alerts.spike.factor", value, listOf("2.0", "3.0", "5.0")) { factor -> "${factor.toDouble().toInt()}x" })
        add(text(labels.quietHours, "alerts.quiet", value, labels.quietNote))
        add(text(labels.budget, "budget.monthly_usd", value, note = null))
    }

    private fun telegram(labels: TuiSettingsLabels, glyphs: TerminalGlyphs, value: (String) -> String?): List<TuiSettingField> = buildList {
        val current = inputs.telegram()
        val hasToken = current.botToken.isNotBlank()
        add(
            if (hasToken) {
                TuiSettingField.Toggle(labels.bot, current.enabled, TuiSettingAction(TuiSettingsCommands.TELEGRAM, (!current.enabled).toString()))
            } else {
                TuiSettingField.Info(labels.bot, labels.off, labels.noBot)
            }
        )
        add(TuiSettingField.Text(labels.botToken, glyphs.secretMask.takeIf { hasToken }, TuiSettingEdit(TuiSettingsCommands.TELEGRAM_TOKEN, secret = true), labels.botTokenNote))
        if (hasToken) {
            add(TuiSettingField.Action(labels.pair, TuiSettingAction(TuiSettingsCommands.TELEGRAM_PAIR), labels.pairNote))
        }
        if (current.authorizedChats.isNotEmpty()) {
            add(TuiSettingField.Action(labels.sendTest, TuiSettingAction(TuiSettingsCommands.TELEGRAM_TEST)))
        }
        current.authorizedChats.sortedBy { chat -> chat.id }.forEach { chat ->
            add(TuiSettingField.Toggle(labels.chatReadOnly(chat.name), chat.readOnly, TuiSettingAction(TuiSettingsCommands.chatReadOnly(chat.id), (!chat.readOnly).toString())))
            add(TuiSettingField.Action(labels.removeChat(chat.name), TuiSettingAction(TuiSettingsCommands.chatRemove(chat.id)), confirm = true))
        }
        add(stepper(labels.dailySummary, "telegram.daily_summary_hour", value, listOf(ConfigValues.OFF) + (0..23).map(Int::toString)) { hour ->
            hour.padStart(2, '0') + ":00"
        })
        add(toggle(labels.livePanel, "telegram.live_panel", value))
        add(toggle(labels.sourceControl, "telegram.source_control", value))
        add(toggle(labels.pcActions, "telegram.pc_actions", value))
        add(toggle(labels.machineName, "telegram.machine_name", value))
        add(toggle(labels.showTeam, "telegram.team", value))
        add(toggle(labels.remoteUpdate, "telegram.remote_update", value))
        add(toggle(labels.soundOnlyCritical, "telegram.sound_only_critical", value))
        add(toggle(labels.groupRepeated, "telegram.group_repeated", value))
        add(toggle(labels.batchAlerts, "telegram.batch_alerts", value))
        add(toggle(labels.compactPanel, "telegram.compact_panel", value))
        add(toggle(labels.sourceHealth, "telegram.source_health_alerts", value))
    }

    private fun team(team: TeamIntegrationSettings?, labels: TuiSettingsLabels, glyphs: TerminalGlyphs): List<TuiSettingField> = buildList {
        val active = team?.isActive == true
        add(TuiSettingField.Info(labels.teamState, if (active) labels.on else labels.off))
        add(TuiSettingField.Info(labels.server, team?.normalizedServerUrl?.ifEmpty { null } ?: labels.none))
        add(TuiSettingField.Info(labels.alias, team?.alias?.ifBlank { null } ?: labels.none))
        add(TuiSettingField.Info(labels.teamAccounts, inputs.teamAccounts(team).joinToString(", ").ifEmpty { labels.none }))
        // Entrar com a chave usa o servidor, o apelido e as contas que já estão gravados.
        if (team != null && team.normalizedServerUrl.isNotEmpty() && team.alias.isNotBlank()) {
            add(TuiSettingField.Text(labels.teamKey, glyphs.secretMask.takeIf { team.apiKey.isNotBlank() }, TuiSettingEdit(TuiSettingsCommands.teamKey(team.participatingProfileIds), secret = true), labels.teamKeyNote))
        }
        if (active) {
            add(TuiSettingField.Action(labels.leave, TuiSettingAction(TuiSettingsCommands.TEAM_LEAVE), labels.leaveNote, confirm = true))
        }
    }

    private fun network(labels: TuiSettingsLabels, glyphs: TerminalGlyphs, value: (String) -> String?): List<TuiSettingField> = listOf(
        toggle(labels.proxyEnvironment, "proxy.use_environment", value),
        text(labels.proxyHost, "proxy.host", value, note = null),
        text(labels.proxyPort, "proxy.port", value, note = null),
        text(labels.proxyUser, "proxy.username", value, note = null),
        TuiSettingField.Text(labels.proxyPassword, value("proxy.password")?.let { glyphs.secretMask }, TuiSettingEdit(TuiSettingsCommands.setId("proxy.password"), secret = true))
    )

    private fun web(labels: TuiSettingsLabels): List<TuiSettingField> = buildList {
        val current = inputs.web()
        add(TuiSettingField.Toggle(labels.web, current.enabled, TuiSettingAction(TuiSettingsCommands.WEB, (!current.enabled).toString()), labels.webPort(current.port)))
        if (current.token.isNotBlank()) {
            add(TuiSettingField.Action(labels.webAddress, TuiSettingAction(TuiSettingsCommands.WEB_TOKEN), labels.webAddressNote, confirm = true))
        }
    }

    private fun system(labels: TuiSettingsLabels, value: (String) -> String?): List<TuiSettingField> = buildList {
        add(stepper(labels.languageLabel, "language", value, listOf("pt", "en")) { language -> if (language == "pt") "Português" else "English" })
        add(toggle(labels.autoUpdate, "updates.auto", value))
        add(toggle(labels.betaUpdates, "updates.beta", value))
        if (inputs.autostartAvailable) {
            add(toggle(labels.autostart, "system.autostart", value))
        }
    }

    private fun toggle(label: String, key: String, value: (String) -> String?): TuiSettingField {
        val on = value(key) == "true"
        return TuiSettingField.Toggle(label, on, TuiSettingsCommands.set(key, (!on).toString()))
    }

    private fun text(label: String, key: String, value: (String) -> String?, note: String?): TuiSettingField {
        val current = value(key)
        return TuiSettingField.Text(label, current, TuiSettingEdit(TuiSettingsCommands.setId(key), current.orEmpty()), note)
    }

    /**
     * `‹ valor ›` sobre [options], na ordem. Valor gravado fora da lista (o `/avise 20` do bot)
     * fica onde está, e as setas vão para o vizinho de cada lado pela ordem numérica.
     */
    private fun stepper(label: String, key: String, value: (String) -> String?, options: List<String>, show: (String) -> String): TuiSettingField {
        val current = value(key) ?: ConfigValues.OFF
        fun shown(option: String): String = if (option == ConfigValues.OFF) offText else show(option)
        val index = options.indexOf(current)
        val (previous, next) = if (index >= 0) {
            options.getOrNull(index - 1) to options.getOrNull(index + 1)
        } else {
            val number = current.toDoubleOrNull()
            val numeric = options.filter { option -> option.toDoubleOrNull() != null }
            val below = numeric.lastOrNull { option -> number != null && option.toDouble() < number } ?: options.first().takeIf { first -> first == ConfigValues.OFF }
            below to numeric.firstOrNull { option -> number != null && option.toDouble() > number }
        }
        return TuiSettingField.Stepper(
            label, shown(current),
            previous?.let { option -> TuiSettingsCommands.set(key, option) },
            next?.let { option -> TuiSettingsCommands.set(key, option) }
        )
    }
}
