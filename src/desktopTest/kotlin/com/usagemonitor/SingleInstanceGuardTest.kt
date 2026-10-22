package com.usagemonitor

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `startup acquisition retries while another process holds the lock for a moment`() {
        val lockFile = Files.createTempDirectory("usage-monitor-lock").resolve("app.lock").toFile()
        var probe = SingleInstanceGuard.tryAcquire(lockFile)
        val pauses = mutableListOf<Long>()
        // A sonda do modo terminal solta a trava durante a primeira pausa.
        val guard = SingleInstanceGuard.acquireAtStartup(lockFile, pause = { millis ->
            pauses += millis
            probe?.close()
            probe = null
        })
        try {
            assertNotNull(guard)
            assertEquals(listOf(50L), pauses)
        } finally {
            guard?.close()
        }
    }

    @Test
    fun `startup acquisition gives up after the attempts when the lock stays held`() {
        val lockFile = Files.createTempDirectory("usage-monitor-lock").resolve("app.lock").toFile()
        val running = SingleInstanceGuard.tryAcquire(lockFile)
        val pauses = mutableListOf<Long>()
        try {
            assertNull(SingleInstanceGuard.acquireAtStartup(lockFile, pause = { millis -> pauses += millis }))
            assertEquals(2, pauses.size)
        } finally {
            running?.close()
        }
    }

    @Test
    fun `isHeld reports the running app and releases the lock after probing`() {
        val lockFile = Files.createTempDirectory("usage-monitor-lock").resolve("app.lock").toFile()
        assertFalse(SingleInstanceGuard.isHeld(lockFile))
        val running = SingleInstanceGuard.tryAcquire(lockFile)
        try {
            assertTrue(SingleInstanceGuard.isHeld(lockFile))
        } finally {
            running?.close()
        }
        val afterProbe = SingleInstanceGuard.tryAcquire(lockFile)
        try {
            assertNotNull(afterProbe)
        } finally {
            afterProbe?.close()
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
