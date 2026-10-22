package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.localized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Os textos do modo terminal nos dois idiomas do app (decisão D1 da #439). Objeto de
 * rótulos, e não `== AppLanguage.PT` espalhado: a catraca de verificação inline não sobe.
 */
internal class TerminalLabels(val language: AppLanguage) {

    /**
     * Os formatadores que o terminal divide com o bot (reinício, valor de saldo) recebem
     * `pt`. A conversão mora aqui, num lugar só, e não em cada chamada.
     */
    val pt: Boolean
        get() = when (language) {
            AppLanguage.PT -> true
            AppLanguage.EN -> false
        }

    fun zone(zone: QuotaZone): String =
        when (zone) {
            QuotaZone.ON_TRACK -> localized(language, "em dia", "on track")
            QuotaZone.ATTENTION -> localized(language, "atenção", "warning")
            QuotaZone.CRITICAL -> localized(language, "crítico", "critical")
        }

    /** A palavra mais longa entre as três, para a coluna de estado não dançar entre linhas. */
    val zoneColumnWidth: Int
        get() = QuotaZone.entries.maxOf { zone -> zone(zone).length }

    fun inDuration(duration: Duration): String = localized(language, "em ", "in ") + countdown(duration)

    /** "há 38 s", "há 18 min", "há 2 h": a idade de uma leitura. */
    fun ago(duration: Duration): String {
        val amount = amount(duration)
        return localized(language, "há $amount", "$amount ago")
    }

    /** "38 s", "18 min", "2 h": um tempo decorrido, com segundos abaixo de um minuto. */
    private fun amount(duration: Duration): String {
        val seconds = duration.inWholeSeconds.coerceAtLeast(0L)
        return when {
            seconds < 60L -> "$seconds s"
            seconds < 3_600L -> "${seconds / 60L} min"
            else -> "${seconds / 3_600L} h"
        }
    }

    /** "lido há 38 s pelo app aberto" ou "pelo serviço": de onde e de quando é a leitura. */
    fun readBy(collector: CollectorKind, age: Duration): String =
        when (collector) {
            CollectorKind.APP -> localized(language, "lido ${ago(age)} pelo app aberto", "read ${ago(age)} by the open app")
            CollectorKind.SERVICE -> localized(language, "lido ${ago(age)} pelo serviço", "read ${ago(age)} by the service")
        }

    fun quotaCount(count: Int): String = localized(language, "$count cotas", if (count == 1) "1 quota" else "$count quotas")

    fun zoneCount(zone: QuotaZone, count: Int): String =
        when (zone) {
            QuotaZone.CRITICAL -> localized(language, if (count == 1) "1 crítica" else "$count críticas", "$count critical")
            QuotaZone.ATTENTION -> localized(language, "$count em atenção", "$count warning")
            QuotaZone.ON_TRACK -> localized(language, "$count em dia", "$count on track")
        }

    val headerSource: String get() = localized(language, "FONTE", "SOURCE")
    val headerAccount: String get() = localized(language, "CONTA", "ACCOUNT")
    val headerQuota: String get() = localized(language, "COTA", "QUOTA")
    val headerUsage: String get() = localized(language, "USO", "USAGE")
    val headerState: String get() = localized(language, "ESTADO", "STATE")
    val headerResets: String get() = localized(language, "REINICIA", "RESETS")
    val resetting: String get() = localized(language, "reiniciando", "resetting")

    /**
     * Quando a cota reinicia, em BRT. Até 6 dias, o formato do bot ("15:10", "qui 21:00"),
     * com um dono só. Mais longe, o dia da semana repetiria o de agora e ficaria ambíguo:
     * sai a data ("05/11 15:47", e "11/05 15:47" em inglês, a ordem do app).
     */
    fun resetAt(at: Instant, now: Instant): String {
        if (at - now < 6.days) {
            return TelegramBotMessages.resetLabel(at, now, pt)
        }
        val date = at.toLocalDateTime(SAO_PAULO).date
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthNumber.toString().padStart(2, '0')
        return localized(language, "$day/$month", "$month/$day") + " " + TelegramBotMessages.clock(at)
    }

    fun nothingCollecting(at: String, age: Duration): String =
        localized(
            language,
            "Nada está coletando. Mostrando a última leitura, de $at BRT (${ago(age)}).",
            "Nothing is collecting. Showing the last reading, from $at BRT (${ago(age)})."
        )

    val openAppToRefresh: String
        get() = localized(language, "Abra o Usage Monitor para voltar a atualizar. Até lá os números não mudam.", "Open Usage Monitor to refresh again. Until then the numbers stay the same.")

    val noReadingYet: String
        get() = localized(language, "Ainda não há leitura. Abra o Usage Monitor e espere a primeira coleta.", "There is no reading yet. Open Usage Monitor and wait for the first collection.")

    val noReadingWord: String get() = localized(language, "sem leitura", "no reading")

    fun limit(percent: Int): String = localized(language, "limite $percent%", "limit $percent%")

    fun highest(quota: String): String = localized(language, "maior uso: $quota", "highest: $quota")

    fun unknownQuota(id: String): String = localized(language, "a cota '$id' não existe", "there is no quota '$id'")

    fun knownQuotas(ids: List<String>): String = localized(language, "Cotas: ", "Quotas: ") + ids.joinToString(", ")

    fun noPercent(name: String): String = localized(language, "$name não tem percentual para esperar", "$name has no percentage to wait for")

    fun reachedBelow(name: String, percent: Int, below: Int): String =
        localized(language, "$name está em $percent%, abaixo de $below%", "$name is at $percent%, below $below%")

    fun waitingFor(name: String, percent: Int, below: Int): String =
        localized(language, "aguardando $name cair abaixo de $below% (agora $percent%)", "waiting for $name to drop below $below% (now $percent%)")

    fun resetsAtIn(at: String, remaining: Duration): String =
        localized(language, "reinicia $at BRT, ", "resets $at BRT, ") + inDuration(remaining)

    val session: String get() = localized(language, "sessão", "session")

    /** O estado em quatro letras, para a barra do tmux; em dia não se escreve ali. */
    fun zoneShort(zone: QuotaZone): String =
        when (zone) {
            QuotaZone.ON_TRACK -> localized(language, "ok", "ok")
            QuotaZone.ATTENTION -> localized(language, "atn", "warn")
            QuotaZone.CRITICAL -> localized(language, "crít", "crit")
        }

    fun resetsIn(remaining: Duration): String = localized(language, "reinicia ", "resets ") + inDuration(remaining)

    fun readingFrom(clock: String): String = localized(language, "leitura de $clock", "reading from $clock")

    val waitNeedsCollector: String
        get() = localized(language, "Nada está coletando: a cota só muda com o Usage Monitor aberto.", "Nothing is collecting: the quota only changes with Usage Monitor open.")

    fun waitTimedOut(id: String, below: Int): String =
        localized(language, "tempo esgotado: '$id' não caiu abaixo de $below%", "timed out: '$id' did not drop below $below%")

    fun staleReading(age: Duration, maxAge: Duration): String =
        localized(
            language,
            "a última tem ${amount(age)}, mais que os ${amount(maxAge)} pedidos",
            "the last one is ${amount(age)} old, more than the ${amount(maxAge)} asked for"
        )

    companion object {
        private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

        /** "47 min", "2h17", "4d18h": quanto falta, sem arredondar para cima. */
        fun countdown(duration: Duration): String {
            val minutes = duration.inWholeMinutes.coerceAtLeast(0L)
            return when {
                minutes < 60L -> "$minutes min"
                minutes < 1_440L -> "${minutes / 60L}h${(minutes % 60L).toString().padStart(2, '0')}"
                else -> "${minutes / 1_440L}d${(minutes % 1_440L / 60L).toString().padStart(2, '0')}h"
            }
        }
    }
}
