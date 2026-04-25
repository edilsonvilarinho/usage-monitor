package com.usagemonitor.domain.entity

/**
 * Agrega todas as cotas de uso de UMA API (Anthropic ou MiniMax).
 *
 * Anthropic → uma única QuotaInfo (tokens da janela de rate limit)
 * MiniMax   → múltiplas QuotaInfo, uma por modelo
 */
data class ApiUsageStats(
    // Nome da API: "Anthropic" ou "MiniMax"
    val apiName: String,

    // Lista de cotas. Para Anthropic terá 1 item; para MiniMax, vários.
    val quotas: List<QuotaInfo>
)
