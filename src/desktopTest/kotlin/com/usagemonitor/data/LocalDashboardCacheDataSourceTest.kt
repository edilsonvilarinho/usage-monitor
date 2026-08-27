package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files

class LocalDashboardCacheDataSourceTest {

    @Test
    fun `persists empty OpenCode and Kilo cards`() = runTest {
        val tempDir = createTempDirectory()
        val cacheFile = tempDir.resolve("dashboard-cache.json").toFile()
        val dataSource = LocalDashboardCacheDataSource(cacheFile)
        val stats = listOf(
            ApiUsageStats(
                source = ApiSource.OPENCODE,
                apiName = "OpenCode Zen Free",
                quotas = emptyList()
            ),
            ApiUsageStats(
                source = ApiSource.KILO,
                apiName = "Kilo Free",
                quotas = emptyList()
            )
        )

        dataSource.save(stats, kotlinx.datetime.Instant.parse("2026-05-07T15:00:00Z"))

        assertEquals(stats, dataSource.load())

        Files.deleteIfExists(cacheFile.toPath())
        Files.deleteIfExists(tempDir)
    }
}
