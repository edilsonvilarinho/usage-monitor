package com.usagemonitor

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexProfileRegistryTest {

    private val root: File = Files.createTempDirectory("codex-profiles").toFile()
    private val preferences: Preferences = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
    private val defaultHome = File(root, "default-home").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        runCatching { preferences.removeNode() }
        root.deleteRecursively()
    }

    private fun codexHome(name: String, withAuth: Boolean = true): File {
        val dir = File(root, name).apply { mkdirs() }
        if (withAuth) File(dir, "auth.json").writeText("{}")
        return dir
    }

    private fun registry() = CodexProfileRegistry(preferences, defaultHome = { defaultHome })

    @Test
    fun `adds a CODEX_HOME directory as an enabled extra account`() {
        val registry = registry()

        val record = registry.add(codexHome("work")).getOrThrow()

        assertTrue(record.id.startsWith("codex-"), "O id não pode colidir com perfil Anthropic: ${record.id}")
        assertEquals("work", record.label)
        assertEquals(listOf(record.ref), registry.enabledProfiles)
        assertEquals(listOf(record), registry().profiles.value, "Persistido e relido de outra instância")
    }

    @Test
    fun `refuses a directory without auth json and the default account`() {
        val registry = registry()

        assertTrue(registry.add(codexHome("empty", withAuth = false)).isFailure)
        File(defaultHome, "auth.json").writeText("{}")
        assertTrue(registry.add(defaultHome).isFailure)
        assertTrue(registry.profiles.value.isEmpty())
    }

    @Test
    fun `adding the same directory twice keeps one account`() {
        val registry = registry()
        val dir = codexHome("work")

        val first = registry.add(dir).getOrThrow()
        val second = registry.add(dir).getOrThrow()

        assertEquals(first, second)
        assertEquals(1, registry.profiles.value.size)
    }

    @Test
    fun `disable and remove update the enabled list`() {
        val registry = registry()
        val work = registry.add(codexHome("work")).getOrThrow()
        val personal = registry.add(codexHome("personal")).getOrThrow()

        registry.setEnabled(work.id, false)
        assertEquals(listOf(personal.ref), registry.enabledProfiles)

        registry.remove(personal.id)
        assertEquals(listOf(work.id), registry.profiles.value.map { it.id })
        assertTrue(registry.enabledProfiles.isEmpty())
    }
}
