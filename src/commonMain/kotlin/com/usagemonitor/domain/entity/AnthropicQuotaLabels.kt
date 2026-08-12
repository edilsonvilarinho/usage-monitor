package com.usagemonitor.domain.entity

/**
 * Rótulos das cotas da Anthropic.
 *
 * Cada rótulo é metade da chave da série histórica ([QuotaSeriesKey]) e do
 * índice do SQLite: renomear qualquer um destes valores quebra a continuidade
 * do histórico já gravado.
 */
object AnthropicQuotaLabels {
    const val FIVE_HOUR = "Claude 5h"
    const val SEVEN_DAY = "Claude 7d"
    const val EXTRA_CREDITS = "Créditos"
}

/**
 * Cota dos créditos de uso extra da Anthropic.
 *
 * Diferente das janelas 5h/7d, os valores brutos desta cota são monetários em
 * unidades menores da moeda da conta (ver [QuotaInfo.currencyCode]), não tokens
 * estimados — por isso a UI a formata em dinheiro e sempre exibe o valor.
 */
val QuotaInfo.isExtraCreditsQuota: Boolean
    get() = label == AnthropicQuotaLabels.EXTRA_CREDITS
