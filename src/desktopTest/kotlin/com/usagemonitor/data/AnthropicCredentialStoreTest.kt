package com.usagemonitor.data

import com.usagemonitor.AnthropicProfileLocation
import com.usagemonitor.data.datasource.FileCredentialStore
import com.usagemonitor.data.datasource.KEYCHAIN_CREDENTIALS_SERVICE
import com.usagemonitor.data.datasource.KeychainCredentialStore
import com.usagemonitor.data.datasource.ShellCommandResult
import com.usagemonitor.data.datasource.defaultCredentialStore
import com.usagemonitor.domain.entity.AnthropicProfileRef
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnthropicCredentialStoreTest {

    private val tempDir: File = createTempDirectory(prefix = "credential-store-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `file store reads null when credentials file is missing`() {
        val store = FileCredentialStore(File(tempDir, ".credentials.json"))

        assertNull(store.read())
    }

    @Test
    fun `file store writes and reads back the content`() {
        val credentialsFile = File(tempDir, "nested/.credentials.json")
        val store = FileCredentialStore(credentialsFile)

        store.write("""{"claudeAiOauth":{}}""")

        assertEquals("""{"claudeAiOauth":{}}""", store.read())
    }

    @Test
    fun `keychain store returns trimmed secret when security succeeds`() {
        val commands = mutableListOf<List<String>>()
        val store = KeychainCredentialStore(accountName = "someone") { command ->
            commands += command
            ShellCommandResult(exitCode = 0, output = "{\"claudeAiOauth\":{}}\n")
        }

        assertEquals("""{"claudeAiOauth":{}}""", store.read())
        assertEquals(
            listOf(
                "security",
                "find-generic-password",
                "-a",
                "someone",
                "-s",
                KEYCHAIN_CREDENTIALS_SERVICE,
                "-w"
            ),
            commands.single()
        )
    }

    @Test
    fun `keychain store returns null when entry is absent`() {
        val store = KeychainCredentialStore(accountName = "someone") {
            ShellCommandResult(exitCode = 44, output = "The specified item could not be found in the keychain.")
        }

        assertNull(store.read())
    }

    @Test
    fun `keychain store returns null when security prints nothing`() {
        val store = KeychainCredentialStore(accountName = "someone") {
            ShellCommandResult(exitCode = 0, output = "   \n")
        }

        assertNull(store.read())
    }

    @Test
    fun `keychain store updates the existing entry on write`() {
        val commands = mutableListOf<List<String>>()
        val store = KeychainCredentialStore(accountName = "someone") { command ->
            commands += command
            ShellCommandResult(exitCode = 0, output = "")
        }

        store.write("""{"claudeAiOauth":{"accessToken":"rotated"}}""")

        assertEquals(
            listOf(
                "security",
                "add-generic-password",
                "-U",
                "-a",
                "someone",
                "-s",
                KEYCHAIN_CREDENTIALS_SERVICE,
                "-w",
                """{"claudeAiOauth":{"accessToken":"rotated"}}"""
            ),
            commands.single()
        )
    }

    @Test
    fun `keychain store fails loudly when security write fails`() {
        val store = KeychainCredentialStore(accountName = "someone") {
            ShellCommandResult(exitCode = 1, output = "boom")
        }

        val error = assertFailsWith<IllegalStateException> { store.write("{}") }
        assertTrue(error.message.orEmpty().contains("Keychain"))
    }

    @Test
    fun `default store prefers the file whenever it exists`() {
        val credentialsFile = File(tempDir, ".credentials.json")
        credentialsFile.writeText("{}")

        val store = defaultCredentialStore(
            location = locationFor(AnthropicProfileRef.DEFAULT, credentialsFile),
            osName = "Mac OS X",
            userName = "someone"
        )

        assertTrue(store is FileCredentialStore)
    }

    @Test
    fun `default store falls back to keychain on macOS for the default profile`() {
        val store = defaultCredentialStore(
            location = locationFor(AnthropicProfileRef.DEFAULT, File(tempDir, ".credentials.json")),
            osName = "Mac OS X",
            userName = "someone"
        )

        assertTrue(store is KeychainCredentialStore)
    }

    @Test
    fun `default store keeps the file on macOS for extra profiles`() {
        val profile = AnthropicProfileRef(id = "work", label = "Work")

        val store = defaultCredentialStore(
            location = locationFor(profile, File(tempDir, ".credentials.json")),
            osName = "Mac OS X",
            userName = "someone"
        )

        assertTrue(store is FileCredentialStore)
    }

    @Test
    fun `default store keeps the file outside macOS`() {
        val store = defaultCredentialStore(
            location = locationFor(AnthropicProfileRef.DEFAULT, File(tempDir, ".credentials.json")),
            osName = "Windows 11",
            userName = "someone"
        )

        assertTrue(store is FileCredentialStore)
    }

    private fun locationFor(profile: AnthropicProfileRef, credentialsFile: File): AnthropicProfileLocation {
        return AnthropicProfileLocation(
            profile = profile,
            configDirectory = tempDir,
            credentialsFile = credentialsFile,
            identityFile = File(tempDir, ".claude.json")
        )
    }
}
