package com.usagemonitor

import com.usagemonitor.domain.entity.WebAccessSettings
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalWebAccessServiceTest {

    private var snapshot: String? = """{"generated_at":"2026-10-06T14:42:00Z","accounts":[]}"""
    private val service = LocalWebAccessService(
        snapshotJson = { snapshot },
        pageHtml = { "<!doctype html><title>HUD</title>" },
        bindAddress = InetAddress.getLoopbackAddress(),
        addressesProvider = { listOf("192.168.0.14") }
    )

    @AfterTest
    fun tearDown() = service.stop()

    private fun start(): Int {
        // Porta 0: o sistema escolhe uma livre, e o estado publica a porta real.
        service.apply(WebAccessSettings(enabled = true, port = 0, token = "segredo-de-teste"))
        return assertIs<WebAccessStatus.Running>(service.status.value).port
    }

    private fun call(port: Int, path: String, method: String = "GET", bearer: String? = null): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        val code = connection.responseCode
        val body = (if (code < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
        connection.disconnect()
        return code to body
    }

    @Test
    fun `every route requires the token`() {
        val port = start()

        assertEquals(401, call(port, "/").first)
        assertEquals(401, call(port, "/api/snapshot?t=errado").first)
        assertEquals(200, call(port, "/?t=segredo-de-teste").first)
    }

    @Test
    fun `snapshot is served as json with bearer token and no cache`() {
        val port = start()

        val (code, body) = call(port, "/api/snapshot", bearer = "segredo-de-teste")

        assertEquals(200, code)
        assertTrue(body.contains("\"generated_at\""))
    }

    @Test
    fun `no reading yet answers 503 and writes are refused`() {
        val port = start()
        snapshot = null

        assertEquals(503, call(port, "/api/snapshot?t=segredo-de-teste").first)
        assertEquals(405, call(port, "/api/snapshot?t=segredo-de-teste", method = "POST").first)
        assertEquals(404, call(port, "/admin?t=segredo-de-teste").first)
    }

    @Test
    fun `disabled settings or blank token keep the server down`() {
        service.apply(WebAccessSettings(enabled = true, port = 0, token = ""))
        assertIs<WebAccessStatus.Stopped>(service.status.value)

        start()
        service.apply(WebAccessSettings(enabled = false, port = 0, token = "segredo-de-teste"))
        assertIs<WebAccessStatus.Stopped>(service.status.value)
    }
}
