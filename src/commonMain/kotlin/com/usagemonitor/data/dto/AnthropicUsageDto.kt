package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta de GET /api/oauth/usage — endpoint dedicado de uso do Claude.ai OAuth.
 * Requer header `anthropic-beta: oauth-2025-04-20`.
 *
 * `utilization` vem como percentual (0.0 a 100.0), não como fração.
 * `resets_at` costuma vir em ISO 8601 com timezone, mas pode ser `null`
 * quando a Anthropic ainda não materializou a janela de reset.
 *
 * A resposta traz outras janelas (`seven_day_opus`, `seven_day_cowork`, ...) e
 * a lista `limits[]`, ignoradas aqui — o `Json` do projeto usa `ignoreUnknownKeys`.
 */
@Serializable
data class AnthropicUsageResponse(
    @SerialName("five_hour") val fiveHour: AnthropicUsageWindow,
    @SerialName("seven_day") val sevenDay: AnthropicUsageWindow,
    @SerialName("seven_day_sonnet") val sevenDaySonnet: AnthropicUsageWindow? = null,
    @SerialName("extra_usage") val extraUsage: AnthropicExtraUsage? = null,
    val spend: AnthropicSpend? = null,
)

@Serializable
data class AnthropicUsageWindow(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String?,
)

/**
 * Créditos de uso extra ("Créditos de uso" nas Configurações do claude.ai).
 *
 * Fonte primária da leitura de créditos. Quando `is_enabled` é `false`, todos os
 * demais campos vêm nulos e a conta não tem créditos para exibir.
 *
 * `monthly_limit` e `used_credits` estão em unidades menores da moeda (centavos):
 * 55000 = R$ 550,00 e 32784 = R$ 327,84 — confere com `utilization` de 59,6%.
 * `used_credits` chega como float JSON; `monthly_limit` como inteiro.
 *
 * `currency` é a moeda real da conta (pode ser BRL). Não usar a de `spend.used`
 * como primária: quando o recurso está desligado ela cai para o default "USD".
 */
@Serializable
data class AnthropicExtraUsage(
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("monthly_limit") val monthlyLimit: Long? = null,
    @SerialName("used_credits") val usedCredits: Double? = null,
    val utilization: Double? = null,
    val currency: String? = null,
    @SerialName("decimal_places") val decimalPlaces: Int? = null,
    @SerialName("disabled_reason") val disabledReason: String? = null,
    @SerialName("user_disabled") val userDisabled: Boolean = false,
    @SerialName("spend_limit_reached") val spendLimitReached: Boolean = false,
    @SerialName("credits_ever_enabled") val creditsEverEnabled: Boolean = false,
)

/**
 * Gasto monetário reportado pelo mesmo endpoint. Serve de reforço para
 * [AnthropicExtraUsage] — o `percent` vem arredondado (60 para 59,6%), então o
 * percentual exibido continua vindo de `extra_usage.utilization`.
 */
@Serializable
data class AnthropicSpend(
    val used: AnthropicSpendAmount? = null,
    val limit: AnthropicSpendAmount? = null,
    val percent: Double? = null,
    val enabled: Boolean = false,
)

/** Valor monetário em unidades menores: `amount_minor` com `exponent` casas decimais. */
@Serializable
data class AnthropicSpendAmount(
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val exponent: Int,
)
