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

/**
 * O artefato baixado ainda serve para alguma coisa?
 *
 * O `Setup.exe` são ~120 MB e fica em `~/.usage-monitor/updates/` depois de
 * aplicado, porque a única poda que existe roda em `prepare()` — ou seja, só
 * quando uma versão **nova** é anunciada. Sem versão nova nunca há poda, e o
 * arquivo mora ali para sempre.
 *
 * As duas condições importam, e nenhuma delas basta sozinha:
 *
 * - **Falha mantém o artefato de propósito.** O download é retomável e um
 *   arquivo íntegro no disco não toca a rede; apagá-lo obrigaria a rebaixar
 *   120 MB na tentativa seguinte.
 * - **Versão diferente da que está rodando é prova de que a troca não
 *   aconteceu.** Recibo de sucesso da 39 com o app em 37 descreve outra coisa
 *   que não este binário, e o artefato ainda pode ser o caminho até ela.
 */
fun shouldDiscardUpdateArtifacts(receipt: AppUpdateReceipt?, currentVersion: String): Boolean {
    if (receipt == null || receipt.status != AppUpdateReceiptStatus.SUCCESS) {
        return false
    }
    return receipt.version == currentVersion
}
