package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** O recibo que o instalador (ou o próprio app, quando o `schedule` falha) grava depois de atualizar. */
class UpdateReceiptReaderTest {

    private val dir: File = Files.createTempDirectory("usage-monitor-receipt").toFile()
    private val file = File(dir, "update-receipt.properties")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun `a complete success receipt is read with the previous version and no reason`() {
        file.writeText("version=41.12.0\nstatus=SUCCESS\npreviousVersion= 41.11.2 \nreason=\n")

        assertEquals(AppUpdateReceipt("41.12.0", "41.11.2", AppUpdateReceiptStatus.SUCCESS, null), readUpdateReceipt(file))
    }

    @Test
    fun `an unknown status is a failure, the safe side`() {
        file.writeText("version=41.12.0\nstatus=succ\nreason=locked\n")

        assertEquals(AppUpdateReceipt("41.12.0", null, AppUpdateReceiptStatus.FAILED, "locked"), readUpdateReceipt(file))
    }

    @Test
    fun `no file, a folder in its place or a receipt without version is no receipt`() {
        assertNull(readUpdateReceipt(file))

        file.mkdirs()
        assertNull(readUpdateReceipt(file))
        file.delete()

        file.writeText("version=  \nstatus=success\n")
        assertNull(readUpdateReceipt(file))
    }

    @Test
    fun `the schedule failure receipt is written in the installer format and read back`() {
        val nested = File(dir, "nova/pasta/update-receipt.properties")

        writeUpdateScheduleFailureReceipt("41.12.0", "schedule-failed", nested)

        // Sem `previousVersion`: quem a conhece é o instalador, e ele não rodou.
        assertEquals(AppUpdateReceipt("41.12.0", null, AppUpdateReceiptStatus.FAILED, "schedule-failed"), readUpdateReceipt(nested))
    }

    @Test
    fun `a failure receipt that cannot be written is dropped without throwing`() {
        // Um arquivo no lugar da pasta: `mkdirs` falha e a escrita também.
        val blocker = File(dir, "bloqueio").apply { writeText("x") }

        writeUpdateScheduleFailureReceipt("41.12.0", "schedule-failed", File(blocker, "update-receipt.properties"))

        assertEquals("x", blocker.readText())
    }
}
