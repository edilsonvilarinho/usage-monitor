package com.usagemonitor.data.dto

import com.usagemonitor.domain.entity.redactedSecret
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representa a estrutura do ficheiro ~/.claude/.credentials.json
 *
 * Este ficheiro é criado pelo Claude Code CLI e contém o token OAuth
 * do Claude.ai, usado para autenticar chamadas à API Anthropic.
 */
@Serializable
data class CredentialsFileDto(
    @SerialName("claudeAiOauth") val claudeAiOauth: OAuthCredentialsDto
)

@Serializable
data class OAuthCredentialsDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String = "",

    // Timestamp de expiração em milissegundos (epoch ms)
    @SerialName("expiresAt") val expiresAt: Long = Long.MAX_VALUE,

    @SerialName("scopes") val scopes: List<String> = emptyList(),
    @SerialName("subscriptionType") val subscriptionType: String? = null,
    @SerialName("rateLimitTier") val rateLimitTier: String? = null
) {
    /** Segredo nunca no `toString` (log, relatório de bug, mensagem de exceção). */
    override fun toString(): String =
        "OAuthCredentialsDto(accessToken=${redactedSecret(accessToken)}, refreshToken=${redactedSecret(refreshToken)}, expiresAt=$expiresAt, " +
            "scopes=$scopes, subscriptionType=$subscriptionType, rateLimitTier=$rateLimitTier)"
}
