package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ModelShareBasis
import com.usagemonitor.presentation.ui.localized

/** Os textos da aba 2, Sessões (3.2). */
internal class TuiSessionsLabels(val language: AppLanguage) {

    fun period(period: TuiSessionPeriod): String =
        when (period) {
            TuiSessionPeriod.TODAY -> localized(language, "hoje", "today")
            TuiSessionPeriod.LAST_7_DAYS -> localized(language, "7 dias", "7 days")
            TuiSessionPeriod.LAST_30_DAYS -> localized(language, "30 dias", "30 days")
        }

    fun sessionCount(count: Int): String = localized(language, if (count == 1) "1 sessão" else "$count sessões", if (count == 1) "1 session" else "$count sessions")

    val headerProject: String get() = localized(language, "PROJETO", "PROJECT")
    val headerModel: String get() = localized(language, "MODELO", "MODEL")
    val headerDuration: String get() = localized(language, "DURAÇÃO", "DURATION")
    val headerTokens: String get() = "TOKENS"
    val headerCost: String get() = localized(language, "CUSTO", "COST")
    val headerThroughput: String get() = localized(language, "VAZÃO", "SPEED")
    val headerTime: String get() = localized(language, "HORA", "TIME")
    val headerInput: String get() = localized(language, "ENTRADA", "INPUT")
    val headerOutput: String get() = localized(language, "SAÍDA", "OUTPUT")
    val headerCacheRead: String get() = localized(language, "CACHE LIDO", "CACHE READ")

    val noSessions: String get() = localized(language, "Nenhuma sessão CLI neste período.", "No CLI session in this period.")
    val noProject: String get() = localized(language, "(sem projeto)", "(no project)")

    fun unpriced(models: List<String>): String =
        localized(
            language,
            "+ ${models.joinToString(", ")} sem tarifa na tabela; o custo mostrado é parcial",
            "+ ${models.joinToString(", ")} not in the price table; the cost shown is partial"
        )

    fun startedAt(clock: String): String = localized(language, "iniciada $clock BRT", "started $clock BRT")

    fun turnCount(count: Int): String = localized(language, if (count == 1) "1 turno" else "$count turnos", if (count == 1) "1 turn" else "$count turns")

    val composition: String get() = localized(language, "composição", "tokens")
    val models: String get() = localized(language, "modelos", "models")

    /** A base da fatia, na segunda linha de modelos: cabe nas 12 células da legenda. */
    fun shareBasis(basis: ModelShareBasis): String =
        when (basis) {
            ModelShareBasis.COST -> localized(language, "% do custo", "% of cost")
            ModelShareBasis.TOKENS -> localized(language, "% tokens", "% of tokens")
        }

    /** Modelo sem tarifa numa base de custo: palavra, nunca 0%. */
    val unpricedShare: String get() = localized(language, "sem tarifa", "no price")
    val unknownModel: String get() = localized(language, "(desconhecido)", "(unknown)")

    /** O separador vem do perfil: sem Unicode o `·` vira o da tabela de glifos. */
    fun modelCache(read: String, written: String, separator: String): String =
        localized(language, "lido $read $separator gravado $written", "read $read $separator written $written")

    fun moreModels(count: Int): String = localized(language, if (count == 1) "+1 modelo" else "+$count modelos", if (count == 1) "+1 model" else "+$count models")

    val context: String get() = localized(language, "contexto", "context")
    val seriesInput: String get() = localized(language, "entrada", "input")
    val seriesOutput: String get() = localized(language, "saída", "output")
    val seriesCacheRead: String get() = localized(language, "cache lido", "cache read")
    val seriesCacheWrite: String get() = localized(language, "cache gravado", "cache written")

    /** A letra de cada série sem cor, uma por série: a inicial colidiria nos dois caches. */
    val markInput: Char get() = localized(language, "e", "i").single()
    val markOutput: Char get() = localized(language, "s", "o").single()
    val markCacheRead: Char get() = localized(language, "l", "r").single()
    val markCacheWrite: Char get() = localized(language, "g", "w").single()

    val pt: Boolean
        get() = when (language) {
            AppLanguage.PT -> true
            AppLanguage.EN -> false
        }

    fun contextOf(window: String): String = localized(language, " de $window", " of $window")

    val keySession: String get() = localized(language, "sessão", "session")
    val keyPeriod: String get() = localized(language, "período", "period")
    val keyTurns: String get() = localized(language, "turnos", "turns")
    val keyScroll: String get() = localized(language, "rola", "scrolls")
    val keyBack: String get() = localized(language, "volta às sessões", "back to the sessions")
    val keyCsv: String get() = "CSV"

    fun exported(path: String, count: Int): String =
        localized(language, "CSV com $count sessões do Claude Code salvo em $path", "CSV with $count Claude Code sessions saved to $path")

    fun exportFailed(reason: String): String = localized(language, "O CSV não foi salvo: $reason", "The CSV was not saved: $reason")

    val nothingToExport: String
        get() = localized(language, "Nenhuma sessão do Claude Code neste período para exportar.", "No Claude Code session in this period to export.")

    val noTurns: String get() = localized(language, "Os turnos desta sessão não estão no índice.", "This session's turns are not in the index.")
}
