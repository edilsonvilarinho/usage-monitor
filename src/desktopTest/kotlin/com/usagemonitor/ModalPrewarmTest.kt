package com.usagemonitor

import com.usagemonitor.presentation.ui.awaitUntil
import com.usagemonitor.presentation.ui.ModalCloak
import com.usagemonitor.presentation.ui.WindowCloak
import com.usagemonitor.presentation.ui.prewarmCloaked
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O pré-aquecimento escondido pelo DWM: ordem dos passos e liberação, sem janela. */
class ModalPrewarmTest {

    /** Nativo falso: grava cada chamada e responde pela lista, `true` quando ela acaba. */
    private class FakeCloak(vararg answers: Boolean) {
        val events = mutableListOf<String>()
        private val pending = answers.toMutableList()

        val native = WindowCloak { cloaked ->
            events += "set($cloaked)"
            if (pending.isEmpty()) true else pending.removeAt(0)
        }
    }

    @Test
    fun `prewarm shows the window only after cloaking it, then hides it, waits and uncloaks`() = runTest {
        val fake = FakeCloak()
        val cloak = ModalCloak(fake.native)

        val shown = prewarmCloaked(
            cloak = cloak,
            show = { fake.events += "show" },
            awaitFrames = { fake.events += "frames" },
            hide = { fake.events += "hide" },
            awaitHidden = { fake.events += "hidden" }
        )

        assertTrue(shown)
        assertEquals(listOf("set(true)", "show", "frames", "hide", "hidden", "set(false)"), fake.events)
        assertFalse(cloak.active)
    }

    /** Janela que o sistema não consegue esconder não pode aparecer no clique do usuário. */
    @Test
    fun `a refused cloak returns false and never shows the window`() = runTest {
        val fake = FakeCloak(false)
        var shown = false

        val result = prewarmCloaked(
            cloak = ModalCloak(fake.native),
            show = { shown = true },
            awaitFrames = {},
            hide = {},
            awaitHidden = {}
        )

        assertFalse(result)
        assertFalse(shown)
        assertEquals(listOf("set(true)"), fake.events)
    }

    /** Cancelar no meio da espera de quadros esconde a janela e só depois libera. */
    @Test
    fun `cancelling while the frames are awaited still hides the window and only then uncloaks it`() = runTest {
        val fake = FakeCloak()
        val cloak = ModalCloak(fake.native)
        val framesStarted = CompletableDeferred<Unit>()

        val job = launch {
            prewarmCloaked(
                cloak = cloak,
                show = { fake.events += "show" },
                awaitFrames = {
                    framesStarted.complete(Unit)
                    awaitCancellation()
                },
                hide = { fake.events += "hide" },
                awaitHidden = { fake.events += "hidden" }
            )
        }
        withTimeoutOrNull(1_000) { framesStarted.await() }
        job.cancelAndJoin()

        assertEquals(listOf("set(true)", "show", "hide", "hidden", "set(false)"), fake.events)
    }

    /** Falha no meio: esconder vem antes de liberar, sempre. */
    @Test
    fun `a failure while awaiting frames still hides before it uncloaks`() = runTest {
        val fake = FakeCloak()
        val cloak = ModalCloak(fake.native)

        assertFailsWith<IllegalStateException> {
            prewarmCloaked(
                cloak = cloak,
                show = { fake.events += "show" },
                awaitFrames = { throw IllegalStateException("falha de quadro") },
                hide = { fake.events += "hide" },
                awaitHidden = { fake.events += "hidden" }
            )
        }

        assertEquals(listOf("set(true)", "show", "hide", "hidden", "set(false)"), fake.events)
    }

    /** Liberação recusada fica oculta; a próxima liberação tenta de novo. */
    @Test
    fun `a refused uncloak is retried by the next release`() {
        val fake = FakeCloak(true, false, true)
        val cloak = ModalCloak(fake.native)

        assertTrue(cloak.cloak())
        assertFalse(cloak.release())
        assertTrue(cloak.active)
        assertTrue(cloak.release())
        assertFalse(cloak.active)
        assertEquals(listOf("set(true)", "set(false)", "set(false)"), fake.events)
    }

    /** Sem cloak não há o que desfazer: o nativo não é chamado. */
    @Test
    fun `release without a cloak does not call the native`() {
        val fake = FakeCloak()

        assertTrue(ModalCloak(fake.native).release())
        assertEquals(emptyList(), fake.events)
    }
}

/** Sondagem da espera da janela escondida: tempo virtual, sem janela. */
class AwaitUntilTest {

    /** A espera volta assim que a condição vale, sem esperar o teto. */
    @Test
    fun `awaitUntil returns as soon as the condition holds`() = runTest {
        var polls = 0
        val holds = awaitUntil(timeoutMillis = 1_000L, stepMillis = 5L) { ++polls >= 5 }
        assertTrue(holds)
        assertEquals(20L, testScheduler.currentTime)
    }

    /** Sem a condição, a espera desiste no teto e não fica presa. */
    @Test
    fun `awaitUntil gives up at the cap`() = runTest {
        val holds = awaitUntil(timeoutMillis = 1_000L, stepMillis = 5L) { false }
        assertFalse(holds)
        assertEquals(1_000L, testScheduler.currentTime)
    }
}
