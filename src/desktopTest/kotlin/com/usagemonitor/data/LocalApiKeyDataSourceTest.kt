package com.usagemonitor.data

import com.usagemonitor.data.datasource.ApiKeySettings
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.domain.entity.ApiSource
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalApiKeyDataSourceTest {
    private val tempDir = createTempDirectory("api-key-settings-test").toFile()
    private val settingsFile = File(tempDir, "api-keys.json")

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `saves and loads keys without placing them in preferences`() {
        val dataSource = LocalApiKeyDataSource(settingsFile)

        dataSource.save(ApiSource.MINIMAX, " minimax-secret ")
        dataSource.save(ApiSource.DEEPSEEK, "deepseek-secret")

        val loaded = LocalApiKeyDataSource(settingsFile).load()
        assertEquals("minimax-secret", loaded.minimax)
        assertEquals("deepseek-secret", loaded.deepSeek)
        assertEquals("deepseek-secret", loaded.forSource(ApiSource.DEEPSEEK))
        assertNull(loaded.forSource(ApiSource.ANTHROPIC))
    }

    @Test
    fun `does not read environment when local file is absent`() {
        val loaded = LocalApiKeyDataSource(settingsFile).load()

        assertEquals(ApiKeySettings(), loaded)
    }
}
