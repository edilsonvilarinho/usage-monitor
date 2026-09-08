package com.usagemonitor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class CodexCliHomeProviderTest {
    @Test
    fun `uses CODEX_HOME when configured`() {
        assertEquals(
            File("D:/isolated-codex").absoluteFile,
            CodexCliHomeProvider.resolve(
                environment = mapOf("CODEX_HOME" to "D:/isolated-codex"),
                userHome = "C:/Users/test"
            ).absoluteFile
        )
    }

    @Test
    fun `falls back to user home when CODEX_HOME is absent or blank`() {
        assertEquals(
            File("C:/Users/test/.codex").absoluteFile,
            CodexCliHomeProvider.resolve(emptyMap(), "C:/Users/test").absoluteFile
        )
        assertEquals(
            File("C:/Users/test/.codex").absoluteFile,
            CodexCliHomeProvider.resolve(mapOf("CODEX_HOME" to "  "), "C:/Users/test").absoluteFile
        )
    }
}
