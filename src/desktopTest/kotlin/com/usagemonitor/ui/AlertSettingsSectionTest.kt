package com.usagemonitor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_QUOTA_COVERAGE_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_SPIKE_FACTOR_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_SPIKE_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.AlertSettingsSection
import androidx.compose.ui.test.assertTextEquals
import com.usagemonitor.domain.entity.QuotaThresholdOverride
import com.usagemonitor.domain.entity.DEFAULT_RESET_REMINDER_MINUTES
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_ACCOUNT_THRESHOLDS_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_RESET_REMINDER_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_QUOTA_RESET_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.ALERT_SETTINGS_BUDGET_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.budgetValidationError
import com.usagemonitor.presentation.ui.components.wrapHour
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Controles da anomalia de gasto na aba Alertas (issue #163).
 *
 * A seção é exercitada direto, e não através de `SettingsDialogContent`: ela é
 * stateless e recebe o valor inteiro já alterado, então o diálogo em volta não
 * acrescentaria nada ao que está sendo verificado.
 */
@OptIn(ExperimentalTestApi::class)
class AlertSettingsSectionTest {

    @Test
    fun `the spike controls appear only when the alert is on`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true))

        onNodeWithText("Avisar quando o consumo do dia fugir do habitual").assertIsDisplayed()
        onNodeWithTag(ALERT_SETTINGS_SPIKE_FACTOR_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `turning the spike alert off hides the factor`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = false))

        onNodeWithText("Avisar quando o consumo do dia fugir do habitual").assertIsDisplayed()
        onNodeWithTag(ALERT_SETTINGS_SPIKE_FACTOR_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `toggling the spike switch reports the whole settings object`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithTag(ALERT_SETTINGS_SPIKE_SWITCH_TEST_TAG).performClick()

        assertEquals(false, updated?.spikeAlertsEnabled)
    }

    @Test
    fun `choosing a factor reports it`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithText("5×").performClick()

        assertEquals(5.0, updated?.spikeFactor)
    }

    /**
     * A frase diz contra o que a régua mede, e diz também quando ela **não** mede:
     * sem isso, o silêncio de quem tem pouco histórico pareceria defeito.
     */
    @Test
    fun `the explanation names the reference and the refusals`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true))

        onNodeWithText(
            "A referência é a mediana dos últimos dias, no mesmo horário — não o limite da " +
                "cota. Sem pelo menos três dias medidos, ou com consumo habitual perto de " +
                "zero, nenhum aviso é emitido."
        ).assertIsDisplayed()
    }

    // ------------------------------------------------------------------
    // Alcance do limiar percentual (issue #194)
    // ------------------------------------------------------------------

    /**
     * A aba oferecia o limiar sem qualificação e cinco das onze fontes nunca são
     * alcançadas — por duas mecânicas diferentes. Sem esta frase, quem lê a tela
     * acredita estar protegido nas onze, que é a falha silenciosa da issue.
     */
    @Test
    fun `the quota threshold declares which sources it does not reach`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = true))

        onNodeWithTag(ALERT_SETTINGS_QUOTA_COVERAGE_TEST_TAG).assertIsDisplayed()
        onNodeWithText(
            "O limiar mede percentual contra o teto da cota. Saldo pré-pago não tem teto " +
                "(DeepSeek, OpenRouter) e atividade observada não informa limite " +
                "(OpenCode Zen Free, Kilo Free, Gemini CLI): nessas fontes nenhum limiar é avaliado."
        ).assertIsDisplayed()
    }

    /**
     * Desligado, o texto é justamente o que explica o que ligar o interruptor não
     * vai cobrir — esconder ali seria devolver o silêncio.
     */
    @Test
    fun `the coverage note stays with the alert turned off`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false))

        onNodeWithTag(ALERT_SETTINGS_QUOTA_COVERAGE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `english translates the coverage note`() = runDesktopComposeUiTest {
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = true),
            language = AppLanguage.EN
        )

        onNodeWithText(
            "The threshold measures a percentage against the quota ceiling. Prepaid balance has " +
                "no ceiling (DeepSeek, OpenRouter) and observed activity reports no limit " +
                "(OpenCode Zen Free, Kilo Free, Gemini CLI): on those sources no threshold is ever evaluated."
        ).assertIsDisplayed()
    }

    @Test
    fun `english translates the spike controls`() = runDesktopComposeUiTest {
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true),
            language = AppLanguage.EN
        )

        onNodeWithText("Warn when the day's usage departs from the usual").assertIsDisplayed()
        onNodeWithTag(ALERT_SETTINGS_SPIKE_FACTOR_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `own thresholds per account are listed and can be dropped`() = runDesktopComposeUiTest {
        var reported: UsageAlertSettings? = null
        val settings = UsageAlertSettings(
            quotaPercentsByTarget = mapOf(
                "b" to QuotaThresholdOverride(label = "Pessoal", percents = listOf(90)),
                "a" to QuotaThresholdOverride(label = "Empresa", percents = listOf(75, 95))
            )
        )
        showSection(settings, language = AppLanguage.EN, onSettingsChange = { reported = it })

        // Ordem por rótulo: a mesma lista nas duas leituras.
        onNodeWithTag(ALERT_SETTINGS_ACCOUNT_THRESHOLDS_TEST_TAG)
            .assertTextEquals("Own threshold: Empresa 75%, 95% · Pessoal 90%")
        onNodeWithText("Use the general one everywhere").performClick()

        assertEquals(emptyMap(), reported?.quotaPercentsByTarget)
    }

    @Test
    fun `no threshold selected says no quota warning will be sent`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings(quotaPercents = emptyList()), language = AppLanguage.EN)

        onNodeWithText("No threshold selected: no quota warning will be sent.").assertIsDisplayed()
    }

    @Test
    fun `reset reminder switch writes the default minutes and clears them`() = runDesktopComposeUiTest {
        var reported: UsageAlertSettings? = null
        showSection(UsageAlertSettings(resetReminderMinutes = null), onSettingsChange = { reported = it })

        onNodeWithTag(ALERT_SETTINGS_RESET_REMINDER_SWITCH_TEST_TAG).performClick()

        assertEquals(DEFAULT_RESET_REMINDER_MINUTES, reported?.resetReminderMinutes)
    }

    @Test
    fun `budget text accepts comma and refuses what is not a dollar amount`() {
        assertNull(budgetValidationError("", isPt = true))
        assertNull(budgetValidationError(" 200,50 ", isPt = true))
        assertEquals("Informe um valor em dólares, ex.: 200", budgetValidationError("abc", isPt = true))
        assertEquals("Enter a dollar amount, e.g. 200", budgetValidationError("-5", isPt = false))
        assertEquals(23, wrapHour(-1))
        assertEquals(1, wrapHour(25))
    }

    @Test
    fun `the section header is Alertas in Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT)

        onNodeWithText("Alertas").assertIsDisplayed()
        onNodeWithText("Alerts").assertDoesNotExist()
    }

    @Test
    fun `the section header is Alerts in English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT, language = AppLanguage.EN)

        onNodeWithText("Alerts").assertIsDisplayed()
        onNodeWithText("Alertas").assertDoesNotExist()
    }

    @Test
    fun `quota chips report nothing while the quota alert is off`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithText("50%").performClick()

        assertNull(updated)
    }

    @Test
    fun `a quota chip reports the sorted selection while the alert is on`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = true),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithText("50%").performClick()

        assertEquals(listOf(50, 75, 90, 100), updated?.quotaPercents)
    }

    @Test
    fun `reset switch is off while the quota alert is off`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false, quotaResetAlertsEnabled = true))

        onNodeWithTag(ALERT_SETTINGS_QUOTA_RESET_SWITCH_TEST_TAG).assertIsOff()
    }

    @Test
    fun `reset switch is on when both quota alerts are on`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = true, quotaResetAlertsEnabled = true))

        onNodeWithTag(ALERT_SETTINGS_QUOTA_RESET_SWITCH_TEST_TAG).assertIsOn()
    }

    @Test
    fun `turning the reset switch on with the quota alert off reports true`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false, quotaResetAlertsEnabled = false),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithTag(ALERT_SETTINGS_QUOTA_RESET_SWITCH_TEST_TAG).performClick()

        assertEquals(true, updated?.quotaResetAlertsEnabled)
    }

    @Test
    fun `no threshold in Portuguese warns only while the quota alert is on`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings(quotaAlertsEnabled = true, quotaPercents = emptyList()))

        onNodeWithText("Nenhum limiar escolhido: nenhum aviso de quota será emitido.").assertIsDisplayed()
    }

    @Test
    fun `no threshold warning stays hidden while the quota alert is off`() = runDesktopComposeUiTest {
        showSection(
            settings = UsageAlertSettings(quotaAlertsEnabled = false, quotaPercents = emptyList()),
            language = AppLanguage.EN
        )

        onNodeWithText("No threshold selected: no quota warning will be sent.").assertDoesNotExist()
    }

    @Test
    fun `no threshold warning stays hidden when a threshold is chosen`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = true), language = AppLanguage.EN)

        onNodeWithText("No threshold selected: no quota warning will be sent.").assertDoesNotExist()
    }

    @Test
    fun `reset note is Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT)

        onNodeWithText("Só das cotas que passaram de um limiar na janela anterior.").assertIsDisplayed()
        onNodeWithText("Only for quotas that crossed a threshold in the previous window.").assertDoesNotExist()
    }

    @Test
    fun `reset note is English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT, language = AppLanguage.EN)

        onNodeWithText("Only for quotas that crossed a threshold in the previous window.").assertIsDisplayed()
    }

    @Test
    fun `turning the reset reminder off reports no minutes`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(UsageAlertSettings(resetReminderMinutes = 15), onSettingsChange = { value -> updated = value })

        onNodeWithTag(ALERT_SETTINGS_RESET_REMINDER_SWITCH_TEST_TAG).performClick()

        val result = assertNotNull(updated)
        assertNull(result.resetReminderMinutes)
    }

    @Test
    fun `reset reminder note names the minutes in Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings(resetReminderMinutes = 15))

        onNodeWithText("15 min antes, só de cota acima de um limiar. No bot, /avise 30 muda os minutos.").assertIsDisplayed()
    }

    @Test
    fun `reset reminder note names the minutes in English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings(resetReminderMinutes = 15), language = AppLanguage.EN)

        onNodeWithText("15 min before, only for a quota above a threshold. In the bot, /remind 30 changes the minutes.").assertIsDisplayed()
    }

    @Test
    fun `CLI saturation switch label is Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT)

        onNodeWithText("Avisar quando uma sessão CLI saturar").assertIsDisplayed()
        onNodeWithText("Warn when a CLI session saturates").assertDoesNotExist()
    }

    @Test
    fun `CLI saturation switch label is English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT, language = AppLanguage.EN)

        onNodeWithText("Warn when a CLI session saturates").assertIsDisplayed()
    }

    @Test
    fun `stalled session label is Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true))

        onNodeWithText("Avisar quando uma sessão CLI ficar sem resposta").assertIsDisplayed()
        onNodeWithText("Warn when a CLI session goes unanswered").assertDoesNotExist()
    }

    @Test
    fun `stalled session label is English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true), language = AppLanguage.EN)

        onNodeWithText("Warn when a CLI session goes unanswered").assertIsDisplayed()
    }

    @Test
    fun `stalled explanation is Portuguese in full`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true))

        onNodeWithText(
            "O aviso diz apenas que não houve resposta desde o último pedido — o app não " +
                "verifica o processo do Claude Code. Pendência acima de 24h é tratada como " +
                "sessão abandonada e não gera aviso."
        ).assertIsDisplayed()
    }

    @Test
    fun `stalled explanation is English in full`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true), language = AppLanguage.EN)

        onNodeWithText(
            "The warning only says there was no reply since the last request — the app does " +
                "not check the Claude Code process. Anything pending for over 24h counts as " +
                "an abandoned session and raises no warning."
        ).assertIsDisplayed()
    }

    @Test
    fun `stall threshold labels and the stored choice are selected`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true))

        onNodeWithText("30min").assertIsDisplayed()
        onNodeWithText("1h").assertIsDisplayed()
        onNodeWithText("2h").assertIsSelected()
        onNodeWithText("4h").assertIsNotSelected()
    }

    @Test
    fun `a stored four hour stall threshold selects the four hour segment`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true, stallThresholdMillis = 240 * 60_000L))

        onNodeWithText("4h").assertIsSelected()
        onNodeWithText("2h").assertIsNotSelected()
    }

    @Test
    fun `choosing thirty minutes reports the stall threshold in milliseconds`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(stalledSessionAlertsEnabled = true),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithText("30min").performClick()

        assertEquals(1_800_000L, updated?.stallThresholdMillis)
    }

    @Test
    fun `the default spike factor is the selected segment`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true))

        onNodeWithText("3×").assertIsSelected()
        onNodeWithText("2×").assertIsNotSelected()
        onNodeWithText("5×").assertIsNotSelected()
    }

    @Test
    fun `a stored five times factor selects the five times segment`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true, spikeFactor = 5.0))

        onNodeWithText("5×").assertIsSelected()
        onNodeWithText("3×").assertIsNotSelected()
    }

    @Test
    fun `spike explanation is English in full`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(spikeAlertsEnabled = true), language = AppLanguage.EN)

        onNodeWithText(
            "The reference is the median of the last few days, at the same time of day — not " +
                "the quota limit. With fewer than three measured days, or with usual " +
                "consumption near zero, no warning is sent."
        ).assertIsDisplayed()
    }

    @Test
    fun `turning the quiet period off reports no range`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(quietHours = QuietHours(22, 8)),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithTag(ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG).performClick()

        val result = assertNotNull(updated)
        assertNull(result.quietHours)
    }

    @Test
    fun `turning the quiet period on reports the 22h to 8h range`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings.DEFAULT.copy(quietHours = null),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithTag(ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG).performClick()

        assertEquals(QuietHours(22, 8), updated?.quietHours)
    }

    @Test
    fun `quiet range steppers are Das and Ate in Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quietHours = QuietHours(22, 8)))

        onNodeWithText("Das").assertIsDisplayed()
        onNodeWithText("Até").assertIsDisplayed()
        onNodeWithText("To").assertDoesNotExist()
        onNodeWithText("No silêncio o aviso é adiado, não descartado: ele sai quando o período terminar.").assertIsDisplayed()
    }

    @Test
    fun `quiet range steppers are From and To in English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quietHours = QuietHours(22, 8)), language = AppLanguage.EN)

        onNodeWithText("From").assertIsDisplayed()
        onNodeWithText("To").assertIsDisplayed()
        onNodeWithText("Até").assertDoesNotExist()
        onNodeWithText("While muted the warning is postponed, not dropped: it is sent once the range ends.").assertIsDisplayed()
    }

    @Test
    fun `budget label is Portuguese`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT)

        onNodeWithText("Teto mensal em USD (vazio desliga)").assertIsDisplayed()
        onNodeWithText("Monthly cap in USD (empty disables)").assertDoesNotExist()
    }

    @Test
    fun `budget label is English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT, language = AppLanguage.EN)

        onNodeWithText("Monthly cap in USD (empty disables)").assertIsDisplayed()
        onNodeWithText("Teto mensal em USD (vazio desliga)").assertDoesNotExist()
    }

    @Test
    fun `budget note is Portuguese in full`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT)

        onNodeWithText(
            "O teto mede o custo estimado das sessões CLI, em USD. Os créditos de uso da Anthropic aparecem à parte, na moeda da conta."
        ).assertIsDisplayed()
    }

    @Test
    fun `budget note is English in full`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT, language = AppLanguage.EN)

        onNodeWithText(
            "The cap measures the estimated CLI session cost, in USD. Anthropic usage credits are shown separately, in the account currency."
        ).assertIsDisplayed()
    }

    @Test
    fun `leaving the budget field commits the typed amount`() = runDesktopComposeUiTest {
        var committed: String? = null
        showSection(
            settings = UsageAlertSettings(quotaPercents = listOf(50)),
            onBudgetCommit = { text -> committed = text }
        )

        onNodeWithTag(ALERT_SETTINGS_BUDGET_FIELD_TEST_TAG).performClick()
        onNodeWithTag(ALERT_SETTINGS_BUDGET_FIELD_TEST_TAG).performTextInput("300")
        onNodeWithTag(ALERT_SETTINGS_BUDGET_FIELD_TEST_TAG).performKeyInput { pressKey(Key.Tab) }

        assertEquals("300", committed)
    }

    @Test
    fun `restore defaults button is hidden at the default thresholds`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT)

        onNodeWithText("Voltar aos limiares padrão").assertDoesNotExist()
    }

    @Test
    fun `restore defaults button restores the default thresholds in Portuguese`() = runDesktopComposeUiTest {
        var updated: UsageAlertSettings? = null
        showSection(
            settings = UsageAlertSettings(quotaPercents = listOf(50)),
            onSettingsChange = { value -> updated = value }
        )

        onNodeWithText("Voltar aos limiares padrão").performClick()

        assertEquals(listOf(75, 90, 100), updated?.quotaPercents)
    }

    @Test
    fun `restore defaults button is English`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings(quotaPercents = listOf(50)), language = AppLanguage.EN)

        onNodeWithText("Restore default thresholds").assertIsDisplayed()
        onNodeWithText("Voltar aos limiares padrão").assertDoesNotExist()
    }

    @Test
    fun `own thresholds line and revert button are Portuguese`() = runDesktopComposeUiTest {
        var reported: UsageAlertSettings? = null
        val settings = UsageAlertSettings(
            quotaPercentsByTarget = mapOf(
                "b" to QuotaThresholdOverride(label = "Pessoal", percents = listOf(90)),
                "a" to QuotaThresholdOverride(label = "Empresa", percents = listOf(75, 95))
            )
        )
        showSection(settings, onSettingsChange = { reported = it })

        onNodeWithTag(ALERT_SETTINGS_ACCOUNT_THRESHOLDS_TEST_TAG)
            .assertTextEquals("Limiar próprio: Empresa 75%, 95% · Pessoal 90%")
        onNodeWithText("Usar o geral em todas").performClick()

        assertEquals(emptyMap(), reported?.quotaPercentsByTarget)
    }

    private fun ComposeUiTest.showSection(
        settings: UsageAlertSettings,
        language: AppLanguage = AppLanguage.PT,
        onSettingsChange: (UsageAlertSettings) -> Unit = {},
        budgetText: String = "",
        onBudgetCommit: (String) -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                AlertSettingsSection(
                    settings = settings,
                    language = language,
                    onSettingsChange = onSettingsChange,
                    budgetText = budgetText,
                    onBudgetCommit = onBudgetCommit
                )
            }
        }
    }
}
