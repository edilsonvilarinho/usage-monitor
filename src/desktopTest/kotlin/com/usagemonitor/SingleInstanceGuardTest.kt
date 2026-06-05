package com.usagemonitor

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SingleInstanceGuardTest {

    @Test
    fun `second acquisition on the same lock file returns null`() {
        val lockFile = Files.createTempDirectory("usage-monitor-lock").resolve("app.lock").toFile()
        val firstGuard = SingleInstanceGuard.tryAcquire(lockFile)

        try {
            assertNotNull(firstGuard)
            val secondGuard = SingleInstanceGuard.tryAcquire(lockFile)
            assertNull(secondGuard)
        } finally {
            firstGuard?.close()
        }
    }

    @Test
    fun `lock can be acquired again after the previous guard is closed`() {
        val lockFile = Files.createTempDirectory("usage-monitor-lock").resolve("app.lock").toFile()
        val firstGuard = SingleInstanceGuard.tryAcquire(lockFile)
        assertNotNull(firstGuard)

        firstGuard.close()

        val secondGuard = SingleInstanceGuard.tryAcquire(lockFile)
        try {
            assertNotNull(secondGuard)
        } finally {
            secondGuard?.close()
        }
    }
}
