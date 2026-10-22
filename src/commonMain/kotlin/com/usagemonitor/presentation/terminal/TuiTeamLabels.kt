package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/** Os textos da aba 4, Time (3.4). */
internal class TuiTeamLabels(val language: AppLanguage) {
    fun counts(people: Int, active: Int): String =
        localized(
            language,
            (if (people == 1) "1 pessoa" else "$people pessoas") + " · " + (if (active == 1) "1 ativa agora" else "$active ativas agora"),
            (if (people == 1) "1 person" else "$people people") + " · $active active now"
        )

    val headerPerson: String get() = localized(language, "PESSOA", "PERSON")
    val headerNow: String get() = localized(language, "AGORA", "NOW")
    val headerSessions: String get() = localized(language, "SESSÕES", "SESSIONS")
    val headerTokens: String get() = "TOKENS"
    val headerToday: String get() = localized(language, "HOJE", "TODAY")

    fun working(sessions: Int): String =
        localized(language, if (sessions == 1) "trabalhando · 1 sessão" else "trabalhando · $sessions sessões", if (sessions == 1) "working · 1 session" else "working · $sessions sessions")

    fun appOpen(lastTurn: String?): String =
        lastTurn?.let { ago -> localized(language, "app aberto · turno $ago", "app open · turn $ago") } ?: localized(language, "app aberto", "app open")

    fun away(ago: String?): String = ago?.let { text -> localized(language, "ausente · $text", "away · $text") } ?: localized(language, "ausente", "away")

    /** A legenda, com os glifos de presença do perfil (`● ◑ ○`, ou `* + -` no ASCII). */
    fun legend(glyphs: TerminalGlyphs): String =
        localized(
            language,
            "${glyphs.presenceWorking} trabalhando · ${glyphs.presenceOnline} app aberto · ${glyphs.presenceAway} ausente",
            "${glyphs.presenceWorking} working · ${glyphs.presenceOnline} app open · ${glyphs.presenceAway} away"
        )

    /** A segunda linha da legenda: numa só, ela não cabe em 96 colunas. */
    val privacy: String get() = localized(language, "custo calculado no seu computador · nunca prompt nem resposta", "cost computed on your computer · never a prompt or a reply")

    val noMembers: String get() = localized(language, "Ninguém enviou uso hoje para esta conta.", "Nobody sent usage for this account today.")

    val unreachable: String get() = localized(language, "o servidor do time não respondeu", "the team server did not answer")

    fun failed(reason: String): String = localized(language, "Não deu para ler o time: $reason", "Could not read the team: $reason")

    fun showing(clock: String?): String =
        clock?.let { time -> localized(language, "Mostrando os números de $time; tenta de novo na próxima leitura.", "Showing the numbers from $time; tries again on the next reading.") }
            ?: localized(language, "Ainda sem números do time; tenta de novo na próxima leitura.", "No team numbers yet; tries again on the next reading.")

    val keyPerson: String get() = localized(language, "pessoa", "person")
    val keySwitch: String get() = localized(language, "troca de conta", "switches account")
}
