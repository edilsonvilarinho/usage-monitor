package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.TeamErrorDto
import com.usagemonitor.data.dto.TeamIngestRequestDto
import com.usagemonitor.data.dto.TeamIngestResponseDto
import com.usagemonitor.data.dto.TeamSnapshotDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Header de autenticação do servidor de time. */
private const val TEAM_KEY_HEADER = "x-team-key"

private const val MAX_ERROR_BODY_CHARS = 300

// Aberto para permitir fakes nos testes, no mesmo padrão de RemoteApiDataSource.
open class RemoteTeamDataSource(
    private val httpClient: HttpClient
) {
    private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** `GET /api/health`. Não exige chave; serve para validar só a URL. */
    open suspend fun checkHealth(baseUrl: String) {
        requireSuccess(httpClient.get("$baseUrl/api/health"), "healthcheck")
    }

    /**
     * `GET /api/v1/team`. Chave inválida devolve 401 e vira erro com mensagem
     * legível — é o que o botão "Testar conexão" das Configurações mostra.
     */
    open suspend fun fetchTeam(
        baseUrl: String,
        apiKey: String,
        accountKey: String,
        sinceEpochMillis: Long?
    ): TeamSnapshotDto {
        val response = requireSuccess(
            response = httpClient.get("$baseUrl/api/v1/team") {
                header(TEAM_KEY_HEADER, apiKey)
                header("Accept", "application/json")
                parameter("accountKey", accountKey)
                if (sinceEpochMillis != null) {
                    parameter("since", sinceEpochMillis)
                }
            },
            operation = "leitura do time"
        )

        return response.body()
    }

    /** `POST /api/v1/ingest`. Idempotente do lado do servidor. */
    open suspend fun pushIngest(
        baseUrl: String,
        apiKey: String,
        request: TeamIngestRequestDto
    ): TeamIngestResponseDto {
        val response = requireSuccess(
            response = httpClient.post("$baseUrl/api/v1/ingest") {
                header(TEAM_KEY_HEADER, apiKey)
                header("Accept", "application/json")
                contentType(ContentType.Application.Json)
                setBody(request)
            },
            operation = "envio ao time"
        )

        return response.body()
    }

    /**
     * Converte status de erro numa exceção com a mensagem que o servidor mandou.
     *
     * O corpo é `{ "error": ..., "code": ... }`; quando o parse falha — proxy no
     * caminho devolvendo HTML, por exemplo — cai no texto cru truncado, que
     * ainda diz mais ao usuário do que só o código de status.
     */
    private suspend fun requireSuccess(response: HttpResponse, operation: String): HttpResponse {
        if (response.status.isSuccess()) {
            return response
        }

        val rawBody = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsedMessage = runCatching {
            errorJson.decodeFromString<TeamErrorDto>(rawBody).message
        }.getOrNull()

        val detail = parsedMessage?.takeIf { it.isNotBlank() }
            ?: rawBody.take(MAX_ERROR_BODY_CHARS).takeIf { it.isNotBlank() }
            ?: "sem detalhe"

        throw TeamServerException(
            statusCode = response.status.value,
            message = "Servidor de time recusou a $operation (HTTP ${response.status.value}): $detail"
        )
    }
}

/** Falha vinda do servidor de time, com o status para a UI diferenciar 401 de 500. */
class TeamServerException(
    val statusCode: Int,
    override val message: String
) : Exception(message)
