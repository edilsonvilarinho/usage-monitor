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
    @SerialName("deviceId") val deviceId: String,
    @SerialName("alias") val alias: String,
    @SerialName("hostName") val hostName: String? = null,
    @SerialName("organizationUuid") val organizationUuid: String? = null,
    @SerialName("organizationName") val organizationName: String? = null
)

@Serializable
data class TeamSessionUploadDto(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("cwd") val cwd: String? = null,
    @SerialName("gitBranch") val gitBranch: String? = null,
    @SerialName("firstTs") val firstTs: Long,
    @SerialName("lastTs") val lastTs: Long,
    @SerialName("liveContextTokens") val liveContextTokens: Long = 0L,
    @SerialName("liveContextModel") val liveContextModel: String? = null
)

@Serializable
data class TeamTurnUploadDto(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("messageId") val messageId: String,
    @SerialName("ts") val ts: Long,
    @SerialName("model") val model: String? = null,
    @SerialName("isSidechain") val isSidechain: Boolean = false,
    @SerialName("inputTokens") val inputTokens: Long = 0L,
    @SerialName("outputTokens") val outputTokens: Long = 0L,
    @SerialName("cacheReadTokens") val cacheReadTokens: Long = 0L,
    @SerialName("cacheWrite5mTokens") val cacheWrite5mTokens: Long = 0L,
    @SerialName("cacheWrite1hTokens") val cacheWrite1hTokens: Long = 0L
)

@Serializable
data class TeamIngestRequestDto(
    @SerialName("accountKey") val accountKey: String,
    @SerialName("accountEmail") val accountEmail: String? = null,
    @SerialName("member") val member: TeamMemberDto,
    @SerialName("sessions") val sessions: List<TeamSessionUploadDto> = emptyList(),
    @SerialName("turns") val turns: List<TeamTurnUploadDto> = emptyList()
)

@Serializable
data class TeamIngestResponseDto(
    @SerialName("acceptedTurns") val acceptedTurns: Int = 0,
    @SerialName("ignoredTurns") val ignoredTurns: Int = 0,
    @SerialName("acceptedSessions") val acceptedSessions: Int = 0
)

/**
 * Corpo de `POST /api/v1/presence`. Reusa [TeamMemberDto] do ingest — é o mesmo
 * membro, sem sessões nem turnos.
 */
@Serializable
data class TeamPresenceRequestDto(
    @SerialName("accountKey") val accountKey: String,
    @SerialName("accountEmail") val accountEmail: String? = null,
    @SerialName("member") val member: TeamMemberDto
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
    @SerialName("lastSeenAt") val lastSeenAt: Long = 0L
)

@Serializable
data class TeamMemberRowDto(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("alias") val alias: String,
    @SerialName("hostName") val hostName: String? = null,
    @SerialName("organizationUuid") val organizationUuid: String? = null,
    @SerialName("organizationName") val organizationName: String? = null,
    @SerialName("lastSeenAt") val lastSeenAt: Long = 0L
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
    @SerialName("deviceId") val deviceId: String,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("cwd") val cwd: String? = null,
    @SerialName("gitBranch") val gitBranch: String? = null,
    @SerialName("liveContextTokens") val liveContextTokens: Long = 0L,
    @SerialName("liveContextModel") val liveContextModel: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("turnCount") val turnCount: Int = 0,
    @SerialName("firstTs") val firstTs: Long = 0L,
    @SerialName("lastTs") val lastTs: Long = 0L,
    @SerialName("inputTokens") val inputTokens: Long = 0L,
    @SerialName("outputTokens") val outputTokens: Long = 0L,
    @SerialName("cacheReadTokens") val cacheReadTokens: Long = 0L,
    @SerialName("cacheWrite5mTokens") val cacheWrite5mTokens: Long = 0L,
    @SerialName("cacheWrite1hTokens") val cacheWrite1hTokens: Long = 0L,
    /**
     * Parte de prompt longo do grupo. `null` quando o servidor não manda os cinco campos
     * (servidor anterior à faixa de prompt longo); o mapper só monta a parte com os cinco presentes.
     */
    @SerialName("longPromptInputTokens") val longPromptInputTokens: Long? = null,
    @SerialName("longPromptOutputTokens") val longPromptOutputTokens: Long? = null,
    @SerialName("longPromptCacheReadTokens") val longPromptCacheReadTokens: Long? = null,
    @SerialName("longPromptCacheWrite5mTokens") val longPromptCacheWrite5mTokens: Long? = null,
    @SerialName("longPromptCacheWrite1hTokens") val longPromptCacheWrite1hTokens: Long? = null
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
    @SerialName("deviceId") val deviceId: String = "",
    @SerialName("sessionId") val sessionId: String = "",
    @SerialName("activeMillis") val activeMillis: Long = 0L
)

