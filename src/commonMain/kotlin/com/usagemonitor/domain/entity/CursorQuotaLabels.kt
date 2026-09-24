package com.usagemonitor.domain.entity

/**
 * Rótulos das janelas do resumo pessoal do Cursor.
 *
 * Cada rótulo é metade da chave da série histórica ([QuotaSeriesKey]); renomear um
 * deles quebra a continuidade do histórico já gravado.
 *
 * Todas as janelas do Cursor são do ciclo de cobrança, ou seja, `MONTHLY`. O título
 * do card sai do `periodType` e diria "Mensal" em todos os blocos; [groupOf]
 * devolve o que distingue um do outro, como `AntigravityQuotaLabels.groupOf` faz
 * para os grupos de modelos.
 */
object CursorQuotaLabels {
    private const val PREFIX = "Cursor "

    /** `autoPercentUsed`: a franquia dos modelos do próprio Cursor — a janela principal. */
    const val AUTO = "Cursor Auto"

    /** `apiPercentUsed`: franquia separada dos modelos de API. Só aparece acima de zero. */
    const val API = "Cursor API"

    /** `individualUsage.onDemand`, quando ligado e com limite. */
    const val ON_DEMAND = "Cursor On-demand"

    /** `individualUsage.overall`: o teto dos planos enterprise e team, que não trazem percentual. */
    const val INCLUDED = "Cursor Included"

    /** `teamUsage.onDemand`, só quando já houve gasto. */
    const val TEAM_ON_DEMAND = "Cursor Team on-demand"

    private val ALL = setOf(AUTO, API, ON_DEMAND, INCLUDED, TEAM_ON_DEMAND)

    /** "Cursor On-demand" → "On-demand"; rótulo de outra fonte → `null`. */
    fun groupOf(label: String): String? = label.takeIf { it in ALL }?.removePrefix(PREFIX)
}
