package com.usagemonitor

import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryPreferencesFactoryTest {

    @Test
    fun `put and get round-trip in the same root`() {
        val root = InMemoryPreferencesFactory().userRoot()

        root.put("chave", "valor")

        assertEquals("valor", root.get("chave", null))
        assertEquals("padrao", root.get("ausente", "padrao"))
    }

    @Test
    fun `child nodes keep their own values and names`() {
        val root = InMemoryPreferencesFactory().userRoot()

        root.node("a/b").put("k", "v")

        assertEquals("v", root.node("a/b").get("k", null))
        assertEquals(listOf("a"), root.childrenNames().toList())
        assertEquals(listOf("b"), root.node("a").childrenNames().toList())
        assertEquals(null, root.node("a").get("k", null))
    }

    @Test
    fun `removeNode drops the subtree`() {
        val root = InMemoryPreferencesFactory().userRoot()
        root.node("c/d").put("k", "v")

        root.node("c").removeNode()

        assertFalse(root.nodeExists("c"))
        assertTrue(root.childrenNames().isEmpty())
    }

    @Test
    fun `concurrent create and remove from 8 threads raises no exception`() {
        val root = InMemoryPreferencesFactory().userRoot()
        val errors = ConcurrentLinkedQueue<Throwable>()
        val threads = (0 until 8).map { thread ->
            Thread {
                try {
                    repeat(200) { round ->
                        val node = root.node("t$thread/n$round")
                        node.put("k", "v")
                        node.removeNode()
                    }
                } catch (failure: Throwable) {
                    errors.add(failure)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(emptyList<Throwable>(), errors.toList())
    }

    @Test
    fun `nothing reaches disk or registry`() {
        val fakeHome = Files.createTempDirectory("prefs-home").toFile()
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", fakeHome.absolutePath)
            val root = InMemoryPreferencesFactory().userRoot()

            root.node("x/y").put("k", "v")
            root.flush()
            root.sync()
            root.node("x").removeNode()

            assertEquals(emptyList<String>(), fakeHome.list()?.toList() ?: emptyList())
        } finally {
            System.setProperty("user.home", previousHome)
            fakeHome.deleteRecursively()
        }
    }
}
