package com.usagemonitor.data

import com.usagemonitor.data.datasource.NoOpCodexDiagnosticsRecorder
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.datasource.parseRetryAfter
import com.usagemonitor.domain.entity.RateLimitedException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** O 429 tipado com `Retry-After` e o User-Agent obrigatório da Anthropic (issue #269). */
class RateLimitHttpTest {

    private val now = Instant.parse("2015-10-21T07:27:00Z")

    @Test
    fun `Retry-After in seconds`() {
        assertEquals(120.seconds, parseRetryAfter("120", now))
        assertEquals(0.seconds, parseRetryAfter("0", now))
    }

    @Test
    fun `Retry-After as an HTTP date`() {
        assertEquals(60.seconds, parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT", now))
    }

    @Test
    fun `absent or unreadable Retry-After is null, never zero`() {
        assertNull(parseRetryAfter(null, now))
        assertNull(parseRetryAfter("  ", now))
        assertNull(parseRetryAfter("amanhã", now))
        assertNull(parseRetryAfter("-5", now))
    }

    @Test
    fun `429 from the usage endpoint is typed and keeps the HTTP 429 marker`() = runTest {
        val dataSource = dataSourceResponding(HttpStatusCode.TooManyRequests, retryAfter = "300")

        val error = assertFailsWith<RateLimitedException> { dataSource.fetchAnthropicUsage("token") }

        assertEquals(300.seconds, error.retryAfter)
        assertTrue(error.message.orEmpty().contains("HTTP 429"))
    }

    @Test
    fun `429 without Retry-After still arms the backoff`() = runTest {
        val dataSource = dataSourceResponding(HttpStatusCode.TooManyRequests, retryAfter = null)

        val error = assertFailsWith<RateLimitedException> { dataSource.fetchAnthropicUsage("token") }

        assertNull(error.retryAfter)
    }

    @Test
    fun `other statuses stay untyped`() = runTest {
        val dataSource = dataSourceResponding(HttpStatusCode.InternalServerError, retryAfter = null)

        val error = assertFailsWith<IllegalStateException> { dataSource.fetchAnthropicUsage("token") }

        assertTrue(error !is RateLimitedException)
    }

    @Test
    fun `usage request goes out with the Claude Code User-Agent`() = runTest {
        var userAgent: String? = null
        val dataSource = RemoteApiDataSource(
            httpClient = jsonClient { request ->
                userAgent = request.headers[HttpHeaders.UserAgent]
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            codexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder
        )

        runCatching { dataSource.fetchAnthropicUsage("token") }

        assertTrue(userAgent.orEmpty().startsWith("claude-code/"), "User-Agent: $userAgent")
    }

    private fun dataSourceResponding(status: HttpStatusCode, retryAfter: String?): RemoteApiDataSource {
        val headers = if (retryAfter == null) {
            headersOf(HttpHeaders.ContentType, "application/json")
        } else {
            headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf(retryAfter))
        }
        return RemoteApiDataSource(
            httpClient = jsonClient { respond("""{"type":"error"}""", status, headers) },
            codexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder
        )
    }

    private fun jsonClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
