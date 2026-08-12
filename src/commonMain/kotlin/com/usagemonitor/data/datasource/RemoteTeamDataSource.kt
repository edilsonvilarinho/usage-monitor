package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CreateTeamKeyRequestDto
import com.usagemonitor.data.dto.TeamErrorDto
import com.usagemonitor.data.dto.TeamIngestRequestDto
import com.usagemonitor.data.dto.TeamIngestResponseDto
import com.usagemonitor.data.dto.TeamKeyDto
import com.usagemonitor.data.dto.TeamKeyListDto
import com.usagemonitor.data.dto.TeamOverviewDto
import com.usagemonitor.data.dto.TeamSessionDetailResponseDto
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.dto.TeamVerificationDto
import com.usagemonitor.data.dto.UpdateTeamKeyRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Header de autenticação do servidor de time. */
private const val TEAM_KEY_HEADER = "x-team-key"

/** Header de administração: vale para qualquer conta e não serve para envio. */
private const val ADMIN_TOKEN_HEADER = "x-admin-token"

private const val MAX_ERROR_BODY_CHARS = 300

/**
 * Credencial de uma chamada ao servidor.
 *
 * Tipada em vez de duas strings opcionais porque o servidor trata as duas de
 * formas diferentes — a chave de time vale para as contas dela, o token de
 * administração vale para todas e é recusado no envio. Um par de `String?`
 * deixaria "as duas" e "nenhuma" representáveis, e nenhum dos dois é válido.
 */
sealed interface TeamCredential {
    val value: String

    data class TeamKey(override val value: String) : TeamCredential

