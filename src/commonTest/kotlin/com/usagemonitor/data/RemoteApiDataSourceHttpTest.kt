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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun `fetchAnthropicUsage parses extra usage credits from the real payload`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(ANTHROPIC_USAGE_WITH_CREDITS_BODY),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            codexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder
        )

        val response = dataSource.fetchAnthropicUsage("token")
        val extraUsage = assertNotNull(response.extraUsage)

        assertEquals(true, extraUsage.isEnabled)
        assertEquals(55000L, extraUsage.monthlyLimit)
        assertEquals(32784.0, extraUsage.usedCredits)
        assertEquals(59.60727272727273, extraUsage.utilization)
        assertEquals("BRL", extraUsage.currency)
        assertEquals(2, extraUsage.decimalPlaces)
        assertEquals(true, extraUsage.creditsEverEnabled)

        val spendUsed = assertNotNull(response.spend?.used)
        assertEquals(32784L, spendUsed.amountMinor)
        assertEquals("BRL", spendUsed.currency)
        assertEquals(2, spendUsed.exponent)
    }

    @Test
    fun `fetchAnthropicUsage parses disabled extra usage credits`() = runTest {
        val dataSource = RemoteApiDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(ANTHROPIC_USAGE_WITHOUT_CREDITS_BODY),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            codexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder
        )

        val response = dataSource.fetchAnthropicUsage("token")
        val extraUsage = assertNotNull(response.extraUsage)

        assertEquals(false, extraUsage.isEnabled)
        assertNull(extraUsage.monthlyLimit)
        assertNull(extraUsage.usedCredits)
        assertNull(extraUsage.currency)
        assertEquals(false, extraUsage.creditsEverEnabled)
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

    private companion object {
        // Corpo real de GET /api/oauth/usage numa conta com créditos ligados.
        const val ANTHROPIC_USAGE_WITH_CREDITS_BODY = """
            {
              "five_hour": { "utilization": 21.5, "resets_at": "2026-08-11T20:00:00Z" },
              "seven_day": { "utilization": 50.0, "resets_at": "2026-08-15T20:00:00Z" },
              "seven_day_oauth_apps": { "utilization": 0.0, "resets_at": null },
              "seven_day_opus": { "utilization": 12.0, "resets_at": "2026-08-15T20:00:00Z" },
              "extra_usage": {
                "is_enabled": true,
                "monthly_limit": 55000,
                "used_credits": 32784.0,
                "utilization": 59.60727272727273,
                "currency": "BRL",
                "decimal_places": 2,
                "disabled_reason": null,
                "user_disabled": false,
                "spend_limit_reached": false,
                "credits_ever_enabled": true,
                "daily": null,
                "weekly": null
              },
              "spend": {
                "used": { "amount_minor": 32784, "currency": "BRL", "exponent": 2 },
                "limit": { "amount_minor": 55000, "currency": "BRL", "exponent": 2 },
                "percent": 60,
                "severity": "normal",
                "enabled": true,
                "disabled_reason": null,
                "cap": { "money": { "amount_minor": 55000, "currency": "BRL", "exponent": 2 }, "credits": null },
                "balance": null,
                "auto_reload": null,
                "can_purchase_credits": false,
                "can_toggle": false
              },
              "limits": [
                {
                  "kind": "five_hour",
                  "group": "default",
                  "percent": 21,
                  "severity": "normal",
                  "resets_at": "2026-08-11T20:00:00Z",
                  "scope": "account",
                  "is_active": true
                }
              ],
              "member_dashboard_available": false
            }
        """

        // Corpo real de uma conta sem créditos: o servidor devolve a moeda
        // default "USD" em `spend`, por isso `extra_usage` é a fonte primária.
        const val ANTHROPIC_USAGE_WITHOUT_CREDITS_BODY = """
            {
              "five_hour": { "utilization": 3.0, "resets_at": "2026-08-11T20:00:00Z" },
              "seven_day": { "utilization": 8.0, "resets_at": "2026-08-15T20:00:00Z" },
              "extra_usage": {
                "is_enabled": false,
                "monthly_limit": null,
                "used_credits": null,
                "utilization": null,
                "currency": null,
                "decimal_places": null,
                "disabled_reason": null,
                "user_disabled": false,
                "spend_limit_reached": false,
                "credits_ever_enabled": false,
                "daily": null,
                "weekly": null
              },
              "spend": {
                "used": { "amount_minor": 0, "currency": "USD", "exponent": 2 },
                "limit": null,
                "percent": 0,
                "enabled": false,
                "cap": null
              }
            }
        """
    }
}
