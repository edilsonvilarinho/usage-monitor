package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.repository.CodexCliSessionRepository
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.presentation.ui.CodexCliSessionsScreen
import com.usagemonitor.presentation.ui.ExportLabels
import com.usagemonitor.presentation.ui.codexApplicationLabel
import com.usagemonitor.presentation.ui.shortCodexId
import com.usagemonitor.presentation.viewmodel.CliSessionsView
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.UsageExportWriter
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A janela de sessões do Codex com o view model real sobre um repositório falso.
 * `Dispatchers.Unconfined` faz cada leitura terminar dentro do clique que a pede,
 * então o estado que a tela desenha é o que o view model acabou de publicar.
 */
@OptIn(ExperimentalTestApi::class)
class CodexCliSessionsScreenTest {

    @Test
    fun `list shows each session with project, tokens, model and application`() =
        runDesktopComposeUiTest(width = 1_200, height = 900) {
            val viewModel = viewModel(FakeCodexRepository())
            setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

            onNodeWithText("Fonte local: Codex").assertExists()
            onNodeWithText("Atualizado", substring = true).assertExists()
            onNodeWithText(shortCodexId(LONG_SESSION_ID)).assertExists()
            onNodeWithText("usage-monitor").assertExists()
            onNodeWithText("3 turno(s)").assertExists()
            onNodeWithText("gpt-6.1-sol").assertExists()
            onNodeWithText("Codex Desktop").assertExists()
            onNodeWithText("Codex CLI").assertExists()
            // Sem modelo nem projeto, a célula diz "—" em vez de ficar vazia.
            onAllNodesWithText("—").assertCountEquals(2)
            viewModel.onDestroy()
        }

