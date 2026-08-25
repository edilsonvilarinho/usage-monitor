package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusRequestChannelTest {

    @Test
    fun `a request is consumed once and only once`() {
        withTempFile { file ->
            val living = FocusRequestChannel(file)
            val secondInstance = FocusRequestChannel(file)

            assertTrue(secondInstance.request(nowMillis = 1_000L))

            assertTrue(living.consume())
            assertFalse(living.consume())
        }
    }

    @Test
    fun `no request file means nothing to consume`() {
        withTempFile { file ->
            assertFalse(FocusRequestChannel(file).consume())
        }
    }

    // Pedido deixado no disco por uma sessao anterior nao e pedido: atende-lo
    // faria a janela saltar para a frente sozinha logo no arranque.
    @Test
    fun `a request left over from a previous session is not consumed`() {
        withTempFile { file ->
            FocusRequestChannel(file).request(nowMillis = 1_000L)

            val freshlyStarted = FocusRequestChannel(file)

            assertFalse(freshlyStarted.consume())
        }
    }

    @Test
    fun `each new request is consumed again`() {
        withTempFile { file ->
            val living = FocusRequestChannel(file)
            val secondInstance = FocusRequestChannel(file)

            secondInstance.request(nowMillis = 1_000L)
            assertTrue(living.consume())

            secondInstance.request(nowMillis = 2_000L)
            assertTrue(living.consume())
            assertFalse(living.consume())
        }
    }

    @Test
    fun `a corrupt request file is not a request`() {
        withTempFile { file ->
            val living = FocusRequestChannel(file)
            file.parentFile?.mkdirs()
            file.writeText("nao e um carimbo")

            assertFalse(living.consume())
        }
    }

    private fun withTempFile(block: (File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("focus-request-test").toFile()
        try {
            block(File(tempDir, "focus.request"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
