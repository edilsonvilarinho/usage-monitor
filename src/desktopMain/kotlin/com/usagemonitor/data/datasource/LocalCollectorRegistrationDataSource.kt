package com.usagemonitor.data.datasource

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.CollectorKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Instant

/** Quem coleta agora, gravado por quem tomou a trava. */
internal data class CollectorRegistration(
    val kind: CollectorKind,
    val pid: Long,
    val startedAt: Instant,
    val version: String
)

@Serializable
private data class CollectorRegistrationDto(
    @SerialName("kind") val kind: String,
    @SerialName("pid") val pid: Long,
    @SerialName("started_at") val startedAtMillis: Long,
    @SerialName("version") val version: String
)

/**
 * `~/.usage-monitor/collector.json` (#440): o dono da trava `app.lock` diz quem é.
 *
 * A trava não guarda conteúdo (`SingleInstanceGuard`), e o terminal precisa dizer se a
 * leitura veio "pelo app aberto" ou "pelo serviço". O arquivo só vale com a trava
 * tomada: sobra de um processo que caiu é ignorada porque a trava já está livre. Sem
 * arquivo e com a trava tomada, o coletor é um app de versão anterior à #440.
 */
internal class LocalCollectorRegistrationDataSource(
    private val file: File = AppDataDirectory.file("collector.json")
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): CollectorRegistration? {
        if (!file.isFile) {
            return null
        }
        return runCatching {
            val dto = json.decodeFromString(CollectorRegistrationDto.serializer(), file.readText())
            CollectorRegistration(
                kind = CollectorKind.valueOf(dto.kind.uppercase()),
                pid = dto.pid,
                startedAt = Instant.fromEpochMilliseconds(dto.startedAtMillis),
                version = dto.version
            )
        }.getOrNull()
    }

    fun write(registration: CollectorRegistration) {
        val dto = CollectorRegistrationDto(
            kind = registration.kind.name.lowercase(),
            pid = registration.pid,
            startedAtMillis = registration.startedAt.toEpochMilliseconds(),
            version = registration.version
        )
        runCatching { writeSecretFile(file, json.encodeToString(CollectorRegistrationDto.serializer(), dto)) }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
