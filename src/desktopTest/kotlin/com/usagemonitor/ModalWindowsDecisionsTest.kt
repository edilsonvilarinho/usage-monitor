package com.usagemonitor

import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** O que as janelas de sessões e de time recebem do painel, por conta (sem janela). */
class ModalWindowsDecisionsTest {

    private val fiveHourEnd = Instant.parse("2026-10-28T17:00:00Z")
    private val weekEnd = Instant.parse("2026-11-01T00:00:00Z")

    private fun anthropic(profileId: String, quotas: List<QuotaInfo>) = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, profileId),
        apiName = "Anthropic",
        quotas = quotas
    )

    private val fiveHour = QuotaInfo("5h", used = 10, total = 100, periodEndAt = fiveHourEnd, unit = UsageUnit.PERCENTAGE)

    @Test
    fun `the five hour window ends at the account's own reset`() {
        val state = UiState.Success(listOf(anthropic("home", emptyList()), anthropic("work", listOf(fiveHour))))

        assertEquals(CliQuotaWindows(fiveHourEndsAt = fiveHourEnd), quotaWindowsForProfile(state, "work"))
        assertEquals(CliQuotaWindows(), quotaWindowsForProfile(state, "home"))
    }

    /** Sem coleta bem-sucedida da conta, o filtro cai para a janela corrida em vez de esvaziar a lista. */
    @Test
    fun `without a successful read of the account the windows are empty`() {
        assertEquals(CliQuotaWindows(), quotaWindowsForProfile(UiState.Loading, "work"))
        assertEquals(CliQuotaWindows(), quotaWindowsForProfile(UiState.Success(listOf(anthropic("work", listOf(fiveHour)))), null))
        assertEquals(CliQuotaWindows(), quotaWindowsForProfile(UiState.Success(listOf(anthropic("home", listOf(fiveHour)))), "work"))
    }

    /** Reset desconhecido não vira fim de janela: só a cota do tipo pedido e com reset conhecido conta. */
    @Test
    fun `the window end reads only a known reset of the asked period`() {
        val unknownReset = fiveHour.copy(hasKnownResetAt = false)
        val weekly = QuotaInfo("7d", used = 1, total = 100, periodEndAt = weekEnd, periodType = PeriodType.WEEKLY, unit = UsageUnit.PERCENTAGE)

        assertNull(anthropic("work", listOf(unknownReset, weekly)).quotaEndAt(PeriodType.INTERVAL))
        assertEquals(weekEnd, anthropic("work", listOf(unknownReset, weekly)).quotaEndAt(PeriodType.WEEKLY))
    }

    @Test
    fun `the credits come in the account's real currency, minor units first`() {
        val credits = QuotaInfo(
            AnthropicQuotaLabels.EXTRA_CREDITS,
            used = 12,
            total = 50,
            periodEndAt = weekEnd,
            unit = UsageUnit.CURRENCY_USD,
            rawUsed = 1_234,
            rawTotal = 5_000,
            currencyCode = "BRL"
        )
        val state = UiState.Success(listOf(anthropic("work", listOf(fiveHour, credits))))

        assertEquals(AccountCreditUsage(usedMinorUnits = 1_234, limitMinorUnits = 5_000, currencyCode = "BRL"), accountCreditsForProfile(state, "work"))
    }

    /** Sem valor bruto (zero), o valor da cota é o que existe. */
    @Test
    fun `without raw minor units the credits fall back to the quota values`() {
        val credits = QuotaInfo(AnthropicQuotaLabels.EXTRA_CREDITS, used = 300, total = 2_000, periodEndAt = weekEnd, unit = UsageUnit.CURRENCY_USD)
        val state = UiState.Success(listOf(anthropic("work", listOf(credits))))

        assertEquals(AccountCreditUsage(usedMinorUnits = 300, limitMinorUnits = 2_000, currencyCode = "USD"), accountCreditsForProfile(state, "work"))
    }

    /** Créditos desligados não criam a cota: a ausência é "desligado", não falha. */
    @Test
    fun `no credits quota, no account or no successful read means no credits`() {
        val state = UiState.Success(listOf(anthropic("work", listOf(fiveHour))))

        assertNull(accountCreditsForProfile(state, "work"))
        assertNull(accountCreditsForProfile(state, "home"))
        assertNull(accountCreditsForProfile(state, null))
        assertNull(accountCreditsForProfile(UiState.NoApisEnabled, "work"))
    }

    @Test
    fun `the history window title names the source in each language`() {
        assertEquals("Histórico - MiniMax", historyWindowTitle(ApiSource.MINIMAX, AppLanguage.PT))
        assertEquals("History - MiniMax", historyWindowTitle(ApiSource.MINIMAX, AppLanguage.EN))
    }
}
