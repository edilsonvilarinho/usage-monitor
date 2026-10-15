package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataRootResolutionTest {

    @Test
    fun `no option keeps the default root`() {
        withTempDir { dir ->
            assertEquals(DataRootResolution.Default, resolveDataRoot(null, dir, defaultRoot = { File(dir, "padrao") }))
        }
    }

    @Test
    fun `a relative folder resolves against the working directory and is created`() {
        withTempDir { dir ->
            val resolution = resolveDataRoot("perfil/../perfil", dir, defaultRoot = { File(dir, "padrao") })

            val expected = File(dir, "perfil").canonicalFile
            assertEquals(DataRootResolution.Custom(expected), resolution)
            assertTrue(expected.isDirectory)
        }
    }

    @Test
    fun `an absolute folder is used as is`() {
        withTempDir { dir ->
            val absolute = File(dir, "absoluta")

            val resolution = resolveDataRoot(absolute.path, File("outro-lugar"), defaultRoot = { File(dir, "padrao") })

            assertEquals(DataRootResolution.Custom(absolute.canonicalFile), resolution)
        }
    }

    @Test
    fun `the default folder named explicitly is the default root`() {
        // Senão a mesma pasta abriria um segundo nó de preferências e perderia as escolhas gravadas.
        withTempDir { dir ->
            val default = File(dir, "padrao").apply { mkdirs() }

            assertEquals(DataRootResolution.Default, resolveDataRoot(default.path, dir, defaultRoot = { default }))
        }
    }

    @Test
    fun `a file in place of the folder is an error`() {
        withTempDir { dir ->
            val file = File(dir, "arquivo").apply { writeText("x") }

            val resolution = resolveDataRoot(file.path, dir, defaultRoot = { File(dir, "padrao") })

            assertEquals(
                DataRootResolution.Error("Error: '${file.canonicalPath}' is a file. --user-data-dir needs a folder."),
                resolution
            )
        }
    }

    @Test
    fun `a folder that cannot be created is an error`() {
        withTempDir { dir ->
            // Pai é arquivo: `mkdirs` falha em qualquer sistema.
            val blocker = File(dir, "bloqueio").apply { writeText("x") }
            val target = File(blocker, "filho")

            val resolution = resolveDataRoot(target.path, dir, defaultRoot = { File(dir, "padrao") })

            assertEquals(
                DataRootResolution.Error("Error: could not create '${target.canonicalPath}' for --user-data-dir."),
                resolution
            )
        }
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = kotlin.io.path.createTempDirectory("data-root-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
