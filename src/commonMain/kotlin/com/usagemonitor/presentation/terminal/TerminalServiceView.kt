package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.CollectorSourceHealth
import com.usagemonitor.domain.entity.CollectorTeamHealth
import com.usagemonitor.domain.entity.CollectorTelegramHealth
import com.usagemonitor.domain.entity.CollectorWebHealth
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.localized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Como o Usage Monitor chegou a este computador, para dizer como atualizar. */
internal enum class TerminalInstallKind { PACKAGE, SCRIPT }

/** O que o `service status` junta: o systemd, o registro de quem coleta, a saúde e a leitura. */
internal data class TerminalServiceStatus(
    val unitInstalled: Boolean,
    val unitActive: Boolean,
    val collector: CollectorKind?,
    val collectorPid: Long? = null,
    val collectorSince: Instant? = null,
    val memoryBytes: Long? = null,
    val lastCollectedAt: Instant? = null,
    val nextCollectionAt: Instant? = null,
    val zones: List<QuotaZone?> = emptyList(),
    val healthUpdatedAt: Instant? = null,
    val sources: List<CollectorSourceHealth> = emptyList(),
    val historyPath: String? = null,
    val historyBytes: Long? = null,
    val telegram: CollectorTelegramHealth? = null,
    val lastAlertAt: Instant? = null,
    val team: CollectorTeamHealth? = null,
    val web: CollectorWebHealth? = null,
    val version: String,
    val installKind: TerminalInstallKind? = null
)

/** Os textos do `service status` (5.1 do protótipo). */
internal class TerminalServiceStatusLabels(val language: AppLanguage) {
    private val reading = TerminalLabels(language)

    fun active(since: Duration?): String = localized(language, " ativo", " active") + (since?.let { duration -> localized(language, " há ", " for ") + TerminalLabels.countdown(duration) } ?: "")
    val stopped: String get() = localized(language, " parado", " stopped")
    val notInstalled: String get() = localized(language, " serviço não instalado", " service not installed")
    fun foreground(since: Duration?): String = localized(language, " coletando em primeiro plano (service run)", " collecting in the foreground (service run)") + (since?.let { duration -> localized(language, " há ", " for ") + TerminalLabels.countdown(duration) } ?: "")
    val appCollects: String get() = localized(language, " quem coleta é o app aberto", " the open app is collecting")
    val rowCollection: String get() = localized(language, "coleta", "collection")
    val rowSources: String get() = localized(language, "fontes", "sources")
    val rowHistory: String get() = localized(language, "histórico", "history")
    val rowTelegram: String get() = "telegram"
    val rowTeam: String get() = localized(language, "time", "team")
    val rowWeb: String get() = localized(language, "acesso web", "web access")
    val rowVersion: String get() = localized(language, "versão", "version")
    fun last(clock: String): String = localized(language, "última $clock BRT", "last $clock BRT")
    fun next(clock: String): String = localized(language, "próxima $clock", "next $clock")
    fun quotas(count: Int, critical: Int): String = reading.quotaCount(count) + if (critical > 0) ", " + reading.zoneCount(QuotaZone.CRITICAL, critical) else ""
    val noReading: String get() = localized(language, "sem leitura ainda", "no reading yet")
    val off: String get() = localized(language, "desligado", "off")
    fun connected(bot: String?, chats: Int): String =
        (bot?.let { name -> localized(language, "conectado como @$name", "connected as @$name") } ?: localized(language, "conectado", "connected")) +
            " · " + localized(language, if (chats == 1) "1 conversa" else "$chats conversas", if (chats == 1) "1 chat" else "$chats chats")
    val connecting: String get() = localized(language, "conectando", "connecting")
    val tokenRejected: String get() = localized(language, "token recusado pelo Telegram", "token rejected by Telegram")
    fun failed(detail: String?): String = localized(language, "falhou", "failed") + (detail?.let { text -> ": $text" } ?: "")
    fun lastAlert(clock: String): String = localized(language, "último aviso $clock BRT", "last alert $clock BRT")
    fun sent(age: Duration, server: String?): String = localized(language, "enviado ${reading.ago(age)}", "sent ${reading.ago(age)}") + (server?.let { host -> localized(language, " para $host", " to $host") } ?: "")
    val notSentYet: String get() = localized(language, "ligado, nada enviado ainda", "on, nothing sent yet")
    fun web(addresses: List<String>, port: Int?): String = (addresses.map { address -> "http://$address:$port" }.ifEmpty { listOf("http://localhost:$port") }).joinToString(", ") + localized(language, " · exige o token", " · needs the token")
    val package_: String get() = localized(language, "pacote do sistema (atualize com o apt ou o dnf)", "system package (update with apt or dnf)")
    val script: String get() = localized(language, "instalado pelo script (atualize rodando o instalador da versão nova)", "installed by the script (update by running the new version's installer)")
    fun staleHealth(clock: String): String = localized(language, "a saúde é de $clock BRT: o coletor pode estar parado", "the health is from $clock BRT: the collector may be stopped")
    fun megabytes(bytes: Long): String = "${bytes / (1024L * 1024L)} MB"
}

/** `usage-monitor service status` (5.1): a saúde de cada parte, uma por linha. */
internal object TerminalServiceView {
    private val LABEL_WIDTH = 13
    private val STALE_HEALTH = 2.minutes

