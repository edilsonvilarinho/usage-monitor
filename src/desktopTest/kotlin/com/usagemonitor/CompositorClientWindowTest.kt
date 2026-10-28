package com.usagemonitor

import com.usagemonitor.presentation.ui.X11WindowTree
import com.usagemonitor.presentation.ui.compositorClientWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A subida até a janela cliente do arrasto pelo compositor (#434). Árvore falsa:
 * `parents` leva cada janela ao pai, `root` é a raiz, e `wmState` diz quais têm
 * `WM_STATE`. Nenhum X11 aqui.
 */
class CompositorClientWindowTest {

    private class FakeTree(
        private val parents: Map<Long, Long>,
        private val wmState: Set<Long>,
        private val root: Long = ROOT,
        private val failing: Set<Long> = emptySet()
    ) : X11WindowTree {
        val queried = mutableListOf<Long>()

        override fun parentAndRoot(window: Long): Pair<Long, Long>? {
            queried += window
            if (window in failing) {
                return null
            }
            return (parents[window] ?: 0L) to root
        }

        override fun hasWmState(window: Long): Boolean = window in wmState
    }

    @Test
    fun `a window that already has WM_STATE is its own client`() {
        val tree = FakeTree(parents = mapOf(CLIENT to FRAME, FRAME to ROOT), wmState = setOf(CLIENT))

        assertEquals(CLIENT, compositorClientWindow(CLIENT, tree))
    }

    @Test
    fun `a reparented frame is climbed to the client with WM_STATE`() {
        // A janela que o AWT entrega é um filho do cliente; o cliente é reparentado na moldura.
        val tree = FakeTree(
            parents = mapOf(INNER to CLIENT, CLIENT to FRAME, FRAME to ROOT),
            wmState = setOf(CLIENT)
        )

        assertEquals(CLIENT, compositorClientWindow(INNER, tree))
    }

    @Test
    fun `without WM_STATE on the path the child of the root is used`() {
        // Gerenciador que não reparenta: a janela do app já é filha da raiz.
        val tree = FakeTree(parents = mapOf(INNER to ROOT), wmState = emptySet())

        assertEquals(INNER, compositorClientWindow(INNER, tree))
    }

    @Test
    fun `without WM_STATE a reparented frame stays the answer`() {
        // Sem `WM_STATE` em nenhum ancestral, o topo do caminho é o que vale.
        val tree = FakeTree(parents = mapOf(INNER to FRAME, FRAME to ROOT), wmState = emptySet())

        assertEquals(FRAME, compositorClientWindow(INNER, tree))
    }

    @Test
    fun `the first WM_STATE on the way up wins over a higher one`() {
        val tree = FakeTree(
            parents = mapOf(INNER to CLIENT, CLIENT to FRAME, FRAME to ROOT),
            wmState = setOf(CLIENT, FRAME)
        )

        assertEquals(CLIENT, compositorClientWindow(INNER, tree))
    }

    @Test
    fun `window zero has no client`() {
        val tree = FakeTree(parents = emptyMap(), wmState = emptySet())

        assertNull(compositorClientWindow(0L, tree))
        assertEquals(emptyList(), tree.queried)
    }

    @Test
    fun `a failed tree query gives no client`() {
        val tree = FakeTree(parents = mapOf(INNER to CLIENT), wmState = emptySet(), failing = setOf(INNER))

        assertNull(compositorClientWindow(INNER, tree))
    }

    @Test
    fun `a tree deeper than the limit gives no client`() {
        // Cadeia de 20 pais sem raiz no caminho: o limite de 16 níveis corta a subida.
        val parents = (1L..19L).associateWith { id -> id + 1 }
        val tree = FakeTree(parents = parents, wmState = emptySet(), root = ROOT)

        assertNull(compositorClientWindow(1L, tree, maxDepth = 16))
        assertEquals(16, tree.queried.size)
    }

    @Test
    fun `the climb stops at the root without querying past it`() {
        val tree = FakeTree(parents = mapOf(INNER to FRAME, FRAME to ROOT), wmState = setOf(FRAME))

        assertEquals(FRAME, compositorClientWindow(INNER, tree))
        assertEquals(listOf(INNER, FRAME), tree.queried)
    }

    private companion object {
        const val ROOT = 0x1L
        const val FRAME = 0x200L
        const val CLIENT = 0x400L
        const val INNER = 0x500L
    }
}
