package com.usagemonitor.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppDataRootTest {

    private val defaultRoot = File("home-de-teste", ".usage-monitor")

    @Test
    fun `without configuration every file lives under the default root`() {
        val root = AppDataRoot { defaultRoot }

        assertEquals(File(defaultRoot, "api-keys.json"), root.file("api-keys.json"))
        assertTrue(root.isDefault)
    }

    @Test
    fun `a configured root moves data files but not installation files`() {
        val custom = File("outra-raiz")
        val root = AppDataRoot { defaultRoot }

        root.configure(custom)

        assertEquals(File(custom, "diagnostics/app.log"), root.file("diagnostics/app.log"))
        assertEquals(File(defaultRoot, "update-receipt.properties"), root.installationFile("update-receipt.properties"))
        assertFalse(root.isDefault)
    }

    @Test
    fun `configuring after the first read fails instead of splitting the files`() {
        val root = AppDataRoot { defaultRoot }
        root.file("app.lock")

        assertFailsWith<IllegalStateException> { root.configure(File("tarde-demais")) }
        assertEquals(File(defaultRoot, "app.lock"), root.file("app.lock"))
    }

    @Test
    fun `asking whether the root is the default also counts as a read`() {
        val root = AppDataRoot { defaultRoot }
        root.isDefault

        assertFailsWith<IllegalStateException> { root.configure(File("tarde-demais")) }
    }

    @Test
    fun `the process root of the test suite is the default one`() {
        // Ninguém configura a raiz global nos testes: um teste que o fizesse
        // mudaria os caminhos de todos os outros na mesma JVM.
        assertTrue(AppDataDirectory.isDefault)
        assertEquals(defaultAppDataRoot(), AppDataDirectory.root)
    }
}
