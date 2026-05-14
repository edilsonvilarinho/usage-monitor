package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO (Data Transfer Object) — representação exata do JSON da MiniMax.
 *
 * `@Serializable` diz ao compilador para gerar código de
 * serialização/deserialização automaticamente.
 *
 * `@SerialName("snake_case")` mapeia o nome JSON (snake_case) para o nome
 * Kotlin (camelCase). É como um alias de campo no TypeScript.
 *
 * ATENÇÃO: esta classe existe apenas para transportar dados da API.
 * A lógica de negócio fica nas entidades do domain, não aqui.
 */
@Serializable
data class MiniMaxTokenPlanResponse(
    @SerialName("model_remains") val modelRemains: List<ModelRemainDto>? = null,
    @SerialName("base_resp") val baseResp: BaseRespDto
)

/**
 * Cota de um modelo específico no plano MiniMax.
 * Todos os timestamps estão em milissegundos (epoch ms).
 */
@Serializable
data class ModelRemainDto(
    @SerialName("start_time") val startTime: Long,
    @SerialName("end_time") val endTime: Long,

    // Milissegundos restantes no intervalo atual
    @SerialName("remains_time") val remainsTime: Long,

    // Limite e uso no intervalo atual (ex: por hora)
    @SerialName("current_interval_total_count") val currentIntervalTotalCount: Long,
    @SerialName("current_interval_usage_count") val currentIntervalUsageCount: Long,

    // Nome do modelo: "MiniMax-M*", "speech-hd", "image-01", etc.
    @SerialName("model_name") val modelName: String,

    // Limite e uso na semana
    @SerialName("current_weekly_total_count") val currentWeeklyTotalCount: Long,
    @SerialName("current_weekly_usage_count") val currentWeeklyUsageCount: Long,

    @SerialName("weekly_start_time") val weeklyStartTime: Long,
    @SerialName("weekly_end_time") val weeklyEndTime: Long,
    @SerialName("weekly_remains_time") val weeklyRemainsTime: Long
)

/** Resposta de status da MiniMax. status_code 0 = sucesso. */
@Serializable
data class BaseRespDto(
    @SerialName("status_code") val statusCode: Int,
    @SerialName("status_msg") val statusMsg: String
)
