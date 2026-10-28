package com.usagemonitor.terminal

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** O caminho que o terminal mostra: a pasta pessoal vira `~`, o resto sai como veio. */
class TerminalPathsTest {

    private val root = Files.createTempDirectory("terminal-paths").toFile()
    private val home = File(root, "home").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a path inside the home folder starts with a tilde`() {
        val dataRoot = File(home, ".usage-monitor")

        assertEquals("~" + File.separator + ".usage-monitor", homeRelativePath(dataRoot.path, home))
    }

    /** Canônicos dos dois lados: o caminho com `..` que cai dentro da pasta pessoal também ganha o `~`. */
    @Test
    fun `a non canonical path that lands inside the home folder still gets the tilde`() {
        val roundabout = File(File(home, "a"), ".." + File.separator + "b.log")

        assertEquals("~" + File.separator + "b.log", homeRelativePath(roundabout.path, home))
    }

    @Test
    fun `the home folder itself and a path outside it are shown as given`() {
        val outside = File(root, "srv" + File.separator + "data").path

        assertEquals(home.path, homeRelativePath(home.path, home))
        assertEquals(outside, homeRelativePath(outside, home))
    }

    /** Pasta pessoal que é prefixo de texto de outra (`home` e `home2`) não troca nada. */
    @Test
    fun `a sibling folder that only shares the name prefix is not inside home`() {
        val sibling = File(root, "home2" + File.separator + "x").path

        assertEquals(sibling, homeRelativePath(sibling, home))
    }
}
