package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.MICROS_PER_USD
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formata micros de USD como `$1.2345` — quatro casas, porque turnos custam centavos de centavo. */
internal fun formatMicrosUsd(micros: Long): String {
    val sign = if (micros < 0L) "-" else ""
    val absolute = abs(micros)
    val dollars = absolute / MICROS_PER_USD
    val fraction = (absolute % MICROS_PER_USD) / 100L
    return "$sign\$$dollars.${fraction.toString().padStart(4, '0')}"
}

/** Versão curta para totais de lista: `$12.34`. */
internal fun formatMicrosUsdShort(micros: Long): String {
    val sign = if (micros < 0L) "-" else ""
    val absolute = abs(micros)
    val dollars = absolute / MICROS_PER_USD
    val cents = (absolute % MICROS_PER_USD) / 10_000L
    return "$sign\$$dollars.${cents.toString().padStart(2, '0')}"
}

internal fun formatPercent(fraction: Double): String {
    return "${(fraction * 100.0).roundToLong()}%"
}

internal fun shortSessionId(sessionId: String): String {
    return sessionId.take(8)
}

internal fun cliSessionsTitle(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Sessões CLI" else "CLI Sessions"
}

/** Título da janela nomeando a conta, para não confundir sessões de perfis diferentes. */
internal fun cliSessionsWindowTitle(language: AppLanguage, profileLabel: String?): String {
    val base = cliSessionsTitle(language)
    if (profileLabel.isNullOrBlank()) {
        return base
    }
    return "$base — $profileLabel"
}

internal object CliSessionsLabels {

