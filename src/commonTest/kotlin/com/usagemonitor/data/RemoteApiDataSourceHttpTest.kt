package com.usagemonitor.data

import com.usagemonitor.data.datasource.NoOpCodexDiagnosticsRecorder
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

class RemoteApiDataSourceHttpTest {

    @Test
    fun `fetchAnthropicUsage parses successful response`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "five_hour": { "utilization": 21.5, "resets_at": "2026-06-04T20:00:00Z" },
                          "seven_day": { "utilization": 50.0, "resets_at": "2026-06-10T20:00:00Z" }
                        }
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            codexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder
        )

        val response = dataSource.fetchAnthropicUsage("token")

        assertEquals(21.5, response.fiveHour.utilization)
        assertEquals("2026-06-10T20:00:00Z", response.sevenDay.resetsAt)
    }

    @Test
    fun `fetchAnthropicUsage throws readable error on non-2xx`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel("""{"error":"unauthorized"}"""),
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.fetchAnthropicUsage("token")
        }

        assertEquals("""Anthropic HTTP 401: {"error":"unauthorized"}""", error.message)
    }

    @Test
    fun `fetchMiniMaxTokenPlan parses successful response`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "model_remains": [],
                          "base_resp": {
                            "status_code": 0,
                            "status_msg": "ok"
                          }
                        }
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val response = dataSource.fetchMiniMaxTokenPlan("api-key")

        assertEquals(0, response.baseResp.statusCode)
    }

    @Test
    fun `fetchMiniMaxTokenPlan throws readable error on non-2xx`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel("""{"base_resp":{"status_code":401,"status_msg":"denied"}}"""),
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.fetchMiniMaxTokenPlan("api-key")
        }

        assertEquals("""MiniMax HTTP 401: {"base_resp":{"status_code":401,"status_msg":"denied"}}""", error.message)
    }

    @Test
    fun `fetchDeepSeekBalance parses successful response`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "is_available": true,
                          "balance_infos": [
                            {
                              "currency": "USD",
                              "total_balance": "4.66",
                              "granted_balance": "0",
                              "topped_up_balance": "4.66"
                            }
                          ]
                        }
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val response = dataSource.fetchDeepSeekBalance("api-key")

        assertEquals(true, response.isAvailable)
        assertEquals("USD", response.balanceInfos.single().currency)
    }

    @Test
    fun `fetchDeepSeekBalance throws readable error on non-2xx`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel("""{"error":"blocked"}"""),
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.fetchDeepSeekBalance("api-key")
        }

        assertEquals("""DeepSeek HTTP 403: {"error":"blocked"}""", error.message)
    }

    @Test
    fun `fetchLatestGitHubRelease parses successful response`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "tag_name": "v14.1.1",
                          "html_url": "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v14.1.1",
                          "assets": []
                        }
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val response = dataSource.fetchLatestGitHubRelease("owner", "repo")

        assertEquals("v14.1.1", response.tagName)
    }

    @Test
    fun `fetchLatestGitHubRelease throws readable error on non-2xx`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel("""{"message":"not found"}"""),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            dataSource.fetchLatestGitHubRelease("owner", "repo")
        }

        assertEquals("""GitHub release HTTP 404: {"message":"not found"}""", error.message)
    }

    private fun jsonHttpClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
