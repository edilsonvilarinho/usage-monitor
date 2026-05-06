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
    val label: String,
    val used: Long,
    val total: Long,
    val periodEndAt: Instant,
    val hasKnownResetAt: Boolean = true,
    val periodType: PeriodType = PeriodType.INTERVAL,
    val unit: UsageUnit,
    val rawUsed: Long = 0L,
    val rawTotal: Long = 0L
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
    REQUESTS,
    PERCENTAGE,   // quando só utilization está disponível (ex: Anthropic OAuth usage endpoint)
    CURRENCY_USD  // saldo monetário em centavos (ex: DeepSeek)
}

/** Tipo de janela temporal da cota. */
enum class PeriodType {
    INTERVAL,  // janela curta (ex: 5 horas no MiniMax)
    WEEKLY     // janela semanal
}
