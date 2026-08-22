package com.usagemonitor.domain.entity

/**
 * O que o instalador silencioso registrou sobre a última tentativa.
 *
 * Existe porque atualização automática que falha é **invisível**: o usuário
 * fecha o app, o instalador roda sem tela e, se algo der errado, o app volta na
 * versão antiga sem nada dizer. Foi assim que o episódio da tentativa anterior
 * não deixou rastro.
 *
 * O arquivo é escrito pelo instalador NSIS em
 * `%USERPROFILE%\.usage-monitor\update-receipt.properties`, antes do
 * relançamento e também nos caminhos de falha.
 */
data class AppUpdateReceipt(
    val version: String,
    /** Versão que estava instalada. Nula quando o instalador não conseguiu lê-la. */
    val previousVersion: String?,
    val status: AppUpdateReceiptStatus,
    /**
     * Motivo curto da falha, em vocabulário do instalador (`locked`,
     * `swap-failed`). Nulo no sucesso.
     */
    val reason: String?
)

enum class AppUpdateReceiptStatus { SUCCESS, FAILED }
