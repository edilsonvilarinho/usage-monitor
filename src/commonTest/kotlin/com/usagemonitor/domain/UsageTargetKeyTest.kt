package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.UsageTargetKey
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
    fun `rejects profile suffix for non Anthropic source`() {
        assertNull(UsageTargetKey.fromStorageKey("CODEX:profile-a"))
    }
}
