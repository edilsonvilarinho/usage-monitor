package com.usagemonitor.data

import com.usagemonitor.data.datasource.CursorUsageApiException
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind
import com.usagemonitor.data.datasource.RemoteApiDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorUsageApiDataSourceTest {
    private val token = "synthetic.jwt.token"

    @Test
    fun `sends the ephemeral cursor session only to the fixed endpoint`() = runTest {
        lateinit var request: HttpRequestData
        val client = HttpClient(MockEngine { data ->
            request = data
            respond(
                content = ByteReadChannel("""{"individualUsage":{"plan":{"totalPercentUsed":0}}}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

        try {
            val payload = RemoteApiDataSource(client).fetchCursorUsageSummary("account-id", token)

            assertEquals("https://cursor.com/api/usage-summary", request.url.toString())
            assertEquals("WorkosCursorSessionToken=account-id::$token", request.headers[HttpHeaders.Cookie])
            assertEquals("0", payload.jsonObject.getValue("individualUsage").jsonObject
                .getValue("plan").jsonObject.getValue("totalPercentUsed").toString())
        } finally {
            client.close()
        }
    }

    @Test
    fun `auth response status is retained but response body and token are discarded`() = runTest {
        val body = "response echoes $token"
        val client = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        })

        try {
            val failure = assertFailsWith<CursorUsageApiException> {
                RemoteApiDataSource(client).fetchCursorUsageSummary("account-id", token)
            }

            assertEquals(401, failure.statusCode)
            assertEquals(CursorUsageApiFailureKind.AUTHENTICATION_REJECTED, failure.kind)
            assertFalse(failure.message.orEmpty().contains(body))
            assertFalse(failure.message.orEmpty().contains(token))
        } finally {
            client.close()
        }
    }

    @Test
    fun `cookie is not forwarded after a redirect`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { data ->
            requests += data
            respond(
                content = ByteReadChannel(""),
                status = if (requests.size == 1) HttpStatusCode.Found else HttpStatusCode.OK,
                headers = if (requests.size == 1) {
                    headersOf(HttpHeaders.Location, "https://attacker.invalid/collect")
                } else {
                    headersOf(HttpHeaders.ContentType, "application/json")
                }
            )
        }) {
            followRedirects = false
        }

        try {
            assertFailsWith<CursorUsageApiException> {
                RemoteApiDataSource(client).fetchCursorUsageSummary("account-id", token)
            }

            assertEquals(1, requests.size)
            assertEquals("https://cursor.com/api/usage-summary", requests.single().url.toString())
            assertTrue(requests.single().headers[HttpHeaders.Cookie].orEmpty().contains(token))
        } finally {
            client.close()
        }
    }
}
