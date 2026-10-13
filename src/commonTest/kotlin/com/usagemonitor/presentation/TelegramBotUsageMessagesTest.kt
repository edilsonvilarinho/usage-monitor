package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.presentation.ui.TelegramBotUsageMessages
import kotlin.test.Test
import kotlin.test.assertEquals

/** Leituras do índice e do histórico no bot (#408). */
class TelegramBotUsageMessagesTest {

    private fun row(source: ApiSource, label: String, turns: Int?, throughput: OutputThroughput?) = ComparisonRow(
        source = source, label = label, turnCount = turns, totalTokens = null, costMicros = null, cacheRate = null,
        throughput = throughput, quotaPercent = null
    )

    @Test
    fun `throughput lists measured models fastest first and skips the unmeasured`() {
        val rows = listOf(
            row(ApiSource.ANTHROPIC, "claude-opus-5-5", 212, OutputThroughput(outputTokens = 48_000, generationMillis = 1_000_000)),
            row(ApiSource.CODEX, "gpt-5-codex", 88, OutputThroughput(outputTokens = 57_000, generationMillis = 1_000_000)),
            row(ApiSource.MINIMAX, "MiniMax", null, null)
        )

        assertEquals(
            "<b>⚡ Vazão de saída — 24 h</b>\n" +
                "<b>Codex · gpt-5-codex</b> — <b>57 tok/s</b> · 88 turnos\n" +
                "<b>Anthropic · claude-opus-5-5</b> — <b>48 tok/s</b> · 212 turnos\n" +
                "<i>ponta a ponta: do pedido à última linha</i>",
            TelegramBotUsageMessages.throughput(rows, AppLanguage.PT)
        )
        assertEquals("⚡ Sem vazão medida nas últimas 24 h.", TelegramBotUsageMessages.throughput(listOf(rows[2]), AppLanguage.PT))
        assertEquals("Não deu para ler o índice das sessões CLI agora.", TelegramBotUsageMessages.throughput(null, AppLanguage.PT))
    }
}
