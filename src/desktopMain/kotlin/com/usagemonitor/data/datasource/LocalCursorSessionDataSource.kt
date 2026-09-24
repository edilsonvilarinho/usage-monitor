package com.usagemonitor.data.datasource

import com.usagemonitor.domain.repository.CursorUsageException
import com.usagemonitor.domain.repository.CursorUsageFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteOpenMode
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Base64

/**
 * Lê os dois valores de autenticação do editor em conexão SQLite read-only.
 *
 * **Duas tentativas de abertura.** A primeira é read-only com `busy_timeout`: o
 * editor aberto escreve no mesmo banco, e sem espera a leitura falharia a cada
 * checkpoint. Se ela falhar — um banco em WAL cujo `-shm` sumiu depois que o editor
 * fechou pode não abrir em read-only —, a segunda abre com `immutable=1`, que lê o
 * arquivo principal sem tocar em WAL nem em lock. É o mesmo recurso do leitor do
 * Codenotch (`cursor.rs`).
 */
class LocalCursorSessionDataSource(
    private val databaseFile: File = defaultDatabaseFile()
) : CursorSessionDataSource {

    override suspend fun readCredentials(): CursorSessionCredentials = withContext(Dispatchers.IO) {
        if (!databaseFile.isFile) throw CursorUsageException(CursorUsageFailureKind.NOT_INSTALLED)

        val values = try {
            openReadOnly().use(::readAuthValues)
        } catch (_: SQLException) {
            openImmutable().use(::readAuthValues)
        }

        val accessToken = values.accessToken?.takeIf(String::isNotBlank)
            ?: throw CursorUsageException(CursorUsageFailureKind.SIGNED_OUT)
        val accountId = values.accountId?.takeIf(String::isNotBlank) ?: subjectFromJwt(accessToken)
            ?: throw CursorUsageException(CursorUsageFailureKind.SIGNED_OUT)
        if (hasUnsafeCookieCharacter(accountId) || hasUnsafeCookieCharacter(accessToken)) {
            throw CursorUsageException(CursorUsageFailureKind.SIGNED_OUT)
        }

        CursorSessionCredentials(accountId = accountId, accessToken = accessToken)
    }

    private class AuthValues(val accessToken: String?, val accountId: String?)

    private fun readAuthValues(connection: Connection): AuthValues =
        connection.prepareStatement(SELECT_ITEM_VALUE).use { statement ->
            fun valueOf(key: String): String? {
                statement.setString(1, key)
                return statement.executeQuery().use { result ->
                    if (result.next()) result.getString("value") else null
                }
            }
            AuthValues(accessToken = valueOf(ACCESS_TOKEN_KEY), accountId = valueOf(ACCOUNT_ID_KEY))
        }

    private fun openReadOnly(): Connection {
        val config = SQLiteConfig()
        config.setReadOnly(true)
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS)
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}", config.toProperties())
    }

    private fun openImmutable(): Connection {
        val config = SQLiteConfig()
        config.setReadOnly(true)
        config.setOpenMode(SQLiteOpenMode.OPEN_URI)
        // `toURI()` devolve `file:/C:/...`; o SQLite quer a forma com três barras.
        val path = databaseFile.absoluteFile.toURI().rawPath.let { raw -> if (raw.startsWith("/")) raw else "/$raw" }
        return DriverManager.getConnection("jdbc:sqlite:file://$path?immutable=1", config.toProperties())
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
        const val BUSY_TIMEOUT_MILLIS = 2_000

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
