package com.usagemonitor.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_RESULT_TEST_TAG
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_TEST_TEST_TAG
import com.usagemonitor.presentation.ui.components.ApiKeyCheckStatus
import com.usagemonitor.presentation.ui.components.ApiKeyCheckUiState
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.components.apiSelectorEditKeyTestTag
import com.usagemonitor.presentation.ui.components.apiSelectorSwitchTestTag
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * "Testar chave" no diálogo da aba APIs (issue #204).
 *
 * O veredito em si é exercitado por `ApiKeyCheckTest`, que é função pura. Aqui
 * fica só o que depende da composição: quando o botão está disponível, o que ele
 * envia, e quando o resultado deixa de valer.
 */
@OptIn(ExperimentalTestApi::class)
class ApiKeyDialogTest {

    @Test
    fun `envia a chave digitada`() = runDesktopComposeUiTest {
        var tested: Pair<ApiSource, String>? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeySave = { _, _ -> true },
                    onApiKeyTest = { source, key -> tested = source to key },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.OPENROUTER)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("  sk-or-v1-abc  ")
        onNodeWithTag(API_KEY_DIALOG_TEST_TEST_TAG).performClick()

        // Chega sem espaço em volta: colar de um gerenciador de senhas traz os
        // dois, e é a mesma normalização que o "Salvar" já faz.
        assertEquals(ApiSource.OPENROUTER to "sk-or-v1-abc", tested)
    }

    /**
     * Abrir pelo lápis com o campo vazio testa a chave **guardada** — o caso da
     * chave que expirou, em que o usuário não tem uma nova para digitar.
     */
    @Test
    fun `campo vazio com chave guardada envia string vazia`() = runDesktopComposeUiTest {
        var tested: Pair<ApiSource, String>? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.DEEPSEEK),
                    configuredApiKeys = setOf(ApiSource.DEEPSEEK),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeyTest = { source, key -> tested = source to key },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_TEST_TEST_TAG).assertIsEnabled().performClick()

        assertEquals(ApiSource.DEEPSEEK to "", tested)
    }

    /** Sem chave digitada e sem chave guardada não há o que enviar. */
    @Test
    fun `sem chave nenhuma o botao fica desabilitado`() = runDesktopComposeUiTest {
        var tested: Pair<ApiSource, String>? = null

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onApiKeySave = { _, _ -> true },
                    onApiKeyTest = { source, key -> tested = source to key },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_TEST_TEST_TAG).assertIsNotEnabled().performClick()

        assertNull(tested)
    }

    /** Segundo clique durante a ida à rede dispararia uma segunda requisição. */
    @Test
    fun `botao fica desabilitado durante a checagem`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.OPENCODE_GO),
                    configuredApiKeys = setOf(ApiSource.OPENCODE_GO),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    apiKeyCheck = ApiKeyCheckUiState(status = ApiKeyCheckStatus.CHECKING),
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.OPENCODE_GO)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_TEST_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `o veredito aparece junto do campo`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.OPENCODE_GO),
                    configuredApiKeys = setOf(ApiSource.OPENCODE_GO),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    apiKeyCheck = ApiKeyCheckUiState(
                        status = ApiKeyCheckStatus.FAILED,
                        message = "Chave válida, sem assinatura Go ativa.",
                        tone = AppTone.WARNING
                    ),
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.OPENCODE_GO)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_RESULT_TEST_TAG).assertIsDisplayed()
        // `AppStatusIndicator` é uma `Row` que não mescla descendentes: o texto
        // vive num filho, e a tag localiza só o contêiner. Mesma forma de asserção
        // que `NetworkSettingsSectionTest` usa para o resultado do proxy.
        onNodeWithText("Chave válida, sem assinatura Go ativa.").assertIsDisplayed()
    }

    /**
     * O veredito descreve o texto que estava no campo. Digitar por cima o torna
     * uma afirmação sobre uma chave que não está mais ali.
     */
    @Test
    fun `digitar apaga o veredito anterior`() = runDesktopComposeUiTest {
        // O estado mora FORA do `setContent`: abrir o diálogo já chama
        // `onApiKeyCheckReset` — o veredito da fonte anterior não pode sobreviver
        // à troca —, então semear o resultado antes da abertura o apagaria.
        val check = mutableStateOf(ApiKeyCheckUiState())

        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.MINIMAX),
                    configuredApiKeys = setOf(ApiSource.MINIMAX),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    apiKeyCheck = check.value,
                    onApiKeyCheckReset = { check.value = ApiKeyCheckUiState() },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()

        check.value = ApiKeyCheckUiState(
            status = ApiKeyCheckStatus.FAILED,
            message = "Chave recusada pela API (HTTP 401/403). Revise a chave.",
            tone = AppTone.CRITICAL
        )
        waitForIdle()
        onNodeWithTag(API_KEY_DIALOG_RESULT_TEST_TAG).assertIsDisplayed()

        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("outra-chave")

        onAllNodesWithTag(API_KEY_DIALOG_RESULT_TEST_TAG).assertCountEquals(0)
    }
}
