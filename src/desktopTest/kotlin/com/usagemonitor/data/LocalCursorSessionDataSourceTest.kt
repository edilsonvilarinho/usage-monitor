package com.usagemonitor.data

import com.usagemonitor.data.datasource.CursorSessionCredentials
import com.usagemonitor.data.datasource.LocalCursorSessionDataSource
import com.usagemonitor.domain.repository.CursorUsageException
import com.usagemonitor.domain.repository.CursorUsageFailureKind
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class LocalCursorSessionDataSourceTest {

    @Test
    fun `reads editor credentials from sqlite without changing the database`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val databaseFile = tempDirectory.resolve("state.vscdb")
            val accessToken = "synthetic.jwt.token"
            seedDatabase(databaseFile, "account-123", accessToken)
            val beforeRead = Files.readAllBytes(databaseFile)

            val credentials = LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()

            assertEquals(CursorSessionCredentials("account-123", accessToken), credentials)
            assertContentEquals(beforeRead, Files.readAllBytes(databaseFile))
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `falls back to the access token subject if stored account id is missing`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val databaseFile = tempDirectory.resolve("state.vscdb")
            val payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"sub":"google-oauth2|synthetic-user"}""".toByteArray())
            val accessToken = "header.$payload.signature"
            seedDatabase(databaseFile, accountId = null, accessToken = accessToken)

            val credentials = LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()

            assertEquals("google-oauth2|synthetic-user", credentials.accountId)
            assertEquals(accessToken, credentials.accessToken)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    /** Cursor 3.27.12+: token opaco (sem pontos) e identificador só em `cachedScopedProfile`. */
    @Test
    fun `opaque access token reads the account id from the cached scoped profile`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val databaseFile = tempDirectory.resolve("state.vscdb")
            seedDatabase(
                databaseFile,
                accountId = null,
                accessToken = "curs_opaque_synthetic_token",
                cachedScopedProfile = """{"authId":"auth0|opaque-user","email":"synthetic@example.com"}"""
            )

            val credentials = LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()

            assertEquals("auth0|opaque-user", credentials.accountId)
            assertEquals("curs_opaque_synthetic_token", credentials.accessToken)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the JWT subject wins over the cached scoped profile`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val databaseFile = tempDirectory.resolve("state.vscdb")
            val payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"sub":"google-oauth2|jwt-user"}""".toByteArray())
            seedDatabase(
                databaseFile,
                accountId = null,
                accessToken = "header.$payload.signature",
                cachedScopedProfile = """{"authId":"auth0|cached-user"}"""
            )

            val credentials = LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()

            assertEquals("google-oauth2|jwt-user", credentials.accountId)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the stored membership id wins over the cached scoped profile`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val databaseFile = tempDirectory.resolve("state.vscdb")
            seedDatabase(
                databaseFile,
                accountId = "stripe-account",
                accessToken = "curs_opaque_synthetic_token",
                cachedScopedProfile = """{"authId":"auth0|cached-user"}"""
            )

            val credentials = LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()

            assertEquals("stripe-account", credentials.accountId)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    /** Perfil em cache sem identificador utilizável não autentica: segue para `SIGNED_OUT`. */
    @Test
    fun `cached scoped profile without a usable authId is signed out`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val profiles = listOf(
                "{not json",
                """{"email":"synthetic@example.com"}""",
                """{"authId":"   "}""",
                """{"authId":""}"""
            )
            profiles.forEachIndexed { index, profile ->
                val databaseFile = tempDirectory.resolve("profile-$index.vscdb")
                seedDatabase(
                    databaseFile,
                    accountId = null,
                    accessToken = "curs_opaque_synthetic_token",
                    cachedScopedProfile = profile
                )

                val failure = assertFailsWith<CursorUsageException> {
                    LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()
                }

                assertEquals(CursorUsageFailureKind.SIGNED_OUT, failure.kind, "perfil: $profile")
            }
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    /** Duas respostas diferentes para o usuário: instalar o Cursor ou entrar nele. */
    @Test
    fun `missing database is not installed and incomplete credentials are signed out`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val missing = assertFailsWith<CursorUsageException> {
                LocalCursorSessionDataSource(tempDirectory.resolve("missing.db").toFile()).readCredentials()
            }
            assertEquals(CursorUsageFailureKind.NOT_INSTALLED, missing.kind)

            val incompleteFile = tempDirectory.resolve("incomplete.db")
            seedDatabase(incompleteFile, accountId = null, accessToken = null)
            val incomplete = assertFailsWith<CursorUsageException> {
                LocalCursorSessionDataSource(incompleteFile.toFile()).readCredentials()
            }
            assertEquals(CursorUsageFailureKind.SIGNED_OUT, incomplete.kind)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    /**
     * Banco em WAL depois que o editor fechou: sem o `-shm`, a abertura read-only
     * pode falhar, e o fallback `immutable=1` lê o arquivo principal.
     */
    @Test
    fun `reads a WAL database whose shared-memory file is gone`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val databaseFile = tempDirectory.resolve("state.vscdb")
            seedDatabase(databaseFile, "account-wal", "wal.jwt.token", journalMode = "WAL")
            Files.deleteIfExists(tempDirectory.resolve("state.vscdb-shm"))
            Files.deleteIfExists(tempDirectory.resolve("state.vscdb-wal"))

            val credentials = LocalCursorSessionDataSource(databaseFile.toFile()).readCredentials()

            assertEquals("account-wal", credentials.accountId)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a file that is not the editor database fails without a fabricated session`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val notSqlite = tempDirectory.resolve("state.vscdb").also { Files.writeString(it, "not a database") }
            assertFails { LocalCursorSessionDataSource(notSqlite.toFile()).readCredentials() }

            val withoutTable = tempDirectory.resolve("other.vscdb")
            DriverManager.getConnection("jdbc:sqlite:${withoutTable.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { it.execute("CREATE TABLE Other (id INTEGER)") }
            }
            assertFails { LocalCursorSessionDataSource(withoutTable.toFile()).readCredentials() }
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    private fun seedDatabase(
        file: java.nio.file.Path,
        accountId: String?,
        accessToken: String?,
        journalMode: String? = null,
        cachedScopedProfile: String? = null
    ) {
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                if (journalMode != null) statement.execute("PRAGMA journal_mode=$journalMode")
                statement.execute("CREATE TABLE ItemTable (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            }
            connection.prepareStatement("INSERT INTO ItemTable(key, value) VALUES (?, ?)").use { statement ->
                if (accountId != null) {
                    statement.setString(1, "cursorAuth/stripeMembershipAuthId")
                    statement.setString(2, accountId)
                    statement.executeUpdate()
                }
                if (accessToken != null) {
                    statement.setString(1, "cursorAuth/accessToken")
                    statement.setString(2, accessToken)
                    statement.executeUpdate()
                }
                if (cachedScopedProfile != null) {
                    statement.setString(1, "cursorAuth/cachedScopedProfile")
                    statement.setString(2, cachedScopedProfile)
                    statement.executeUpdate()
                }
            }
        }
    }
}
