package com.usagemonitor

import com.usagemonitor.data.AppDataDirectory
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

internal class SingleInstanceGuard private constructor(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {

    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun tryAcquire(lockFile: File = defaultLockFile()): SingleInstanceGuard? {
            lockFile.parentFile?.mkdirs()

            val channel = FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )

            return try {
                val lock = channel.tryLock() ?: run {
                    channel.close()
                    return null
                }
                SingleInstanceGuard(channel, lock)
            } catch (_: OverlappingFileLockException) {
                channel.close()
                null
            } catch (error: Throwable) {
                channel.close()
                throw error
            }
        }

        private fun defaultLockFile(): File {
            return AppDataDirectory.file("app.lock")
        }
    }
}