    fun status(input: TerminalServiceStatus, now: Instant, labels: TerminalServiceStatusLabels, profile: TerminalProfile): TerminalOutput {
        val glyphs = profile.glyphs
        val since = input.collectorSince?.let { at -> now - at }
        val header = when {
            input.unitInstalled && input.unitActive -> headerLine(TerminalColor.Ok, "usage-monitor", labels.active(since), input, labels, glyphs)
            input.collector == CollectorKind.SERVICE -> headerLine(TerminalColor.Ok, "usage-monitor", labels.foreground(since), input, labels, glyphs)
            input.collector == CollectorKind.APP -> headerLine(TerminalColor.Ok, "usage-monitor", labels.appCollects, input, labels, glyphs)
            input.unitInstalled -> headerLine(TerminalColor.Crit, "usage-monitor", labels.stopped, input.copy(collectorPid = null, memoryBytes = null), labels, glyphs)
            else -> headerLine(TerminalColor.Muted, "usage-monitor", labels.notInstalled, input.copy(collectorPid = null, memoryBytes = null), labels, glyphs)
        }
        val lines = mutableListOf(header)
        val collecting = input.collector != null
        lines += row(labels.rowCollection, collectionText(input, labels))
        if (collecting && input.sources.isNotEmpty()) {
            val spans = input.sources.flatMapIndexed { index, source ->
                listOfNotNull(
                    TerminalSpan(if (index == 0) "" else "  "),
                    TerminalSpan(if (source.ok) glyphs.done else glyphs.warning, if (source.ok) TerminalColor.Ok else TerminalColor.Warn),
                    TerminalSpan(" " + source.label)
                )
            }
            lines += TerminalLine(listOf(TerminalSpan("  " + labels.rowSources.padEnd(LABEL_WIDTH), TerminalColor.Muted)) + spans)
        }
        input.historyPath?.let { path -> lines += row(labels.rowHistory, path + (input.historyBytes?.let { bytes -> " · " + labels.megabytes(bytes) } ?: "")) }
        if (collecting) {
            input.telegram?.let { telegram -> lines += row(labels.rowTelegram, telegramText(telegram, input.lastAlertAt, labels)) }
            input.team?.let { team -> lines += row(labels.rowTeam, teamText(team, now, labels)) }
            input.web?.let { web -> lines += row(labels.rowWeb, webText(web, labels)) }
        }
        lines += row(labels.rowVersion, input.version + when (input.installKind) {
            TerminalInstallKind.PACKAGE -> " · " + labels.package_
            TerminalInstallKind.SCRIPT -> " · " + labels.script
            null -> ""
        })
        val healthAt = input.healthUpdatedAt
        if (collecting && healthAt != null && now - healthAt > STALE_HEALTH) {
            lines += terminalLine(TerminalSpan(glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(labels.staleHealth(TelegramBotMessages.clock(healthAt))))
        }
        return TerminalOutput(lines, if (collecting) 0 else 1)
    }

    private fun headerLine(color: TerminalColor, name: String, state: String, input: TerminalServiceStatus, labels: TerminalServiceStatusLabels, glyphs: TerminalGlyphs): TerminalLine {
        val details = listOfNotNull(input.collectorPid?.let { pid -> "pid $pid" }, input.memoryBytes?.let(labels::megabytes))
        return terminalLine(
            TerminalSpan(glyphs.sourceMark + " ", color),
            TerminalSpan(name, bold = true),
            TerminalSpan(state, color),
            TerminalSpan(details.joinToString("") { detail -> " ${glyphs.separator} $detail" }, TerminalColor.Muted)
        )
    }

    private fun row(label: String, value: String): TerminalLine =
        terminalLine(TerminalSpan("  " + label.padEnd(LABEL_WIDTH), TerminalColor.Muted), TerminalSpan(value))

    private fun collectionText(input: TerminalServiceStatus, labels: TerminalServiceStatusLabels): String {
        val last = input.lastCollectedAt ?: return labels.noReading
        val critical = input.zones.count { zone -> zone == QuotaZone.CRITICAL }
        val parts = mutableListOf(labels.last(TelegramBotMessages.clock(last)))
        input.nextCollectionAt?.let { next -> parts += labels.next(TelegramBotMessages.clock(next)) }
        parts += labels.quotas(input.zones.size, critical)
        return parts.joinToString(" · ")
    }

    private fun telegramText(telegram: CollectorTelegramHealth, lastAlertAt: Instant?, labels: TerminalServiceStatusLabels): String {
        val state = when (telegram.state) {
            "connected" -> labels.connected(telegram.botUsername, telegram.chatCount)
            "connecting" -> labels.connecting
            "token_rejected" -> labels.tokenRejected
            "failed" -> labels.failed(null)
            else -> labels.off
        }
        return state + (lastAlertAt?.let { at -> " · " + labels.lastAlert(TelegramBotMessages.clock(at)) } ?: "")
    }

    private fun teamText(team: CollectorTeamHealth, now: Instant, labels: TerminalServiceStatusLabels): String {
        if (!team.active) return labels.off
        team.lastFailure?.let { failure -> return labels.failed(failure) }
        val host = team.server?.substringAfter("://")?.substringBefore('/')
        return team.lastSuccessAtEpochMillis?.let { at -> labels.sent(now - Instant.fromEpochMilliseconds(at), host) } ?: labels.notSentYet
    }

    private fun webText(web: CollectorWebHealth, labels: TerminalServiceStatusLabels): String =
        when (web.state) {
            "running" -> labels.web(web.addresses, web.port)
            "failed" -> labels.failed(web.failure)
            else -> labels.off
        }
}
