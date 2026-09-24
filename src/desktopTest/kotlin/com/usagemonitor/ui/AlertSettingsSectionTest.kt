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
import com.usagemonitor.presentation.ui.theme.AppTheme
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
     * A aba oferecia o limiar sem qualificação e seis das onze fontes nunca são
     * alcançadas — por duas mecânicas diferentes. Sem esta frase, quem lê a tela
     * acredita estar protegido em todas, que é a falha silenciosa da issue.
     */
    @Test
    fun `the quota threshold declares which sources it does not reach`() = runDesktopComposeUiTest {
        showSection(UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = true))

        onNodeWithTag(ALERT_SETTINGS_QUOTA_COVERAGE_TEST_TAG).assertIsDisplayed()
        onNodeWithText(
            "O limiar mede percentual contra cotas normalizadas. Saldo pré-pago não tem teto " +
                "(DeepSeek, OpenRouter); atividade observada ou métricas fora do modelo de cotas " +
                "(OpenCode Zen Free, Kilo Free, Gemini CLI, Antigravity CLI) não passam por essa avaliação."
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
            "The threshold is evaluated against normalized quotas. Prepaid balances have no ceiling " +
                "(DeepSeek, OpenRouter); observed activity and metrics outside the quota model " +
                "(OpenCode Zen Free, Kilo Free, Gemini CLI, Antigravity CLI) do not go through this evaluation."
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

    private fun ComposeUiTest.showSection(
        settings: UsageAlertSettings,
        language: AppLanguage = AppLanguage.PT,
        onSettingsChange: (UsageAlertSettings) -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                AlertSettingsSection(
                    settings = settings,
                    language = language,
                    onSettingsChange = onSettingsChange
                )
            }
        }
    }
}
