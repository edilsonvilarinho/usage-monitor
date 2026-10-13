package com.usagemonitor

import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.SourceHealthObservation
import com.usagemonitor.domain.entity.UsageExportFormat
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
    val focusHud: () -> Boolean = { false },
    /** Nome deste PC (AA20); `null` quando o sistema não o informa. */
    val machineName: () -> String? = { null },
    /**
     * `/exportar` (AA23): o consumo do Claude CLI desde o instante, no formato pedido —
     * só modelo, ferramenta e totais; `null` quando o índice falha.
     */
    val exportUsage: suspend (sinceEpochMillis: Long, format: UsageExportFormat) -> String? = { _, _ -> null },
    /**
     * `/time` (AA16): a presença do time das contas que participam. `null` sem
     * integração de time; falha é o servidor que não respondeu.
     */
    val teamPresence: suspend () -> Result<List<TeamMemberPresence>>? = { null },
    /** AA17: a versão baixada e conferida, pronta para instalar; `null` sem nenhuma. */
    val readyUpdate: () -> String? = { null },
    /** AA17: fecha o app e instala a versão pronta — o mesmo botão da faixa do app. */
    val applyUpdate: () -> Unit = {},
    /** AA18: como cada conta saiu da última coleta; `null` antes da primeira. */
    val sourceHealth: () -> List<SourceHealthObservation>? = { null }
)
