package com.usagemonitor.data

import com.usagemonitor.data.datasource.CursorSessionCredentials
import com.usagemonitor.data.datasource.LocalCursorSessionDataSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

            assertEquals("google-oauth2|synthetic-user", credentials?.accountId)
            assertEquals(accessToken, credentials?.accessToken)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing database or incomplete credentials means unavailable`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            assertNull(LocalCursorSessionDataSource(tempDirectory.resolve("missing.db").toFile()).readCredentials())
            val incompleteFile = tempDirectory.resolve("incomplete.db")
            seedDatabase(incompleteFile, accountId = null, accessToken = null)
            assertNull(LocalCursorSessionDataSource(incompleteFile.toFile()).readCredentials())
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    private fun seedDatabase(file: java.nio.file.Path, accountId: String?, accessToken: String?) {
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
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
            }
        }
    }
}
