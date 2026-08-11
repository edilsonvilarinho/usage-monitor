package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.TeamIntegrationSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
private data class TeamSettingsDto(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = "",
    val alias: String = "",
    val deviceId: String = "",
    val participatingProfileIds: List<String> = emptyList()
)

/**
 * Persiste a configuração da integração com time em `~/.usage-monitor/team.json`.
 *
 * **Por que arquivo e não `PreferencesSettings`:** a chave do servidor é um
 * segredo, e as preferências do app vão em claro para o registro do Windows
 * (`HKCU`), onde hoje nada sigiloso é guardado. Aqui vale o mesmo mecanismo que o
 * app já usa para o cache: escrita atômica mais [restrictToOwnerReadWrite], que
 * remove as permissões de grupo e de outros usuários do arquivo.
 *
 * O `deviceId` é gerado uma única vez, na primeira leitura, e persistido junto: é
 * o que separa duas máquinas do mesmo usuário no servidor.
 */
internal class LocalTeamSettingsDataSource(
    private val settingsFile: File = defaultSettingsFile()
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Lê a configuração, criando o `deviceId` se ainda não houver.
     *
     * Um arquivo corrompido devolve o default em vez de derrubar o boot: perder a
     * configuração de time é ruim, mas não abrir o app é pior. A falha de
     * gravação aqui também não sobe pelo mesmo motivo — ela reaparece na
     * primeira edição das Configurações, que já reporta sucesso ou falha.
     */
    fun load(): TeamIntegrationSettings {
        val dto = readDto()
        if (dto.deviceId.isNotBlank()) {
            return dto.toDomain()
        }

        val withDeviceId = dto.copy(deviceId = UUID.randomUUID().toString())
        runCatching { writeDto(withDeviceId) }
        return withDeviceId.toDomain()
    }

    fun save(settings: TeamIntegrationSettings) {
        // O deviceId nunca é editável pela UI; se vier vazio, preserva o gravado.
        val deviceId = settings.deviceId.takeIf { it.isNotBlank() }
            ?: readDto().deviceId.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        writeDto(
            TeamSettingsDto(
                enabled = settings.enabled,
                serverUrl = settings.serverUrl.trim(),
                apiKey = settings.apiKey.trim(),
                alias = settings.alias.trim(),
                deviceId = deviceId,
                participatingProfileIds = settings.participatingProfileIds.sorted()
            )
        )
    }

    /**
     * Devolve o default quando o arquivo não dá para ler, guardando o original.
     *
     * O arquivo ilegível não pode simplesmente virar default: [load] gravaria os
     * defaults por cima e, junto com o servidor, a chave e o apelido, levaria o
     * `deviceId` — a máquina reapareceria como um integrante novo no time. Movê-lo
     * para `.corrupt` mantém o conteúdo recuperável em vez de sobrescrevê-lo.
     */
    private fun readDto(): TeamSettingsDto {
        if (!settingsFile.isFile) {
            return TeamSettingsDto()
        }
        return runCatching {
            json.decodeFromString(TeamSettingsDto.serializer(), settingsFile.readText())
        }.getOrElse {
            runCatching {
                Files.move(
                    settingsFile.toPath(),
                    File(settingsFile.parentFile, "${settingsFile.name}.corrupt").toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            TeamSettingsDto()
        }
    }

    private fun writeDto(dto: TeamSettingsDto) {
        val parentDir = settingsFile.parentFile
            ?: throw IllegalStateException("Diretório pai de team.json não encontrado.")
        parentDir.mkdirs()

        val content = json.encodeToString(TeamSettingsDto.serializer(), dto)
        // Nome único: com um `.tmp` fixo, duas instâncias gravando ao mesmo tempo
        // escrevem no mesmo intermediário e uma move o conteúdo da outra por cima
        // do team.json — o arquivo resultante fica truncado e ilegível.
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

    private fun TeamSettingsDto.toDomain(): TeamIntegrationSettings {
        return TeamIntegrationSettings(
            enabled = enabled,
            serverUrl = serverUrl,
            apiKey = apiKey,
            alias = alias,
            deviceId = deviceId,
            participatingProfileIds = participatingProfileIds.toSet()
        )
    }

    private companion object {
        fun defaultSettingsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/team.json")
        }
    }
}
