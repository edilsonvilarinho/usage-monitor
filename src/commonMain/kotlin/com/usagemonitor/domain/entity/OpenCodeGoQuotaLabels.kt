package com.usagemonitor.domain.entity

/**
 * Rótulos das três janelas da assinatura OpenCode Go.
 *
 * Cada rótulo é metade da chave da série histórica ([QuotaSeriesKey]) e do
 * índice do SQLite: renomear qualquer um destes valores quebra a continuidade
 * do histórico já gravado.
 *
 * O prefixo `Go` fica no rótulo mesmo com o nome da fonte já no cabeçalho do
 * card porque a tela de histórico agrupa por rótulo e ali `5h` colidiria
 * visualmente com a janela de 5h da Anthropic.
 */
object OpenCodeGoQuotaLabels {
    const val ROLLING = "Go 5h"
    const val WEEKLY = "Go semanal"
    const val MONTHLY = "Go mensal"
}