    fun sessionCount(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            if (count == 1) "1 sessão" else "$count sessões"
        } else {
            if (count == 1) "1 session" else "$count sessions"
        }
    }

    fun estimatedTotal(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "custo estimado" else "estimated cost"
    }

    /**
     * Deixa explícito que os totais do header são só do período selecionado — e,
     * quando o corte está ancorado na quota, até quando essa janela vale. Sem o
     * horário, "5h" seria confundido com as últimas cinco horas corridas.
     */
    fun estimatedTotalInRange(
        range: CliSessionRange,
        endsAt: Instant?,
        language: AppLanguage
    ): String {
        val window = rangeLabel(range, language)
        if (endsAt == null) {
            return if (language == AppLanguage.PT) {
                "custo estimado · últimas $window"
            } else {
                "estimated cost · last $window"
            }
        }
        return if (language == AppLanguage.PT) {
            "custo estimado · janela $window até ${formatInstant(endsAt)}"
        } else {
            "estimated cost · $window window until ${formatInstant(endsAt)}"
        }
    }

    fun rangeLabel(range: CliSessionRange, language: AppLanguage): String {
        return when (range) {
            CliSessionRange.LAST_5H -> "5h"
            CliSessionRange.LAST_7D -> if (language == AppLanguage.PT) "7 dias" else "7 days"
            CliSessionRange.LAST_30D -> if (language == AppLanguage.PT) "30 dias" else "30 days"
            CliSessionRange.ALL -> if (language == AppLanguage.PT) "Total" else "All time"
        }
    }

    fun refresh(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Atualizar" else "Refresh"
    }

    fun back(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Voltar" else "Back"
    }

    fun empty(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Nenhuma sessão do Claude Code encontrada em ~/.claude/projects."
        } else {
            "No Claude Code sessions found in ~/.claude/projects."
        }
    }

    /**
     * Lista vazia com filtro ativo não significa índice vazio — dizer que não há
     * nada em `~/.claude/projects` seria falso na maioria das vezes.
     */
    fun emptyInRange(range: CliSessionRange, isAnchored: Boolean, language: AppLanguage): String {
        if (range == CliSessionRange.ALL) {
            return empty(language)
        }
        val window = rangeLabel(range, language)
        if (isAnchored) {
            return if (language == AppLanguage.PT) {
                "Nenhuma sessão nesta janela de quota ($window). Escolha uma janela maior."
            } else {
                "No session in the current quota window ($window). Pick a wider range."
            }
        }
        return if (language == AppLanguage.PT) {
            "Nenhuma sessão com atividade nas últimas $window. Escolha uma janela maior."
        } else {
            "No session active in the last $window. Pick a wider range."
        }
    }

    fun machine(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Máquina" else "Machine"
    }

    fun projectPath(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Projeto" else "Project"
    }

    fun branch(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Branch" else "Branch"
    }

    fun period(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Período" else "Period"
    }

    fun loading(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Indexando transcripts…" else "Indexing transcripts…"
    }

    fun columnSession(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Sessão" else "Session"
    }

    fun columnProject(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Projeto" else "Project"
    }

    fun columnWhen(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Quando" else "When"
    }

    fun columnTokens(language: AppLanguage): String = "Tokens"

    fun columnCache(language: AppLanguage): String = "Cache"

    fun columnCost(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Custo" else "Cost"
    }

    fun input(language: AppLanguage): String = "Input"

    fun output(language: AppLanguage): String = "Output"

    fun cacheRead(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Cache lido" else "Cache read"
    }

    fun cacheWrite(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Cache gravado" else "Cache write"
    }

    fun cacheHitRate(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Taxa de acerto de cache" else "Cache hit rate"
    }

    fun costDistribution(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Distribuição de custo" else "Cost distribution"
    }

    fun savings(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Economia do cache" else "Cache savings"
    }

    fun savingsExplanation(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "O que os tokens lidos do cache custariam como input, menos o que custaram."
        } else {
            "What the cached tokens would have cost as input, minus what they cost."
        }
    }

    fun averageContext(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Contexto médio/turno" else "Avg context/turn"
    }

    fun liveContext(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Contexto vivo" else "Live context"
    }

    fun nextInteraction(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Custo da próxima msg" else "Next message cost"
    }

    fun saturation(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Janela de contexto" else "Context window"
    }

    fun saturated(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Sessão saturada" else "Session saturated"
    }

    fun healthTitle(health: CliSessionHealth, language: AppLanguage): String {
        return when (health) {
            CliSessionHealth.HEALTHY ->
                if (language == AppLanguage.PT) "Sessão saudável" else "Healthy session"
            CliSessionHealth.ATTENTION ->
                if (language == AppLanguage.PT) "Contexto crescendo" else "Context growing"
            CliSessionHealth.SATURATED ->
                if (language == AppLanguage.PT) "Sessão saturada" else "Session saturated"
        }
    }

    /**
     * A ação correta no Claude Code é `/compact` — o "Retomar do resumo" do
     * legado era o fluxo do Claude Desktop e não existe aqui.
     */
    fun healthAdvice(health: CliSessionHealth, language: AppLanguage): String {
        return when (health) {
            CliSessionHealth.HEALTHY -> if (language == AppLanguage.PT) {
                "Contexto folgado na janela do modelo; pode seguir."
            } else {
                "Plenty of room left in the model window; carry on."
            }
            CliSessionHealth.ATTENTION -> if (language == AppLanguage.PT) {
                "Cada mensagem já reenvia bastante contexto. Um /compact reduz o custo daqui pra frente."
            } else {
                "Each message now resends a lot of context. A /compact cuts the cost from here on."
            }
            CliSessionHealth.SATURATED -> if (language == AppLanguage.PT) {
                "Considere /compact ou abrir uma sessão nova: continuar assim sai caro e a janela está perto do limite."
            } else {
                "Consider /compact or a fresh session: carrying on is expensive and the window is near its limit."
            }
        }
    }

    /** Explicita por que o status foi atribuído — sem isso o alerta é opaco. */
    fun healthReason(
        saturationLabel: String?,
        nextCostLabel: String,
        language: AppLanguage
    ): String {
        val window = saturationLabel ?: (if (language == AppLanguage.PT) "janela desconhecida" else "unknown window")
        return if (language == AppLanguage.PT) {
            "$window da janela · $nextCostLabel por mensagem"
        } else {
            "$window of the window · $nextCostLabel per message"
        }
    }

    fun contextPerTurnChart(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Contexto por turno" else "Context per turn"
    }

    fun cacheWritePerTurnChart(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Cache gravado por turno" else "Cache write per turn"
    }

    fun costVersusSavingsChart(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Custo x economia acumulados"
        } else {
            "Cumulative cost vs savings"
        }
    }

    fun chartContextLegend(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Contexto" else "Context"
    }

    fun chartCostLegend(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Custo" else "Cost"
    }

    fun chartSavingsLegend(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Economia" else "Savings"
    }

    fun estimatedCostNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Custo estimado a preço de tabela. Não é fatura."
        } else {
            "Estimated at list price. Not an invoice."
        }
    }

    fun unpricedNotice(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "$count turno(s) sem preço conhecido: o custo exibido está incompleto."
        } else {
            "$count turn(s) with unknown pricing: the displayed cost is incomplete."
        }
    }

    fun staleNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Transcript removido do disco; só o resumo indexado permanece."
        } else {
            "Transcript removed from disk; only the indexed summary remains."
        }
    }

    fun sidechainNotice(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "$count turno(s) de subagente somam no custo mas ficam fora dos gráficos de contexto."
        } else {
            "$count subagent turn(s) count toward cost but stay out of the context charts."
        }
    }

    fun turnsLabel(count: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) "$count turnos" else "$count turns"
    }
}
