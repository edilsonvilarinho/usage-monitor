package com.usagemonitor.data.datasource

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.AlertDestination
import com.usagemonitor.domain.entity.AlertLogEntry
import com.usagemonitor.domain.entity.AlertLogKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class AlertLogLineDto(
    @SerialName("at") val atEpochMillis: Long,
    @SerialName("kind") val kind: String,
    @SerialName("title") val title: String,
    @SerialName("percent") val percent: Int? = null,
    @SerialName("to") val destinations: List<String> = emptyList()
)

/**
 * `~/.usage-monitor/alerts.jsonl` (#440): um aviso por linha, gravado por quem coleta e
 * lido pelo `usage-monitor alerts`. Só cresce até [MAX_BYTES]: aí guarda as últimas
 * [KEEP_LINES] linhas. Linha ilegível é pulada, e não derruba a leitura.
 */
internal class LocalAlertLogDataSource(
    private val file: File = AppDataDirectory.file("alerts.jsonl")
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun append(entry: AlertLogEntry) {
        runCatching {
            file.parentFile?.mkdirs()
            val dto = AlertLogLineDto(entry.atEpochMillis, entry.kind.name.lowercase(), entry.title, entry.percent, entry.destinations.map { destination -> destination.name.lowercase() })
            file.appendText(json.encodeToString(AlertLogLineDto.serializer(), dto) + "\n")
            if (file.length() > MAX_BYTES) {
                val kept = file.readLines().takeLast(KEEP_LINES)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** Os avisos desde [sinceEpochMillis], na ordem em que saíram. */
    fun read(sinceEpochMillis: Long): List<AlertLogEntry> {
        if (!file.isFile) {
            return emptyList()
        }
        return runCatching { file.readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            runCatching { json.decodeFromString(AlertLogLineDto.serializer(), line) }.getOrNull()?.let { dto ->
                AlertLogEntry(
                    atEpochMillis = dto.atEpochMillis,
                    kind = AlertLogKind.entries.firstOrNull { kind -> kind.name.equals(dto.kind, ignoreCase = true) } ?: return@mapNotNull null,
                    title = dto.title,
                    percent = dto.percent,
                    destinations = dto.destinations.mapNotNull { name -> AlertDestination.entries.firstOrNull { destination -> destination.name.equals(name, ignoreCase = true) } }
                )
            }
        }.filter { entry -> entry.atEpochMillis >= sinceEpochMillis }
    }

    private companion object {
        const val MAX_BYTES = 512L * 1024L
        const val KEEP_LINES = 1_000
    }
}
