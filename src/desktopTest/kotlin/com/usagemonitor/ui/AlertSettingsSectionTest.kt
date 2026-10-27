package com.usagemonitor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
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
import com.usagemonitor.presentation.ui.components.budgetValidationError
import com.usagemonitor.presentation.ui.components.wrapHour
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

    private fun ComposeUiTest.showSection(
        settings: UsageAlertSettings,
        language: AppLanguage = AppLanguage.PT,
        onSettingsChange: (UsageAlertSettings) -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                AlertSettingsSection(
                    settings = settings,
                    language = language,
                    onSettingsChange = onSettingsChange
                )
            }
        }
    }
}
