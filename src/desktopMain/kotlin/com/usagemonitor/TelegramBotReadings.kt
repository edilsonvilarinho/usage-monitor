package com.usagemonitor

import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.UsageSpike

/**
 * O que o bot lê do app além do retrato (#408). Um tipo só, em vez de um
 * parâmetro por função nova no [TelegramBotService] e no [TelegramBotHandlers]:
 * cada leitura tem default vazio, e o teste passa só a que exercita.
 */
internal class TelegramBotReadings(
    /** `/ping` (AA19): versão, quando o app abriu e a próxima coleta. */
    val ping: () -> TelegramPing? = { null },
    /** `/pico` (AA11): as anomalias de gasto que o painel já calcula para o alerta. */
    val spikes: () -> List<UsageSpike> = { emptyList() },
    /**
     * Linhas da comparação entre modelos (#386) desde o instante dado (`/vazao`,
     * `/comparar`); `null` quando o índice CLI falha — "não medido", não lista vazia.
     */
    val comparison: suspend (sinceEpochMillis: Long) -> List<ComparisonRow>? = { null },
    /** Histórico de cada conta da última coleta no intervalo, na ordem do retrato. */
    val accountHistory: suspend (HistoryRange) -> List<TelegramAccountHistory> = { emptyList() },
    /** `/hud` (AA29): pede à HUD que venha à frente; `false` quando o pedido não foi gravado. */
    val focusHud: () -> Boolean = { false }
)
