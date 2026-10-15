package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.domain.entity.TelegramPendingRestart
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * O reinício pedido pelo bot (#417) atravessa o próprio reinício pelo
 * `telegram.json`: o app que fecha grava, o que abre lê.
 */
class LocalTelegramSettingsDataSourceTest {

    @Test
    fun `o reinicio pendente volta igual depois de gravado`() {
        withSettingsFile { file ->
            val dataSource = LocalTelegramSettingsDataSource(file)
            val pending = TelegramPendingRestart(targetVersion = "41.11.0-beta.3", fromVersion = "41.11.0-beta.2")
            val original = dataSource.load()

            dataSource.save(original.copy(preferences = original.preferences.copy(pendingRestart = pending)))

            assertEquals(pending, LocalTelegramSettingsDataSource(file).load().preferences.pendingRestart)
        }
    }

    @Test
    fun `arquivo anterior ao campo continua valendo sem reinicio pendente`() {
        withSettingsFile { file ->
            file.writeText(
                """{"enabled":true,"botToken":"123:abc","preferences":{"allowRemoteUpdate":true,""" +
                    """"announcedUpdateVersion":"41.11.0-beta.2"}}"""
            )

            val loaded = LocalTelegramSettingsDataSource(file).load()

            assertEquals(true, loaded.preferences.allowRemoteUpdate)
            assertEquals("41.11.0-beta.2", loaded.preferences.announcedUpdateVersion)
            assertNull(loaded.preferences.pendingRestart)
        }
    }

    private fun withSettingsFile(block: (File) -> Unit) {
        val tempDir = createTempDirectory().toFile()
        try {
            block(File(tempDir, "telegram.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
