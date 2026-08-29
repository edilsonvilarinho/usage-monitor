package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.OpenCodeGoUsageDto
import com.usagemonitor.data.dto.OpenCodeGoUsageResponse
import com.usagemonitor.data.dto.OpenCodeGoWindowDto
import com.usagemonitor.data.repository.OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE
import com.usagemonitor.data.repository.OpenCodeGoRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.viewmodel.UiApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCodeGoRepositoryImplTest {

    private val successResponse = OpenCodeGoUsageResponse(
        usage = OpenCodeGoUsageDto(
            rolling = OpenCodeGoWindowDto("ok", 0.0, "2026-08-29T10:28:33.651Z"),
            weekly = OpenCodeGoWindowDto("ok", 51.0, "2026-08-31T00:00:00.651Z"),
            monthly = OpenCodeGoWindowDto("ok", 47.0, "2026-09-05T18:47:55.651Z")
        )
    )

    private fun fakeDataSource(response: OpenCodeGoUsageResponse): RemoteApiDataSource {
        return object : RemoteApiDataSource(io.ktor.client.HttpClient()) {
            override suspend fun fetchOpenCodeGoUsage(apiKey: String) = response
        }
    }

    private fun failingDataSource(message: String): RemoteApiDataSource {
        return object : RemoteApiDataSource(io.ktor.client.HttpClient()) {
            override suspend fun fetchOpenCodeGoUsage(apiKey: String): OpenCodeGoUsageResponse {
                throw IllegalStateException(message)
            }
        }
    }

    @Test
    fun `returns success when api key present and response ok`() = kotlinx.coroutines.test.runTest {
        val repo = OpenCodeGoRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isSuccess)
        assertEquals(ApiSource.OPENCODE_GO, result.getOrThrow().source)
        assertEquals(3, result.getOrThrow().quotas.size)
    }

    @Test
    fun `returns failure when api key missing`() = kotlinx.coroutines.test.runTest {
        val repo = OpenCodeGoRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { "  " }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message!!
        assertTrue(message.contains("Chave da API OpenCode não configurada"))
        assertTrue(
            UiApiError(source = ApiSource.OPENCODE_GO, message = message).isOpenCodeGoApiKeyIssue
        )
    }

    /**
     * 403 de conta sem plano Go é estado normal de quem só usa o Zen pago: vira
     * banner de configuração, nunca pedido de revisar a credencial nem toast a
     * cada coleta.
     */
    @Test
    fun `translates the entitlement 403 into a missing subscription failure`() =
        kotlinx.coroutines.test.runTest {
            val repo = OpenCodeGoRepositoryImpl(
                apiDataSource = failingDataSource(
                    """OpenCode Go HTTP 403: {"type":"error","error":{"type":"EntitlementError","message":"OpenCode Go subscription required."}}"""
                ),
                apiKeyReader = { "test-key" }
            )

            val result = repo.getUsage()

            assertTrue(result.isFailure)
            assertEquals(OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE, result.exceptionOrNull()!!.message)
            val uiError = UiApiError(
                source = ApiSource.OPENCODE_GO,
                message = result.exceptionOrNull()!!.message!!
            )
            assertTrue(uiError.isOpenCodeGoSubscriptionIssue)
            assertTrue(uiError.isConfigurationIssue)
            assertFalse(uiError.isOpenCodeGoApiKeyIssue)
        }

    /**
     * 401 é chave inválida e continua sendo erro comum: traduzi-lo para "sem
     * assinatura" esconderia o único caso em que revisar a credencial resolve.
     */
    @Test
    fun `keeps the 401 as a plain failure`() = kotlinx.coroutines.test.runTest {
        val repo = OpenCodeGoRepositoryImpl(
            apiDataSource = failingDataSource(
                """OpenCode Go HTTP 401: {"type":"error","error":{"type":"AuthError","message":"Missing API key."}}"""
            ),
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
        val uiError = UiApiError(
            source = ApiSource.OPENCODE_GO,
            message = result.exceptionOrNull()!!.message!!
        )
        assertFalse(uiError.isConfigurationIssue)
    }

    /**
     * 403 que não é de direito de acesso — bloqueio de rede corporativa, por
     * exemplo — não pode virar "assine o plano Go".
     */
    @Test
    fun `does not treat every 403 as a missing subscription`() = kotlinx.coroutines.test.runTest {
        val repo = OpenCodeGoRepositoryImpl(
            apiDataSource = failingDataSource("""OpenCode Go HTTP 403: {"error":"blocked by proxy"}"""),
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("blocked by proxy"))
    }
}
