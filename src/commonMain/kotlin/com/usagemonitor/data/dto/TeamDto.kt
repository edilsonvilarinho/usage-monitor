package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contrato HTTP com o servidor de time (`server/`).
 *
 * Os nomes são camelCase dos dois lados — o servidor já devolve as colunas com
 * alias em camelCase, então não há mapeamento de snake_case aqui. Timestamps são
 * epoch millis, iguais aos que o índice local grava.
 */
@Serializable
data class TeamMemberDto(
    val deviceId: String,
    val alias: String,
    val hostName: String? = null,
    val organizationUuid: String? = null,
    val organizationName: String? = null
)

@Serializable
data class TeamSessionUploadDto(
    val sessionId: String,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val firstTs: Long,
    val lastTs: Long,
    val liveContextTokens: Long = 0L,
    val liveContextModel: String? = null
)

@Serializable
data class TeamTurnUploadDto(
    val sessionId: String,
    val messageId: String,
    val ts: Long,
    val model: String? = null,
    val isSidechain: Boolean = false,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
)

@Serializable
data class TeamIngestRequestDto(
    val accountKey: String,
    val member: TeamMemberDto,
    val sessions: List<TeamSessionUploadDto> = emptyList(),
    val turns: List<TeamTurnUploadDto> = emptyList()
)

@Serializable
data class TeamIngestResponseDto(
    val acceptedTurns: Int = 0,
    val ignoredTurns: Int = 0,
    val acceptedSessions: Int = 0
)

@Serializable
data class TeamMemberRowDto(
    val deviceId: String,
    val alias: String,
    val hostName: String? = null,
    val organizationUuid: String? = null,
    val organizationName: String? = null,
    val lastSeenAt: Long = 0L
)

/**
 * Uma linha por `(deviceId, sessionId, model)`.
 *
 * O servidor não precifica: uma sessão que trocou de modelo no meio chega como
 * várias linhas, e é o cliente que aplica `ModelPricingTable` a cada trecho.
 * Assim a tabela de preços não é duplicada em TypeScript e o custo do modal de
 * time acompanha as atualizações do app.
 */
@Serializable
data class TeamUsageRowDto(
    val deviceId: String,
    val sessionId: String,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val liveContextTokens: Long = 0L,
    val liveContextModel: String? = null,
    val model: String? = null,
    val turnCount: Int = 0,
    val firstTs: Long = 0L,
    val lastTs: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
)

@Serializable
data class TeamSnapshotDto(
    val members: List<TeamMemberRowDto> = emptyList(),
    val rows: List<TeamUsageRowDto> = emptyList()
)

/** Corpo de erro do servidor: `{ "error": "...", "code": "..." }`. */
@Serializable
data class TeamErrorDto(
    @SerialName("error") val message: String = "",
    val code: String = ""
)
