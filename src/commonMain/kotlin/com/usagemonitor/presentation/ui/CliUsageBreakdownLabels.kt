package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliUsageBucket

/**
 * Textos do resumo por eixo.
 *
 * Separado de [CliSessionsLabels] porque aquele objeto já responde pela lista e
 * pelo detalhe; misturar mais um assunto ali dificultaria achar qualquer coisa.
 */
internal object BreakdownLabels {

    fun tabSessions(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Sessões" else "Sessions"
    }

    fun tabBreakdown(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Resumo" else "Breakdown"
    }

    fun byProject(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Por projeto" else "By project"
    }

    fun byMember(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Por integrante" else "By member"
    }

    fun unknownMember(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Integrante desconhecido" else "Unknown member"
    }

    fun byModel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Por modelo" else "By model"
    }

    fun byBranch(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Por branch" else "By branch"
    }

    fun unknownProject(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Projeto desconhecido" else "Unknown project"
    }

    fun unknownModel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Modelo desconhecido" else "Unknown model"
    }

    fun unknownBranch(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Sem branch" else "No branch"
    }

    /** Nome da aba do eixo, no singular: a aba é o recorte, não a lista. */
    fun axisTab(axis: BreakdownAxis, language: AppLanguage): String {
        val portuguese = language == AppLanguage.PT
        return when (axis) {
            BreakdownAxis.MEMBER -> if (portuguese) "Integrante" else "Member"
            BreakdownAxis.PROJECT -> if (portuguese) "Projeto" else "Project"
            BreakdownAxis.MODEL -> if (portuguese) "Modelo" else "Model"
            BreakdownAxis.BRANCH -> if (portuguese) "Branch" else "Branch"
            BreakdownAxis.TOOL -> if (portuguese) "Ferramentas" else "Tools"
            BreakdownAxis.ACTIVITY -> if (portuguese) "Atividade" else "Activity"
        }
    }

    /**
     * Nome da primeira coluna da tabela, que é o eixo.
     *
     * Singular como a aba, e não o "Por projeto" do plural: a legenda nomeia o
     * conteúdo da célula, não o recorte.
     */
    fun columnAxis(axis: BreakdownAxis, language: AppLanguage): String {
        val portuguese = language == AppLanguage.PT
        return when (axis) {
            BreakdownAxis.MEMBER -> if (portuguese) "Integrante" else "Member"
            BreakdownAxis.PROJECT -> if (portuguese) "Projeto" else "Project"
            BreakdownAxis.MODEL -> if (portuguese) "Modelo" else "Model"
            BreakdownAxis.BRANCH -> if (portuguese) "Branch" else "Branch"
            BreakdownAxis.TOOL -> if (portuguese) "Ferramenta" else "Tool"
            BreakdownAxis.ACTIVITY -> if (portuguese) "Hora" else "Hour"
        }
    }

    fun columnTurns(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Turnos" else "Turns"
    }

    fun columnCalls(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Chamadas" else "Calls"
    }

    /** Rótulo do bloco de métrica; o valor sai de [bucketCost]. */
    fun estimatedCostLabel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Custo estimado" else "Estimated cost"
    }

    fun cacheSavingsLabel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Economia do cache" else "Cache savings"
    }

    /**
     * Rodapé curto do bloco de ritmo.
     *
     * A frase inteira — quantas horas decorreram — é [burnRateElapsed] e fica
     * **fora** do bloco: dentro dele, um rodapé de uma linha longa mede três
     * vezes a largura do bloco vizinho e a fileira perde o alinhamento.
     */
    fun burnRateFooter(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "medido sobre o tempo decorrido"
        } else {
            "measured over elapsed time"
        }
    }

    /** Texto que a linha sem rótulo mostra, por eixo. */
    fun unknownLabel(axis: BreakdownAxis, language: AppLanguage): String {
        return when (axis) {
            BreakdownAxis.MEMBER -> unknownMember(language)
            BreakdownAxis.PROJECT -> unknownProject(language)
            BreakdownAxis.MODEL -> unknownModel(language)
            BreakdownAxis.BRANCH -> unknownBranch(language)
            BreakdownAxis.TOOL, BreakdownAxis.ACTIVITY -> ""
        }
    }

    /**
     * Nome da coluna de ordenação, que muda com o eixo: a ferramenta não tem
     * custo nem tokens, e chamar de "custo" o número dela seria mentira.
     */
    fun sortOption(sort: BreakdownSort, axis: BreakdownAxis, language: AppLanguage): String {
        val portuguese = language == AppLanguage.PT
        val isTool = axis == BreakdownAxis.TOOL
        return when (sort) {
            BreakdownSort.SHARE -> when {
                isTool && portuguese -> "Chamadas"
                isTool -> "Calls"
                portuguese -> "Custo"
                else -> "Cost"
            }
            BreakdownSort.VOLUME -> when {
                isTool && portuguese -> "Turnos"
                isTool -> "Turns"
                portuguese -> "Tokens"
                else -> "Tokens"
            }
            BreakdownSort.NAME -> if (portuguese) "Nome" else "Name"
        }
    }

    fun sortLabel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ordenar por" else "Sort by"
    }

    fun filterPlaceholder(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Filtrar" else "Filter"
    }

    fun clearFilter(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Limpar filtro" else "Clear filter"
    }

    fun sortDirection(descending: Boolean, language: AppLanguage): String {
        val portuguese = language == AppLanguage.PT
        return when {
            descending && portuguese -> "Maior primeiro"
            descending -> "Highest first"
            portuguese -> "Menor primeiro"
            else -> "Lowest first"
        }
    }

    /** Onde a página começa e termina, para o rodapé não dizer só "1 de 4". */
    fun pageSummary(page: BreakdownPage<*>, language: AppLanguage): String {
        val portuguese = language == AppLanguage.PT
        if (page.filteredCount == 0) {
            return if (portuguese) "nenhum resultado" else "no results"
        }
        val first = page.fromIndex + 1
        val shown = "${first}–${page.fromIndex + page.items.size}"
        val suffix = if (page.isFiltered) {
            if (portuguese) " (de ${page.totalCount} sem filtro)" else " (of ${page.totalCount} unfiltered)"
        } else {
            ""
        }
        return if (portuguese) {
            "$shown de ${page.filteredCount}$suffix"
        } else {
            "$shown of ${page.filteredCount}$suffix"
        }
    }

    fun pageSizeLabel(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Por página" else "Per page"
    }

    fun previousPage(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Página anterior" else "Previous page"
    }

    fun nextPage(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Próxima página" else "Next page"
    }

    fun noMatches(query: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Nada com \"$query\" neste eixo."
        } else {
            "Nothing matching \"$query\" on this axis."
        }
    }

    /**
     * Aviso de que os números na tela ainda são os da janela anterior.
     *
     * Texto e não indicador animado: uma animação infinita trava o `waitForIdle`
     * dos testes de componente — a mesma razão pela qual o ponto da tela de
     * presença não pisca.
     */
    fun refreshing(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Atualizando… os números abaixo ainda são da janela anterior."
        } else {
            "Refreshing… the numbers below are still from the previous window."
        }
    }

    fun empty(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Nenhum turno nesta janela."
        } else {
            "No turns in this window."
        }
    }

    /** O `+` marca que há turno sem preço e o valor é piso, não total. */
    fun bucketCost(bucket: CliUsageBucket): String {
        val formatted = formatMicrosUsdShort(bucket.costMicros)
        return if (bucket.isCostComplete) formatted else "$formatted+"
    }

    fun totalCost(totals: CliUsageBucket, language: AppLanguage): String {
        val prefix = if (language == AppLanguage.PT) "Custo estimado" else "Estimated cost"
        return "$prefix: ${bucketCost(totals)}"
    }

    fun totalSubtitle(totals: CliUsageBucket, language: AppLanguage): String {
        val base = if (language == AppLanguage.PT) {
            "${totals.sessionCount} sessões · ${totals.turnCount} turnos · ${formatQuantity(totals.totalTokens)} tokens"
        } else {
            "${totals.sessionCount} sessions · ${totals.turnCount} turns · ${formatQuantity(totals.totalTokens)} tokens"
        }
        val activeMillis = totals.activeMillis ?: return base
        if (activeMillis <= 0L) {
            return base
        }
        // "Tempo ativo" nomeado por extenso aqui, e não só o valor: no cartão de
        // totais ele fica ao lado de tokens e turnos, e um "4h35" solto seria
        // lido como duração da janela.
        val label = if (language == AppLanguage.PT) "tempo ativo" else "active time"
        return "$base · ${formatActiveTime(activeMillis)} $label"
    }

    fun bucketSubtitle(bucket: CliUsageBucket, language: AppLanguage): String {
        val base = if (language == AppLanguage.PT) {
            "${bucket.sessionCount} sessões · ${formatQuantity(bucket.totalTokens)} tokens · cache ${formatPercent(bucket.cacheHitRate)}"
        } else {
            "${bucket.sessionCount} sessions · ${formatQuantity(bucket.totalTokens)} tokens · cache ${formatPercent(bucket.cacheHitRate)}"
        }
        // Hora nula é eixo sem medida (modelo, ferramenta, time de servidor
        // antigo) e hora zero é balde só de sessões de um turno: nos dois casos o
        // trecho some, porque "0min" seria lido como trabalho instantâneo.
        val activeMillis = bucket.activeMillis ?: return base
        if (activeMillis <= 0L) {
            return base
        }
        return "$base · ${formatActiveTime(activeMillis)}"
    }

    /**
     * Economia do cache com a fatia ao lado.
     *
     * O valor sozinho não diz nada: US$ 40 economizados podem ser 5% ou 90% do
     * que teria sido gasto sem cache, e é a fatia que responde se vale mexer.
     */
    fun cacheSavings(
        savingsMicros: Long,
        share: Double,
        hitRate: Double,
        language: AppLanguage
    ): String {
        return if (language == AppLanguage.PT) {
            "Cache economizou ${formatMicrosUsdShort(savingsMicros)} (${formatPercent(share)} do que seria gasto) · reaproveitamento ${formatPercent(hitRate)}"
        } else {
            "Cache saved ${formatMicrosUsdShort(savingsMicros)} (${formatPercent(share)} of what it would have cost) · reuse ${formatPercent(hitRate)}"
        }
    }

    fun unpricedNotice(turnCount: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "$turnCount turnos sem preço conhecido: o custo exibido é o piso."
        } else {
            "$turnCount turns with no known price: the cost shown is a floor."
        }
    }

    /**
     * Sem contar as seções: o resumo do time tem um eixo a mais — "por
     * integrante" — e um literal "três" mentiria numa das duas telas.
     */
    fun axisNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "As seções descrevem os mesmos turnos por eixos diferentes — não se somam."
        } else {
            "The sections describe the same turns along different axes — they do not add up."
        }
    }

    fun budgetTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Orçamento do mês" else "Monthly budget"
    }

    fun budgetValue(spentMicros: Long, limitMicros: Long, isComplete: Boolean, language: AppLanguage): String {
        val spent = formatMicrosUsdShort(spentMicros) + if (isComplete) "" else "+"
        val limit = formatMicrosUsdShort(limitMicros)
        return if (language == AppLanguage.PT) "$spent de $limit" else "$spent of $limit"
    }

    fun budgetProjection(projectedMicros: Long, willExceed: Boolean, language: AppLanguage): String {
        val projected = formatMicrosUsdShort(projectedMicros)
        return if (language == AppLanguage.PT) {
            if (willExceed) {
                "No ritmo atual o mês fecha em $projected — acima do teto."
            } else {
                "No ritmo atual o mês fecha em $projected."
            }
        } else {
            if (willExceed) {
                "At this pace the month closes at $projected — over the cap."
            } else {
                "At this pace the month closes at $projected."
            }
        }
    }

    fun budgetScopeNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Custo estimado das sessões CLI no mês corrente, em USD. Independe do filtro de janela."
        } else {
            "Estimated CLI session cost for the current month, in USD. Independent of the window filter."
        }
    }

    /**
     * Créditos da conta ao lado do orçamento, **nunca somados** a ele: o
     * `extra_usage` da Anthropic vem na moeda real da conta e o custo do índice
     * é sempre USD.
     */
    fun accountCredits(
        usedMinorUnits: Long,
        limitMinorUnits: Long,
        currencyCode: String,
        language: AppLanguage
    ): String {
        val used = formatMinorUnits(usedMinorUnits)
        val limit = formatMinorUnits(limitMinorUnits)
        return if (language == AppLanguage.PT) {
            "Créditos de uso da conta: $currencyCode $used de $currencyCode $limit (moeda da conta, não convertida)"
        } else {
            "Account usage credits: $currencyCode $used of $currencyCode $limit (account currency, not converted)"
        }
    }

    private fun formatMinorUnits(minorUnits: Long): String {
        val units = minorUnits / 100L
        val cents = (minorUnits % 100L).toString().padStart(2, '0')
        return "$units.$cents"
    }

    fun burnRateTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ritmo de queima" else "Burn rate"
    }

    /**
     * Ritmo em dinheiro por hora — o valor do bloco de métrica.
     *
     * Diferente do consumo médio do histórico, que mede **percentual de quota**
     * sobre snapshots. As duas convivem e o rótulo precisa separá-las.
     *
     * Os tokens por hora saem em [burnRateTokensPerHour], numa linha própria: os
     * dois juntos não cabem num bloco de largura fixa sem truncar, e truncado o
     * valor deixa de ser conferível.
     */
    fun burnRateCostPerHour(costMicrosPerHour: Long): String {
        return "${formatMicrosUsdShort(costMicrosPerHour)}/h"
    }

    fun burnRateTokensPerHour(tokensPerHour: Double, language: AppLanguage): String {
        val tokens = formatQuantity(tokensPerHour.toLong())
        return if (language == AppLanguage.PT) {
            "$tokens tokens por hora."
        } else {
            "$tokens tokens per hour."
        }
    }

    fun burnRateElapsed(elapsedMillis: Long, language: AppLanguage): String {
        val minutes = elapsedMillis / 60_000L
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        val elapsed = if (hours > 0L) "${hours}h${remainingMinutes.toString().padStart(2, '0')}" else "${minutes}min"
        return if (language == AppLanguage.PT) {
            "Medido sobre $elapsed decorridos da janela, não sobre a duração nominal dela."
        } else {
            "Measured over $elapsed elapsed in the window, not over its nominal length."
        }
    }

    fun burnRateProjection(projectedMicros: Long, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Mantido o ritmo, a janela fecha em ${formatMicrosUsdShort(projectedMicros)}."
        } else {
            "At this pace, the window closes at ${formatMicrosUsdShort(projectedMicros)}."
        }
    }

    fun burnRateUnavailable(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Ritmo indisponível: a janela é aberta ou começou há pouco."
        } else {
            "Burn rate unavailable: the window is open-ended or just started."
        }
    }

    fun byTool(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Ferramentas mais usadas" else "Most used tools"
    }

    fun toolSubtitle(callCount: Int, turnCount: Int, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "$callCount chamadas em $turnCount turnos"
        } else {
            "$callCount calls across $turnCount turns"
        }
    }

    /**
     * A ferramenta não carrega custo: o turno gastou tokens uma vez, mesmo tendo
     * chamado duas. Sem este aviso a seção seria lida como rateio de gasto.
     */
    fun toolNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Contagem de chamadas, não de custo — um turno que chama duas ferramentas gastou tokens uma vez só."
        } else {
            "Call counts, not cost — a turn calling two tools spent tokens only once."
        }
    }

    fun activityTitle(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Atividade por hora" else "Activity by hour"
    }

    fun activityNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Intensidade pelo custo da célula, relativa ao pico desta janela. Horário BRT."
        } else {
            "Intensity by cell cost, relative to this window's peak. BRT time."
        }
    }

    fun staleNotice(errorMessage: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "Última leitura falhou; os números são da anterior. $errorMessage"
        } else {
            "Last read failed; the numbers are from the previous one. $errorMessage"
        }
    }
}