@Serializable
data class TeamSnapshotDto(
    @SerialName("members") val members: List<TeamMemberRowDto> = emptyList(),
    @SerialName("rows") val rows: List<TeamUsageRowDto> = emptyList(),
    /**
     * Vazia contra servidor anterior à 0.7.0, que não conhece o campo.
     *
     * Lista vazia vira hora **não medida** no domínio, e não zero: o campo
     * ausente diz que o servidor não sabe, não que ninguém trabalhou.
     */
    @SerialName("activity") val activity: List<TeamSessionActivityDto> = emptyList()
)

/** Metadados da sessão pedida em `GET /api/v1/session`, já com a máquina de origem. */
@Serializable
data class TeamSessionRowDto(
    @SerialName("deviceId") val deviceId: String = "",
    @SerialName("sessionId") val sessionId: String = "",
    @SerialName("hostName") val hostName: String? = null,
    @SerialName("cwd") val cwd: String? = null,
    @SerialName("gitBranch") val gitBranch: String? = null,
    @SerialName("firstTs") val firstTs: Long = 0L,
    @SerialName("lastTs") val lastTs: Long = 0L,
    @SerialName("liveContextTokens") val liveContextTokens: Long = 0L,
    @SerialName("liveContextModel") val liveContextModel: String? = null
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
    @SerialName("messageId") val messageId: String,
    @SerialName("ts") val ts: Long,
    @SerialName("model") val model: String? = null,
    @SerialName("isSidechain") val isSidechain: Boolean = false,
    @SerialName("inputTokens") val inputTokens: Long = 0L,
    @SerialName("outputTokens") val outputTokens: Long = 0L,
    @SerialName("cacheReadTokens") val cacheReadTokens: Long = 0L,
    @SerialName("cacheWrite5mTokens") val cacheWrite5mTokens: Long = 0L,
    @SerialName("cacheWrite1hTokens") val cacheWrite1hTokens: Long = 0L
)

@Serializable
data class TeamSessionDetailResponseDto(
    @SerialName("session") val session: TeamSessionRowDto = TeamSessionRowDto(),
    @SerialName("turns") val turns: List<TeamTurnRowDto> = emptyList()
)

/**
 * Corpo de `POST /api/v1/claim`. A credencial vai no header.
 *
 * O `accountEmail` viaja junto porque o servidor confere a conta contra o rótulo
 * da chave (servidor 0.11.0+): sem ele o vínculo seria aprovado aqui e recusado
 * no envio seguinte, deixando a sincronia parada em silêncio. Servidor anterior
 * ignora o campo.
 */
@Serializable
data class TeamClaimRequestDto(
    @SerialName("accountKey") val accountKey: String,
    @SerialName("accountEmail") val accountEmail: String? = null
)

/** Resposta de `GET /api/v1/verify` e de `POST /api/v1/claim`. */
@Serializable
data class TeamVerificationDto(
    @SerialName("authorized") val authorized: Boolean = false,
    @SerialName("claimed") val claimed: Boolean = false,
    @SerialName("label") val label: String? = null,
    @SerialName("maxAccounts") val maxAccounts: Int = 0,
    @SerialName("claimedAccounts") val claimedAccounts: Int = 0
)

/**
 * Chave de time nas rotas administrativas.
 *
 * `key` vem crua: o painel é a lista de "quem tem qual chave", e o servidor a
 * guarda cifrada justamente para poder devolvê-la aqui.
 */
@Serializable
data class TeamKeyDto(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String = "",
    @SerialName("key") val key: String = "",
    @SerialName("keyPrefix") val keyPrefix: String = "",
    @SerialName("maxAccounts") val maxAccounts: Int = 1,
    @SerialName("accounts") val accounts: List<String> = emptyList(),
    /**
     * As mesmas contas de [accounts], com e-mail e veredito (servidor 0.11.0+).
     *
     * Campo novo ao lado da lista antiga, e não no lugar dela: mudar a forma de
     * `accounts` quebraria app anterior. Vazia contra servidor mais velho, e aí
     * a tela cai em [accounts] e mostra só o UUID, como sempre mostrou.
     */
    @SerialName("accountDetails") val accountDetails: List<TeamKeyAccountDto> = emptyList(),
    @SerialName("createdAt") val createdAt: Long? = null,
    @SerialName("revokedAt") val revokedAt: Long? = null,
    @SerialName("lastUsedAt") val lastUsedAt: Long? = null
)

/** Conta vinculada a uma chave, com o que a tela precisa para decidir sobre ela. */
@Serializable
data class TeamKeyAccountDto(
    @SerialName("accountKey") val accountKey: String,
    @SerialName("accountEmail") val accountEmail: String? = null,
    /** `false` quando o e-mail da conta não está na relação declarada no rótulo. */
    @SerialName("authorized") val authorized: Boolean = true
)

