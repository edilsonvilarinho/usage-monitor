package com.usagemonitor.data.datasource

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.redactedSecret
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Nome do time migrado quando o arquivo plano não tem servidor. */
private const val LEGACY_TEAM_NAME = "Time"

/** Um time no `team.json` v2. Mesmos campos do time único, mais `id` e `name`. */
@Serializable
private data class TeamEntryDto(
    val id: String = "",
    val name: String = "",
    val serverUrl: String = "",
    val apiKey: String = "",
    val alias: String = "",
    val participatingProfileIds: List<String> = emptyList(),
    val adminToken: String = ""
) {
    /** Segredos nunca no `toString`, mesmo dentro da lista de times do DTO. */
    override fun toString(): String =
        "TeamEntryDto(id=$id, name=$name, serverUrl=$serverUrl, apiKey=${redactedSecret(apiKey)}, alias=$alias, " +
            "participatingProfileIds=$participatingProfileIds, adminToken=${redactedSecret(adminToken)})"
}

@Serializable
private data class TeamSettingsDto(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = "",
    val alias: String = "",
    val deviceId: String = "",
    val participatingProfileIds: List<String> = emptyList(),
    /**
     * Token de administração, quando esta máquina administra o servidor.
     *
     * Campo novo com default: um `team.json` gravado por versão anterior continua
     * legível, e o `ignoreUnknownKeys` cobre o caminho de volta.
     */
    val adminToken: String = "",
    /**
     * Times do formato v2. `null` é o arquivo plano da versão anterior: os campos planos
     * viram um time só. Lista presente (mesmo vazia) manda e ignora os campos planos.
     */
    val teams: List<TeamEntryDto>? = null
) {
    /** Segredo nunca no `toString` (log, relatório de bug, mensagem de exceção). */
    override fun toString(): String =
        "TeamSettingsDto(enabled=$enabled, serverUrl=$serverUrl, apiKey=${redactedSecret(apiKey)}, alias=$alias, " +
            "deviceId=$deviceId, participatingProfileIds=$participatingProfileIds, adminToken=${redactedSecret(adminToken)}, " +
            "teams=$teams)"
}

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
 *
 * **Formato v2 (`teams`):** o arquivo guarda uma lista de times. Os campos planos
 * espelham o primeiro time, para que uma versão anterior (downgrade, canal beta)
 * continue sincronizando esse time em vez de perder a integração.
 */
