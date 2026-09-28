package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.anthropicProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageTargetKeyTest {
    @Test
    fun `migrates legacy Anthropic source key to default profile`() {
        val target = UsageTargetKey.fromStorageKey("ANTHROPIC")

        assertEquals(ApiSource.ANTHROPIC, target?.source)
        assertEquals(DEFAULT_ANTHROPIC_PROFILE_ID, target?.profileId)
    }

    @Test
    fun `round trips custom Anthropic profile storage key`() {
        val original = UsageTargetKey(ApiSource.ANTHROPIC, "profile-a")

        assertEquals(original, UsageTargetKey.fromStorageKey(original.storageKey))
    }

    @Test
    fun `rejects profile suffix for sources without profiles`() {
        assertNull(UsageTargetKey.fromStorageKey("MINIMAX:profile-a"))
    }

    /** Issue #329: conta Codex extra tem perfil; a padrão continua sem, e a chave antiga não muda. */
    @Test
    fun `Codex keeps the bare key for the default account and round trips extra profiles`() {
        assertEquals("CODEX", UsageTargetKey.forSource(ApiSource.CODEX).storageKey)
        assertEquals(UsageTargetKey(ApiSource.CODEX), UsageTargetKey.fromStorageKey("CODEX"))

        val extra = UsageTargetKey(ApiSource.CODEX, "codex-a")
        assertEquals("CODEX:codex-a", extra.storageKey)
        assertEquals(extra, UsageTargetKey.fromStorageKey(extra.storageKey))
    }

    /** Cor e emoji por conta são da Anthropic: um perfil Codex não pode achar a cor de outro. */
    @Test
    fun `only Anthropic targets expose an Anthropic profile id`() {
        assertEquals("profile-a", UsageTargetKey(ApiSource.ANTHROPIC, "profile-a").anthropicProfileId)
        assertNull(UsageTargetKey(ApiSource.CODEX, "codex-a").anthropicProfileId)
    }
}
