package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.decodeHudMinimizedAccounts
import com.usagemonitor.presentation.ui.encodeHudMinimizedAccounts
import com.usagemonitor.presentation.ui.toggleHudMinimizedAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudMinimizedAccountsTest {
    private val codex = UsageTargetKey.forSource(ApiSource.CODEX)
    private val minimax = UsageTargetKey.forSource(ApiSource.MINIMAX)
    private val anthropicA = UsageTargetKey(ApiSource.ANTHROPIC, "profile-a")
    private val anthropicGone = UsageTargetKey(ApiSource.ANTHROPIC, "gone")

    @Test
    fun `toggle adds an account that is not minimized yet`() {
        assertEquals(setOf(codex), toggleHudMinimizedAccount(emptySet(), codex))
    }

    @Test
    fun `toggle removes an account that is already minimized`() {
        assertEquals(emptySet(), toggleHudMinimizedAccount(setOf(codex), codex))
    }

    @Test
    fun `toggle leaves the other accounts alone`() {
        val toggled = toggleHudMinimizedAccount(setOf(anthropicA, minimax), codex)

        assertEquals(setOf(anthropicA, minimax, codex), toggled)
    }

    /** Ordem de publicação (fonte, depois perfil): duas leituras iguais geram o mesmo texto. */
    @Test
    fun `encode writes the storage keys in publication order`() {
        assertEquals(
            "ANTHROPIC:profile-a,MINIMAX,CODEX",
            encodeHudMinimizedAccounts(setOf(codex, anthropicA, minimax))
        )
        assertEquals(
            encodeHudMinimizedAccounts(setOf(codex, anthropicA, minimax)),
            encodeHudMinimizedAccounts(setOf(minimax, codex, anthropicA))
        )
    }

    @Test
    fun `encode of an empty set is blank`() {
        assertEquals("", encodeHudMinimizedAccounts(emptySet()))
    }

    @Test
    fun `decode of an absent preference is empty`() {
        assertEquals(emptySet(), decodeHudMinimizedAccounts(null))
        assertEquals(emptySet(), decodeHudMinimizedAccounts(""))
    }

    @Test
    fun `decode round trips the set`() {
        val minimized = setOf(codex, anthropicA, minimax)

        assertEquals(minimized, decodeHudMinimizedAccounts(encodeHudMinimizedAccounts(minimized)))
    }

    /** Entradas em branco e chaves desconhecidas são ignoradas; espaços em volta somem. */
    @Test
    fun `decode ignores blank and unknown entries`() {
        val decoded = decodeHudMinimizedAccounts(" CODEX ,, ,FOO,ANTHROPIC:,MINIMAX")

        assertEquals(setOf(codex, minimax), decoded)
    }

    /**
     * Conta que deixou de existir não é apagada: a leitura não a descarta, e a escrita
     * seguinte (outro alvo alternado) a devolve ao texto gravado.
     */
    @Test
    fun `a stale account survives a later toggle of another account`() {
        val stored = decodeHudMinimizedAccounts("ANTHROPIC:gone,CODEX")
        assertTrue(anthropicGone in stored)

        val rewritten = encodeHudMinimizedAccounts(toggleHudMinimizedAccount(stored, minimax))

        assertEquals("ANTHROPIC:gone,MINIMAX,CODEX", rewritten)
    }
}