    @Test
    fun `english labels follow the app language`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val viewModel = viewModel(FakeCodexRepository())
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.EN) } }

        onNodeWithText("Local source: Codex").assertExists()
        onNodeWithText("Updated", substring = true).assertExists()
        onNodeWithText("3 turn(s)").assertExists()
        onNodeWithText("All").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `summary tab groups sessions by project and by primary model`() =
        runDesktopComposeUiTest(width = 1_200, height = 1_400) {
            val viewModel = viewModel(FakeCodexRepository())
            setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

            onNodeWithText("Resumo").performClick()
            waitForIdle()

            onNodeWithText("Por modelo").assertExists()
            onNodeWithText("Modelo principal de cada sessão").assertExists()
            assertEquals(
                CliSessionsView.BREAKDOWN,
                assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).view
            )
            viewModel.onDestroy()
        }

    @Test
    fun `range segment reloads with the chosen cutoff`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val repository = FakeCodexRepository()
        val viewModel = viewModel(repository)
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText("Tudo").performClick()
        waitForIdle()

        assertNull(repository.lastSince)
        assertEquals(
            CodexCliSessionRange.ALL,
            assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).range
        )
        viewModel.onDestroy()
    }

    @Test
    fun `clicking a session opens its detail and hides the list toolbar`() =
        runDesktopComposeUiTest(width = 1_200, height = 1_400) {
            val viewModel = viewModel(FakeCodexRepository())
            setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

            onNodeWithText(shortCodexId(LONG_SESSION_ID)).performClick()
            waitForIdle()

            val state = assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value)
            assertEquals(LONG_SESSION_ID, state.detail?.summary?.sessionId)
            // Detalhe aberto: barra de status e abas da lista saem da tela.
            onAllNodesWithText("Fonte local: Codex").assertCountEquals(0)
            onAllNodesWithText("CSV").assertCountEquals(0)
            viewModel.onDestroy()
        }

    @Test
    fun `export banner shows the saved path`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val viewModel = viewModel(FakeCodexRepository(), writer = { "C:/exports/codex.csv" })
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText("CSV").performClick()
        waitForIdle()

        onNodeWithText("C:/exports/codex.csv").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `export failure shows the error message`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val viewModel = viewModel(FakeCodexRepository(), writer = { error("disco cheio") })
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText("JSON").performClick()
        waitForIdle()

        onNodeWithText("disco cheio").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `pdf export goes through the same writer`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        var requests = 0
        val viewModel = viewModel(FakeCodexRepository(), writer = { requests += 1; "C:/exports/codex.pdf" })
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText(ExportLabels.exportPdf(AppLanguage.PT)).performClick()
        waitForIdle()

        assertEquals(1, requests)
        onNodeWithText("C:/exports/codex.pdf").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `detail failure keeps the list and explains why`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val viewModel = viewModel(FakeCodexRepository(detailFailure = IllegalStateException("rollout sumiu")))
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText(shortCodexId(LONG_SESSION_ID)).performClick()
        waitForIdle()

        assertNull(assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).detail)
        onNodeWithText("rollout sumiu").assertExists()
        onNodeWithText("usage-monitor").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `partial index keeps the list and shows a warning`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val viewModel = viewModel(FakeCodexRepository(indexFailure = IllegalStateException("rollout ilegível")))
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText("Índice parcial").assertExists()
        onNodeWithText("rollout ilegível").assertExists()
        onNodeWithText("usage-monitor").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `empty source explains that it is local`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val viewModel = viewModel(FakeCodexRepository(sessions = emptyList()))
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText("Nenhuma sessão Codex encontrada.").assertExists()
        onNodeWithText("A origem é local e não depende de login.").assertExists()
        viewModel.onDestroy()
    }

    @Test
    fun `read failure offers retry that reloads`() = runDesktopComposeUiTest(width = 1_200, height = 900) {
        val repository = FakeCodexRepository(readFailure = IllegalStateException("sem permissão"))
        val viewModel = viewModel(repository)
        setContent { ScreenTestTheme { CodexCliSessionsScreen(viewModel, AppLanguage.PT) } }

        onNodeWithText("sem permissão").assertExists()
        val before = repository.readCalls
        onNodeWithText("Tentar novamente").performClick()
        waitForIdle()

        assertEquals(before + 1, repository.readCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `application label normalizes the originator`() {
        val base = summary(LONG_SESSION_ID)
        assertEquals("Codex Desktop", codexApplicationLabel(base.copy(originator = " Codex Desktop ")))
        assertEquals("Codex CLI", codexApplicationLabel(base.copy(originator = "codex-tui")))
        assertEquals("Codex CLI", codexApplicationLabel(base.copy(originator = "codex tui")))
        assertEquals("Codex CLI", codexApplicationLabel(base.copy(originator = null)))
        assertEquals("Codex CLI", codexApplicationLabel(base.copy(originator = "  ")))
        assertEquals("vscode", codexApplicationLabel(base.copy(originator = " vscode ")))
        assertEquals("abc", shortCodexId("abc"))
        assertEquals("12345678…", shortCodexId("1234567890"))
    }

    private fun viewModel(
        repository: FakeCodexRepository,
        writer: UsageExportWriter? = null
    ): CodexCliSessionsViewModel = CodexCliSessionsViewModel(
        getSessions = GetCodexCliSessionsUseCase(repository),
        getDetail = GetCodexCliSessionDetailUseCase(repository),
        exportWriter = writer,
        exportEncoder = DefaultUsageExportEncoder,
        dispatcher = Dispatchers.Unconfined
    )
}

private const val LONG_SESSION_ID = "0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"
private const val SHORT_SESSION_ID = "s2"

private fun summary(sessionId: String): CodexCliSessionSummary = CodexCliSessionSummary(
    sessionId = sessionId,
    filePath = "rollout-$sessionId.jsonl",
    firstTs = Instant.parse("2026-10-27T12:00:00Z"),
    lastTs = Instant.parse("2026-10-27T12:30:00Z"),
    turnCount = 1,
    responseCount = 1
)

private class FakeCodexRepository(
    private val sessions: List<CodexCliSessionSummary> = listOf(
        summary(LONG_SESSION_ID).copy(
            cwd = "C:\\workspace\\usage-monitor",
            primaryModel = "gpt-6.1-sol",
            originator = "Codex Desktop",
            cliVersion = "0.50.0",
            turnCount = 3,
            responseCount = 4,
            inputTokens = 10_000L,
            cachedInputTokens = 8_000L,
            totalTokens = 12_500L
        ),
        summary(SHORT_SESSION_ID).copy(originator = "codex-tui")
    ),
    private val indexFailure: Throwable? = null,
    private val readFailure: Throwable? = null,
    private val detailFailure: Throwable? = null
) : CodexCliSessionRepository {
    var lastSince: Long? = Long.MIN_VALUE
    var readCalls = 0

    override suspend fun syncIndex(): Result<CodexCliSessionIndexReport> =
        indexFailure?.let { failure -> Result.failure(failure) }
            ?: Result.success(CodexCliSessionIndexReport(scannedFiles = sessions.size))

    override suspend fun getSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> {
        readCalls += 1
        lastSince = sinceEpochMillis
        return readFailure?.let { failure -> Result.failure(failure) } ?: Result.success(sessions)
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?> {
        if (detailFailure != null) return Result.failure(detailFailure)
        val session = sessions.firstOrNull { it.sessionId == sessionId } ?: return Result.success(null)
        val turn = CodexCliSessionTurn(
            sessionId = sessionId,
            turnId = "turn-1",
            responseId = "resp-1",
            seq = 0,
            ts = session.lastTs
        )
        return Result.success(CodexCliSessionDetail(session, listOf(turn)))
    }
}
