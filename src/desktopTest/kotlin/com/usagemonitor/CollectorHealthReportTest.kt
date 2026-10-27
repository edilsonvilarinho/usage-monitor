package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CollectorWebHealth
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A saúde que o `service status` lê de fora (#440): cada conta coletada, as que só
 * falharam, o bot, o time e o acesso web — tudo derivado do estado vivo do motor.
 */
class CollectorHealthReportTest {

    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val earlier = Instant.parse("2026-10-27T14:00:00Z")

    private fun report(
        dashboard: UiState = UiState.Loading,
        telegram: TelegramBotStatus = TelegramBotStatus.Off,
        team: TeamIntegrationSettings = TeamIntegrationSettings(),
        sync: TeamSyncStatus = TeamSyncStatus(),
        web: WebAccessStatus = WebAccessStatus.Stopped
    ) = collectorHealthOf(
        dashboard = dashboard,
        nextCollectionAt = now,
        telegramStatus = telegram,
        telegramSettings = TelegramBotSettings(authorizedChats = listOf(TelegramChat(1L, "ana"), TelegramChat(2L, "bia"))),
        teamSettings = team,
        teamSync = sync,
        webStatus = web,
        now = now,
        teamClockOffsetMillis = 1_500L
    )

    @Test
    fun `collected accounts carry their failure and failing-only accounts still appear`() {
        val anthropic = UsageTargetKey.forSource(ApiSource.ANTHROPIC)
        val dashboard = UiState.Success(
            data = listOf(
                ApiUsageStats(source = ApiSource.ANTHROPIC, apiName = "Anthropic", quotas = emptyList(), profileLabel = "trabalho"),
                ApiUsageStats(source = ApiSource.CODEX, apiName = "Codex", quotas = emptyList())
            ),
            errors = listOf(
                UiApiError(target = anthropic, message = "Anthropic HTTP 429: {}", retryAt = now),
                UiApiError(ApiSource.MINIMAX, "MiniMax HTTP 500: boom")
            )
        )

        val health = report(dashboard = dashboard)

        assertEquals(now.toEpochMilliseconds(), health.updatedAtEpochMillis)
        assertEquals(now.toEpochMilliseconds(), health.nextCollectionAtEpochMillis)
        assertEquals(listOf(ApiSource.ANTHROPIC, ApiSource.CODEX, ApiSource.MINIMAX), health.sources.map { it.source })
        val claude = health.sources[0]
        assertEquals("Anthropic trabalho", claude.label)
        assertEquals(false, claude.ok)
        assertEquals(now.toEpochMilliseconds(), claude.retryAtEpochMillis)
        assertEquals(true, health.sources[1].ok)
        assertNull(health.sources[1].failure)
        assertEquals(false, health.sources[2].ok)
        assertEquals("MiniMax HTTP 500: boom", health.sources[2].failure)
    }

    @Test
    fun `a dashboard error lists only the failing accounts`() {
        val health = report(dashboard = UiState.Error(listOf(UiApiError(ApiSource.DEEPSEEK, "offline"))))

        assertEquals(listOf(ApiSource.DEEPSEEK), health.sources.map { it.source })
        assertEquals(emptyList(), report(dashboard = UiState.Loading).sources)
    }

    @Test
    fun `telegram state is written as a word, with the bot name only when connected`() {
        assertEquals("off", report(telegram = TelegramBotStatus.Off).telegram.state)
        assertEquals("connecting", report(telegram = TelegramBotStatus.Connecting).telegram.state)
        assertEquals("failed", report(telegram = TelegramBotStatus.Failed("x")).telegram.state)
        assertEquals("token_rejected", report(telegram = TelegramBotStatus.TokenRejected).telegram.state)
        val connected = report(telegram = TelegramBotStatus.Connected(chatCount = 2, botUsername = "monitor_bot")).telegram
        assertEquals("connected", connected.state)
        assertEquals("monitor_bot", connected.botUsername)
        assertEquals(2, connected.chatCount)
    }

    @Test
    fun `team failure only shows when it is newer than the last success`() {
        val team = TeamIntegrationSettings(enabled = true, serverUrl = "http://t/", apiKey = "k", alias = "a", deviceId = "d")
        val failedAfter = report(
            team = team,
            sync = TeamSyncStatus(lastSuccessAt = earlier, lastFailureAt = now, lastFailureMessage = "401")
        ).team
        val recovered = report(
            team = team,
            sync = TeamSyncStatus(lastSuccessAt = now, lastFailureAt = earlier, lastFailureMessage = "401")
        ).team

        assertEquals(true, failedAfter.active)
        assertEquals("http://t", failedAfter.server)
        assertEquals("401", failedAfter.lastFailure)
        assertEquals(1_500L, failedAfter.clockOffsetMillis)
        assertNull(recovered.lastFailure)
        assertEquals(now.toEpochMilliseconds(), recovered.lastSuccessAtEpochMillis)
        assertNull(report().team.server)
        assertEquals("never", report(sync = TeamSyncStatus(lastFailureAt = now, lastFailureMessage = "never")).team.lastFailure)
    }

    @Test
    fun `web access never carries the token`() {
        assertEquals(CollectorWebHealth(), report(web = WebAccessStatus.Stopped).web)
        assertEquals(
            CollectorWebHealth("running", 8_123, listOf("192.168.0.2")),
            report(web = WebAccessStatus.Running(8_123, listOf("192.168.0.2"))).web
        )
        assertEquals(CollectorWebHealth("failed", failure = "porta ocupada"), report(web = WebAccessStatus.Failed("porta ocupada")).web)
    }
}
