package com.usagemonitor.data

import com.usagemonitor.data.mapper.ANTIGRAVITY_COMMAND_FAILED
import com.usagemonitor.data.mapper.ANTIGRAVITY_NO_QUOTA_WINDOWS
import com.usagemonitor.data.mapper.ANTIGRAVITY_UNRECOGNIZED_OUTPUT
import com.usagemonitor.data.mapper.AntigravityModelTurnException
import com.usagemonitor.data.mapper.AntigravityUsageMapper
import com.usagemonitor.domain.entity.AntigravityQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntigravityUsageMapperTest {

    /**
     * Forma medida contra o agy 1.2.9 (`--output-format json --print /usage`), com a
     * janela de 5h acrescentada: ela aparece no Codenotch, mas não estava ativa na
     * conta medida. A descrição textual do CLI fica de fora de propósito — o mapper
     * não a lê.
     */
    private fun envelope(
        groups: String = MEASURED_GROUPS,
        numTurns: String = "0",
        totalTokens: String = "0",
        commandName: String = "\"usage\"",
        status: String = "\"SUCCESS\""
    ): String = """
        {"conversation_id":"","status":$status,"response":"ignored","duration_seconds":0,
         "num_turns":$numTurns,
         "usage":{"input_tokens":0,"output_tokens":0,"thinking_tokens":0,"cache_read_tokens":0,"total_tokens":$totalTokens},
         "command":{"name":$commandName,"data":{"description":"ignored","groups":$groups}}}
    """.trimIndent()

    @Test
    fun `maps every bucket to a percentage quota with the group in the label`() {
        val stats = AntigravityUsageMapper.parse(envelope())

        assertEquals(ApiSource.ANTIGRAVITY, stats.source)
        assertEquals(
            listOf("Antigravity Gemini 7d", "Antigravity Gemini 5h", "Antigravity Claude/GPT 7d"),
            stats.quotas.map { it.label }
        )
        val geminiWeekly = stats.quotas[0]
        assertEquals(UsageUnit.PERCENTAGE, geminiWeekly.unit)
        assertEquals(100L, geminiWeekly.total)
        // 1 − 0,9592728 = 4,07% usado, truncado.
        assertEquals(4L, geminiWeekly.used)
        assertEquals(PeriodType.WEEKLY, geminiWeekly.periodType)
        assertTrue(geminiWeekly.hasKnownResetAt)
        assertEquals(Instant.parse("2026-09-30T21:57:08Z"), geminiWeekly.periodEndAt)
        assertEquals(0L, geminiWeekly.rawUsed)
        assertEquals(0L, geminiWeekly.rawTotal)

        assertEquals(PeriodType.INTERVAL, stats.quotas[1].periodType)
        assertEquals(22L, stats.quotas[1].used)
    }

    /**
     * Medido: com a janela intacta o `reset_time` é "agora + 7 dias" e andou de
     * 00:38:35 para 00:45:04 entre chamadas. Tomado como reset real, cada coleta
     * pareceria um período novo.
     */
    @Test
    fun `an untouched window has no known reset`() {
        val claude = AntigravityUsageMapper.parse(envelope()).quotas.single { it.label == "Antigravity Claude/GPT 7d" }

        assertEquals(0L, claude.used)
        assertFalse(claude.hasKnownResetAt)
    }

    @Test
    fun `an envelope without proof that the CLI answered alone trips the breaker`() {
        listOf(
            envelope(numTurns = "1"),
            envelope(totalTokens = "12"),
            envelope(commandName = "\"model\""),
            envelope(numTurns = "null"),
            // Um prompt comum volta sem `command` nenhum.
            """{"status":"SUCCESS","num_turns":1,"usage":{"total_tokens":340},"response":"..."}"""
        ).forEach { raw ->
            val error = assertFailsWith<AntigravityModelTurnException> { AntigravityUsageMapper.parse(raw) }
            assertEquals(AntigravityUsageFailureKind.COLLECTION_PAUSED.safeMessage, error.message)
        }
    }

    @Test
    fun `an ERROR envelope is classified without echoing the CLI text`() {
        val auth = assertFailsWith<IllegalStateException> {
            AntigravityUsageMapper.parse("""{"status":"ERROR","error":"You are not logged in as someone@example.com"}""")
        }
        assertEquals(AntigravityUsageFailureKind.AUTHENTICATION_UNAVAILABLE.safeMessage, auth.message)

        val other = assertFailsWith<IllegalStateException> {
            AntigravityUsageMapper.parse("""{"status":"ERROR","error":"backend unavailable"}""")
        }
        assertEquals(ANTIGRAVITY_COMMAND_FAILED, other.message)
    }

    @Test
    fun `invalid buckets are dropped and an empty answer fails`() {
        val stats = AntigravityUsageMapper.parse(
            envelope(
                groups = """[{"name":"Gemini Models","buckets":[
                    {"id":"a","window":"weekly","remaining_fraction":1.5,"reset_time":"2026-09-30T21:57:08Z"},
                    {"id":"b","window":"weekly","remaining_fraction":-0.1},
                    {"id":"c","window":"weekly"},
                    {"id":"d","window":"weekly","remaining_fraction":0.5,"reset_time":"not-a-date"}]}]"""
            )
        )
        val only = stats.quotas.single()
        assertEquals(50L, only.used)
        // Reset ilegível é reset desconhecido, não cota descartada.
        assertFalse(only.hasKnownResetAt)

        val empty = assertFailsWith<IllegalStateException> { AntigravityUsageMapper.parse(envelope(groups = "[]")) }
        assertEquals(ANTIGRAVITY_NO_QUOTA_WINDOWS, empty.message)
    }

    @Test
    fun `unknown windows keep a stable label from the bucket id`() {
        val stats = AntigravityUsageMapper.parse(
            envelope(groups = """[{"name":"Gemini Models","buckets":[{"id":"gemini-daily","window":"daily","remaining_fraction":0.8}]}]""")
        )
        assertEquals("Antigravity Gemini gemini-daily", stats.quotas.single().label)
        assertEquals(PeriodType.REPORTED, stats.quotas.single().periodType)
    }

    @Test
    fun `text that is not the JSON envelope is rejected without echoing it`() {
        listOf("", "Gemini Models\tWeekly Limit Remaining\t96%\t2026-09-30T21:57:08Z", "{not json").forEach { raw ->
            val error = assertFailsWith<IllegalStateException> { AntigravityUsageMapper.parse(raw) }
            assertEquals(ANTIGRAVITY_UNRECOGNIZED_OUTPUT, error.message)
        }
    }

    @Test
    fun `labels expose the group for the card title and the HUD`() {
        assertEquals("Claude/GPT", AntigravityQuotaLabels.groupOf("Antigravity Claude/GPT 7d"))
        assertEquals("Claude/GPT 7d", AntigravityQuotaLabels.withoutSource("Antigravity Claude/GPT 7d"))
        assertEquals(null, AntigravityQuotaLabels.groupOf("Go semanal"))
        assertEquals(null, AntigravityQuotaLabels.withoutSource("Claude 5h"))
    }

    private companion object {
        const val MEASURED_GROUPS = """[
            {"name":"Gemini Models","description":"Models within this group: Gemini Flash, Gemini Pro","buckets":[
                {"id":"gemini-weekly","name":"Weekly Limit Remaining","window":"weekly","remaining_fraction":0.9592728018760681,"reset_time":"2026-09-30T21:57:08Z"},
                {"id":"gemini-5h","name":"Five Hour Limit Remaining","window":"five_hour","remaining_fraction":0.78,"reset_time":"2026-09-24T06:12:31Z"}]},
            {"name":"Claude and GPT models","buckets":[
                {"id":"3p-weekly","name":"Weekly Limit Remaining","window":"weekly","remaining_fraction":1,"reset_time":"2026-10-01T00:38:53Z"}]}
        ]"""
    }
}
