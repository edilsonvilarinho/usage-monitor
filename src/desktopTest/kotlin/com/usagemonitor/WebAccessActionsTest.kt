package com.usagemonitor

import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.domain.entity.WebAccessSettings
import com.usagemonitor.presentation.ui.components.WebAccessUiStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebAccessActionsTest {
    private val tempDir = createTempDirectory("web-access-actions").toFile()
    private val settingsFile = File(tempDir, "web-access.json")
    private val dataSource = LocalWebAccessSettingsDataSource(settingsFile)

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `turning on for the first time creates the token and saves it`() {
        val flow = MutableStateFlow(WebAccessSettings(enabled = false, port = 8765, token = ""))
        val actions = WebAccessActions(dataSource, flow)

        actions.setEnabled(true)

        assertTrue(flow.value.enabled)
        assertEquals(64, flow.value.token.length, "32 bytes em hex")
        assertEquals(flow.value, dataSource.load())
    }

    @Test
    fun `turning off and on again keeps the token already shared`() {
        val flow = MutableStateFlow(WebAccessSettings(enabled = true, port = 8765, token = "existing-token"))
        val actions = WebAccessActions(dataSource, flow)

        actions.setEnabled(false)
        actions.setEnabled(true)

        assertEquals("existing-token", flow.value.token)
        assertTrue(dataSource.load().enabled)
    }

    @Test
    fun `only a valid port is applied and partial typing is ignored`() {
        val initial = WebAccessSettings(enabled = true, port = 8765, token = "t")
        val flow = MutableStateFlow(initial)
        val actions = WebAccessActions(dataSource, flow)

        actions.changePort("")
        actions.changePort("80a")
        actions.changePort("0")
        actions.changePort("70000")
        assertEquals(initial, flow.value)
        assertFalse(settingsFile.exists(), "texto inválido não grava nada")

        actions.changePort(" 9000 ")
        assertEquals(9000, flow.value.port)
        assertEquals(9000, dataSource.load().port)
    }

    @Test
    fun `regenerating the token replaces the shared link`() {
        val flow = MutableStateFlow(WebAccessSettings(enabled = true, port = 8765, token = "old"))
        val actions = WebAccessActions(dataSource, flow)

        actions.regenerateToken()

        assertNotEquals("old", flow.value.token)
        assertEquals(flow.value.token, dataSource.load().token)
    }

    @Test
    fun `a failed save still updates the screen`() {
        // Pasta no lugar do arquivo: a gravação falha.
        val blocked = File(tempDir, "blocked").also { directory -> directory.mkdirs() }
        File(blocked, "keep").writeText("x")
        val flow = MutableStateFlow(WebAccessSettings(enabled = false, port = 8765, token = "t"))
        val actions = WebAccessActions(LocalWebAccessSettingsDataSource(blocked), flow)

        actions.setEnabled(true)

        assertTrue(flow.value.enabled)
    }

    @Test
    fun `section model shows one link per address only while running`() {
        val settings = WebAccessSettings(enabled = true, port = 8765, token = "abc")
        val actions = WebAccessActions(dataSource, MutableStateFlow(settings))

        val running = webAccessSectionModel(settings, WebAccessStatus.Running(port = 8766, addresses = listOf("192.168.0.10", "10.0.0.2")), actions)
        assertEquals(WebAccessUiStatus.RUNNING, running.status)
        assertEquals(listOf("http://192.168.0.10:8766/?t=abc", "http://10.0.0.2:8766/?t=abc"), running.urls)
        assertEquals("8765", running.portText)
        assertTrue(running.enabled)
        assertNull(running.failureMessage)

        val failed = webAccessSectionModel(settings, WebAccessStatus.Failed("porta em uso"), actions)
        assertEquals(WebAccessUiStatus.FAILED, failed.status)
        assertEquals("porta em uso", failed.failureMessage)
        assertEquals(emptyList(), failed.urls)

        val stopped = webAccessSectionModel(settings.copy(enabled = false), WebAccessStatus.Stopped, actions)
        assertEquals(WebAccessUiStatus.STOPPED, stopped.status)
        assertEquals(emptyList(), stopped.urls)
        assertFalse(stopped.enabled)
    }

    @Test
    fun `section callbacks reach the actions`() {
        val flow = MutableStateFlow(WebAccessSettings(enabled = false, port = 8765, token = ""))
        val actions = WebAccessActions(dataSource, flow)
        val model = webAccessSectionModel(flow.value, WebAccessStatus.Stopped, actions)

        model.onEnabledChange(true)
        model.onPortChange("9100")
        val tokenBefore = flow.value.token
        model.onRegenerateToken()

        assertTrue(flow.value.enabled)
        assertEquals(9100, flow.value.port)
        assertNotEquals(tokenBefore, flow.value.token)
    }
}
