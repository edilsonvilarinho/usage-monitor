package com.usagemonitor.data.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.DriverManager
import java.util.Base64

/** Lê os dois valores de autenticação do editor em conexão SQLite read-only. */
class LocalCursorSessionDataSource(
    private val databaseFile: File = defaultDatabaseFile()
) : CursorSessionDataSource {

    override suspend fun readCredentials(): CursorSessionCredentials? = withContext(Dispatchers.IO) {
        if (!databaseFile.isFile) return@withContext null

        val sqliteConfig = SQLiteConfig().apply { setReadOnly(true) }
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}", sqliteConfig.toProperties()).use { connection ->
            connection.prepareStatement(SELECT_ITEM_VALUE).use { statement ->
                statement.setString(1, ACCESS_TOKEN_KEY)
                val accessToken = statement.executeQuery().use { result ->
                    if (result.next()) result.getString("value") else null
                }?.takeIf(String::isNotBlank) ?: return@withContext null

                statement.setString(1, ACCOUNT_ID_KEY)
                val storedAccountId = statement.executeQuery().use { result ->
                    if (result.next()) result.getString("value") else null
                }?.takeIf(String::isNotBlank)

                val accountId = storedAccountId ?: subjectFromJwt(accessToken) ?: return@withContext null
                if (hasUnsafeCookieCharacter(accountId) || hasUnsafeCookieCharacter(accessToken)) {
                    return@withContext null
                }

                CursorSessionCredentials(accountId = accountId, accessToken = accessToken)
            }
        }
    }

    private fun subjectFromJwt(token: String): String? {
        val payloadPart = token.split('.').getOrNull(1) ?: return null
        return runCatching {
            val payload = Base64.getUrlDecoder().decode(payloadPart)
            val claim = Json.parseToJsonElement(payload.decodeToString()).jsonObject["sub"]
                ?.jsonPrimitive?.content
            claim?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun hasUnsafeCookieCharacter(value: String): Boolean =
        value.any { character -> character.isWhitespace() || character == ';' || character == '\r' || character == '\n' }

    private companion object {
        const val ACCESS_TOKEN_KEY = "cursorAuth/accessToken"
        const val ACCOUNT_ID_KEY = "cursorAuth/stripeMembershipAuthId"
        const val SELECT_ITEM_VALUE = "SELECT value FROM ItemTable WHERE key = ?"

        fun defaultDatabaseFile(): File {
            val homeDirectory = System.getProperty("user.home")
                ?: throw IllegalStateException("Cursor local session is unavailable")
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA")
                        ?: File(homeDirectory, "AppData/Roaming").absolutePath
                    File(appData, "Cursor/User/globalStorage/state.vscdb")
                }
                osName.contains("mac") || osName.contains("darwin") ->
                    File(homeDirectory, "Library/Application Support/Cursor/User/globalStorage/state.vscdb")
                else -> File(homeDirectory, ".config/Cursor/User/globalStorage/state.vscdb")
            }
        }
    }
}
