package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalGeminiUsageDataSource
import com.usagemonitor.domain.repository.GeminiUsageException
import com.usagemonitor.domain.repository.GeminiUsageFailureKind
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalGeminiUsageDataSourceTest {

    @Test
    fun `reads token metadata from nested chat files and drops private content`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val chatsDirectory = Files.createDirectories(tempDirectory.resolve("project-hash/chats"))
            Files.writeString(
                chatsDirectory.resolve("session-synthetic.jsonl"),
                """{"sessionId":"synthetic-session","projectHash":"project-hash"}""" + "\n" +
                    """{"id":"message-id","type":"gemini","timestamp":"2026-09-23T11:45:00Z","model":"gemini-2.5-flash","tokens":{"input":100,"output":30,"cached":20,"total":110},"content":[{"text":"private fixture prompt"}]}"""
            )

            val sessions = LocalGeminiUsageDataSource(chatsDirectory.parent.parent.toFile()).loadSessions()

            assertEquals(1, sessions.size)
            assertEquals("synthetic-session", sessions.single().sessionId)
            assertEquals(110L, sessions.single().messages.single().totalTokens)
            assertEquals("gemini-2.5-flash", sessions.single().messages.single().modelName)
            assertTrue(sessions.toString().contains("private fixture prompt").not())
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `empty existing session directory is distinct from missing or corrupt source`() = runTest {
        val tempDirectory = createTempDirectory()
        try {
            val emptyDirectory = Files.createDirectories(tempDirectory.resolve("empty"))
            assertTrue(LocalGeminiUsageDataSource(emptyDirectory.toFile()).loadSessions().isEmpty())

            val missingDirectory = tempDirectory.resolve("missing").toFile()
            val missingError = assertFailsWith<GeminiUsageException> {
                LocalGeminiUsageDataSource(missingDirectory).loadSessions()
            }
            assertEquals(GeminiUsageFailureKind.SESSION_DIRECTORY_MISSING, missingError.kind)

            val corruptDirectory = Files.createDirectories(tempDirectory.resolve("corrupt/project/chats"))
            Files.writeString(corruptDirectory.resolve("session-corrupt.jsonl"), "not-json")
            val unreadableError = assertFailsWith<GeminiUsageException> {
                LocalGeminiUsageDataSource(corruptDirectory.parent.parent.toFile()).loadSessions()
            }
            assertEquals(GeminiUsageFailureKind.SESSION_HISTORY_UNREADABLE, unreadableError.kind)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }
}
