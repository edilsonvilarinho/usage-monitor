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

        /**
         * A tomada do arranque: até [attempts] tentativas, com [pauseMillis] entre elas.
         *
         * O modo terminal (#439) descobre se o app está aberto tomando e soltando esta
         * trava ([isHeld]). Um app que abrisse exatamente nesse instante a acharia tomada
         * e sairia como segunda instância, sem janela. A segunda tentativa cobre essa
         * janela de microssegundos; a segunda instância de verdade só espera 100 ms a mais.
         */
        fun acquireAtStartup(
            lockFile: File = defaultLockFile(),
            attempts: Int = 3,
            pauseMillis: Long = 50L,
            pause: (Long) -> Unit = Thread::sleep
        ): SingleInstanceGuard? {
            repeat(attempts) { attempt ->
                tryAcquire(lockFile)?.let { guard -> return guard }
                if (attempt < attempts - 1) {
                    pause(pauseMillis)
                }
            }
            return null
        }

        /**
         * Há um coletor vivo nesta raiz de dados? Toma e solta a trava na hora; quem a
         * segura é o app aberto. Nunca a mantém: o modo terminal só lê.
         */
        fun isHeld(lockFile: File = defaultLockFile()): Boolean {
            val guard = tryAcquire(lockFile) ?: return true
            guard.close()
            return false
        }

        private fun defaultLockFile(): File {
            return AppDataDirectory.file("app.lock")
        }
    }
}
