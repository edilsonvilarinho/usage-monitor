package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.viewmodel.PendingFetchRequest
import com.usagemonitor.presentation.viewmodel.mergePendingFetch
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pedidos de coleta que chegam enquanto outro espera na fila. */
class PendingFetchMergeTest {

    private val anthropic = UsageTargetKey(ApiSource.ANTHROPIC, "default")
    private val codex = UsageTargetKey.forSource(ApiSource.CODEX)
    private val minimax = UsageTargetKey.forSource(ApiSource.MINIMAX)
    private val all = setOf(anthropic, codex, minimax)

    @Test
    fun `the first request waits as it came`() {
        assertEquals(
            PendingFetchRequest(setOf(codex), preserveDataOnFailure = true),
            mergePendingFetch(existing = null, targets = setOf(codex), preserveDataOnFailure = true, allTargets = { all })
        )
    }

    @Test
    fun `two requests that keep the data add up their targets`() {
        val merged = mergePendingFetch(
            existing = PendingFetchRequest(setOf(codex), preserveDataOnFailure = true),
            targets = setOf(anthropic),
            preserveDataOnFailure = true,
            allTargets = { error("não deve pedir todos os alvos") }
        )

        assertEquals(PendingFetchRequest(setOf(codex, anthropic), preserveDataOnFailure = true), merged)
    }

    @Test
    fun `any request that does not keep the data becomes a full fetch that covers both`() {
        val full = PendingFetchRequest(all, preserveDataOnFailure = false)

        assertEquals(
            full,
            mergePendingFetch(PendingFetchRequest(setOf(codex), preserveDataOnFailure = true), setOf(anthropic), preserveDataOnFailure = false) { all }
        )
        assertEquals(
            full,
            mergePendingFetch(PendingFetchRequest(setOf(codex), preserveDataOnFailure = false), setOf(anthropic), preserveDataOnFailure = true) { all }
        )
    }
}
