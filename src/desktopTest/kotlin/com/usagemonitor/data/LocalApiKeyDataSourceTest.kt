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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * Issue #125: apagar uma chave não pode apagar as outras duas — o arquivo é
     * um só e as três fontes convivem nele.
     */
    @Test
    fun `withoutKey clears only the requested source`() {
        val settings = ApiKeySettings(
            minimax = "minimax-secret",
            deepSeek = "deepseek-secret",
            openCodeGo = "opencode-secret"
        )

        val cleared = settings.withoutKey(ApiSource.DEEPSEEK)

        assertEquals("", cleared.deepSeek)
        assertNull(cleared.forSource(ApiSource.DEEPSEEK))
        assertEquals("minimax-secret", cleared.minimax)
        assertEquals("opencode-secret", cleared.openCodeGo)
        assertEquals(setOf(ApiSource.MINIMAX, ApiSource.OPENCODE_GO), cleared.configuredSources())
    }

    /** Fonte sem chave local não tem o que apagar: devolve o mesmo objeto. */
    @Test
    fun `withoutKey ignores a source that has no local api key`() {
        val settings = ApiKeySettings(minimax = "minimax-secret")

        assertEquals(settings, settings.withoutKey(ApiSource.ANTHROPIC))
    }

    /**
     * Issue #125: até esta passada não existia caminho nenhum para apagar uma
     * chave — `save` recusa branco e não havia `clear`.
     */
    @Test
    fun `clear removes a single key and keeps the file readable`() {
        val dataSource = LocalApiKeyDataSource(settingsFile)
        dataSource.save(ApiSource.MINIMAX, "minimax-secret")
        dataSource.save(ApiSource.DEEPSEEK, "deepseek-secret")

        dataSource.clear(ApiSource.MINIMAX)

        val loaded = LocalApiKeyDataSource(settingsFile).load()
        assertNull(loaded.forSource(ApiSource.MINIMAX))
        assertEquals("deepseek-secret", loaded.forSource(ApiSource.DEEPSEEK))
        assertEquals(setOf(ApiSource.DEEPSEEK), loaded.configuredSources())
        // `encodeDefaults` está desligado, então o campo apagado sai do JSON —
        // o mesmo estado de uma fonte que nunca foi configurada. O arquivo
        // continua legível por versão anterior do app, que cai no default `""`.
        val written = settingsFile.readText()
        assertFalse(written.contains("minimax"))
        assertTrue(written.contains("deepseek-secret"))
    }

    @Test
    fun `clear refuses a source that has no local api key`() {
        val dataSource = LocalApiKeyDataSource(settingsFile)

        assertFailsWith<IllegalArgumentException> {
            dataSource.clear(ApiSource.OPENCODE)
        }
    }

    @Test
    fun `does not read environment when local file is absent`() {
        val loaded = LocalApiKeyDataSource(settingsFile).load()

        assertEquals(ApiKeySettings(), loaded)
    }
}
