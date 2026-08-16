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
        return if (language == AppLanguage.PT) {
            "${totals.sessionCount} sessões · ${totals.turnCount} turnos · ${formatQuantity(totals.totalTokens)} tokens"
        } else {
            "${totals.sessionCount} sessions · ${totals.turnCount} turns · ${formatQuantity(totals.totalTokens)} tokens"
        }
    }

    fun bucketSubtitle(bucket: CliUsageBucket, language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "${bucket.sessionCount} sessões · ${formatQuantity(bucket.totalTokens)} tokens · cache ${formatPercent(bucket.cacheHitRate)}"
        } else {
            "${bucket.sessionCount} sessions · ${formatQuantity(bucket.totalTokens)} tokens · cache ${formatPercent(bucket.cacheHitRate)}"
        }
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

    fun axisNotice(language: AppLanguage): String {
        return if (language == AppLanguage.PT) {
            "As três seções descrevem os mesmos turnos por eixos diferentes — não se somam."
        } else {
            "The three sections describe the same turns along different axes — they do not add up."
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
