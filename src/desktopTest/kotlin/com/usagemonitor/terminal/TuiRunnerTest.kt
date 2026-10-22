package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.parseCommandLine
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TuiData
import com.usagemonitor.presentation.terminal.TuiKey
import com.usagemonitor.presentation.terminal.TuiLabels
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiSize
import com.usagemonitor.presentation.terminal.TuiState
import com.usagemonitor.presentation.terminal.TuiTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Um terminal de mentira: as teclas de uma fila, cada `null` é um prazo que venceu. */
internal class FakeTuiIo(keys: List<TuiKey?>, var size: TuiSize = TuiSize(96, 24), private val onTimeout: () -> Unit = {}) : TuiIo {
    private val pending = ArrayDeque(keys)
    val frames = mutableListOf<List<String>>()
    var resized = false

    override fun size(): TuiSize = size

    override fun readKey(timeoutMillis: Long): TuiKey? {
        if (pending.isEmpty()) {
            return TuiKey.Text('q')
        }
        val key = pending.removeFirst()
        if (key == null) {
            onTimeout()
        }
        return key
    }

    override fun consumeResize(): Boolean = resized.also { resized = false }

    override fun draw(lines: List<String>) {
        frames += lines
    }
}

/** Relógio que só anda quando o teste manda. */
internal class SteppingClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class TuiRunnerTest {

    private val start = Instant.parse("2026-10-21T17:23:00Z")
    private val profile = TerminalProfile(TerminalPalette.NONE, unicode = true)
    private val labels = TuiLabels(AppLanguage.PT)

    private fun data(clock: Clock) = TuiData(now = clock.now(), collector = CollectorKind.SERVICE, lastCollectedAt = start)

    @Test
    fun `tui is a terminal command without options`() {
        assertEquals(CommandLineRequest.Terminal(TuiCommand), parseCommandLine(arrayOf("tui")))
        assertTrue(parseCommandLine(arrayOf("tui", "--json")) is CommandLineRequest.Invalid)
    }

    @Test
    fun `desenha uma vez ao abrir, outra a cada tecla, e sai com q`() {
        val clock = SteppingClock(start)
        val io = FakeTuiIo(listOf(TuiKey.Text('?'), TuiKey.Escape, TuiKey.Text('3'), TuiKey.Text('q')))
        val states = mutableListOf<TuiState>()
        val code = TuiRunner(io, { state -> states += state; data(clock) }, labels = labels, profile = profile, clock = clock).run()
        assertEquals(TuiRunner.EXIT_OK, code)
        assertEquals(4, io.frames.size)
        assertTrue(io.frames[1].any { line -> "Ajuda" in line })
        assertTrue(io.frames[2].none { line -> "Ajuda" in line })
        assertTrue(io.frames[3][1].startsWith("│ 1 Visão geral   2 Sessões  [3 Histórico]  5 Configurações "), io.frames[3][1])
        // Abrir e trocar para a aba 3: cada aba lê o que é dela.
        assertEquals(listOf(TuiTab.OVERVIEW, TuiTab.HISTORY), states.map { state -> state.tab })
        assertTrue(io.frames.all { frame -> frame.size == 24 })
    }

    @Test
    fun `rele ao trocar de aba, quando o prazo vence e com r`() {
        val clock = SteppingClock(start)
        val io = FakeTuiIo(listOf(TuiKey.Text('2'), null, TuiKey.Text('r'))) { clock.now += 16.seconds }
        val loaded = mutableListOf<TuiTab>()
        TuiRunner(io, { state -> loaded += state.tab; data(clock) }, labels = labels, profile = profile, clock = clock).run()
        assertEquals(listOf(TuiTab.OVERVIEW, TuiTab.SESSIONS, TuiTab.SESSIONS, TuiTab.SESSIONS), loaded)
    }

    @Test
    fun `redesenha no novo tamanho sem reler`() {
        val clock = SteppingClock(start)
        lateinit var io: FakeTuiIo
        io = FakeTuiIo(listOf(null)) { io.size = TuiSize(70, 20); io.resized = true }
        var loads = 0
        TuiRunner(io, { _ -> loads += 1; data(clock) }, labels = labels, profile = profile, clock = clock).run()
        assertEquals(1, loads)
        assertEquals(20, io.frames.last().size)
        assertEquals(70, io.frames.last().first().length)
    }

    @Test
    fun `e na aba 2 exporta e o aviso fica ate a proxima tecla`() {
        val clock = SteppingClock(start)
        val io = FakeTuiIo(listOf(TuiKey.Text('2'), TuiKey.Text('e'), TuiKey.Down))
        var exports = 0
        TuiRunner(io, { _ -> data(clock) }, { _ -> exports += 1; TuiNotice("CSV salvo", ok = true) }, labels, profile, clock).run()
        assertEquals(1, exports)
        assertTrue(io.frames[2].any { line -> "CSV salvo" in line })
        assertTrue(io.frames[3].none { line -> "CSV salvo" in line })
    }
}
