package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalCodexCliSessionDataSource
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Os bancos abertos em somente leitura pelo modo interativo (#441, D2): leem o que o
 * coletor gravou, não criam arquivo, não rodam schema e não escrevem nada.
 */
class ReadOnlyDatabaseTest {

    private val capturedAt = Instant.parse("2026-10-21T17:00:00Z")

    /**
     * O conteúdo de cada banco da pasta: nome e hash, para provar que nada mudou. Os sidecars
     * do WAL ficam de fora (o SQLite os cria para ler); o `-wal` tem de estar vazio.
     */
    private fun fingerprint(directory: File): Map<String, String> {
        val files = directory.listFiles().orEmpty()
        assertTrue(files.filter { file -> file.name.endsWith("-wal") }.all { file -> file.length() == 0L })
        return files.filter { file -> file.name.endsWith(".db") }.sortedBy { file -> file.name }.associate { file ->
            file.name to MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    @Test
    fun `historico em somente leitura le o que o coletor gravou e nao muda nada`() = runTest {
        val directory = createTempDirectory().toFile()
        val database = File(directory, "usage-history.db")
        LocalUsageHistoryDataSource(database).use { writer -> writer.insertSnapshot(sampleStats(), capturedAt) }
        val before = fingerprint(directory)

        LocalUsageHistoryDataSource(database, readOnly = true).use { reader ->
            assertEquals(2, reader.readSnapshots(ApiSource.CODEX, capturedAt - kotlin.time.Duration.parse("1h")).size)
            assertTrue(runCatching { reader.insertSnapshot(sampleStats(), capturedAt) }.isFailure)
        }

        assertEquals(before, fingerprint(directory))
        directory.deleteRecursively()
    }

    @Test
    fun `leitura com o coletor ainda aberto ve o que ja foi gravado`() = runTest {
        val directory = createTempDirectory().toFile()
        val database = File(directory, "usage-history.db")
        LocalUsageHistoryDataSource(database).use { writer ->
            writer.insertSnapshot(sampleStats(), capturedAt)
            LocalUsageHistoryDataSource(database, readOnly = true).use { reader ->
                assertEquals(2, reader.readSnapshots(ApiSource.CODEX, capturedAt - kotlin.time.Duration.parse("1h")).size)
            }
        }
        directory.deleteRecursively()
    }

    @Test
    fun `banco que nao existe e falha de leitura e nenhum arquivo nasce`() = runTest {
        val directory = createTempDirectory().toFile()
        val database = File(directory, "sub/usage-history.db")
        LocalUsageHistoryDataSource(database, readOnly = true).use { reader ->
            assertTrue(runCatching { reader.readSnapshots(ApiSource.CODEX, capturedAt) }.isFailure)
        }
        LocalCliSessionDataSource(databaseFile = database, readOnly = true).use { reader ->
            assertTrue(runCatching { reader.readSessions() }.isFailure)
        }
        assertFalse(File(directory, "sub").exists())
        directory.deleteRecursively()
    }

    @Test
    fun `indices de sessao em somente leitura nao indexam e nao mexem no arquivo`() = runTest {
        val directory = createTempDirectory().toFile()
        val cliIndex = File(directory, "usage-history.db")
        val codexIndex = File(directory, "codex-cli-history.db")
        // O coletor cria o schema na primeira indexação.
        LocalCliSessionDataSource(projectRootsProvider = { emptyList() }, databaseFile = cliIndex).use { writer -> writer.syncIndex() }
        LocalCodexCliSessionDataSource(codexHomeProvider = { File(directory, "sem-codex") }, databaseFile = codexIndex).use { writer -> writer.syncIndex() }
        val before = fingerprint(directory)

        LocalCliSessionDataSource(projectRootsProvider = { error("a TUI não procura transcripts") }, databaseFile = cliIndex, readOnly = true).use { reader ->
            assertEquals(CliSessionIndexReport(), reader.syncIndex())
            assertEquals(emptyList(), reader.readSessions())
        }
        LocalCodexCliSessionDataSource(codexHomeProvider = { error("a TUI não procura rollouts") }, databaseFile = codexIndex, readOnly = true).use { reader ->
            assertEquals(CodexCliSessionIndexReport(), reader.syncIndex())
            assertEquals(emptyList(), reader.readSessions(null))
        }

        assertEquals(before, fingerprint(directory))
        directory.deleteRecursively()
    }

    private fun sampleStats(): ApiUsageStats =
        ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            accountContext = UsageAccountContext(
                key = UsageAccountKey(source = ApiSource.CODEX, providerAccountId = "codex-a", workspaceId = "workspace-a"),
                email = "a@example.com"
            ),
            quotas = listOf(
                QuotaInfo(label = "Codex 5h", used = 45L, total = 100L, periodEndAt = capturedAt, periodType = PeriodType.REPORTED, unit = UsageUnit.PERCENTAGE, rawUsed = 0L, rawTotal = 0L),
                QuotaInfo(label = "Codex 7d", used = 20L, total = 100L, periodEndAt = capturedAt, periodType = PeriodType.WEEKLY, unit = UsageUnit.PERCENTAGE, rawUsed = 0L, rawTotal = 0L)
            )
        )
}
