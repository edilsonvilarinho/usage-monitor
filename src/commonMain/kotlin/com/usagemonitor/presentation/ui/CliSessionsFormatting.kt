package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionHealthTally
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

/**
 * Comando que retoma a sessão no Claude Code.
 *
 * Leva o id **inteiro**: o `--resume` só retoma direto quando recebe o session ID
 * completo — com um prefixo ele cai no seletor interativo usando o texto como
 * busca. Os oito caracteres que a tela mostra ([shortSessionId]) não servem para
 * isso, e é justamente por isso que existe o botão de copiar.
 */
internal fun resumeSessionCommand(sessionId: String): String {
    return "claude --resume $sessionId"
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
     *
     * Ancorado sem [endsAt] é a janela recém-aberta: o reset venceu, o corte já é
     * o dele, mas o fim novo só existe quando a API o publicar. Dizer "até
     * HH:MM" ali seria inventar um horário.
     */
    fun estimatedTotalInRange(
        range: CliSessionRange,
        endsAt: Instant?,
        isAnchored: Boolean,
        language: AppLanguage
    ): String {
        val window = rangeLabel(range, language)
        if (endsAt == null && isAnchored) {
            return if (language == AppLanguage.PT) {
                "custo estimado · janela $window desde o reinício"
            } else {
                "estimated cost · $window window since the reset"
            }
        }
        if (endsAt == null) {
            return "${estimatedTotal(language)} · ${lastRangeLabel(range, language)}"
        }
        return if (language == AppLanguage.PT) {
            "custo estimado · janela $window até ${formatInstant(endsAt)}"
        } else {
            "estimated cost · $window window until ${formatInstant(endsAt)}"
        }
    }

    /**
     * Janela corrida por extenso: "últimas 5h", "últimos 7 dias".
     *
     * "5h" concorda no feminino (horas) e "7 dias"/"30 dias" no masculino — sem a
     * flexão o rótulo saía como "últimas 7 dias".
     */
    private fun lastRangeLabel(range: CliSessionRange, language: AppLanguage): String {
        val window = rangeLabel(range, language)
        if (language != AppLanguage.PT) {
            return "last $window"
        }
        return if (range == CliSessionRange.LAST_5H) "últimas $window" else "últimos $window"
    }

    /** Mesma flexão de [lastRangeLabel], já contraída com a preposição. */
    private fun inLastRangeLabel(range: CliSessionRange, language: AppLanguage): String {
        val window = rangeLabel(range, language)
        if (language != AppLanguage.PT) {
            return "in the last $window"
        }
        return if (range == CliSessionRange.LAST_5H) "nas últimas $window" else "nos últimos $window"
    }

    fun rangeLabel(range: CliSessionRange, language: AppLanguage): String {
        return when (range) {
            CliSessionRange.LAST_5H -> "5h"
            CliSessionRange.LAST_7D -> if (language == AppLanguage.PT) "7 dias" else "7 days"
            CliSessionRange.LAST_30D -> if (language == AppLanguage.PT) "30 dias" else "30 days"
            CliSessionRange.ALL -> if (language == AppLanguage.PT) "Total" else "All time"
        }
    }

    /** A tela se atualiza sozinha; a pílula existe para o usuário saber disso. */
    fun live(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "AO VIVO" else "LIVE"
    }

    /**
     * Marca a última **mudança**, não a última varredura. Se nada mudou o carimbo
     * não anda — e é exatamente isso que aconteceu.
     */
    fun lastChange(instantLabel: String?, language: AppLanguage): String {
        if (instantLabel == null) {
            return if (language == AppLanguage.PT) "sem alterações ainda" else "no changes yet"
        }
        return if (language == AppLanguage.PT) {
            "última alteração $instantLabel"
        } else {
            "last change $instantLabel"
        }
    }

    fun back(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Voltar" else "Back"
    }

    fun copyResumeCommand(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Copiar comando de retomada"
        } else {
            "Copy resume command"
        }
    }

    /**
     * Rótulo da sessão de outra máquina.
     *
     * Ali o transcript não existe localmente e o `--resume` não teria o que
     * retomar, então o botão copia só o identificador — e o texto não promete
     * uma retomada que não acontece.
     */
    fun copySessionId(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Copiar id da sessão" else "Copy session id"
    }

    fun copied(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Copiado" else "Copied"
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
        // Vale tanto para a janela em curso quanto para a que acabou de abrir no
        // reset: nos dois casos o vazio é da janela de quota, não do índice.
        if (isAnchored) {
            return if (language == AppLanguage.PT) {
                "Nenhuma sessão nesta janela de quota ($window). Escolha uma janela maior."
            } else {
                "No session in the current quota window ($window). Pick a wider range."
            }
        }
        return if (language == AppLanguage.PT) {
            "Nenhuma sessão com atividade ${inLastRangeLabel(range, language)}. Escolha uma janela maior."
        } else {
            "No session active ${inLastRangeLabel(range, language)}. Pick a wider range."
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

    /**
     * O total inclui o cache lido, que costuma responder por mais de 95% dele —
     * cada turno relê o contexto inteiro. Sem o qualificador o número é lido como
     * volume de conteúdo produzido, que ele não é.
     */
    fun columnTokens(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Tokens (com cache)" else "Tokens (with cache)"
    }

    /** Composição do total, para o número grande poder ser conferido. */
    fun tokensBreakdown(
        inputTokens: Long,
        outputTokens: Long,
        cacheReadTokens: Long,
        cacheWriteTokens: Long,
        language: AppLanguage
    ): String {
        val read = if (language == AppLanguage.PT) "cache lido" else "cache read"
        val write = if (language == AppLanguage.PT) "cache gravado" else "cache write"
        return "in ${formatQuantity(inputTokens)} · out ${formatQuantity(outputTokens)} · " +
            "$read ${formatQuantity(cacheReadTokens)} · $write ${formatQuantity(cacheWriteTokens)}"
    }

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

    /**
     * Não é a duração da sessão: intervalos acima de cinco minutos entre turnos
     * são descartados por serem o usuário parado, não tempo de trabalho.
     */
    fun activeTime(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Tempo ativo" else "Active time"
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

    /** Versão de uma palavra para a linha da lista; o detalhe usa [healthTitle]. */
    fun healthShort(health: CliSessionHealth, language: AppLanguage): String {
        return when (health) {
            CliSessionHealth.HEALTHY ->
                if (language == AppLanguage.PT) "Saudável" else "Healthy"
            CliSessionHealth.ATTENTION ->
                if (language == AppLanguage.PT) "Atenção" else "Attention"
            CliSessionHealth.SATURATED ->
                if (language == AppLanguage.PT) "Saturada" else "Saturated"
        }
    }

    fun columnStatus(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Status" else "Status"
    }

    /**
     * "1 saturada · 2 em atenção" para o cabeçalho. `null` quando não há alerta.
     *
     * Nulo, e não string vazia: um alerta que não existe não deve ocupar linha
     * no cabeçalho. Sessão com janela de contexto desconhecida não entra na
     * contagem — quem filtra é [tallyHealth], que se recusa a chutar a fração.
     */
    fun healthTally(tally: CliSessionHealthTally, language: AppLanguage): String? {
        if (!tally.hasWarnings) {
            return null
        }

        val parts = mutableListOf<String>()
        if (tally.saturated > 0) {
            parts += saturatedCount(tally.saturated, language)
        }
        if (tally.attention > 0) {
            parts += attentionCount(tally.attention, language)
        }
        return parts.joinToString(" · ")
    }

    private fun saturatedCount(count: Int, language: AppLanguage): String {
        if (language != AppLanguage.PT) {
            return "$count saturated"
        }
        return if (count == 1) "1 saturada" else "$count saturadas"
    }

    private fun attentionCount(count: Int, language: AppLanguage): String {
        if (language != AppLanguage.PT) {
            return "$count needing attention"
        }
        return "$count em atenção"
    }

    /** Explicita por que o status foi atribuído — sem isso o alerta é opaco. */
    fun healthReason(
        saturationLabel: String?,
        nextCostLabel: String,
        language: AppLanguage
    ): String {
        // Sem a janela do modelo não há fração para mostrar; dizer isso é melhor
        // que emendar o rótulo de erro na frase da fração.
        val window = if (saturationLabel == null) {
            if (language == AppLanguage.PT) "janela do modelo desconhecida" else "unknown model window"
        } else {
            if (language == AppLanguage.PT) "$saturationLabel da janela" else "$saturationLabel of the window"
        }
        return if (language == AppLanguage.PT) {
            "$window · $nextCostLabel por mensagem"
        } else {
            "$window · $nextCostLabel per message"
        }
    }

    /**
     * O bloco recolhido não é "mais do mesmo": é a apuração por trás dos quatro
     * números do resumo. O rótulo evita prometer novidade onde há detalhamento.
     */
    fun advancedToggle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Avançado" else "Advanced"
    }

    /** Diz o que está lá dentro para que abrir não seja aposta. */
    fun advancedHint(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "composição dos tokens, distribuição do custo e gráficos por turno"
        } else {
            "token breakdown, cost distribution and per-turn charts"
        }
    }

    fun glossaryTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Como ler esta tela" else "How to read this screen"
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

/**
 * Tempo ativo como `1h05` ou `18min`.
 *
 * Segundos ficam de fora: o corte de pausa é de cinco minutos, então precisão
 * abaixo do minuto sugeriria uma exatidão que a medida não tem.
 */
internal fun formatActiveTime(millis: Long): String {
    val minutes = millis / 60_000L
    val hours = minutes / 60L
    if (hours <= 0L) {
        return "${minutes}min"
    }
    return "${hours}h${(minutes % 60L).toString().padStart(2, '0')}"
}
