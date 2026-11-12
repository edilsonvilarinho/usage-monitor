package com.usagemonitor.domain.entity

/**
 * Motivo pelo qual a fonte bloqueou uma cota de créditos de uso (issue #567).
 *
 * O bloqueio mantém a linha no card, com o gasto e o limite, e este valor escolhe
 * o texto do motivo. Conta sem créditos ligados não chega aqui: essa conta não
 * tem linha.
 */
enum class QuotaBlockReason {
    /** Saldo de créditos esgotado no servidor (`disabled_reason = out_of_credits`). */
    OUT_OF_CREDITS,

    /** Limite mensal de gasto atingido (`spend_limit_reached`). */
    SPEND_LIMIT_REACHED
}
