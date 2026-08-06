package com.usagemonitor

import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnthropicProfileRegistryTest {
    private val tempDir = createTempDirectory("anthropic-profiles").toFile()
    private val preferences = Preferences.userRoot().node("com.usagemonitor.test/${UUID.randomUUID()}")

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
        runCatching { preferences.removeNode() }
    }

    @Test
    fun `discovers default environment and claude suffix directories without duplicating paths`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        val workDir = File(tempDir, ".claude-work").also { it.mkdirs() }
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "default@example.com", "account-a")
        writeProfileFiles(workDir, File(workDir, ".claude.json"), "work@example.com", "account-b")

        val registry = AnthropicProfileRegistry(
            preferences = preferences,
            defaultEnabled = true,
            homeDirProvider = { tempDir },
            environmentProvider = { name -> if (name == "CLAUDE_CONFIG_DIR") workDir.absolutePath else null }
        )

        val profiles = registry.profiles.value
        assertEquals(2, profiles.size)
        assertTrue(profiles.first { it.id == "default" }.enabled)
        val work = profiles.first { it.id != "default" }
        assertFalse(work.enabled)
        assertEquals(AnthropicProfileOrigin.ENVIRONMENT, work.origin)
        assertEquals("work@example.com", registry.inspect(work).accountContext?.email)
    }

    @Test
    fun `persists enable rename removal and explicit restore without deleting directory`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        val workDir = File(tempDir, ".claude-work").also { it.mkdirs() }
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "default@example.com", "account-a")
        writeProfileFiles(workDir, File(workDir, ".claude.json"), "work@example.com", "account-b")
        val registry = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })
        val work = registry.profiles.value.first { it.id != "default" }

        registry.setEnabled(work.id, true)
        registry.updateLabel(work.id, "Empresa")
        val updated = registry.profiles.value.first { it.id == work.id }
        assertTrue(updated.enabled)
        assertEquals("Empresa", updated.label)

        registry.removeFromMonitor(work.id)
        assertEquals(null, registry.profiles.value.firstOrNull { it.id == work.id })
        assertTrue(workDir.exists())

        registry.rescan(restoreRemoved = true)
        assertNotNull(registry.profiles.value.firstOrNull { it.id == work.id })
    }

    @Test
    fun `updateLabel persists an empty value instead of reverting to the previous label`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        val workDir = File(tempDir, ".claude-work").also { it.mkdirs() }
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "default@example.com", "account-a")
        writeProfileFiles(workDir, File(workDir, ".claude.json"), "work@example.com", "account-b")
        val registry = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })
        val work = registry.profiles.value.first { it.id != "default" }

        registry.updateLabel(work.id, "Empresa")
        registry.updateLabel(work.id, "")
        val updated = registry.profiles.value.first { it.id == work.id }
        assertEquals("", updated.label)
    }

    @Test
    fun `updateLabel does not reorder the published profile list`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        val aDir = File(tempDir, ".claude-alpha").also { it.mkdirs() }
        val bDir = File(tempDir, ".claude-beta").also { it.mkdirs() }
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "default@example.com", "account-a")
        writeProfileFiles(aDir, File(aDir, ".claude.json"), "alpha@example.com", "account-b")
        writeProfileFiles(bDir, File(bDir, ".claude.json"), "beta@example.com", "account-c")
        val registry = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })
        val alpha = registry.profiles.value.first { it.configDirectory == aDir.absolutePath }
        val beta = registry.profiles.value.first { it.configDirectory == bDir.absolutePath }
        val orderBefore = registry.profiles.value.map { it.id }

        // Rótulo escolhido de propósito para inverter a ordem alfabética (alpha < beta)
        // caso a lista fosse reordenada por label, como fazia o fluxo antigo via rescan()/publish().
        registry.updateLabel(beta.id, "Aaa - antes de tudo")

        assertEquals(orderBefore, registry.profiles.value.map { it.id })
        assertEquals("Aaa - antes de tudo", registry.profiles.value.first { it.id == beta.id }.label)
        assertEquals(alpha.label, registry.profiles.value.first { it.id == alpha.id }.label)
    }

    @Test
    fun `updateLabel autosaves to disk after flushing the pending write`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        val workDir = File(tempDir, ".claude-work").also { it.mkdirs() }
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "default@example.com", "account-a")
        writeProfileFiles(workDir, File(workDir, ".claude.json"), "work@example.com", "account-b")
        val registry = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })
        val work = registry.profiles.value.first { it.id != "default" }

        // updateLabel atualiza a memória na hora, mas grava em disco de forma
        // debounced (fora da UI thread) para não travar o cursor do TextField (#19).
        registry.updateLabel(work.id, "Novo apelido")
        registry.flushPendingLabelWrite()

        val reloaded = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })
        assertEquals("Novo apelido", reloaded.profiles.value.first { it.id == work.id }.label)
    }

    @Test
    fun `marks profile with missing identity as incomplete`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        File(defaultDir, ".credentials.json").writeText("{\"claudeAiOauth\":{}}")
        val registry = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })

        val inspection = registry.inspect(registry.profiles.value.single())

        assertEquals(AnthropicProfileInspectionStatus.INCOMPLETE, inspection.status)
        assertTrue(inspection.detail.orEmpty().contains(".claude.json"))
    }

    @Test
    fun `keeps only first enabled profile when two paths resolve to same account`() {
        val defaultDir = File(tempDir, ".claude").also { it.mkdirs() }
        val duplicateDir = File(tempDir, ".claude-copy").also { it.mkdirs() }
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "same@example.com", "same-account")
        writeProfileFiles(duplicateDir, File(duplicateDir, ".claude.json"), "same@example.com", "same-account")
        val registry = AnthropicProfileRegistry(preferences, true, { tempDir }, { null })
        val duplicate = registry.profiles.value.first { it.id != "default" }
        registry.setEnabled(duplicate.id, true)

        val resolution = resolveAnthropicProfiles(registry, registry.profiles.value)

        assertEquals(listOf("default"), resolution.enabledProfiles.map { it.id })
        assertEquals(setOf(duplicate.id), resolution.duplicateProfileIds)
    }

    private fun writeProfileFiles(
        configDirectory: File,
        identityFile: File,
        email: String,
        accountId: String
    ) {
        File(configDirectory, ".credentials.json").writeText(
            "{\"claudeAiOauth\":{\"accessToken\":\"test-token\"}}"
        )
        identityFile.writeText(
            """
            {
              "oauthAccount": {
                "accountUuid": "$accountId",
                "emailAddress": "$email",
                "organizationUuid": "org-$accountId",
                "organizationName": "Org $accountId"
              }
            }
            """.trimIndent()
        )
    }
}
