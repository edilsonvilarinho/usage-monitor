package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.decodeRateLimitBackoffs
import com.usagemonitor.domain.entity.encodeRateLimitBackoffs
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private const val RATE_LIMIT_BACKOFFS_KEY = "rateLimitBackoffUntil"

/**
 * Os prazos de backoff por 429, um por alvo (issue #269).
 *
 * Gravados para reiniciar o app não zerar a punição: sem isso o arranque logo
 * depois de um 429 voltava a bater no endpoint que ainda estava limitando. Não é
 * segredo — só o `storageKey` do alvo e um instante —, então fica em
 * `PreferencesSettings`, ao lado de `nextRefreshAtMillis`.
 */
internal fun readPersistedRateLimitBackoffs(
    settings: PreferencesSettings,
    now: Instant = Clock.System.now()
): Map<UsageTargetKey, Instant> {
    return decodeRateLimitBackoffs(settings.getStringOrNull(RATE_LIMIT_BACKOFFS_KEY), now)
}

internal fun persistRateLimitBackoffs(settings: PreferencesSettings, backoffs: Map<UsageTargetKey, Instant>) {
    if (backoffs.isEmpty()) {
        settings.remove(RATE_LIMIT_BACKOFFS_KEY)
        return
    }
    settings.putString(RATE_LIMIT_BACKOFFS_KEY, encodeRateLimitBackoffs(backoffs))
}
