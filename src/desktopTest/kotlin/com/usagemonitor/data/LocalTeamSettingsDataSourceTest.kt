package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * O `deviceId` é o que separa uma máquina da outra no servidor de time.
 *
 * Gerar um novo onde já havia um faz a mesma máquina aparecer duas vezes na tela
 * do time, e a linha antiga fica lá até a retenção do servidor recolhê-la.
 */
class LocalTeamSettingsDataSourceTest {

    @Test
    fun `mantem o mesmo deviceId entre leituras`() {
        withSettingsFile { file ->
            val first = LocalTeamSettingsDataSource(file).load()
            val second = LocalTeamSettingsDataSource(file).load()

            assertTrue(first.deviceId.isNotBlank())
            assertEquals(first.deviceId, second.deviceId)
        }
    }

    @Test
    fun `preserva o deviceId ao gravar sem informa-lo`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            val original = dataSource.load()

            dataSource.save(original.copy(alias = "edilson", deviceId = ""))

            assertEquals(original.deviceId, dataSource.load().deviceId)
            assertEquals("edilson", dataSource.load().alias)
        }
    }

    @Test
    fun `trocar o apelido nao troca o deviceId`() {
        withSettingsFile { file ->
            val dataSource = LocalTeamSettingsDataSource(file)
            val original = dataSource.load()

            dataSource.save(original.copy(alias = "edilson"))
            dataSource.save(dataSource.load().copy(alias = "SUETONIO"))

            val reloaded = dataSource.load()
            assertEquals("SUETONIO", reloaded.alias)
            assertEquals(original.deviceId, reloaded.deviceId)
        }
    }

    @Test
    fun `arquivo ilegivel vai para corrupt em vez de ser sobrescrito`() {
        withSettingsFile { file ->
            val previousDeviceId = LocalTeamSettingsDataSource(file).load().deviceId
            file.writeText("{ isto nao e json")

            val recovered = LocalTeamSettingsDataSource(file).load()

            // O conteúdo antigo continua recuperável em vez de ser destruído
            // pelos defaults — é o único vestígio de qual era o deviceId.
            val corruptFile = File(file.parentFile, "${file.name}.corrupt")
            assertTrue(corruptFile.isFile)
            assertEquals("{ isto nao e json", corruptFile.readText())
            assertNotEquals(previousDeviceId, recovered.deviceId)
            assertTrue(recovered.deviceId.isNotBlank())
        }
    }

    private fun withSettingsFile(block: (File) -> Unit) {
        val tempDir = createTempDirectory().toFile()
        try {
            block(File(tempDir, "team.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
