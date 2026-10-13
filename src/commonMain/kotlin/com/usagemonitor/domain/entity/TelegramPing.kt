package com.usagemonitor.domain.entity

import kotlin.time.Instant

/**
 * Resposta do `/ping` (#408, AA19): o app está aberto e coletando? Diz a versão,
 * desde quando o processo está de pé e a próxima coleta agendada — o que separa
 * "o bot está calado porque não há nada" de "o PC desligou".
 */
data class TelegramPing(
    val version: String,
    val startedAt: Instant,
    /** `null` quando o painel ainda não agendou a primeira coleta. */
    val nextRefreshAt: Instant?
)
