package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.DashboardCacheDto
import com.usagemonitor.data.mapper.toCacheDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.domain.entity.ApiUsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Guarda o último `UiState.Success` exibido no dashboard em
 * `~/.usage-monitor/dashboard-cache.json`, para reidratar a UI imediatamente
 * ao reabrir o app antes do próximo ciclo de atualização (evita o app ficar
 * preso em "Carregando dados das APIs..." só porque o timer ainda não venceu).
 */
internal class LocalDashboardCacheDataSource(
    private val cacheFile: File = defaultCacheFile()
) : DashboardCacheDataSource {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    override suspend fun save(stats: List<ApiUsageStats>, capturedAt: Instant) {
        if (stats.isEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dto = DashboardCacheDto(
                    savedAtEpochMillis = capturedAt.toEpochMilliseconds(),
                    entries = stats.map { entry -> entry.toCacheDto() }
                )
                runCatching {
                    atomicWriteText(
                        target = cacheFile,
                        content = json.encodeToString(DashboardCacheDto.serializer(), dto)
                    )
                }
            }
        }
    }

    override suspend fun load(): List<ApiUsageStats> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!cacheFile.exists()) {
                    return@withLock emptyList()
                }
                runCatching {
                    json.decodeFromString(DashboardCacheDto.serializer(), cacheFile.readText()).toDomain()
                }.getOrElse { emptyList() }
            }
        }
    }

    private fun atomicWriteText(target: File, content: String) {
        val parentDir = target.parentFile
            ?: throw IllegalStateException("Diretório pai do cache do dashboard não encontrado.")
        parentDir.mkdirs()
        val tempFile = File(parentDir, "${target.name}.tmp")
        try {
            Files.writeString(tempFile.toPath(), content)
            try {
                Files.move(
                    tempFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            restrictToOwnerReadWrite(target.toPath())
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private companion object {
        fun defaultCacheFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/dashboard-cache.json")
        }
    }
}
