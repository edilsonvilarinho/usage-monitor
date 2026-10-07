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

/**
 * Update recebido (#387): mensagem de texto ou toque num botão inline (#396).
 * Só o que o bot usa: conversa, nome, texto e, no botão, o `callback_data`, o id do
 * toque (para o `answerCallbackQuery`) e a mensagem que o carrega (para editá-la).
 */
data class TelegramIncomingMessage(
    val updateId: Long,
    val chatId: Long,
    val chatName: String,
    val text: String?,
    val callbackId: String? = null,
    val callbackData: String? = null,
    val messageId: Long? = null
)

/** Botão de teclado inline: [data] volta no `callback_query`. */
data class TelegramButton(val text: String, val data: String)

/** 429 da Bot API: [retryAfterSeconds] é o que o Telegram mandou esperar. */
class TelegramRateLimitedException(val retryAfterSeconds: Long) : RuntimeException("Telegram: limite de envio, aguarde $retryAfterSeconds s")

/** Token recusado (401/404 do Telegram): não adianta repetir até o usuário trocar o token. */
class TelegramUnauthorizedException : RuntimeException("Token do bot recusado pelo Telegram.")

/**
 * Cliente mínimo da Bot API do Telegram (#387): `getUpdates` em long polling,
 * `sendMessage`, `editMessageText`, `answerCallbackQuery`, `deleteWebhook` e `setMyCommands`.
 * Só HTTPS de saída — nenhuma porta aberta.
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
            parameter("allowed_updates", "[\"message\",\"callback_query\"]")
            timeout {
                requestTimeoutMillis = (timeoutSeconds + LONG_POLL_MARGIN_SECONDS) * 1_000L
                socketTimeoutMillis = (timeoutSeconds + LONG_POLL_MARGIN_SECONDS) * 1_000L
            }
        }
        val body = decode(response, UpdatesResponseDto.serializer())
        return body.result.orEmpty().mapNotNull { update ->
            val callback = update.callbackQuery
            val message = update.message ?: callback?.message ?: return@mapNotNull null
            TelegramIncomingMessage(
                updateId = update.updateId,
                chatId = message.chat.id,
                chatName = message.chat.username?.let { name -> "@$name" } ?: message.chat.firstName ?: message.chat.title ?: message.chat.id.toString(),
                text = if (callback == null) message.text else null,
                callbackId = callback?.id,
                callbackData = callback?.data,
                messageId = message.messageId
            )
        }
    }

    /**
     * [html] liga `parse_mode: HTML`: quem chama já escapou o texto variável.
     * [buttons] viram uma linha de teclado inline embaixo da mensagem.
     */
    open suspend fun sendMessage(token: String, chatId: Long, text: String, html: Boolean = false, buttons: List<TelegramButton> = emptyList()) {
        val body = SendMessageDto(chatId, text, if (html) PARSE_MODE_HTML else null, keyboard(buttons))
        val response = httpClient.post("$baseUrl/bot$token/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SendMessageDto.serializer(), body))
        }
        decode(response, SimpleResponseDto.serializer())
    }

    /** Troca o texto de uma mensagem já enviada, mantendo [buttons] embaixo dela. */
    open suspend fun editMessageText(token: String, chatId: Long, messageId: Long, text: String, buttons: List<TelegramButton> = emptyList()) {
        val body = EditMessageDto(chatId, messageId, text, PARSE_MODE_HTML, keyboard(buttons))
        val response = httpClient.post("$baseUrl/bot$token/editMessageText") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EditMessageDto.serializer(), body))
        }
        decode(response, SimpleResponseDto.serializer())
    }

    /** Fecha o "carregando" do botão no Telegram; [text] aparece como aviso curto. */
    open suspend fun answerCallbackQuery(token: String, callbackId: String, text: String? = null) {
        val response = httpClient.post("$baseUrl/bot$token/answerCallbackQuery") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AnswerCallbackDto.serializer(), AnswerCallbackDto(callbackId, text)))
        }
        decode(response, SimpleResponseDto.serializer())
    }

    private fun keyboard(buttons: List<TelegramButton>): KeyboardDto? =
        if (buttons.isEmpty()) null else KeyboardDto(listOf(buttons.map { button -> ButtonDto(button.text, button.data) }))

    /** Lista do botão "Menu" do Telegram: os comandos aparecem sem digitar `/ajuda`. */
    open suspend fun setMyCommands(token: String, commands: List<Pair<String, String>>) {
        val body = SetCommandsDto(commands.map { (command, description) -> CommandDto(command, description) })
        val response = httpClient.post("$baseUrl/bot$token/setMyCommands") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SetCommandsDto.serializer(), body))
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
        val message: MessageDto? = null,
        @SerialName("callback_query") val callbackQuery: CallbackQueryDto? = null
    )

    @Serializable
    private data class MessageDto(
        val chat: ChatDto,
        val text: String? = null,
        @SerialName("message_id") val messageId: Long? = null
    )

    @Serializable
    private data class CallbackQueryDto(val id: String, val data: String? = null, val message: MessageDto? = null)

    @Serializable
    private data class ChatDto(
        val id: Long,
        val username: String? = null,
        @SerialName("first_name") val firstName: String? = null,
        val title: String? = null
    )

    @Serializable
    private data class SendMessageDto(
        @SerialName("chat_id") val chatId: Long,
        val text: String,
        @SerialName("parse_mode") val parseMode: String? = null,
        @SerialName("reply_markup") val replyMarkup: KeyboardDto? = null
    )

    @Serializable
    private data class EditMessageDto(
        @SerialName("chat_id") val chatId: Long,
        @SerialName("message_id") val messageId: Long,
        val text: String,
        @SerialName("parse_mode") val parseMode: String,
        @SerialName("reply_markup") val replyMarkup: KeyboardDto? = null
    )

    @Serializable
    private data class AnswerCallbackDto(@SerialName("callback_query_id") val callbackQueryId: String, val text: String? = null)

    @Serializable
    private data class KeyboardDto(@SerialName("inline_keyboard") val inlineKeyboard: List<List<ButtonDto>>)

    @Serializable
    private data class ButtonDto(val text: String, @SerialName("callback_data") val callbackData: String)

    @Serializable
    private data class CommandDto(val command: String, val description: String)

    @Serializable
    private data class SetCommandsDto(val commands: List<CommandDto>)

    private companion object {
        const val LONG_POLL_MARGIN_SECONDS = 10
        const val DEFAULT_RETRY_SECONDS = 5L
        const val PARSE_MODE_HTML = "HTML"
    }
}
