package com.usagemonitor.data

import com.usagemonitor.data.datasource.ApiKeySettings
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.domain.entity.ApiSource
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        dataSource.save(ApiSource.OPENCODE_GO, "opencode-secret")

        val loaded = LocalApiKeyDataSource(settingsFile).load()
        assertEquals("minimax-secret", loaded.minimax)
        assertEquals("deepseek-secret", loaded.deepSeek)
        assertEquals("opencode-secret", loaded.openCodeGo)
        assertEquals("deepseek-secret", loaded.forSource(ApiSource.DEEPSEEK))
        assertEquals("opencode-secret", loaded.forSource(ApiSource.OPENCODE_GO))
        assertNull(loaded.forSource(ApiSource.ANTHROPIC))
        assertEquals(
            setOf(ApiSource.MINIMAX, ApiSource.DEEPSEEK, ApiSource.OPENCODE_GO),
            loaded.configuredSources()
        )
    }

    /**
     * A chave do Go não pode vazar para o plano gratuito do Zen: ele é lido do
     * SQLite local e nunca faz requisição autenticada.
     */
    @Test
    fun `does not expose the go key to the free opencode source`() {
        LocalApiKeyDataSource(settingsFile).save(ApiSource.OPENCODE_GO, "opencode-secret")

        val loaded = LocalApiKeyDataSource(settingsFile).load()

        assertNull(loaded.forSource(ApiSource.OPENCODE))
    }

    @Test
    fun `refuses a source that has no local api key`() {
        val dataSource = LocalApiKeyDataSource(settingsFile)

        assertFailsWith<IllegalArgumentException> {
            dataSource.save(ApiSource.OPENCODE, "should-not-persist")
        }
    }

    @Test
    fun `does not read environment when local file is absent`() {
        val loaded = LocalApiKeyDataSource(settingsFile).load()

        assertEquals(ApiKeySettings(), loaded)
    }
}
