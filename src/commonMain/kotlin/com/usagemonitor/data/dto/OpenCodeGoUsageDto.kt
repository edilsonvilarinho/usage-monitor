package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resposta de `GET https://opencode.ai/zen/go/v1/usage`.
 *
 * Forma confirmada em duas frentes: o código de
 * `packages/console/app/src/routes/zen/go/v1/usage.ts` (PR anomalyco/opencode#16513,
 * merged em 2026-08-11) e uma chamada real contra uma conta com assinatura Go
 * registrada na issue #124. Só percentual — a API **não** devolve valor gasto
 * nem limite em dinheiro, e é por isso que a cota é [UsageUnit.PERCENTAGE] e não
 * [UsageUnit.CURRENCY_USD].
 *
 * As três janelas são opcionais porque o endpoint não é documentado publicamente
 * e não tem versionamento formal: uma janela que deixe de vir some do card em vez
 * de derrubar a leitura inteira das outras duas.
 */
@Serializable
data class OpenCodeGoUsageResponse(
    @SerialName("usage") val usage: OpenCodeGoUsageDto = OpenCodeGoUsageDto()
)

@Serializable
data class OpenCodeGoUsageDto(
    @SerialName("rolling") val rolling: OpenCodeGoWindowDto? = null,
    @SerialName("weekly") val weekly: OpenCodeGoWindowDto? = null,
    @SerialName("monthly") val monthly: OpenCodeGoWindowDto? = null
)

/**
 * [status] é `ok` ou `rate-limited`. Ele não vira cota nem aviso: um
 * [ApiUsageNotice] vive na fonte inteira e não saberia dizer **qual** das três
 * janelas está bloqueada, e o percentual da janela limitada já chega no teto.
 */
@Serializable
data class OpenCodeGoWindowDto(
    @SerialName("status") val status: String = "ok",
    @SerialName("percent") val percent: Double = 0.0,
    @SerialName("resetsAt") val resetsAt: String? = null
)
