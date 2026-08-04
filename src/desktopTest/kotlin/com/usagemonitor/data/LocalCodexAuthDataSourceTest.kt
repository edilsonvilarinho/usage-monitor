package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCodexAuthDataSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalCodexAuthDataSourceTest {

    @Test
    fun `loads session from deterministic temp files`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        writeAuth(codexDir, accessToken = "abc123")
        codexDir.resolve("cap_sid").writeText("cap-cookie")

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        val session = dataSource.loadSession()

        assertEquals("abc123", session.accessToken)
        assertEquals("cap-cookie", session.capSid)
        assertEquals("user-1", session.accountContext.key.providerAccountId)
        assertEquals("workspace-1", session.accountContext.key.workspaceId)
        assertEquals("first@example.com", session.accountContext.email)
    }

    @Test
    fun `fails when auth file is missing`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        Files.createDirectories(homeDir.resolve(".codex"))
        homeDir.resolve(".codex/cap_sid").writeText("cap-cookie")

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.loadSession()
        }

        assertEquals(true, error.message?.contains("Sessão do Codex não encontrada"))
    }

    @Test
    fun `fails when cap sid file is missing`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        writeAuth(codexDir)

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.loadSession()
        }

        assertEquals(true, error.message?.contains("Cookie cap_sid do Codex não encontrado"))
    }

    @Test
    fun `fails when access token is blank`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        writeAuth(codexDir, accessToken = "")
        codexDir.resolve("cap_sid").writeText("cap-cookie")

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.loadSession()
        }

        assertEquals("Sessão do Codex inválida: access_token ausente.", error.message)
    }

    @Test
    fun `fails when cap sid is blank`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        writeAuth(codexDir)
        codexDir.resolve("cap_sid").writeText("   ")

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.loadSession()
        }

        assertEquals("Sessão do Codex inválida: cap_sid vazio.", error.message)
    }

    @Test
    fun `fails when auth json is malformed`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        codexDir.resolve("auth.json").writeText("""{"tokens":""")
        codexDir.resolve("cap_sid").writeText("cap-cookie")

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        assertFailsWith<Throwable> {
            dataSource.loadSession()
        }
    }

    @Test
    fun `detects account switch without recreating datasource`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        writeAuth(codexDir, accessToken = "token-a", accountId = "workspace-a", userId = "user-a", email = "a@example.com")
        codexDir.resolve("cap_sid").writeText("cap-a")
        val dataSource = LocalCodexAuthDataSource(homeDirProvider = { homeDir.toString() })

        val firstSession = dataSource.loadSession()
        writeAuth(codexDir, accessToken = "token-b", accountId = "workspace-b", userId = "user-b", email = "b@example.com")
        codexDir.resolve("cap_sid").writeText("cap-b")
        val secondSession = dataSource.loadSession()

        assertEquals(false, dataSource.isSessionCurrent(firstSession))
        assertEquals(true, dataSource.isSessionCurrent(secondSession))
        assertEquals("b@example.com", secondSession.accountContext.email)
    }

    private fun writeAuth(
        codexDir: java.nio.file.Path,
        accessToken: String = "abc123",
        accountId: String = "workspace-1",
        userId: String = "user-1",
        email: String = "first@example.com"
    ) {
        val payload = """{"sub":"$userId","email":"$email"}"""
        val encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.encodeToByteArray())
        val idToken = "header.$encodedPayload.signature"
        codexDir.resolve("auth.json").writeText(
            """{"tokens":{"id_token":"$idToken","access_token":"$accessToken","account_id":"$accountId"}}"""
        )
    }
}