    data class AdminToken(override val value: String) : TeamCredential
}

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
        credential: TeamCredential,
        accountKey: String,
        sinceEpochMillis: Long?
    ): TeamSnapshotDto {
        val response = requireSuccess(
            response = httpClient.get("$baseUrl/api/v1/team") {
                authenticate(credential)
                parameter("accountKey", accountKey)
                if (sinceEpochMillis != null) {
                    parameter("since", sinceEpochMillis)
                }
            },
            operation = "leitura do time"
        )

        return response.body()
    }

    /**
     * `GET /api/v1/session`. Turnos crus de uma sessão, sem recorte temporal.
     *
     * Existe a partir da versão 0.2.0 do servidor. Contra um servidor mais antigo
     * a rota não existe e a resposta é `404` — quem chama trata isso como
     * "sem detalhe disponível", não como falha.
     */
    open suspend fun fetchSessionDetail(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): TeamSessionDetailResponseDto {
        val response = requireSuccess(
            response = httpClient.get("$baseUrl/api/v1/session") {
                authenticate(credential)
                parameter("accountKey", accountKey)
                parameter("deviceId", deviceId)
                parameter("sessionId", sessionId)
            },
            operation = "leitura da sessão do time"
        )

        return response.body()
    }

    /**
     * `POST /api/v1/ingest`. Idempotente do lado do servidor.
     *
     * Só aceita chave de time: o servidor recusa o token de administração aqui,
     * porque quem administra lê, mas não escreve consumo em nome de ninguém.
     */
    open suspend fun pushIngest(
        baseUrl: String,
        apiKey: String,
        request: TeamIngestRequestDto
    ): TeamIngestResponseDto {
        val response = requireSuccess(
            response = httpClient.post("$baseUrl/api/v1/ingest") {
                authenticate(TeamCredential.TeamKey(apiKey))
                contentType(ContentType.Application.Json)
                setBody(request)
            },
            operation = "envio ao time"
        )

        return response.body()
    }

    /**
     * `DELETE /api/v1/member`. Apaga o integrante e tudo o que ele enviou.
     *
     * Parâmetros na query porque corpo em `DELETE` é mal suportado por proxy —
     * o servidor fica atrás de um no deploy padrão.
     */
    open suspend fun deleteMember(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String,
        deviceId: String
    ) {
        requireSuccess(
            response = httpClient.delete("$baseUrl/api/v1/member") {
                authenticate(credential)
                parameter("accountKey", accountKey)
                parameter("deviceId", deviceId)
            },
            operation = "remoção de integrante"
        )
    }

    /**
     * `GET /api/v1/verify`. A chave configurada serve para esta conta?
     *
     * Substitui a consulta a uma conta inventada que o "Testar conexão" fazia:
     * com autorização por conta, aquela consulta passaria a ser recusada e o
     * botão reprovaria uma configuração correta.
     */
    open suspend fun verifyKey(
        baseUrl: String,
        credential: TeamCredential,
        accountKey: String
    ): TeamVerificationDto {
        val response = requireSuccess(
            response = httpClient.get("$baseUrl/api/v1/verify") {
                authenticate(credential)
                parameter("accountKey", accountKey)
            },
            operation = "verificação da chave"
        )

        return response.body()
    }

    /** `GET /api/admin/v1/ping`. Confere o token sem efeito colateral. */
    open suspend fun checkAdminToken(baseUrl: String, adminToken: String) {
        requireSuccess(
            response = httpClient.get("$baseUrl/api/admin/v1/ping") {
                authenticate(TeamCredential.AdminToken(adminToken))
            },
            operation = "validação do token de administração"
        )
    }

    open suspend fun fetchOverview(
        baseUrl: String,
        adminToken: String,
        sinceEpochMillis: Long?
    ): TeamOverviewDto {
        val response = requireSuccess(
            response = httpClient.get("$baseUrl/api/admin/v1/overview") {
                authenticate(TeamCredential.AdminToken(adminToken))
                if (sinceEpochMillis != null) {
                    parameter("since", sinceEpochMillis)
                }
            },
            operation = "leitura da visão do administrador"
        )

        return response.body()
    }

    open suspend fun listKeys(baseUrl: String, adminToken: String): TeamKeyListDto {
        val response = requireSuccess(
            response = httpClient.get("$baseUrl/api/admin/v1/keys") {
                authenticate(TeamCredential.AdminToken(adminToken))
            },
            operation = "leitura das chaves de time"
        )

        return response.body()
    }

    open suspend fun createKey(
        baseUrl: String,
        adminToken: String,
        request: CreateTeamKeyRequestDto
    ): TeamKeyDto {
        val response = requireSuccess(
            response = httpClient.post("$baseUrl/api/admin/v1/keys") {
                authenticate(TeamCredential.AdminToken(adminToken))
                contentType(ContentType.Application.Json)
                setBody(request)
            },
            operation = "criação da chave de time"
        )

        return response.body()
    }

    open suspend fun updateKey(
        baseUrl: String,
        adminToken: String,
        id: String,
        request: UpdateTeamKeyRequestDto
    ): TeamKeyDto {
        val response = requireSuccess(
            response = httpClient.patch("$baseUrl/api/admin/v1/keys/${id.encodeURLPathPart()}") {
                authenticate(TeamCredential.AdminToken(adminToken))
                contentType(ContentType.Application.Json)
                setBody(request)
            },
            operation = "alteração da chave de time"
        )

        return response.body()
    }

    open suspend fun regenerateKey(baseUrl: String, adminToken: String, id: String): TeamKeyDto {
        val response = requireSuccess(
            response = httpClient.post(
                "$baseUrl/api/admin/v1/keys/${id.encodeURLPathPart()}/regenerate"
            ) {
                authenticate(TeamCredential.AdminToken(adminToken))
            },
            operation = "regeração da chave de time"
        )

        return response.body()
    }

    open suspend fun revokeKey(baseUrl: String, adminToken: String, id: String): TeamKeyDto {
        val response = requireSuccess(
            response = httpClient.delete("$baseUrl/api/admin/v1/keys/${id.encodeURLPathPart()}") {
                authenticate(TeamCredential.AdminToken(adminToken))
            },
            operation = "revogação da chave de time"
        )

        return response.body()
    }

    open suspend fun unclaimAccount(
        baseUrl: String,
        adminToken: String,
        id: String,
        accountKey: String
    ): TeamKeyDto {
        val path = "${id.encodeURLPathPart()}/accounts/${accountKey.encodeURLPathPart()}"
        val response = requireSuccess(
            response = httpClient.delete("$baseUrl/api/admin/v1/keys/$path") {
                authenticate(TeamCredential.AdminToken(adminToken))
            },
            operation = "remoção do vínculo da conta"
        )

        return response.body()
    }

    private fun HttpRequestBuilder.authenticate(credential: TeamCredential) {
        val headerName = when (credential) {
            is TeamCredential.TeamKey -> TEAM_KEY_HEADER
            is TeamCredential.AdminToken -> ADMIN_TOKEN_HEADER
        }
        header(headerName, credential.value)
        header("Accept", "application/json")
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
