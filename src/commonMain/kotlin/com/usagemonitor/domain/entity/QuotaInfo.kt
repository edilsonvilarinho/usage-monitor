package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Representa a cota de uso de um recurso específico (modelo ou tipo de API).
 *
 * Em Kotlin, `data class` é equivalente a uma classe simples com:
 * - Igualdade por valor (como objetos em JavaScript)
 * - `toString()` automático para debug
 * - `copy()` para criar variações imutáveis
 *
 * REGRA CLEAN ARCHITECTURE: esta classe não pode ter imports de Ktor, Compose
 * ou qualquer biblioteca externa. Apenas tipos puros e kotlinx.datetime.
 */
data class QuotaInfo(
    // Identificador legível: nome do modelo ou "Tokens"
    val label: String,

    // Quantidade consumida no período atual
    val used: Long,

    // Limite total do período (0 = sem limite definido)
    val total: Long,

    // Quando esta cota reseta (ex: fim da janela de 1h ou fim do dia)
    val periodEndAt: Instant,

    // Uso acumulado na semana (MiniMax tem esta dimensão adicional)
    val weeklyUsed: Long = 0L,

    // Limite semanal (0 = sem limite)
    val weeklyTotal: Long = 0L,

    // Fim da semana corrente
    val weeklyEndAt: Instant = Instant.DISTANT_FUTURE,

    // Unidade de medida: tokens (Anthropic) ou requisições (MiniMax)
    val unit: UsageUnit
) {
    /**
     * Percentual de uso no período atual (valor entre 0.0 e 1.0).
     *
     * Em Kotlin, `get()` define uma propriedade calculada — equivalente a um
     * getter em JavaScript: `get percentageUsed() { ... }`
     */
    val percentageUsed: Float
        get() = if (total > 0L) {
            (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val remaining: Long
        get() = if (total > 0L) (total - used).coerceAtLeast(0L) else 0L
}

/**
 * Unidade de medida da cota.
 * Anthropic mede em TOKENS; MiniMax mede em REQUESTS (número de chamadas).
 */
enum class UsageUnit {
    TOKENS,
    REQUESTS
}
