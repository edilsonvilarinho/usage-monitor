package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCodexAuthDataSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalCodexAuthDataSourceTest {

    @Test
    fun `loads session from deterministic temp files`() = runTest {
        val homeDir = Files.createTempDirectory("codex-auth-home")
        val codexDir = Files.createDirectories(homeDir.resolve(".codex"))
        codexDir.resolve("auth.json").writeText("""{"tokens":{"access_token":"abc123"}}""")
        codexDir.resolve("cap_sid").writeText("cap-cookie")

        val dataSource = LocalCodexAuthDataSource(
            homeDirProvider = { homeDir.toString() }
        )

        val session = dataSource.loadSession()

        assertEquals("abc123", session.accessToken)
        assertEquals("cap-cookie", session.capSid)
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
        codexDir.resolve("auth.json").writeText("""{"tokens":{"access_token":"abc123"}}""")

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
        codexDir.resolve("auth.json").writeText("""{"tokens":{"access_token":""}}""")
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
        codexDir.resolve("auth.json").writeText("""{"tokens":{"access_token":"abc123"}}""")
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
}
