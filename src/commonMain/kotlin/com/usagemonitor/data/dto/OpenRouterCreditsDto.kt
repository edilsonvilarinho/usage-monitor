package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta de `GET https://openrouter.ai/api/v1/credits`.
 *
 * Confirmada por chamada real contra a API (issue #138): `total_credits` é o
 * total de créditos já comprados, `total_usage` é o total já gasto — o saldo
 * disponível é a diferença, e é exatamente o número que o dashboard do
 * OpenRouter mostra como "Total Available" (confirmado por captura de tela na
 * mesma issue). A chave normal de inferência autentica este endpoint, sem
 * "Provisioning API Key" separada.
 *
 * Deliberadamente **não** usamos `GET /api/v1/key`: `limit`/`limit_remaining`
 * ali são um teto opcional por chave (geralmente `null`, inclusive com saldo
 * positivo — testado), não o saldo da conta.
 */
@Serializable
data class OpenRouterCreditsResponse(
    @SerialName("data") val data: OpenRouterCreditsDto = OpenRouterCreditsDto()
)

@Serializable
data class OpenRouterCreditsDto(
    @SerialName("total_credits") val totalCredits: Double = 0.0,
    @SerialName("total_usage") val totalUsage: Double = 0.0
)
