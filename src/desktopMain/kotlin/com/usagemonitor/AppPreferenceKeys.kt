package com.usagemonitor

import androidx.compose.ui.input.key.key
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey

/*
 * As chaves de preferência que o app lê e grava em `PreferencesSettings`, e a
 * leitura das coleções gravadas nelas. Segredos não moram aqui: chaves de API e
 * do time vão para arquivos com permissão restrita ao dono.
 */

internal val DEFAULT_ENABLED_APIS = emptySet<ApiSource>()

/**
 * Fontes cuja coleta exige uma chave em `~/.usage-monitor/api-keys.json`.
 *
 * Existe como constante e não como literal repetido no filtro de arranque porque
 * a mesma pergunta é feita nas Configurações (`requiresApiKey`) e no
 * `LocalApiKeyDataSource`: três donos do mesmo conjunto já seria demais.
 */
internal val API_KEY_DEPENDENT_SOURCES = setOf(
    ApiSource.MINIMAX,
    ApiSource.DEEPSEEK,
    ApiSource.OPENCODE_GO,
    ApiSource.OPENROUTER
)

internal const val ENABLED_APIS_KEY = "enabledApis"

internal const val LANGUAGE_KEY = "language"

/** Idioma persistido; valor irreconhecivel cai no default em vez de derrubar. */
internal fun storedLanguage(settings: Settings): AppLanguage {
    return settings.getStringOrNull(LANGUAGE_KEY)
        ?.let { stored -> runCatching { AppLanguage.valueOf(stored) }.getOrNull() }
        ?: AppLanguage.PT
}

internal const val AUTO_START_KEY = "autoStart"

internal const val CARD_ORDER_KEY = "cardOrder"

internal const val NEXT_REFRESH_AT_KEY = "nextRefreshAtMillis"

internal fun readApiSourceCollection(
    settings: PreferencesSettings,
    key: String
): List<ApiSource> {
    return settings.getStringOrNull(key)
        ?.split(",")
        ?.filter { token -> token.isNotBlank() }
        ?.mapNotNull { token -> runCatching { ApiSource.valueOf(token) }.getOrNull() }
        ?: emptyList()
}

internal fun writeApiSourceCollection(
    settings: PreferencesSettings,
    key: String,
    sources: Collection<ApiSource>
) {
    settings.putString(
        key,
        sources.joinToString(",") { source -> source.name }
    )
}

internal fun readUsageTargetCollection(
    settings: PreferencesSettings,
    key: String
): List<UsageTargetKey> {
    return settings.getStringOrNull(key)
        ?.split(",")
        ?.filter { token -> token.isNotBlank() }
        ?.mapNotNull(UsageTargetKey::fromStorageKey)
        ?: emptyList()
}

internal fun writeUsageTargetCollection(
    settings: PreferencesSettings,
    key: String,
    targets: Collection<UsageTargetKey>
) {
    settings.putString(key, targets.joinToString(",") { target -> target.storageKey })
}
