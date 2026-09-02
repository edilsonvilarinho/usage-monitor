package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.seriesKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.worstQuotaRisk
import kotlinx.datetime.Instant

/**
 * A cota que hoje pesa mais no risco global — a fonte, a cota vencedora e a
 * projeção dela —, para a barra HUD (issue #164).
 *
 * `worstRisk` em [UsageAlertViewModel] já devolve o pior nível entre todas as
 * cotas, mas só o nível: a bandeja não precisa de mais que isso. A barra HUD
 * precisa também dizer **qual** fonte e **quando** ela reseta, e é isso que
 * este tipo carrega.
 */
internal data class WorstQuotaSnapshot(
    val stats: ApiUsageStats,
    val quota: QuotaInfo,
    val risk: QuotaRiskSummary
)

/**
 * O risco de cada fonte, pior primeiro — para o hover da barra HUD (issue
 * #164) listar as fontes que não são a vencedora, não só ela.
 *
 * Reaproveita [worstQuotaRisk] — a mesma regra usada pelo badge do cabeçalho
 * de cada card (cota vencida não entra, sem projeção não há badge) — card a
 * card. Duplicar a regra aqui divergiria do que o card mostra. Ordem total e
 * determinística (nível desc., rótulo da fonte asc. como desempate): duas
 * leituras iguais têm de produzir a mesma lista, ou o `StateFlow` reemite e a
 * tela recompõe à toa.
 */
internal fun allSourceRisks(
    stats: List<ApiUsageStats>,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>,
    now: Instant
): List<WorstQuotaSnapshot> {
    return stats
        .mapNotNull { entry ->
            worstQuotaRisk(entry.quotas, riskSummaries[entry.targetKey].orEmpty(), now)
                ?.let { (quota, risk) -> WorstQuotaSnapshot(entry, quota, risk) }
        }
        .sortedWith(
            compareByDescending<WorstQuotaSnapshot> { snapshot -> snapshot.risk.level.ordinal }
                .thenBy { snapshot -> snapshot.stats.apiName }
        )
}

/** A primeira de [allSourceRisks] — a fonte que hoje pesa mais no risco global. */
internal fun worstQuotaSnapshot(
    stats: List<ApiUsageStats>,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>,
    now: Instant
): WorstQuotaSnapshot? {
    return allSourceRisks(stats, riskSummaries, now).firstOrNull()
}

/**
 * Uma cota de uma fonte, com a projeção dela quando existe — para a barra HUD
 * (issue #164) listar **todos os limites**, não um por fonte.
 *
 * [allSourceRisks] devolve a pior cota de cada fonte, e era o que o HUD
 * mostrava: uma conta Anthropic com janela de 5h e de 7d aparecia com uma linha
 * só, e o outro limite não existia na tela. Quem usa pediu os dois.
 *
 * **`risk` é nulo quando não há projeção**, e a linha continua saindo: o
 * percentual é fato medido e não depende de previsão nenhuma. É a diferença
 * para o badge do card, que some sem projeção — lá a pergunta é "qual o
 * estado", aqui é "quanto já foi". Kilo e OpenCode nunca têm projeção
 * (`currentSegment` vê um ponto por segmento e devolve `InsufficientData`), e
 * com a regra do badge eles sumiriam do HUD inteiro.
 *
 * **Cota vencida continua fora**, como em [allSourceRisks]: o número na tela
 * seria o da janela anterior.
 */
internal data class HudQuotaEntry(
    val stats: ApiUsageStats,
    val quota: QuotaInfo,
    val risk: QuotaRiskSummary?
)

/**
 * Todas as cotas de todas as fontes, **na ordem em que chegaram**.
 *
 * Não ordena por risco, e é decisão: quem ordena a barra HUD é a ordem de cards
 * que o usuário arrastou (`orderedByCardOrder`), porque foi o que ele pediu —
 * "deve respeitar a ordem que ele escolher". Ordenar por risco aqui e por card
 * ali daria duas ordens brigando, e a linha parada trocaria de conta sozinha
 * conforme o risco mudasse.
 *
 * Dentro de cada fonte a ordem é a de declaração das cotas, que é a da resposta
 * da API — 5h antes de 7d. É ela que o resumo da linha parada imprime.
 */
internal fun allQuotaRisks(
    stats: List<ApiUsageStats>,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>,
    now: Instant
): List<HudQuotaEntry> {
    return stats.flatMap { entry ->
        val riskByQuota = riskSummaries[entry.targetKey].orEmpty()
        entry.quotas
            .filterNot { quota -> quota.isExpiredAt(now) }
            .map { quota -> HudQuotaEntry(entry, quota, riskByQuota[quota.seriesKey]) }
    }
}
