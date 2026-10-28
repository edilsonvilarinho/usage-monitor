package com.usagemonitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A primeira âncora da HUD registra o arranque e confirma a atualização; as
 * seguintes (a HUD renasce ao trocar de monitor, #426) não repetem nenhum dos dois.
 */
class AppWindowAnchorTest {

    private val shown = mutableListOf<Boolean?>()
    private val acks = mutableListOf<String>()

    private fun anchor(anchored: MutableStateFlow<Boolean>, ackToken: String?, effective: Boolean? = true) = runBlocking {
        recordAppAnchored(
            anchored = anchored,
            alwaysOnTopEffective = { effective },
            recordShown = { value -> shown += value },
            ackToken = ackToken,
            acknowledge = { token -> acks += token }
        )
    }

    @Test
    fun `the first anchor records the window and acknowledges the update`() {
        val anchored = MutableStateFlow(false)

        anchor(anchored, ackToken = "token-1", effective = false)

        assertEquals(listOf<Boolean?>(false), shown, "o efetivo lido da AWT vai ao registro")
        assertEquals(listOf("token-1"), acks)
        assertTrue(anchored.value)
    }

    @Test
    fun `without an update token nothing is acknowledged`() {
        val anchored = MutableStateFlow(false)

        anchor(anchored, ackToken = null, effective = null)

        assertEquals(listOf<Boolean?>(null), shown)
        assertEquals(emptyList(), acks)
        assertTrue(anchored.value)
    }

    @Test
    fun `a reborn window does not record the launch or acknowledge again`() {
        val anchored = MutableStateFlow(false)

        anchor(anchored, ackToken = "token-1")
        anchor(anchored, ackToken = "token-1")
        anchor(anchored, ackToken = "token-1")

        assertEquals(1, shown.size)
        assertEquals(listOf("token-1"), acks)
    }
}
