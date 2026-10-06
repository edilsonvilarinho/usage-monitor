package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.DEFAULT_WEB_ACCESS_PORT
import com.usagemonitor.domain.entity.WebAccessSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

@Serializable
private data class WebAccessSettingsDto(
    val enabled: Boolean = false,
    val port: Int = DEFAULT_WEB_ACCESS_PORT,
    val token: String = ""
)

/**
 * `~/.usage-monitor/web-access.json` (#388). Arquivo e não `PreferencesSettings`
 * pelo mesmo motivo do `team.json`: o token é segredo, e as preferências vão em
 * claro para o registro do Windows.
 */
internal class LocalWebAccessSettingsDataSource(
    private val settingsFile: File = defaultSettingsFile()
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): WebAccessSettings {
        val dto = readSecretFile(settingsFile) { text -> json.decodeFromString(WebAccessSettingsDto.serializer(), text) }
            ?: WebAccessSettingsDto()
        return WebAccessSettings(enabled = dto.enabled, port = dto.port, token = dto.token)
    }

    fun save(settings: WebAccessSettings) {
        val dto = WebAccessSettingsDto(enabled = settings.enabled, port = settings.port, token = settings.token)
        writeSecretFile(settingsFile, json.encodeToString(WebAccessSettingsDto.serializer(), dto))
    }

    companion object {
        fun defaultSettingsFile(): File {
            val home = System.getProperty("user.home") ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(home, ".usage-monitor/web-access.json")
        }

        /** 32 bytes aleatórios em hex: 256 bits, o mesmo tamanho das chaves de time. */
        fun newToken(random: SecureRandom = SecureRandom()): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return bytes.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
