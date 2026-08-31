package com.usagemonitor.domain.entity

/**
 * Rótulo da única cota do OpenRouter: saldo pré-pago (`total_credits -
 * total_usage` de `GET /api/v1/credits`), mesmo padrão do
 * [DeepSeekQuotaLabels.BALANCE] — não é fonte de tokens/requisições, é
 * dinheiro. Chave da série histórica; renomear quebra a continuidade do que
 * já foi gravado.
 */
object OpenRouterQuotaLabels {
    const val BALANCE = "Saldo"
}
