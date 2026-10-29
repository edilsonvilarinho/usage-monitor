package com.usagemonitor.terminal

import org.junit.AssumptionViolatedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SymbolicLinksTest {

    private val link = Path.of("usage-monitor-link")
    private val target = Path.of("usage-monitor-real")

    @Test
    fun `a Windows without the symbolic link privilege skips the test instead of failing it`() {
        assertFailsWith<AssumptionViolatedException> {
            createSymbolicLinkOrSkip(link, target) { _, _ ->
                throw FileSystemException(link.toString(), null, "O cliente não tem o privilégio necessário")
            }
        }
    }

    @Test
    fun `a file system without symbolic links skips the test instead of failing it`() {
        assertFailsWith<AssumptionViolatedException> {
            createSymbolicLinkOrSkip(link, target) { _, _ -> throw UnsupportedOperationException("symbolic links") }
        }
    }

    @Test
    fun `an existing path is a defect of the test and still fails it`() {
        assertFailsWith<FileAlreadyExistsException> {
            createSymbolicLinkOrSkip(link, target) { _, _ -> throw FileAlreadyExistsException(link.toString()) }
        }
    }

    @Test
    fun `with the privilege the link is created from the link to the target`() {
        val created = mutableListOf<Pair<Path, Path>>()
        createSymbolicLinkOrSkip(link, target) { from, to -> created += from to to }
        assertEquals(listOf(link to target), created)
    }
}
