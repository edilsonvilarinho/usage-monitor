package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.terminal.TerminalAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TerminalJsonTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")

    private val reading = TerminalReading(
        accounts = listOf(
            TerminalAccount(
                ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "Padrão", now, listOf(70, 90),
                listOf(UsageSnapshotQuota("Claude 5h", PeriodType.INTERVAL, UsageUnit.PERCENTAGE, 91, 100, 91, now + 1.hours, "USD"))
            ),
            TerminalAccount(
                ApiSource.DEEPSEEK, "DEEPSEEK", "DeepSeek", null, now, listOf(70, 90),
                listOf(UsageSnapshotQuota("Saldo", PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 0, 782, null, null, "USD"))
            )
        ),
        collector = CollectorKind.SERVICE,
        language = AppLanguage.PT,
        now = now
    )

    @Test
    fun `the contract fields are stable, in English and snake case`() {
        val root = Json.parseToJsonElement(TerminalJson.encode(reading)).jsonObject
        assertEquals(setOf("version", "generated_at", "last_collected_at", "collector_running", "collector", "quotas"), root.keys)
        assertEquals("service", root.getValue("collector").jsonPrimitive.content)
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        val quota = root.getValue("quotas").jsonArray.first().jsonObject
        assertEquals(
            setOf("id", "source", "source_name", "account", "label", "period", "unit", "used", "total", "percent", "state", "thresholds", "resets_at", "currency", "fetched_at", "window_starts_at", "time_elapsed_percent"),
            quota.keys
        )
        assertEquals("anthropic:padrao:claude-5h", quota.getValue("id").jsonPrimitive.content)
        assertEquals("anthropic", quota.getValue("source").jsonPrimitive.content)
        assertEquals("interval", quota.getValue("period").jsonPrimitive.content)
        assertEquals("critical", quota.getValue("state").jsonPrimitive.content)
        assertEquals("2026-10-21T18:23:00Z", quota.getValue("resets_at").jsonPrimitive.content)
    }

    @Test
    fun `a balance has no percent, state or reset, written as null`() {
        val root = Json.parseToJsonElement(TerminalJson.encode(reading)).jsonObject
        val balance = root.getValue("quotas").jsonArray[1].jsonObject
        assertEquals(JsonNull, balance.getValue("percent"))
        assertEquals(JsonNull, balance.getValue("state"))
        assertEquals(JsonNull, balance.getValue("resets_at"))
        assertEquals(JsonNull, balance.getValue("account"))
        assertEquals("currency_usd", balance.getValue("unit").jsonPrimitive.content)
    }

    @Test
    fun `ascii only output escapes accents and stays valid json with the same data`() {
        val text = TerminalJson.encode(reading, asciiOnly = true)
        assertTrue(text.all { char -> char.code < 128 })
        assertTrue(text.contains("Padr\\u00e3o"))
        val quota = Json.parseToJsonElement(text).jsonObject.getValue("quotas").jsonArray.first().jsonObject
        assertEquals("Padrão", quota.getValue("account").jsonPrimitive.content)
    }

    @Test
    fun `no credential or path field leaks into the contract`() {
        val text = TerminalJson.encode(reading)
        assertFalse(text.contains("email"))
        assertFalse(text.contains("token"))
        assertFalse(text.contains("path"))
    }
}
