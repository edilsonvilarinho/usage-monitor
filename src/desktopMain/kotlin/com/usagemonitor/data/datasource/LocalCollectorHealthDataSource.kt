package com.usagemonitor.data.datasource

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.domain.entity.CollectorSourceHealth
import com.usagemonitor.domain.entity.CollectorTeamHealth
import com.usagemonitor.domain.entity.CollectorTelegramHealth
import com.usagemonitor.domain.entity.CollectorWebHealth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
private data class HealthDto(
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("next_collection_at") val nextCollectionAt: Long? = null,
    @SerialName("sources") val sources: List<SourceDto> = emptyList(),
    @SerialName("telegram") val telegram: TelegramDto = TelegramDto(),
    @SerialName("team") val team: TeamDto = TeamDto(),
    @SerialName("web") val web: WebDto = WebDto()
)

@Serializable
private data class SourceDto(
    @SerialName("source") val source: String,
    @SerialName("label") val label: String,
    @SerialName("ok") val ok: Boolean,
    @SerialName("retry_at") val retryAt: Long? = null
)

@Serializable
private data class TelegramDto(
    @SerialName("state") val state: String = "off",
    @SerialName("bot") val bot: String? = null,
    @SerialName("chats") val chats: Int = 0
)

@Serializable
private data class TeamDto(
    @SerialName("active") val active: Boolean = false,
    @SerialName("server") val server: String? = null,
    @SerialName("last_success_at") val lastSuccessAt: Long? = null,
    @SerialName("last_failure") val lastFailure: String? = null
)

@Serializable
private data class WebDto(
    @SerialName("state") val state: String = "stopped",
    @SerialName("port") val port: Int? = null,
    @SerialName("addresses") val addresses: List<String> = emptyList(),
    @SerialName("failure") val failure: String? = null
)

/**
 * `~/.usage-monitor/collector-health.json` (#440): o que o coletor sabe de si, para o
 * `service status`. Gravado de forma atômica (o leitor nunca vê meio arquivo) e apagado
 * ao encerrar, como o `collector.json`.
 */
internal class LocalCollectorHealthDataSource(
    private val file: File = AppDataDirectory.file("collector-health.json")
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun write(health: CollectorHealth) {
        runCatching {
            val dto = HealthDto(
                updatedAt = health.updatedAtEpochMillis,
                nextCollectionAt = health.nextCollectionAtEpochMillis,
                sources = health.sources.map { source -> SourceDto(source.source.name, source.label, source.ok, source.retryAtEpochMillis) },
                telegram = TelegramDto(health.telegram.state, health.telegram.botUsername, health.telegram.chatCount),
                team = TeamDto(health.team.active, health.team.server, health.team.lastSuccessAtEpochMillis, health.team.lastFailure),
                web = WebDto(health.web.state, health.web.port, health.web.addresses, health.web.failure)
            )
            file.parentFile?.mkdirs()
            val temp = Files.createTempFile(file.parentFile.toPath(), file.name, ".tmp")
            Files.writeString(temp, json.encodeToString(HealthDto.serializer(), dto))
            try {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun read(): CollectorHealth? {
        if (!file.isFile) {
            return null
        }
        return runCatching {
            val dto = json.decodeFromString(HealthDto.serializer(), file.readText())
            CollectorHealth(
                updatedAtEpochMillis = dto.updatedAt,
                nextCollectionAtEpochMillis = dto.nextCollectionAt,
                sources = dto.sources.mapNotNull { source ->
                    val api = ApiSource.entries.firstOrNull { entry -> entry.name == source.source } ?: return@mapNotNull null
                    CollectorSourceHealth(api, source.label, source.ok, source.retryAt)
                },
                telegram = CollectorTelegramHealth(dto.telegram.state, dto.telegram.bot, dto.telegram.chats),
                team = CollectorTeamHealth(dto.team.active, dto.team.server, dto.team.lastSuccessAt, dto.team.lastFailure),
                web = CollectorWebHealth(dto.web.state, dto.web.port, dto.web.addresses, dto.web.failure)
            )
        }.getOrNull()
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
