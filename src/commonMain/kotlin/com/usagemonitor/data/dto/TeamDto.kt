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
    val accountEmail: String? = null,
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

/**
 * Corpo de `POST /api/v1/presence`. Reusa [TeamMemberDto] do ingest — é o mesmo
 * membro, sem sessões nem turnos.
 */
@Serializable
data class TeamPresenceRequestDto(
    val accountKey: String,
    val accountEmail: String? = null,
    val member: TeamMemberDto
)

/**
 * Resposta de `POST /api/v1/presence`.
 *
 * [lastSeenAt] é o relógio do **servidor** no instante do carimbo — é dele que o
 * cliente deduz o próprio desvio. `0L` significa servidor que não mandou o
 * campo, e não época zero.
 */
@Serializable
data class TeamPresenceResponseDto(
    val lastSeenAt: Long = 0L
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

/**
 * Tempo de trabalho de uma sessão na janela, medido pelo servidor.
 *
 * Lista à parte de [TeamUsageRowDto] porque tempo é propriedade da sessão, não
 * do trecho de um modelo: como coluna daquela linha, uma sessão que trocou de
 * modelo teria a hora dela somada uma vez por modelo.
 */
@Serializable
data class TeamSessionActivityDto(
    val deviceId: String = "",
    val sessionId: String = "",
    val activeMillis: Long = 0L
)

@Serializable
data class TeamSnapshotDto(
    val members: List<TeamMemberRowDto> = emptyList(),
    val rows: List<TeamUsageRowDto> = emptyList(),
    /**
     * Vazia contra servidor anterior à 0.7.0, que não conhece o campo.
     *
     * Lista vazia vira hora **não medida** no domínio, e não zero: o campo
     * ausente diz que o servidor não sabe, não que ninguém trabalhou.
     */
    val activity: List<TeamSessionActivityDto> = emptyList()
)

/** Metadados da sessão pedida em `GET /api/v1/session`, já com a máquina de origem. */
@Serializable
data class TeamSessionRowDto(
    val deviceId: String = "",
    val sessionId: String = "",
    val hostName: String? = null,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val firstTs: Long = 0L,
    val lastTs: Long = 0L,
    val liveContextTokens: Long = 0L,
    val liveContextModel: String? = null
)

/**
 * Um turno cru devolvido pelo servidor.
 *
 * Não tem `seq`, ao contrário de [TeamTurnUploadDto] — o servidor não guarda a
 * sequência. A ordem é a da resposta, `(ts, messageId)`, e o `seq` do domínio é
 * sintetizado dela na leitura.
 */
@Serializable
data class TeamTurnRowDto(
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
data class TeamSessionDetailResponseDto(
    val session: TeamSessionRowDto = TeamSessionRowDto(),
    val turns: List<TeamTurnRowDto> = emptyList()
)

/** Corpo de `POST /api/v1/claim`. A credencial vai no header. */
@Serializable
data class TeamClaimRequestDto(
    val accountKey: String
)

/** Resposta de `GET /api/v1/verify` e de `POST /api/v1/claim`. */
@Serializable
data class TeamVerificationDto(
    val authorized: Boolean = false,
    val claimed: Boolean = false,
    val label: String? = null,
    val maxAccounts: Int = 0,
    val claimedAccounts: Int = 0
)

/**
 * Chave de time nas rotas administrativas.
 *
 * `key` vem crua: o painel é a lista de "quem tem qual chave", e o servidor a
 * guarda cifrada justamente para poder devolvê-la aqui.
 */
@Serializable
data class TeamKeyDto(
    val id: String,
    val label: String = "",
    val key: String = "",
    val keyPrefix: String = "",
    val maxAccounts: Int = 1,
    val accounts: List<String> = emptyList(),
    val createdAt: Long? = null,
    val revokedAt: Long? = null,
    val lastUsedAt: Long? = null
)

@Serializable
data class TeamKeyListDto(
    val keys: List<TeamKeyDto> = emptyList()
)

@Serializable
data class CreateTeamKeyRequestDto(
    val label: String,
    val maxAccounts: Int = 1
)

/**
 * Campos nulos são omitidos na serialização por default do kotlinx, e é disso
 * que o `PATCH` parcial depende: mandar `label: null` reescreveria o rótulo.
 */
@Serializable
data class UpdateTeamKeyRequestDto(
    val label: String? = null,
    val maxAccounts: Int? = null
)

/**
 * Recibo de `DELETE /api/admin/v1/accounts/{accountKey}`.
 *
 * Defaults em tudo pelo mesmo motivo dos outros DTOs: um servidor que passe a
 * responder com menos campos não pode quebrar a desserialização.
 */
@Serializable
data class TeamAccountDeletionDto(
    val deletedTurns: Int = 0,
    val deletedSessions: Int = 0,
    val deletedMembers: Int = 0,
    val unlinkedKeys: Int = 0
)

/** Uma conta dentro de `GET /api/admin/v1/overview`. */
@Serializable
data class TeamAccountSnapshotDto(
    val accountKey: String,
    val label: String? = null,
    val accountEmail: String? = null,
    val emailSource: String? = null,
    val members: List<TeamMemberRowDto> = emptyList(),
    val rows: List<TeamUsageRowDto> = emptyList(),
    val activity: List<TeamSessionActivityDto> = emptyList()
)

@Serializable
data class TeamOverviewDto(
    val accounts: List<TeamAccountSnapshotDto> = emptyList()
)

/** Corpo de erro do servidor: `{ "error": "...", "code": "..." }`. */
@Serializable
data class TeamErrorDto(
    @SerialName("error") val message: String = "",
    val code: String = ""
)

/**
 * Uma linha da série diária do time: uma máquina, um dia (UTC) e um modelo.
 *
 * O servidor não precifica nada — devolve tokens, e o cliente aplica a própria
 * `ModelPricingTable`, como já faz com `/v1/team` e `/v1/session`.
 */
@Serializable
data class TeamTrendRowDto(
    val deviceId: String,
    val dayStartMillis: Long,
    val model: String? = null,
    val turnCount: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
)

@Serializable
data class TeamTrendResponseDto(
    val members: List<TeamMemberDto> = emptyList(),
    val rows: List<TeamTrendRowDto> = emptyList()
)
