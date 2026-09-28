package com.usagemonitor.data.datasource

import com.usagemonitor.data.CodexCliHomeProvider
import com.usagemonitor.data.parser.CodexRolloutRateLimit
import com.usagemonitor.data.parser.CodexRolloutRateLimitParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Lê os `rate_limits` mais recentes dos rollouts do Codex CLI (issue #324), em
 * `$CODEX_HOME/sessions/AAAA/MM/DD/rollout-*.jsonl`.
 *
 * Só leitura, só o fim de cada arquivo e só os mais novos: o `rate_limits` vem em
 * cada `token_count`, então o último evento do arquivo mais recente já é a leitura
 * atual, e um rollout longo passa de dezenas de MB. Nunca abre `auth.json` nem
 * reescreve nada — mesma regra do índice de sessões do Codex.
 */
class LocalCodexRolloutRateLimitDataSource(
    private val codexHome: () -> File = { CodexCliHomeProvider.resolve() },
    private val maxFiles: Int = MAX_FILES,
    private val tailBytes: Long = TAIL_BYTES
) : CodexRolloutRateLimitDataSource {

    override suspend fun latestRateLimits(): List<CodexRolloutRateLimit> = withContext(Dispatchers.IO) {
        val files = newestRollouts(File(codexHome(), "sessions"))
        val lines = files.asSequence().flatMap { file -> tailLines(file).asSequence() }
        CodexRolloutRateLimitParser.latestByLimitId(lines)
    }

    /**
     * Os [maxFiles] rollouts mais novos pela árvore de datas, do dia mais recente
     * para trás. A ordem é a do nome — o nome carrega o carimbo de início —, e não
     * `lastModified`, que empata entre arquivos escritos no mesmo segundo.
     */
    internal fun newestRollouts(sessionsDir: File): List<File> {
        val result = mutableListOf<File>()
        val years = childDirectoriesDescending(sessionsDir)
        for (year in years) {
            for (month in childDirectoriesDescending(year)) {
                for (day in childDirectoriesDescending(month)) {
                    val rollouts = day.listFiles { file -> file.isFile && file.name.endsWith(".jsonl") }
                        .orEmpty()
                        .sortedByDescending { file -> file.name }
                    for (file in rollouts) {
                        result += file
                        if (result.size >= maxFiles) return result
                    }
                }
            }
        }
        return result
    }

    private fun childDirectoriesDescending(dir: File): List<File> {
        return dir.listFiles { file -> file.isDirectory }.orEmpty().sortedByDescending { file -> file.name }
    }

    /** As linhas completas dos últimos [tailBytes] do arquivo; a primeira, cortada no meio, sai. */
    internal fun tailLines(file: File): List<String> {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val start = (length - tailBytes).coerceAtLeast(0L)
                raf.seek(start)
                val bytes = ByteArray((length - start).toInt())
                raf.readFully(bytes)
                val lines = String(bytes, Charsets.UTF_8).split('\n')
                if (start > 0L) lines.drop(1) else lines
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_FILES = 5
        const val TAIL_BYTES = 256L * 1024L
    }
}
