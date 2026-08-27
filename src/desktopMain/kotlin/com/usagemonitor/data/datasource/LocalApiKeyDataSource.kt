package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.ApiSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Chaves das integrações remotas que não possuem login local. */
data class ApiKeySettings(
    val minimax: String = "",
    val deepSeek: String = ""
) {
    fun forSource(source: ApiSource): String? {
        return when (source) {
            ApiSource.MINIMAX -> minimax
            ApiSource.DEEPSEEK -> deepSeek
            else -> null
        }?.takeIf { value -> value.isNotBlank() }
    }

    fun withKey(source: ApiSource, value: String): ApiKeySettings {
        return when (source) {
            ApiSource.MINIMAX -> copy(minimax = value)
            ApiSource.DEEPSEEK -> copy(deepSeek = value)
            else -> this
        }
    }

    fun configuredSources(): Set<ApiSource> {
        return buildSet {
            if (minimax.isNotBlank()) add(ApiSource.MINIMAX)
            if (deepSeek.isNotBlank()) add(ApiSource.DEEPSEEK)
        }
    }
}

@Serializable
private data class ApiKeySettingsDto(
    val minimax: String = "",
    val deepSeek: String = ""
)

/**
 * Persiste as chaves de MiniMax e DeepSeek fora das preferências do Windows.
 *
 * O arquivo usa o mesmo contrato de segurança de `team.json`: escrita atômica
 * e acesso restrito ao usuário dono. Esta é a única origem das chaves no desktop.
 */
internal class LocalApiKeyDataSource(
    private val settingsFile: File = defaultSettingsFile()
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): ApiKeySettings {
        return readDto().toDomain()
    }

    fun save(source: ApiSource, apiKey: String) {
        require(source == ApiSource.MINIMAX || source == ApiSource.DEEPSEEK) {
            "Apenas MiniMax e DeepSeek possuem chave de API local."
        }
        require(apiKey.isNotBlank()) { "A chave de API não pode ficar vazia." }
        write(load().withKey(source, apiKey.trim()))
    }

    private fun readDto(): ApiKeySettingsDto {
        if (!settingsFile.isFile) return ApiKeySettingsDto()
        return runCatching {
            json.decodeFromString(ApiKeySettingsDto.serializer(), settingsFile.readText())
        }.getOrElse { ApiKeySettingsDto() }
    }

    private fun write(settings: ApiKeySettings) {
        val parentDir = settingsFile.parentFile
            ?: throw IllegalStateException("Diretório pai de api-keys.json não encontrado.")
        parentDir.mkdirs()
        val content = json.encodeToString(
            ApiKeySettingsDto.serializer(),
            ApiKeySettingsDto(settings.minimax, settings.deepSeek)
        )
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
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun ApiKeySettingsDto.toDomain(): ApiKeySettings {
        return ApiKeySettings(minimax = minimax, deepSeek = deepSeek)
    }

    private companion object {
        fun defaultSettingsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/api-keys.json")
        }
    }
}
