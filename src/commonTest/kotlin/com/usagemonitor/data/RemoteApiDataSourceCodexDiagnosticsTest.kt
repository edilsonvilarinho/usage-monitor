package com.usagemonitor.data

import com.usagemonitor.data.datasource.CodexDiagnosticsFailureEvent
import com.usagemonitor.data.datasource.CodexDiagnosticsRecorder
import com.usagemonitor.data.datasource.CodexDiagnosticsSuccessEvent
import com.usagemonitor.data.datasource.CodexSession
import com.usagemonitor.data.datasource.RemoteApiDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteApiDataSourceCodexDiagnosticsTest {

    @Test
    fun `records sanitized success payload for Codex`() = runTest {
        val recorder = RecordingCodexDiagnosticsRecorder()
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "plan_type": "plus",
                          "rate_limit": {
                            "allowed": true,
                            "limit_reached": false,
                            "primary_window": {
                              "used_percent": 1,
                              "limit_window_seconds": 18000,
                              "reset_after_seconds": 17940,
                              "reset_at": 1780610643
                            },
                            "secondary_window": {
                              "used_percent": 12,
                              "limit_window_seconds": 604800,
                              "reset_after_seconds": 580000,
                              "reset_at": 1781139417
                            }
                          }
                        }
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            codexDiagnosticsRecorder = recorder
        )

        val response = dataSource.fetchCodexUsage(sampleSession())

        assertEquals("plus", response.planType)
        assertEquals(1, recorder.successEvents.size)
        assertTrue(recorder.failureEvents.isEmpty())
        assertEquals(1L, recorder.successEvents.single().primaryUsedPercent)
        assertEquals(12L, recorder.successEvents.single().secondaryUsedPercent)
    }

    @Test
    fun `records sanitized http failure for Codex`() = runTest {
        val recorder = RecordingCodexDiagnosticsRecorder()
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """
                        <html>
                          Enable JavaScript and cookies to continue
                          Bearer secret-token
                          cap_sid=super-secret-cookie
                        </html>
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            },
            codexDiagnosticsRecorder = recorder
        )

        assertFailsWith<IllegalStateException> {
            dataSource.fetchCodexUsage(sampleSession())
        }

        assertTrue(recorder.successEvents.isEmpty())
        assertEquals(1, recorder.failureEvents.size)
        val failure = recorder.failureEvents.single()
        assertEquals("http_error", failure.failureKind)
        assertEquals(403, failure.statusCode)
        assertEquals("Cloudflare challenge page returned by chatgpt.com", failure.message)
    }

    @Test
    fun `records parse failure without leaking credentials`() = runTest {
        val recorder = RecordingCodexDiagnosticsRecorder()
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel("""{"plan_type":"plus","rate_limit":"Bearer broken-token cap_sid=abc"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            codexDiagnosticsRecorder = recorder
        )

        assertFailsWith<Throwable> {
            dataSource.fetchCodexUsage(sampleSession())
        }

        assertTrue(recorder.successEvents.isEmpty())
        assertEquals(1, recorder.failureEvents.size)
        val failure = recorder.failureEvents.single()
        assertEquals("parse_error", failure.failureKind)
        assertTrue("Bearer broken-token" !in failure.message)
        assertTrue("cap_sid=abc" !in failure.message)
    }

    private fun jsonHttpClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun sampleSession(): CodexSession {
        return CodexSession(
            accessToken = "test-access-token",
            capSid = "test-cap-sid"
        )
    }

    private class RecordingCodexDiagnosticsRecorder : CodexDiagnosticsRecorder {
        val successEvents = mutableListOf<CodexDiagnosticsSuccessEvent>()
        val failureEvents = mutableListOf<CodexDiagnosticsFailureEvent>()

        override fun recordSuccess(event: CodexDiagnosticsSuccessEvent) {
            successEvents += event
        }

        override fun recordFailure(event: CodexDiagnosticsFailureEvent) {
            failureEvents += event
        }
    }
}
