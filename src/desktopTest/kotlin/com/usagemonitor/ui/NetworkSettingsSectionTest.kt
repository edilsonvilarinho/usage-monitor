package com.usagemonitor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.presentation.ui.components.NETWORK_HOST_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.NETWORK_TEST_CONNECTION_TEST_TAG
import com.usagemonitor.presentation.ui.components.NETWORK_USE_ENV_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiState
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiStatus
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Aba "Rede" das Configurações (issue #174).
 *
 * A cena não precisa da altura estendida das outras suítes de Configurações:
 * a aba tem poucos campos e cabe na cena padrão de 768.
 */
@OptIn(ExperimentalTestApi::class)
class NetworkSettingsSectionTest {

    @Test
    fun `environment mode hides the manual fields`() = runDesktopComposeUiTest {
        showNetworkTab(settings = ProxySettings(useEnvironmentProxy = true))

        onNodeWithText("Usar variável de ambiente do sistema (HTTP_PROXY/HTTPS_PROXY)").assertIsDisplayed()
        onNodeWithTag(NETWORK_HOST_FIELD_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(NETWORK_TEST_CONNECTION_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `manual mode shows host port credentials and test connection`() = runDesktopComposeUiTest {
        showNetworkTab(
            settings = ProxySettings(
                useEnvironmentProxy = false,
                host = "proxy.empresa.com",
                port = 8080
            )
        )

        onNodeWithTag(NETWORK_HOST_FIELD_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(NETWORK_TEST_CONNECTION_TEST_TAG).assertIsDisplayed()
        onNodeWithText(
            "Suporta apenas autenticação Basic. NTLM e proxy com CA própria não são suportados."
        ).assertIsDisplayed()
    }

    @Test
    fun `toggling the switch reports the new value`() = runDesktopComposeUiTest {
        var lastValue: Boolean? = null
        showNetworkTab(
            settings = ProxySettings(useEnvironmentProxy = true),
            onUseEnvironmentProxyChange = { value -> lastValue = value }
        )

        onNodeWithTag(NETWORK_USE_ENV_SWITCH_TEST_TAG).performClick()

        assertEquals(false, lastValue)
    }

    @Test
    fun `test connection button is disabled without host`() = runDesktopComposeUiTest {
        // Botão sem host configurado não pode disparar teste nenhum: não há
        // proxy para testar. `performClick` num nó desabilitado não invoca o
        // `onClick`, então a ausência de clique é a prova de que o botão está
        // desligado, e não de que o teste não tentou.
        var clicked = false
        showNetworkTab(
            settings = ProxySettings(useEnvironmentProxy = false, host = "", port = 0),
            onTestConnection = { clicked = true }
        )

        onNodeWithTag(NETWORK_TEST_CONNECTION_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(NETWORK_TEST_CONNECTION_TEST_TAG).performClick()

        assertFalse(clicked)
    }

    @Test
    fun `clicking test connection with a configured proxy reports it`() = runDesktopComposeUiTest {
        var clicks = 0
        showNetworkTab(
            settings = ProxySettings(useEnvironmentProxy = false, host = "proxy.empresa.com", port = 8080),
            onTestConnection = { clicks += 1 }
        )

        onNodeWithTag(NETWORK_TEST_CONNECTION_TEST_TAG).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `test connection status message is displayed`() = runDesktopComposeUiTest {
        showNetworkTab(
            settings = ProxySettings(useEnvironmentProxy = false, host = "proxy.empresa.com", port = 8080),
            connection = ProxyConnectionUiState(
                status = ProxyConnectionUiStatus.FAILED,
                message = "Connection refused: connect"
            )
        )

        onNodeWithText("Connection refused: connect").assertIsDisplayed()
    }

    @Test
    fun `english translates the section`() = runDesktopComposeUiTest {
        showNetworkTab(
            settings = ProxySettings(useEnvironmentProxy = false, host = "proxy.empresa.com", port = 8080),
            language = AppLanguage.EN
        )

        onNodeWithText("Use the system environment variable (HTTP_PROXY/HTTPS_PROXY)").assertIsDisplayed()
        onNodeWithText("Test connection").assertIsDisplayed()
    }

    private fun ComposeUiTest.showNetworkTab(
        settings: ProxySettings,
        language: AppLanguage = AppLanguage.PT,
        connection: ProxyConnectionUiState = ProxyConnectionUiState(),
        onUseEnvironmentProxyChange: (Boolean) -> Unit = {},
        onTestConnection: () -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = language,
                    enabledApis = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    proxySettings = settings,
                    proxyConnection = connection,
                    onProxyUseEnvironmentChange = onUseEnvironmentProxyChange,
                    onProxyTestConnection = onTestConnection,
                    initialTab = SettingsTab.NETWORK
                )
            }
        }
    }
}
