package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.presentation.ui.components.riskLevelLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Textos do bot do Telegram (#387), no idioma do app. Só metadados de uso —
 * nunca prompt, resposta ou caminho de projeto.
 */
internal object TelegramBotMessages {

    /** `/status`: uma linha por conta, cotas com percentual e reinício em BRT. */
    fun status(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) {
            return if (pt) "Sem leitura ainda. Abra o Usage Monitor e habilite ao menos uma API." else "No reading yet. Open Usage Monitor and enable at least one API."
        }
        val lines = snapshot.accounts.map { account ->
            val quotas = account.quotas.joinToString(" · ") { quota ->
                val value = when {
                    quota.percent != null -> "${quota.percent}%"
                    quota.unit == UsageUnit.CURRENCY_USD -> formatCents(quota.total - quota.used, quota.currencyCode)
                    else -> quota.used.toString()
                }
                val reset = quota.resetsAt?.let { instant -> " (${clock(instant)})" }.orEmpty()
                "${quota.label} $value$reset"
            }
            val active = if (account.active) (if (pt) " · em uso" else " · in use") else ""
            val risk = account.worstRisk?.let { level -> " · ${riskLevelLabel(level, language)}" }.orEmpty()
            "${account.label}$risk$active\n  $quotas"
        }
        // A hora é a da coleta, não a do pedido: o número pode ter minutos.
        val collected = snapshot.lastCollectedAt?.let { at -> "${clock(at)} BRT" } ?: "—"
        val header = if (pt) "Usage Monitor · coleta $collected" else "Usage Monitor · collected $collected"
        return (listOf(header) + lines).joinToString("\n")
    }

    fun help(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Comandos:\n/status — cotas de todas as contas\n/alertas on|off — liga ou desliga os alertas\n" +
            "/silencio 22-07 — horário de silêncio (off desliga)\n/limiar 75,90 — limiares de alerta de cota"
    } else {
        "Commands:\n/status — quotas of every account\n/alerts on|off — turn alerts on or off\n" +
            "/quiet 22-07 — quiet hours (off disables)\n/threshold 75,90 — quota alert thresholds"
    }

    fun paired(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Conversa pareada com o Usage Monitor. Envie /ajuda para ver os comandos." else "Chat paired with Usage Monitor. Send /help for commands."

    fun applied(command: BotCommand, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (command) {
            is BotCommand.Alerts -> if (command.enabled) (if (pt) "Alertas ligados." else "Alerts on.") else (if (pt) "Alertas desligados." else "Alerts off.")
            is BotCommand.Quiet -> command.hours?.let { hours ->
                val range = "${hours.startHour.toString().padStart(2, '0')}:00 → ${hours.endHour.toString().padStart(2, '0')}:00"
                if (pt) "Silêncio $range aplicado." else "Quiet hours $range set."
            } ?: if (pt) "Silêncio desligado." else "Quiet hours off."
            is BotCommand.Threshold -> (if (pt) "Limiares de cota: " else "Quota thresholds: ") + command.percents.joinToString(", ") { "$it%" }
            else -> help(language)
        }
    }

    fun invalid(usage: String, language: AppLanguage): String =
        if (language == AppLanguage.PT) "Não entendi. Uso: $usage" else "Not understood. Usage: $usage"

    /** O alerta da bandeja, no mesmo texto: título e corpo de `usageAlertMessage`. */
    fun alert(alert: UsageAlert, language: AppLanguage): String {
        val message = usageAlertMessage(alert, language)
        return "${message.title}\n${message.body}"
    }

    fun test(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Mensagem de teste do Usage Monitor. Os alertas chegam aqui." else "Usage Monitor test message. Alerts arrive here."

    private fun clock(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun formatCents(cents: Long, currency: String): String {
        val units = cents / 100
        val rest = (cents % 100).toString().padStart(2, '0')
        val symbol = if (currency == "USD") "US$" else currency
        return "$symbol $units,$rest"
    }
}
