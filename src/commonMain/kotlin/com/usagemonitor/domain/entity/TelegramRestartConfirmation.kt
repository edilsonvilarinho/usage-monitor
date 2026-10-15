package com.usagemonitor.domain.entity

/**
 * Reinício para instalar pedido por uma conversa do bot (#417).
 *
 * Gravado **antes** de o app fechar e lido pelo app que abre depois: o toque em
 * "Atualizar e reiniciar" só tinha a resposta efêmera do Telegram, e o processo
 * novo não sabia que alguém esperava uma confirmação.
 */
data class TelegramPendingRestart(
    /** A versão pronta que o toque mandou instalar. */
    val targetVersion: String,
    /** A versão em execução no toque: de onde as novidades partem. */
    val fromVersion: String
)

/** O que o app que abriu diz à conversa sobre o reinício pedido. */
enum class TelegramRestartOutcome {
    /** Abriu na versão pedida, ou numa mais nova. */
    UPDATED,

    /** Abriu numa versão anterior à pedida: o instalador não aplicou, ou o Linux restaurou a anterior. */
    NOT_APPLIED
}

/**
 * Decide pela versão em execução, nunca pelo recibo do instalador: no Linux o
 * recibo só é gravado depois do ACK e descreve a atualização anterior quando
 * este código roda (mesma corrida da `releaseNotesDecision`, #127).
 */
fun telegramRestartOutcome(pending: TelegramPendingRestart, currentVersion: String): TelegramRestartOutcome =
    if (compareAppVersions(currentVersion, pending.targetVersion) >= 0) {
        TelegramRestartOutcome.UPDATED
    } else {
        TelegramRestartOutcome.NOT_APPLIED
    }
