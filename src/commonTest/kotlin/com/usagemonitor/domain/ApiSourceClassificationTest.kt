package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.QuotaThresholdGap
import com.usagemonitor.domain.entity.hasCliSessionIndex
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.domain.entity.requiresLocalApiKey
import com.usagemonitor.domain.entity.sourcesWithQuotaThresholdGap
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

    /**
     * Dois `when` exaustivos descrevem o mesmo conjunto; nada no compilador os amarra,
     * então este teste é quem impede uma fonte nova de entrar só num deles.
     */
    @Test
    fun `atividade observada e a lacuna de limiar observada sao o mesmo conjunto`() {
        val observed = ApiSource.entries.filter { source -> source.isObservedActivitySource() }

        assertEquals(sourcesWithQuotaThresholdGap(QuotaThresholdGap.OBSERVED_ACTIVITY), observed)
    }

    @Test
    fun `so Claude CLI e Codex tem indice local de sessoes`() {
        val indexed = ApiSource.entries.filter { source -> source.hasCliSessionIndex() }.toSet()

        assertEquals(setOf(ApiSource.ANTHROPIC, ApiSource.CODEX), indexed)
    }
}
