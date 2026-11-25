package com.usagemonitor

import com.usagemonitor.presentation.ui.ModalCloak
import com.usagemonitor.presentation.ui.PrewarmOutcome
import com.usagemonitor.presentation.ui.WindowCloak
import com.usagemonitor.presentation.ui.awaitUntil
import com.usagemonitor.presentation.ui.modalPrewarmOutcomeBreadcrumb
import com.usagemonitor.presentation.ui.prewarmCloaked
import com.usagemonitor.presentation.ui.showUncloaked
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

        val outcome = prewarmCloaked(
            cloak = cloak,
            show = { fake.events += "show" },
            awaitFrames = { fake.events += "frames" },
            hide = { fake.events += "hide" },
            awaitHidden = { fake.events += "hidden"; true }
        )

        assertEquals(PrewarmOutcome.DONE, outcome)
        assertEquals(listOf("set(true)", "show", "frames", "hide", "hidden", "set(false)"), fake.events)
        assertFalse(cloak.active)
    }

    /** Janela que o sistema não consegue esconder não pode aparecer no clique do usuário. */
    @Test
    fun `a refused cloak returns REFUSED and never shows the window`() = runTest {
        val fake = FakeCloak(false)
        var shown = false

        val result = prewarmCloaked(
            cloak = ModalCloak(fake.native),
            show = { shown = true },
            awaitFrames = {},
            hide = {},
            awaitHidden = { true }
        )

        assertEquals(PrewarmOutcome.REFUSED, result)
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
                awaitHidden = { fake.events += "hidden"; true }
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
                awaitHidden = { fake.events += "hidden"; true }
            )
        }

        assertEquals(listOf("set(true)", "show", "hide", "hidden", "set(false)"), fake.events)
    }

    /** Janela ainda visível depois de esconder: o cloak não é liberado aqui, fica para a abertura. */
    @Test
    fun `a window still visible after hiding keeps its cloak for the open`() = runTest {
        val fake = FakeCloak()
        val cloak = ModalCloak(fake.native)

        val outcome = prewarmCloaked(
            cloak = cloak,
            show = { fake.events += "show" },
            awaitFrames = {},
            hide = { fake.events += "hide" },
            awaitHidden = { fake.events += "hidden"; false }
        )

        assertEquals(PrewarmOutcome.STILL_VISIBLE, outcome)
        assertEquals(listOf("set(true)", "show", "hide", "hidden"), fake.events)
        assertTrue(cloak.active)
    }

    /** Liberação recusada depois de a janela sumir: o resultado diz que o cloak segue ativo. */
    @Test
    fun `a refused release after the window is hidden reports STILL_CLOAKED`() = runTest {
        val fake = FakeCloak(true, false)
        val cloak = ModalCloak(fake.native)

        val outcome = prewarmCloaked(
            cloak = cloak,
            show = { fake.events += "show" },
            awaitFrames = {},
            hide = { fake.events += "hide" },
            awaitHidden = { fake.events += "hidden"; true }
        )

        assertEquals(PrewarmOutcome.STILL_CLOAKED, outcome)
        assertEquals(listOf("set(true)", "show", "hide", "hidden", "set(false)"), fake.events)
        assertTrue(cloak.active)
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

    /** Libera o cloak antes de mostrar: a janela nunca aparece coberta. */
    @Test
    fun `showUncloaked releases the cloak before it shows the window`() = runTest {
        val fake = FakeCloak()
        val cloak = ModalCloak(fake.native)
        assertTrue(cloak.cloak())

        val released = showUncloaked(cloak) { fake.events += "show" }

        assertTrue(released)
        assertEquals(listOf("set(true)", "set(false)", "show"), fake.events)
        assertFalse(cloak.active)
    }

    /** Uma liberação recusada é tentada de novo antes de mostrar. */
    @Test
    fun `showUncloaked retries a refused release and still releases before showing`() = runTest {
        val fake = FakeCloak(true, false, true)
        val cloak = ModalCloak(fake.native)
        assertTrue(cloak.cloak())

        val released = showUncloaked(cloak) { fake.events += "show" }

        assertTrue(released)
        assertEquals(listOf("set(true)", "set(false)", "set(false)", "show"), fake.events)
    }

    /** Três liberações recusadas: mostra mesmo assim, devolve false e o cloak segue ativo. */
    @Test
    fun `showUncloaked shows the window after three refused releases and reports false`() = runTest {
        val fake = FakeCloak(true, false, false, false)
        val cloak = ModalCloak(fake.native)
        assertTrue(cloak.cloak())

        val released = showUncloaked(cloak) { fake.events += "show" }

        assertFalse(released)
        assertEquals(listOf("set(true)", "set(false)", "set(false)", "set(false)", "show"), fake.events)
        assertTrue(cloak.active)
    }

    /** Sem cloak (fora do Windows) só mostra: não há nativo a chamar. */
    @Test
    fun `showUncloaked without a cloak only shows and reports true`() = runTest {
        val events = mutableListOf<String>()

        val released = showUncloaked(null) { events += "show" }

        assertTrue(released)
        assertEquals(listOf("show"), events)
    }
}

/** Trilha do pré-aquecimento: a linha do sucesso não muda; cada desfecho incompleto nomeia o motivo. */
class ModalPrewarmBreadcrumbTest {

    @Test
    fun `a finished prewarm keeps the usual prewarmed line`() {
        assertEquals(
            "janela Configurações pré-aquecida em 310 ms",
            modalPrewarmOutcomeBreadcrumb("Configurações", PrewarmOutcome.DONE, elapsedMillis = 310)
        )
    }

    @Test
    fun `a refused cloak names the window and the reason`() {
        assertEquals(
            "janela Configurações não concluiu o pré-aquecimento: o DWM recusou o cloak antes de mostrar",
            modalPrewarmOutcomeBreadcrumb("Configurações", PrewarmOutcome.REFUSED, elapsedMillis = 0)
        )
    }

    @Test
    fun `a window still visible names the reason`() {
        assertEquals(
            "janela Configurações não concluiu o pré-aquecimento: a janela ainda estava visível depois de esconder",
            modalPrewarmOutcomeBreadcrumb("Configurações", PrewarmOutcome.STILL_VISIBLE, elapsedMillis = 0)
        )
    }

    @Test
    fun `a refused release names the reason`() {
        assertEquals(
            "janela Configurações não concluiu o pré-aquecimento: o DWM recusou a liberação do cloak",
            modalPrewarmOutcomeBreadcrumb("Configurações", PrewarmOutcome.STILL_CLOAKED, elapsedMillis = 0)
        )
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
