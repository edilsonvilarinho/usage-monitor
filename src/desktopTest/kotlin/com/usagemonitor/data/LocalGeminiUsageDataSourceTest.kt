package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalGeminiUsageDataSource
import com.usagemonitor.domain.repository.GeminiUsageException
import com.usagemonitor.domain.repository.GeminiUsageFailureKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalGeminiUsageDataSourceTest {

    private val now = Instant.parse("2026-09-24T12:00:00Z")

    private fun withTemp(block: suspend (Path) -> Unit) = runTest {
        val tempDirectory = createTempDirectory()
        try {
            block(tempDirectory)
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    private fun writeChat(root: Path, project: String, name: String, content: String, modifiedAt: Instant = now): File {
        val chats = Files.createDirectories(root.resolve("$project/chats"))
        val file = chats.resolve(name).toFile()
        file.writeText(content)
        file.setLastModified(modifiedAt.toEpochMilliseconds())
        return file
    }

    private fun source(root: Path) = LocalGeminiUsageDataSource(root.toFile(), nowProvider = { now })

    @Test
    fun `reads token metadata from project chat files and drops private content`() = withTemp { root ->
        writeChat(
            root, "project-hash", "session-synthetic.jsonl",
            """{"sessionId":"synthetic-session","projectHash":"project-hash"}""" + "\n" +
                """{"id":"message-id","type":"gemini","timestamp":"2026-09-24T11:45:00Z","model":"gemini-2.5-flash","tokens":{"input":100,"output":30,"cached":20,"total":110},"content":[{"text":"private fixture prompt"}]}"""
        )

        val sessions = source(root).loadSessions()

        assertEquals("synthetic-session", sessions.single().sessionId)
        assertEquals(110L, sessions.single().messages.single().totalTokens)
        assertTrue(sessions.toString().contains("private fixture prompt").not())
    }

    /**
     * Sem `.gemini/tmp` o Gemini CLI nunca rodou aqui: card vazio, não banner de
     * erro a cada coleta. É o estado desta máquina de desenvolvimento, que tem a
     * pasta mas nenhum chat.
     */
    @Test
    fun `missing or empty roots are empty, not failures`() = withTemp { root ->
        assertTrue(source(root.resolve("missing")).loadSessions().isEmpty())
        Files.createDirectories(root.resolve("empty/usage-monitor/chats"))
        assertTrue(source(root.resolve("empty")).loadSessions().isEmpty())
    }

    @Test
    fun `only unreadable recent files are a failure and a corrupt file among valid ones is skipped`() = withTemp { root ->
        writeChat(root, "corrupt-only", "a.jsonl", "not-json")
        val unreadable = assertFailsWith<GeminiUsageException> { source(root).loadSessions() }
        assertEquals(GeminiUsageFailureKind.SESSION_HISTORY_UNREADABLE, unreadable.kind)

        writeChat(
            root, "valid", "b.jsonl",
            """{"id":"call","type":"gemini","timestamp":"2026-09-24T11:00:00Z","model":"m","tokens":{"total":9}}"""
        )
        val sessions = source(root).loadSessions()
        assertEquals(9L, sessions.single().messages.single().totalTokens)
    }

    @Test
    fun `files last written before the seven-day window are not read`() = withTemp { root ->
        writeChat(
            root, "old", "old.jsonl", "not-json",
            modifiedAt = Instant.parse("2026-09-10T00:00:00Z")
        )

        // O arquivo corrompido está fora da janela: não é lido, e por isso não
        // transforma a leitura em falha.
        assertTrue(source(root).loadSessions().isEmpty())
    }

    @Test
    fun `files outside a project chats directory are ignored`() = withTemp { root ->
        Files.createDirectories(root)
        root.resolve("stray.jsonl").toFile().writeText(
            """{"id":"call","type":"gemini","timestamp":"2026-09-24T11:00:00Z","tokens":{"total":9}}"""
        )
        writeChat(root, "project", "notes.txt", "irrelevant")

        assertTrue(source(root).loadSessions().isEmpty())
    }

    @Test
    fun `unchanged files come from cache and changed files are parsed again`() = withTemp { root ->
        val file = writeChat(
            root, "project", "s.jsonl",
            """{"id":"one","type":"gemini","timestamp":"2026-09-24T11:00:00Z","tokens":{"total":1}}"""
        )
        val dataSource = source(root)
        assertEquals(1, dataSource.loadSessions().single().messages.size)

        file.appendText("\n" + """{"id":"two","type":"gemini","timestamp":"2026-09-24T11:10:00Z","tokens":{"total":2}}""")
        file.setLastModified(now.toEpochMilliseconds() + 1_000)

        assertEquals(2, dataSource.loadSessions().single().messages.size)
    }
}
