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
        if (source == ApiSource.ANTHROPIC) {
            require(!profileId.isNullOrBlank()) { "O alvo Anthropic exige um perfil." }
        } else {
            require(profileId == null) { "Somente alvos Anthropic podem informar perfil." }
        }
    }

    val storageKey: String
        get() = if (source == ApiSource.ANTHROPIC) {
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
