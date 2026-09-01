package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalProxySettingsDataSourceTest {

    @Test
    fun `default e usar variavel de ambiente sem configuracao manual`() {
        withSettingsFile { file ->
            val loaded = LocalProxySettingsDataSource(file).load()

            assertTrue(loaded.useEnvironmentProxy)
            assertEquals("", loaded.host)
        }
    }

    @Test
    fun `round-trip preserva host porta usuario e senha`() {
        withSettingsFile { file ->
            val dataSource = LocalProxySettingsDataSource(file)

            dataSource.save(
                dataSource.load().copy(
                    useEnvironmentProxy = false,
                    host = "proxy.empresa.com",
                    port = 8080,
                    username = "usuario",
                    password = "senha-secreta"
                )
            )

            val reloaded = dataSource.load()
            assertEquals(false, reloaded.useEnvironmentProxy)
            assertEquals("proxy.empresa.com", reloaded.host)
            assertEquals(8080, reloaded.port)
            assertEquals("usuario", reloaded.username)
            assertEquals("senha-secreta", reloaded.password)
        }
    }

    @Test
    fun `arquivo ausente devolve default sem lancar`() {
        withSettingsFile { file ->
            val loaded = LocalProxySettingsDataSource(file).load()

            assertTrue(loaded.useEnvironmentProxy)
        }
    }

    @Test
    fun `arquivo ilegivel vai para corrupt em vez de ser sobrescrito`() {
        withSettingsFile { file ->
            file.writeText("{ isto nao e json")

            val recovered = LocalProxySettingsDataSource(file).load()

            val corruptFile = File(file.parentFile, "${file.name}.corrupt")
            assertTrue(corruptFile.isFile)
            assertEquals("{ isto nao e json", corruptFile.readText())
            // Devolve o default em vez de derrubar o boot.
            assertTrue(recovered.useEnvironmentProxy)
        }
    }

    @Test
    fun `arquivo sem campos novos continua legivel`() {
        withSettingsFile { file ->
            file.writeText("""{"useEnvironmentProxy":false}""")

            val loaded = LocalProxySettingsDataSource(file).load()

            assertEquals(false, loaded.useEnvironmentProxy)
            assertEquals("", loaded.host)
        }
    }

    private fun withSettingsFile(block: (File) -> Unit) {
        val tempDir = createTempDirectory().toFile()
        try {
            block(File(tempDir, "proxy.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
