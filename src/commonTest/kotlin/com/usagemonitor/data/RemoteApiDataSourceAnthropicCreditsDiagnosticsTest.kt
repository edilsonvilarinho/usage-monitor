package com.usagemonitor.data

import com.usagemonitor.data.datasource.AnthropicCreditsDiagnosticsEvent
import com.usagemonitor.data.datasource.AnthropicCreditsDiagnosticsRecorder
import com.usagemonitor.data.datasource.NoOpAnthropicCreditsDiagnosticsRecorder
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteApiDataSourceAnthropicCreditsDiagnosticsTest {

    @Test
    fun `records the raw extra_usage node when diagnostics is on`() = runTest {
        val recorder = RecordingRecorder(isEnabled = true)
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(USAGE_WITH_CREDITS_BODY),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            anthropicCreditsDiagnosticsRecorder = recorder
        )

        val response = dataSource.fetchAnthropicUsage("token")

        // O caminho instrumentado tem de devolver a mesma coisa que o normal.
        assertEquals(21.5, response.fiveHour.utilization)
        assertEquals(55000L, response.extraUsage?.monthlyLimit)

        val event = recorder.events.single()
        assertEquals("QUOTA_FROM_EXTRA_USAGE", event.outcome)
        val extraUsageRaw = assertNotNull(event.extraUsageRaw)
        // Cru é o ponto: um campo renomeado só aparece se o nó inteiro for guardado.
        assertTrue("\"monthly_limit\":55000" in extraUsageRaw.replace(" ", ""))
        assertTrue("\"currency\":\"BRL\"" in extraUsageRaw.replace(" ", ""))
        assertNotNull(event.spendRaw)
    }

    @Test
    fun `records the failure outcome when the limit disappears from the payload`() = runTest {
        val recorder = RecordingRecorder(isEnabled = true)
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(USAGE_WITHOUT_LIMIT_BODY),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            anthropicCreditsDiagnosticsRecorder = recorder
        )

        dataSource.fetchAnthropicUsage("token")

        assertEquals("LIMIT_ABSENT", recorder.events.single().outcome)
    }

    @Test
    fun `records nothing when diagnostics is off`() = runTest {
        val recorder = RecordingRecorder(isEnabled = false)
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(USAGE_WITH_CREDITS_BODY),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            anthropicCreditsDiagnosticsRecorder = recorder
        )

        val response = dataSource.fetchAnthropicUsage("token")

        assertEquals(55000L, response.extraUsage?.monthlyLimit)
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun `defaults to the no-op recorder`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(USAGE_WITH_CREDITS_BODY),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        assertEquals(false, NoOpAnthropicCreditsDiagnosticsRecorder.isEnabled)
        assertEquals(55000L, dataSource.fetchAnthropicUsage("token").extraUsage?.monthlyLimit)
    }

    private fun jsonHttpClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private class RecordingRecorder(
        override val isEnabled: Boolean
    ) : AnthropicCreditsDiagnosticsRecorder {
        val events = mutableListOf<AnthropicCreditsDiagnosticsEvent>()

        override fun record(event: AnthropicCreditsDiagnosticsEvent) {
            events += event
        }
    }

    private companion object {
        const val USAGE_WITH_CREDITS_BODY = """
            {
              "five_hour": { "utilization": 21.5, "resets_at": "2026-08-11T20:00:00Z" },
              "seven_day": { "utilization": 50.0, "resets_at": "2026-08-15T20:00:00Z" },
              "extra_usage": {
                "is_enabled": true,
                "monthly_limit": 55000,
                "used_credits": 32784.0,
                "utilization": 59.60727272727273,
                "currency": "BRL",
                "decimal_places": 2,
                "credits_ever_enabled": true,
                "daily": null,
                "weekly": null
              },
              "spend": {
                "used": { "amount_minor": 32784, "currency": "BRL", "exponent": 2 },
                "limit": { "amount_minor": 55000, "currency": "BRL", "exponent": 2 },
                "percent": 60,
                "enabled": true
              }
            }
        """

        // Mesma conta com o limite fora do payload — a forma suspeita do episódio
        // de agosto/2026, em que a linha de créditos sumiu sem deixar rastro.
        const val USAGE_WITHOUT_LIMIT_BODY = """
            {
              "five_hour": { "utilization": 21.5, "resets_at": "2026-08-11T20:00:00Z" },
              "seven_day": { "utilization": 50.0, "resets_at": "2026-08-15T20:00:00Z" },
              "extra_usage": {
                "is_enabled": true,
                "credits_ever_enabled": true
              }
            }
        """
    }
}
