package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.BugReportIssueOpener
import com.usagemonitor.NoWindowScreenshotCapturer
import com.usagemonitor.WindowScreenshotCapturer
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.BugReportMachineInfo
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.usecase.GenerateBugReportUseCase
import com.usagemonitor.presentation.ui.BugReportHost
import com.usagemonitor.presentation.ui.components.BUG_REPORT_DESCRIPTION_TEST_TAG
import com.usagemonitor.presentation.ui.components.BUG_REPORT_STATUS_TEST_TAG
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.BugReportSaveRequest
import com.usagemonitor.presentation.viewmodel.BugReportSaveResult
import com.usagemonitor.presentation.viewmodel.BugReportWriter
import kotlinx.datetime.Instant
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BugReportHostTest {

    @Test
    fun `save failure is recorded in the next issue and successful opening dismisses the modal`() =
        runDesktopComposeUiTest {
            val recorder = CapturingBreadcrumbRecorder()
            val opened = mutableListOf<URI>()
            var dismissCount = 0
            val writer = failingWriter(
                IOException("Falha ao gravar C:\\Users\\Alice\\report.json; api_key=very-secret")
            )
            val opener = BugReportIssueOpener(desktopBrowser = { uri -> opened += uri; true })

            showHost(recorder, writer, opener, onDismiss = { dismissCount += 1 })
            describeBug()
            onNodeWithText("Salvar arquivo").performClick()
            waitUntil(timeoutMillis = 5_000) { recorder.events.isNotEmpty() }

            assertEquals(BreadcrumbCategory.ERROR, recorder.events.last().category)
            assertTrue(recorder.events.last().message.contains("IOException"))
            assertTrue(recorder.events.last().message.contains("<caminho>/report.json"))
            assertFalse(recorder.events.last().message.contains("Alice"))
            assertFalse(recorder.events.last().message.contains("very-secret"))

            onNodeWithText("Abrir issue no GitHub").performClick()
            waitUntil(timeoutMillis = 5_000) { dismissCount == 1 }
            assertEquals(1, opened.size)
            val body = issueBody(opened.single())
            assertTrue(body.contains("salvar relatório de bug falhou"), body)
            assertTrue(body.contains("IOException"), body)
            assertFalse(body.contains("Alice"), body)
            assertFalse(body.contains("very-secret"), body)
            onNodeWithText("Reportar um bug").assertDoesNotExist()
        }

    @Test
    fun `failed issue opening keeps the modal open and retry closes it only after success`() =
        runDesktopComposeUiTest {
            val recorder = CapturingBreadcrumbRecorder()
            val opened = mutableListOf<URI>()
            var browserAccepts = false
            var dismissCount = 0
            val opener = BugReportIssueOpener(
                processLauncher = { _, _ -> throw IOException("browser launcher failed") },
                desktopBrowser = { uri ->
                    opened += uri
                    browserAccepts
                },
                osNameProvider = { "Windows" }
            )

            showHost(recorder, successfulWriter(), opener, onDismiss = { dismissCount += 1 })
            describeBug()
            onNodeWithText("Abrir issue no GitHub").performClick()
            waitUntil(timeoutMillis = 5_000) { recorder.events.isNotEmpty() }

            assertEquals(0, dismissCount)
            onNodeWithText("Reportar um bug").assertIsDisplayed()
            onNodeWithTag(BUG_REPORT_STATUS_TEST_TAG).assertIsDisplayed()
            assertTrue(recorder.events.last().message.contains("abrir issue de relatório de bug falhou"))

            browserAccepts = true
            onNodeWithText("Abrir issue no GitHub").performClick()
            waitUntil(timeoutMillis = 5_000) { dismissCount == 1 }

            assertEquals(2, opened.size)
            assertTrue(issueBody(opened.last()).contains("browser launcher failed"))
            onNodeWithText("Reportar um bug").assertDoesNotExist()
        }

    @Test
    fun `generation failure is recorded and keeps the dialog available`() = runDesktopComposeUiTest {
        val recorder = CapturingBreadcrumbRecorder()
        val generator = generator(recorder) {
            throw IllegalStateException("HTTP 500: {\"token\":\"must-not-leak\"}")
        }
        var dismissed = false

        showHost(recorder, successfulWriter(), BugReportIssueOpener(desktopBrowser = { true }),
            onDismiss = { dismissed = true }, useCase = generator)
        describeBug()
        onNodeWithText("Abrir issue no GitHub").performClick()
        waitUntil(timeoutMillis = 5_000) { recorder.events.isNotEmpty() }

        assertFalse(dismissed)
        onNodeWithText("Reportar um bug").assertIsDisplayed()
        assertTrue(recorder.events.single().message.contains("IllegalStateException"))
        assertTrue(recorder.events.single().message.contains("corpo omitido"))
        assertFalse(recorder.events.single().message.contains("must-not-leak"))
    }

    @Test
    fun `canceling the save dialog does not create an error event`() = runDesktopComposeUiTest {
        val recorder = CapturingBreadcrumbRecorder()

        showHost(
            recorder,
            writer = object : BugReportWriter {
                override suspend fun write(request: BugReportSaveRequest): BugReportSaveResult? = null
            },
            issueOpener = BugReportIssueOpener(desktopBrowser = { true })
        )
        describeBug()
        onNodeWithText("Salvar arquivo").performClick()
        waitForIdle()

        assertTrue(recorder.events.isEmpty())
        onNodeWithTag(BUG_REPORT_STATUS_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `screenshot capture failure is recorded without closing the dialog`() = runDesktopComposeUiTest {
        val recorder = CapturingBreadcrumbRecorder()
        var dismissed = false

        showHost(
            recorder = recorder,
            writer = successfulWriter(),
            issueOpener = BugReportIssueOpener(desktopBrowser = { true }),
            screenshots = WindowScreenshotCapturer { throw IOException("screenshot failed") },
            onDismiss = { dismissed = true }
        )
        waitUntil(timeoutMillis = 5_000) { recorder.events.isNotEmpty() }

        assertFalse(dismissed)
        assertEquals("capturar tela para relatório de bug falhou: IOException: screenshot failed", recorder.events.single().message)
        onNodeWithTag(BUG_REPORT_STATUS_TEST_TAG).assertIsDisplayed()
        onNodeWithText("Reportar um bug").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showHost(
        recorder: CapturingBreadcrumbRecorder,
        writer: BugReportWriter,
        issueOpener: BugReportIssueOpener,
        screenshots: WindowScreenshotCapturer = NoWindowScreenshotCapturer,
        onDismiss: () -> Unit = {},
        useCase: GenerateBugReportUseCase = generator(recorder)
    ) {
        setContent {
            AppTheme(isDark = true) {
                val hostVisible = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(true)
                }
                if (hostVisible.value) {
                    BugReportHost(
                        generateBugReport = useCase,
                        writer = writer,
                        issueOpener = issueOpener,
                        screenshots = screenshots,
                        breadcrumbs = recorder,
                        language = AppLanguage.PT,
                        onDismiss = {
                            onDismiss()
                            hostVisible.value = false
                        }
                    )
                }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.describeBug() {
        onNodeWithTag(BUG_REPORT_DESCRIPTION_TEST_TAG).performTextInput("Falha ao salvar relatório")
    }

    private fun generator(
        recorder: CapturingBreadcrumbRecorder,
        machineInfo: () -> BugReportMachineInfo = { testMachineInfo() }
    ): GenerateBugReportUseCase {
        return GenerateBugReportUseCase(
            breadcrumbs = recorder,
            machineInfo = machineInfo,
            breadcrumbLimit = 200
        )
    }

    private fun successfulWriter(): BugReportWriter = object : BugReportWriter {
        override suspend fun write(request: BugReportSaveRequest): BugReportSaveResult =
            BugReportSaveResult("report.json")
    }

    private fun failingWriter(error: Throwable): BugReportWriter = object : BugReportWriter {
        override suspend fun write(request: BugReportSaveRequest): BugReportSaveResult? {
            throw error
        }
    }

    private fun testMachineInfo() = BugReportMachineInfo(
        osName = "Windows 11",
        osVersion = "10.0",
        osArch = "amd64",
        javaVersion = "17",
        appVersion = "test",
        language = AppLanguage.PT,
        uiScalePercent = 100
    )

    private fun issueBody(uri: URI): String {
        val encodedBody = uri.rawQuery.orEmpty().substringAfter("&body=")
        return URLDecoder.decode(encodedBody, StandardCharsets.UTF_8)
    }

    private class CapturingBreadcrumbRecorder : BreadcrumbRecorder {
        val events = mutableListOf<Breadcrumb>()

        override fun record(category: BreadcrumbCategory, message: String) {
            events += Breadcrumb.of(
                at = Instant.fromEpochMilliseconds(events.size.toLong()),
                category = category,
                message = message
            )
        }

        override fun read(limit: Int): List<Breadcrumb> = events.takeLast(limit)
    }
}
