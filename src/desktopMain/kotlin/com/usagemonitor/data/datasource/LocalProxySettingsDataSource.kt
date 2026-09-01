package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.ProxySettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
private data class ProxySettingsDto(
    val useEnvironmentProxy: Boolean = true,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
)

/**
 * Persiste a configuração de proxy HTTP corporativo em
 * `~/.usage-monitor/proxy.json` (issue #174).
 *
 * **Por que arquivo e não `PreferencesSettings`:** usuário/senha do proxy são
 * segredo, e as preferências do app vão em claro para o registro do Windows
 * (`HKCU`) — mesmo argumento já documentado em [LocalTeamSettingsDataSource].
 * Um arquivo só cobre toda a configuração (inclusive a senha), em vez de
 * dividir entre `PreferencesSettings` e um arquivo — não há necessidade de
 * dois donos para uma configuração pequena e coesa.
 */
internal class LocalProxySettingsDataSource(
    private val settingsFile: File = defaultSettingsFile()
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Arquivo corrompido devolve o default em vez de derrubar o boot: perder a
     * configuração de proxy é ruim, mas não abrir o app é pior.
     */
    fun load(): ProxySettings = readDto().toDomain()

    fun save(settings: ProxySettings) {
        writeDto(
            ProxySettingsDto(
                useEnvironmentProxy = settings.useEnvironmentProxy,
                host = settings.host.trim(),
                port = settings.port,
                username = settings.username.trim(),
                password = settings.password
            )
        )
    }

    private fun readDto(): ProxySettingsDto {
        if (!settingsFile.isFile) {
            return ProxySettingsDto()
        }
        return runCatching {
            json.decodeFromString(ProxySettingsDto.serializer(), settingsFile.readText())
        }.getOrElse {
            runCatching {
                Files.move(
                    settingsFile.toPath(),
                    File(settingsFile.parentFile, "${settingsFile.name}.corrupt").toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            ProxySettingsDto()
        }
    }

    private fun writeDto(dto: ProxySettingsDto) {
        val parentDir = settingsFile.parentFile
            ?: throw IllegalStateException("Diretório pai de proxy.json não encontrado.")
        parentDir.mkdirs()

        val content = json.encodeToString(ProxySettingsDto.serializer(), dto)
        // Nome único: com um `.tmp` fixo, duas instâncias gravando ao mesmo tempo
        // escrevem no mesmo intermediário e uma move o conteúdo da outra por cima
        // do proxy.json — o arquivo resultante fica truncado e ilegível.
        val tempFile = Files.createTempFile(parentDir.toPath(), settingsFile.name, ".tmp").toFile()
        try {
            Files.writeString(tempFile.toPath(), content)
            restrictToOwnerReadWrite(tempFile.toPath())
            try {
                Files.move(
                    tempFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            restrictToOwnerReadWrite(settingsFile.toPath())
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun ProxySettingsDto.toDomain(): ProxySettings {
        return ProxySettings(
            useEnvironmentProxy = useEnvironmentProxy,
            host = host,
            port = port,
            username = username,
            password = password
        )
    }

    private companion object {
        fun defaultSettingsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/proxy.json")
        }
    }
}
