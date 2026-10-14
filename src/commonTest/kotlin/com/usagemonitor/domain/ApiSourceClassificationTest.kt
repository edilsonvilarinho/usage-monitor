package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.requiresLocalApiKey
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiSourceClassificationTest {

    @Test
    fun `so as fontes com chave em api-keys json exigem chave local`() {
        val withKey = ApiSource.entries.filter { source -> source.requiresLocalApiKey() }.toSet()

        assertEquals(
            setOf(ApiSource.MINIMAX, ApiSource.DEEPSEEK, ApiSource.OPENCODE_GO, ApiSource.OPENROUTER),
            withKey
        )
    }
}
