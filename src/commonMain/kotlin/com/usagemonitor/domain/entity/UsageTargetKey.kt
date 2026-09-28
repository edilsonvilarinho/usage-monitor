package com.usagemonitor.domain.entity

const val DEFAULT_ANTHROPIC_PROFILE_ID = "default"

data class AnthropicProfileRef(
    val id: String,
    val label: String
) {
    init {
        require(id.isNotBlank()) { "O identificador do perfil Anthropic não pode ser vazio." }
        require(label.isNotBlank()) { "O nome do perfil Anthropic não pode ser vazio." }
    }

    companion object {
        val DEFAULT = AnthropicProfileRef(DEFAULT_ANTHROPIC_PROFILE_ID, "Padrão")
    }
}

data class UsageTargetKey(
    val source: ApiSource,
    val profileId: String? = null
) {
    init {
        when (source) {
            ApiSource.ANTHROPIC -> require(!profileId.isNullOrBlank()) { "O alvo Anthropic exige um perfil." }
            // Codex: sem perfil é a conta padrão; com perfil, uma conta extra (issue #329).
            ApiSource.CODEX -> require(profileId == null || profileId.isNotBlank()) { "Perfil Codex vazio." }
            else -> require(profileId == null) { "Somente alvos Anthropic e Codex podem informar perfil." }
        }
    }

    val storageKey: String
        get() = if (profileId != null) {
            "${source.name}:${profileId}"
        } else {
            source.name
        }

    companion object {
        fun forSource(source: ApiSource): UsageTargetKey {
            return if (source == ApiSource.ANTHROPIC) {
                UsageTargetKey(source, DEFAULT_ANTHROPIC_PROFILE_ID)
            } else {
                UsageTargetKey(source)
            }
        }

        fun fromStorageKey(value: String): UsageTargetKey? {
            if (value == ApiSource.ANTHROPIC.name) {
                return forSource(ApiSource.ANTHROPIC)
            }

            val separatorIndex = value.indexOf(':')
            if (separatorIndex >= 0) {
                val source = runCatching { ApiSource.valueOf(value.substring(0, separatorIndex)) }.getOrNull()
                    ?: return null
                val profileId = value.substring(separatorIndex + 1).takeIf { it.isNotBlank() }
                return runCatching { UsageTargetKey(source, profileId) }.getOrNull()
            }

            val source = runCatching { ApiSource.valueOf(value) }.getOrNull() ?: return null
            return runCatching { forSource(source) }.getOrNull()
        }
    }
}

/**
 * O perfil Anthropic do alvo, ou `null` para qualquer outra fonte. Cor e emoji por
 * conta (issues #275 e #287) são mapas por `profileId` de perfil **Anthropic**; com
 * contas Codex extras (issue #329) o `profileId` sozinho deixou de dizer de qual
 * registro ele é.
 */
val UsageTargetKey.anthropicProfileId: String?
    get() = profileId.takeIf { source == ApiSource.ANTHROPIC }
