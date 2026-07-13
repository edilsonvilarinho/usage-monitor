package com.usagemonitor.domain.entity

/**
 * Agrega todas as cotas de uso de UMA API (Anthropic ou MiniMax).
 *
 * Anthropic → uma única QuotaInfo (tokens da janela de rate limit)
 * MiniMax   → múltiplas QuotaInfo, uma por modelo
 */
data class ApiUsageStats(
    // Identificador estável da fonte para filtros e lógica da UI.
    val source: ApiSource,

    // Nome da API: "Anthropic" ou "MiniMax"
    val apiName: String,

    // Lista de cotas. Para Anthropic terá 1 item; para MiniMax, vários.
    val quotas: List<QuotaInfo>,

    // Avisos não fatais que a UI pode expor sem rebaixar a fonte para erro.
    val notices: Set<ApiUsageNotice> = emptySet()
)

enum class ApiUsageNotice {
    WEEKLY_QUOTA_UNAVAILABLE,
    SOURCE_UNSTABLE
}
