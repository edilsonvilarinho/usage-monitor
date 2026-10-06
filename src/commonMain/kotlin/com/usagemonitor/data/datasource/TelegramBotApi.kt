package com.usagemonitor.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mensagem de texto recebida (#387). Só o que o bot usa: conversa, nome e texto. */
data class TelegramIncomingMessage(
    val updateId: Long,
    val chatId: Long,
    val chatName: String,
    val text: String?
)

/** 429 da Bot API: [retryAfterSeconds] é o que o Telegram mandou esperar. */
class TelegramRateLimitedException(val retryAfterSeconds: Long) : RuntimeException("Telegram: limite de envio, aguarde $retryAfterSeconds s")

/** Token recusado (401/404 do Telegram): não adianta repetir até o usuário trocar o token. */
class TelegramUnauthorizedException : RuntimeException("Token do bot recusado pelo Telegram.")

/**
 * Cliente mínimo da Bot API do Telegram (#387): `getUpdates` em long polling,
 * `sendMessage` e `deleteWebhook`. Só HTTPS de saída — nenhuma porta aberta.
 *
 * O token vai no caminho da URL, como a API exige; por isso nenhuma mensagem de
 * erro daqui repete a URL ou o corpo bruto.
 */
open class TelegramBotApi(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.telegram.org"
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Long polling: o Telegram segura a resposta até [timeoutSeconds] sem update.
     * O timeout da requisição é estendido além disso — o padrão do app (20 s)
     * cortaria toda espera de 25 s como falha.
     */
    open suspend fun getUpdates(token: String, offset: Long?, timeoutSeconds: Int): List<TelegramIncomingMessage> {
        val response = httpClient.get("$baseUrl/bot$token/getUpdates") {
            if (offset != null) parameter("offset", offset)
            parameter("timeout", timeoutSeconds)
            parameter("allowed_updates", "[\"message\"]")
            timeout {
                requestTimeoutMillis = (timeoutSeconds + LONG_POLL_MARGIN_SECONDS) * 1_000L
                socketTimeoutMillis = (timeoutSeconds + LONG_POLL_MARGIN_SECONDS) * 1_000L
            }
        }
        val body = decode(response, UpdatesResponseDto.serializer())
        return body.result.orEmpty().mapNotNull { update ->
            val message = update.message ?: return@mapNotNull null
            TelegramIncomingMessage(
                updateId = update.updateId,
                chatId = message.chat.id,
                chatName = message.chat.username?.let { name -> "@$name" } ?: message.chat.firstName ?: message.chat.title ?: message.chat.id.toString(),
                text = message.text
            )
        }
    }

    open suspend fun sendMessage(token: String, chatId: Long, text: String) {
        val response = httpClient.post("$baseUrl/bot$token/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SendMessageDto.serializer(), SendMessageDto(chatId, text)))
        }
        decode(response, SimpleResponseDto.serializer())
    }

    /** `getUpdates` não funciona com webhook configurado: o pareamento remove antes de começar. */
    open suspend fun deleteWebhook(token: String) {
        decode(httpClient.post("$baseUrl/bot$token/deleteWebhook"), SimpleResponseDto.serializer())
    }

    private suspend fun <T : OkResponse> decode(response: HttpResponse, serializer: kotlinx.serialization.KSerializer<T>): T {
        val text = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        val code = response.status.value
        if (code == 401 || code == 404) throw TelegramUnauthorizedException()
        if (code == 429) throw TelegramRateLimitedException(parsed?.parameters?.retryAfter ?: DEFAULT_RETRY_SECONDS)
        if (parsed == null || !parsed.ok) {
            throw IllegalStateException("Telegram respondeu HTTP $code: ${parsed?.description ?: "resposta ilegível"}")
        }
        return parsed
    }

    private interface OkResponse {
        val ok: Boolean
        val description: String?
        val parameters: ResponseParametersDto?
    }

    @Serializable
    private data class ResponseParametersDto(@SerialName("retry_after") val retryAfter: Long? = null)

    @Serializable
    private data class UpdatesResponseDto(
        override val ok: Boolean = false,
        override val description: String? = null,
        override val parameters: ResponseParametersDto? = null,
        val result: List<UpdateDto>? = null
    ) : OkResponse

    @Serializable
    private data class SimpleResponseDto(
        override val ok: Boolean = false,
        override val description: String? = null,
        override val parameters: ResponseParametersDto? = null
    ) : OkResponse

    @Serializable
    private data class UpdateDto(
        @SerialName("update_id") val updateId: Long,
        val message: MessageDto? = null
    )

    @Serializable
    private data class MessageDto(val chat: ChatDto, val text: String? = null)

    @Serializable
    private data class ChatDto(
        val id: Long,
        val username: String? = null,
        @SerialName("first_name") val firstName: String? = null,
        val title: String? = null
    )

    @Serializable
    private data class SendMessageDto(@SerialName("chat_id") val chatId: Long, val text: String)

    private companion object {
        const val LONG_POLL_MARGIN_SECONDS = 10
        const val DEFAULT_RETRY_SECONDS = 5L
    }
}
