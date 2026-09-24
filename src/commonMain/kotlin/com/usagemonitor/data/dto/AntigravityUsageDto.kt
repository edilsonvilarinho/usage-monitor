package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope de `agy --output-format json --print /usage`.
 *
 * Forma medida contra o agy 1.2.9 no Windows (plano
 * `docs/planos/integracoes-267-ajustes-execucao.md`). O comando é respondido pelo
 * próprio CLI — `num_turns` e `usage.total_tokens` chegam zerados —, e são esses
 * dois campos, junto de `command.name`, que provam que nenhum turno de modelo foi
 * aberto. Por isso `numTurns` e `usage` são **nulos** quando ausentes, e não zero:
 * um default zero aprovaria justamente o envelope que não trouxe a prova.
 *
 * Todo campo é opcional porque o formato não é versionado nem documentado campo a
 * campo; o que faltar degrada no mapper, nunca no parse.
 */
@Serializable
data class AntigravityUsageEnvelopeDto(
    @SerialName("status") val status: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("num_turns") val numTurns: Int? = null,
    @SerialName("usage") val usage: AntigravityTokenUsageDto? = null,
    @SerialName("command") val command: AntigravityCommandDto? = null
)

@Serializable
data class AntigravityTokenUsageDto(
    @SerialName("total_tokens") val totalTokens: Long? = null
)

@Serializable
data class AntigravityCommandDto(
    @SerialName("name") val name: String? = null,
    @SerialName("data") val data: AntigravityUsageDataDto? = null
)

@Serializable
data class AntigravityUsageDataDto(
    @SerialName("groups") val groups: List<AntigravityQuotaGroupDto> = emptyList()
)

@Serializable
data class AntigravityQuotaGroupDto(
    @SerialName("name") val name: String? = null,
    @SerialName("buckets") val buckets: List<AntigravityQuotaBucketDto> = emptyList()
)

/**
 * Uma janela de cota. [remainingFraction] é o que **resta** (0..1), não o que foi
 * usado. [window] vem como `weekly` na versão medida; a janela de 5h ainda não foi
 * observada, e é reconhecida por conter `hour`.
 */
@Serializable
data class AntigravityQuotaBucketDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("window") val window: String? = null,
    @SerialName("remaining_fraction") val remainingFraction: Double? = null,
    @SerialName("reset_time") val resetTime: String? = null
)
