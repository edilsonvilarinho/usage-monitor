package com.usagemonitor.presentation.viewmodel

/**
 * Qual janela de cota o card do Histórico mostra quando a fonte tem as duas
 * (issue #320): só a intervalar (5h), só a semanal (7d) ou as duas no mesmo
 * gráfico.
 *
 * Enum próprio, e não valor novo em `PeriodType`: aquele descreve a cota que a
 * API devolve, este descreve o que o **controle** oferece — `BOTH` não é tipo de
 * período nenhum.
 */
enum class HistoryQuotaView {
    INTERVAL,
    WEEKLY,
    BOTH
}
