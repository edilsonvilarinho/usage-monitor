package com.usagemonitor

import com.usagemonitor.domain.entity.TelegramPing

/**
 * O que o bot lê do app além do retrato (#408). Um tipo só, em vez de um
 * parâmetro por função nova no [TelegramBotService] e no [TelegramBotHandlers]:
 * cada leitura tem default vazio, e o teste passa só a que exercita.
 */
internal class TelegramBotReadings(
    /** `/ping` (AA19): versão, quando o app abriu e a próxima coleta. */
    val ping: () -> TelegramPing? = { null }
)