/** Resposta de `GET` e `DELETE /api/admin/v1/blocked-accounts`. */
@Serializable
data class TeamBlockedAccountListDto(
    @SerialName("accounts") val accounts: List<TeamBlockedAccountDto> = emptyList()
)

@Serializable
data class TeamBlockedAccountDto(
    @SerialName("accountKey") val accountKey: String,
    /** Retrato do e-mail no momento do bloqueio; a conta em si já foi apagada. */
    @SerialName("accountEmail") val accountEmail: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("blockedAt") val blockedAt: Long? = null
)

@Serializable
data class TeamKeyListDto(
    @SerialName("keys") val keys: List<TeamKeyDto> = emptyList()
)

@Serializable
data class CreateTeamKeyRequestDto(
    @SerialName("label") val label: String,
    @SerialName("maxAccounts") val maxAccounts: Int = 1
)

/**
 * Campos nulos são omitidos na serialização por default do kotlinx, e é disso
 * que o `PATCH` parcial depende: mandar `label: null` reescreveria o rótulo.
 */
@Serializable
data class UpdateTeamKeyRequestDto(
    @SerialName("label") val label: String? = null,
    @SerialName("maxAccounts") val maxAccounts: Int? = null
)

/**
 * Recibo de `DELETE /api/admin/v1/accounts/{accountKey}`.
 *
 * Defaults em tudo pelo mesmo motivo dos outros DTOs: um servidor que passe a
 * responder com menos campos não pode quebrar a desserialização.
 */
@Serializable
data class TeamAccountDeletionDto(
    @SerialName("deletedTurns") val deletedTurns: Int = 0,
    @SerialName("deletedSessions") val deletedSessions: Int = 0,
    @SerialName("deletedMembers") val deletedMembers: Int = 0,
    @SerialName("unlinkedKeys") val unlinkedKeys: Int = 0
)

/** Uma conta dentro de `GET /api/admin/v1/overview`. */
@Serializable
data class TeamAccountSnapshotDto(
    @SerialName("accountKey") val accountKey: String,
    @SerialName("label") val label: String? = null,
    @SerialName("accountEmail") val accountEmail: String? = null,
    @SerialName("emailSource") val emailSource: String? = null,
    @SerialName("members") val members: List<TeamMemberRowDto> = emptyList(),
    @SerialName("rows") val rows: List<TeamUsageRowDto> = emptyList(),
    @SerialName("activity") val activity: List<TeamSessionActivityDto> = emptyList()
)

@Serializable
data class TeamOverviewDto(
    @SerialName("accounts") val accounts: List<TeamAccountSnapshotDto> = emptyList()
)

/** Corpo de erro do servidor: `{ "error": "...", "code": "..." }`. */
@Serializable
data class TeamErrorDto(
    @SerialName("error") val message: String = "",
    @SerialName("code") val code: String = ""
)

/**
 * Uma linha da série diária do time: uma máquina, um dia (UTC) e um modelo.
 *
 * O servidor não precifica nada — devolve tokens, e o cliente aplica a própria
 * `ModelPricingTable`, como já faz com `/v1/team` e `/v1/session`.
 */
@Serializable
data class TeamTrendRowDto(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("dayStartMillis") val dayStartMillis: Long,
    @SerialName("model") val model: String? = null,
    @SerialName("turnCount") val turnCount: Int = 0,
    @SerialName("inputTokens") val inputTokens: Long = 0L,
    @SerialName("outputTokens") val outputTokens: Long = 0L,
    @SerialName("cacheReadTokens") val cacheReadTokens: Long = 0L,
    @SerialName("cacheWrite5mTokens") val cacheWrite5mTokens: Long = 0L,
    @SerialName("cacheWrite1hTokens") val cacheWrite1hTokens: Long = 0L,
    /**
     * Parte de prompt longo da linha. `null` quando o servidor não manda os cinco campos
     * (servidor anterior à faixa de prompt longo); o mapper só monta a parte com os cinco presentes.
     */
    @SerialName("longPromptInputTokens") val longPromptInputTokens: Long? = null,
    @SerialName("longPromptOutputTokens") val longPromptOutputTokens: Long? = null,
    @SerialName("longPromptCacheReadTokens") val longPromptCacheReadTokens: Long? = null,
    @SerialName("longPromptCacheWrite5mTokens") val longPromptCacheWrite5mTokens: Long? = null,
    @SerialName("longPromptCacheWrite1hTokens") val longPromptCacheWrite1hTokens: Long? = null
)

@Serializable
data class TeamTrendResponseDto(
    @SerialName("members") val members: List<TeamMemberDto> = emptyList(),
    @SerialName("rows") val rows: List<TeamTrendRowDto> = emptyList()
)