internal class LocalTeamSettingsDataSource(
    private val settingsFile: File = defaultSettingsFile()
) {
    // encodeDefaults: campos planos vazios precisam estar no arquivo para o downgrade ler o espelho sem ambiguidade.
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    /**
     * Lê o primeiro time, criando o `deviceId` se ainda não houver.
     *
     * Um arquivo corrompido devolve o default em vez de derrubar o boot: perder a
     * configuração de time é ruim, mas não abrir o app é pior. A falha de
     * gravação aqui também não sobe pelo mesmo motivo — ela reaparece na
     * primeira edição das Configurações, que já reporta sucesso ou falha.
     *
     * Transição: enquanto a UI não lê a lista, quem chama vê um time só.
     */
    fun load(): TeamIntegrationSettings = loadAll().firstTeamOrDefault()

    /**
     * Lê o agregado, criando o `deviceId` e regravando no formato v2 quando há
     * `deviceId` faltando ou quando o arquivo plano foi migrado.
     */
    fun loadAll(): TeamIntegrations {
        val dto = readDto()
        val integrations = dto.toAggregate()
        if (dto.teams != null && integrations.deviceId.isNotBlank()) {
            return integrations
        }

        val migrated = TeamIntegrations(
            deviceId = integrations.deviceId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            enabled = integrations.enabled,
            teams = integrations.teams
        )
        runCatching { writeAggregate(migrated) }
        return migrated
    }

    /**
     * Lê o primeiro time sem efeito nenhum (#441), para quem só olha o arquivo de fora, como o modo interativo:
     * sem criar o `deviceId` e sem mover arquivo ilegível, que são decisões do dono da
     * configuração. `null` sem arquivo ou com arquivo ilegível.
     */
    fun peek(): TeamIntegrationSettings? = peekAll()?.firstTeamOrDefault()

    /** Lê o agregado sem efeito nenhum, com as mesmas regras de [peek]. */
    fun peekAll(): TeamIntegrations? {
        if (!settingsFile.isFile) {
            return null
        }
        return runCatching { json.decodeFromString(TeamSettingsDto.serializer(), settingsFile.readText()).toAggregate() }.getOrNull()
    }

    /**
     * Grava o time pelo `id` no agregado gravado: substitui o de mesmo id mantendo a
     * posição, ou acrescenta. Id vazio aponta para o primeiro time, ou para o time
     * migrado quando não há nenhum.
     */
    fun save(settings: TeamIntegrationSettings) {
        val stored = readDto().toAggregate()
        val teamId = settings.id.takeIf { it.isNotBlank() }
            ?: stored.teams.firstOrNull()?.id
            ?: TeamIntegrations.LEGACY_TEAM_ID
        val merged = stored.upsert(settings.copy(id = teamId))
        saveAll(TeamIntegrations(deviceId = settings.deviceId, enabled = settings.enabled, teams = merged.teams))
    }

    /** Grava o agregado inteiro. O `deviceId` vazio preserva o gravado. */
    fun saveAll(integrations: TeamIntegrations) {
        // O deviceId nunca é editável pela UI; se vier vazio, preserva o gravado.
        val deviceId = integrations.deviceId.takeIf { it.isNotBlank() }
            ?: readDto().deviceId.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        writeAggregate(
            TeamIntegrations(
                deviceId = deviceId,
                enabled = integrations.enabled,
                teams = integrations.teams.map { it.trimmed() }
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

    /** Grava o agregado no formato v2, com os campos planos espelhando o primeiro time. */
    private fun writeAggregate(integrations: TeamIntegrations) {
        val first = integrations.teams.firstOrNull()
        writeDto(
            TeamSettingsDto(
                enabled = integrations.enabled,
                serverUrl = first?.serverUrl.orEmpty(),
                apiKey = first?.apiKey.orEmpty(),
                alias = first?.alias.orEmpty(),
                deviceId = integrations.deviceId,
                participatingProfileIds = first?.participatingProfileIds?.sorted().orEmpty(),
                adminToken = first?.adminToken.orEmpty(),
                teams = integrations.teams.map { it.toEntryDto() }
            )
        )
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

    /** Com `teams` presente, ele manda; sem ele, os campos planos viram um time só. */
    private fun TeamSettingsDto.toAggregate(): TeamIntegrations {
        return TeamIntegrations(
            deviceId = deviceId,
            enabled = enabled,
            teams = (teams ?: legacyEntries()).map { it.toDomain() }
        )
    }

    /** Time do formato plano, ou lista vazia quando nenhum campo plano tem valor. */
    private fun TeamSettingsDto.legacyEntries(): List<TeamEntryDto> {
        val hasFlatValue = serverUrl.isNotBlank() || apiKey.isNotBlank() || alias.isNotBlank() ||
            adminToken.isNotBlank() || participatingProfileIds.isNotEmpty()
        if (!hasFlatValue) {
            return emptyList()
        }
        return listOf(
            TeamEntryDto(
                id = TeamIntegrations.LEGACY_TEAM_ID,
                name = hostOf(serverUrl),
                serverUrl = serverUrl,
                apiKey = apiKey,
                alias = alias,
                participatingProfileIds = participatingProfileIds,
                adminToken = adminToken
            )
        )
    }

    private fun TeamEntryDto.toDomain(): TeamIntegrationSettings {
        return TeamIntegrationSettings(
            id = id,
            name = name,
            serverUrl = serverUrl,
            apiKey = apiKey,
            alias = alias,
            participatingProfileIds = participatingProfileIds.toSet(),
            adminToken = adminToken
        )
    }

    private fun TeamIntegrationSettings.toEntryDto(): TeamEntryDto {
        return TeamEntryDto(
            id = id,
            name = name,
            serverUrl = serverUrl,
            apiKey = apiKey,
            alias = alias,
            participatingProfileIds = participatingProfileIds.sorted(),
            adminToken = adminToken
        )
    }

    private fun TeamIntegrationSettings.trimmed(): TeamIntegrationSettings {
        return copy(
            id = id.trim(),
            name = name.trim(),
            serverUrl = serverUrl.trim(),
            apiKey = apiKey.trim(),
            alias = alias.trim(),
            adminToken = adminToken.trim()
        )
    }

    private fun TeamIntegrations.firstTeamOrDefault(): TeamIntegrationSettings {
        return teams.firstOrNull() ?: TeamIntegrationSettings(enabled = enabled, deviceId = deviceId)
    }

    /** Host do servidor, sem esquema, porta, caminho nem credencial; `Time` quando não há servidor. */
    private fun hostOf(serverUrl: String): String {
        val trimmed = serverUrl.trim()
        val authority = trimmed.substringAfter("://", trimmed)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = if (authority.startsWith('[')) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }
        return host.ifBlank { LEGACY_TEAM_NAME }
    }

    private companion object {
        fun defaultSettingsFile(): File {
            return AppDataDirectory.file("team.json")
        }
    }
}
