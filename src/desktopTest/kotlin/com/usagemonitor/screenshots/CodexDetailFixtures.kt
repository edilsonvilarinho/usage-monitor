package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.CodexCliRolloutSource
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Sessão Codex do print da #393: as três primeiras respostas com os valores do
 * rollout real, o resto sintético com uma compactação na resposta 150 e uma
 * resposta a cada 17 sem vazão medida.
 */
internal fun codexDetailFixture(responses: Int = 220): CodexCliSessionDetail {
    val start = Instant.parse("2026-10-06T16:10:11Z")
    val real = listOf(Triple(35_789L, 22_272L, 194L), Triple(40_463L, 35_584L, 259L), Triple(48_627L, 40_320L, 351L))
    val turns = (0 until responses).map { seq ->
        val (input, cached, output) = real.getOrNull(seq) ?: run {
            val context = if (seq < 150) 36_000L + seq * 1_480L else 60_000L + (seq - 150) * 2_600L
            Triple(context, context * 94 / 100, 300L + (seq * 37L) % 900L)
        }
        val at = start + (seq * 39).seconds
        CodexCliSessionTurn(
            sessionId = "01a111fa-cd9a-7751-b28d-9d8fd0205c4a",
            turnId = "turn-${seq / 10}",
            responseId = "resp_$seq",
            seq = seq,
            ts = at,
            model = "gpt-6.1-sol",
            source = CodexCliRolloutSource.VSCODE,
            usage = CodexCliUsageDelta(
                inputTokens = input,
                cachedInputTokens = cached,
                outputTokens = output,
                reasoningOutputTokens = output / 3,
                totalTokens = input + output
            ),
            requestTs = if (seq % 17 == 0) null else at - (output / 30L).coerceAtLeast(1L).seconds
        )
    }
    val summary = CodexCliSessionSummary(
        sessionId = "01a111fa-cd9a-7751-b28d-9d8fd0205c4a",
        filePath = "rollout.jsonl",
        cwd = """C:\Users\dev\workspace\usage-monitor""",
        firstTs = turns.first().ts,
        lastTs = turns.last().ts,
        primaryModel = "gpt-6.1-sol",
        originator = "Codex Desktop",
        source = CodexCliRolloutSource.VSCODE,
        cliVersion = "0.160.1",
        turnCount = responses / 10,
        responseCount = responses,
        inputTokens = turns.sumOf { it.usage.inputTokens },
        cachedInputTokens = turns.sumOf { it.usage.cachedInputTokens },
        outputTokens = turns.sumOf { it.usage.outputTokens },
        reasoningOutputTokens = turns.sumOf { it.usage.reasoningOutputTokens },
        totalTokens = turns.sumOf { it.usage.totalTokens }
    )
    return CodexCliSessionDetail(summary = summary, turns = turns)
}
