package com.usagemonitor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.performMouseInput
import com.usagemonitor.SETTINGS_HUD_PREVIEW_TEST_TAG
import com.usagemonitor.SettingsHudPreview
import com.usagemonitor.presentation.ui.HUD_BALLOON_TEST_TAG
import com.usagemonitor.screenshots.ScreenshotFixtures
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.components.API_KEY_DIALOG_REMOVE_TEST_TAG
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.CODEX_ACCOUNTS_ADD_TEST_TAG
import com.usagemonitor.presentation.ui.components.CodexAccountsSettings
import com.usagemonitor.presentation.ui.components.CodexProfileUiModel
import com.usagemonitor.presentation.ui.components.codexAccountSwitchTestTag
import com.usagemonitor.presentation.ui.components.REDUCED_MOTION_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.SETTINGS_TOAST_HOST_TEST_TAG
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.components.TRAY_USAGE_RING_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.UI_SCALE_VALUE_TEST_TAG
import com.usagemonitor.presentation.ui.components.WINDOW_OPACITY_VALUE_TEST_TAG
import com.usagemonitor.presentation.ui.components.apiSelectorEditKeyTestTag
import com.usagemonitor.presentation.ui.components.apiSelectorSwitchTestTag
import com.usagemonitor.presentation.ui.components.settingsTabTestTag
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes de componente de `SettingsDialogContent`, fora de `ComponentTest` porque o Gradle
 * distribui forks por classe e aquela classe era o caminho crítico da suíte paralela (issue #295).
 */
@OptIn(ExperimentalTestApi::class)
class SettingsDialogContentTest {

    @Test
    fun `SettingsDialogContent requests an API key before enabling MiniMax`() = runDesktopComposeUiTest {
        var toggledApi: ApiSource? = null
        var savedKey: String? = null

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { api, checked ->
                        if (checked) toggledApi = api
                    },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performClick()
        onNodeWithText("Configurar MiniMax").assertIsDisplayed()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("minimax-secret")
        onNodeWithText("Salvar").performClick()

        assertEquals("MINIMAX:minimax-secret", savedKey)
        assertEquals(ApiSource.MINIMAX, toggledApi)
    }

    /**
     * Issue #124: a assinatura Go é a terceira fonte que depende de chave local, e
     * o caminho é o mesmo do MiniMax — ligar sem chave abre o diálogo em vez de
     * persistir um interruptor que só produziria erro na próxima coleta.
     */
    @Test
    fun `SettingsDialogContent requests an API key before enabling OpenCode Go`() = runDesktopComposeUiTest {
        var toggledApi: ApiSource? = null
        var savedKey: String? = null

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { api, checked ->
                        if (checked) toggledApi = api
                    },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.OPENCODE_GO)).performScrollTo().performClick()
        onNodeWithText("Configurar OpenCode Go").assertIsDisplayed()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("opencode-secret")
        onNodeWithText("Salvar").performClick()

        assertEquals("OPENCODE_GO:opencode-secret", savedKey)
        assertEquals(ApiSource.OPENCODE_GO, toggledApi)
    }

    /**
     * O plano gratuito do Zen não tem chave: ligar a linha dele grava direto, sem
     * diálogo. É o que separa as duas fontes de OpenCode na mesma lista.
     */
    @Test
    fun `SettingsDialogContent toggles the free OpenCode source without asking for a key`() = runDesktopComposeUiTest {
        var toggledApi: ApiSource? = null

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { api, checked ->
                        if (checked) toggledApi = api
                    },
                    onApiKeySave = { _, _ -> true },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.OPENCODE)).performScrollTo().performClick()

        assertEquals(ApiSource.OPENCODE, toggledApi)
        onAllNodesWithText("Configurar OpenCode Zen Free").assertCountEquals(0)
    }

    @Test
    fun `SettingsDialogContent enables local integrations without API keys`() = runDesktopComposeUiTest {
        val enabledSources = mutableSetOf<ApiSource>()

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { source, checked ->
                        if (checked) enabledSources += source
                    },
                    onApiKeySave = { _, _ -> true },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        listOf(ApiSource.GEMINI, ApiSource.CURSOR, ApiSource.ANTIGRAVITY).forEach { source ->
            onNodeWithTag(apiSelectorSwitchTestTag(source)).performScrollTo().performClick()
        }

        assertEquals(setOf(ApiSource.GEMINI, ApiSource.CURSOR, ApiSource.ANTIGRAVITY), enabledSources)
        onAllNodesWithText("Configurar Gemini CLI").assertCountEquals(0)
        onAllNodesWithText("Configurar Cursor").assertCountEquals(0)
        onAllNodesWithText("Configurar Antigravity CLI").assertCountEquals(0)
    }

    /**
     * Issue #125: o caminho que não existia. Até esta passada o diálogo só abria
     * ao **ligar** uma fonte sem chave; cadastrada uma vez, ela era definitiva
     * pela interface. O lápis abre o mesmo diálogo com a fonte já configurada.
     */
    @Test
    fun `SettingsDialogContent opens the key dialog from the pencil of a configured source`() = runDesktopComposeUiTest {
        var savedKey: String? = null

        setContent {
            ScreenTestTheme(isDark = true) {
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
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithText("Configurar MiniMax").assertIsDisplayed()
        // O campo nunca vem pré-preenchido com a chave guardada: para trocar,
        // digita-se a nova.
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("minimax-rotated")
        onNodeWithText("Salvar").performClick()

        assertEquals("MINIMAX:minimax-rotated", savedKey)
    }

    /** Fonte sem chave local não ganha lápis: não há o que gerenciar. */
    @Test
    fun `SettingsDialogContent omits the pencil for sources without a local key`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    configuredApiKeys = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onAllNodesWithTag(apiSelectorEditKeyTestTag(ApiSource.ANTHROPIC)).assertCountEquals(0)
        onAllNodesWithTag(apiSelectorEditKeyTestTag(ApiSource.OPENCODE)).assertCountEquals(0)
        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().assertExists()
    }

    /**
     * Issue #125: apagar a chave era impossível pela interface. O botão fica no
     * mesmo diálogo, como `GHOST` — `PRIMARY` é uma por tela e continua sendo o
     * "Salvar", que é o que o diálogo propõe.
     */
    @Test
    fun `SettingsDialogContent removes a stored key from the dialog`() = runDesktopComposeUiTest {
        var removed: ApiSource? = null

        setContent {
            ScreenTestTheme(isDark = true) {
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
                    onApiKeyRemove = { api ->
                        removed = api
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_REMOVE_TEST_TAG).performClick()

        assertEquals(ApiSource.DEEPSEEK, removed)
        // Gravação confirmada fecha o diálogo.
        onAllNodesWithText("Configurar DeepSeek").assertCountEquals(0)
    }

    /**
     * Ligar uma fonte que nunca foi configurada abre o mesmo diálogo, e ali um
     * botão de remover não teria o que remover.
     */
    @Test
    fun `SettingsDialogContent hides the remove button when there is no stored key`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithText("Configurar MiniMax").assertIsDisplayed()
        onAllNodesWithTag(API_KEY_DIALOG_REMOVE_TEST_TAG).assertCountEquals(0)
    }

    /** Remoção recusada pela camada de dados mantém o diálogo aberto. */
    @Test
    fun `SettingsDialogContent keeps the dialog open when removal fails`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
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
                    onApiKeyRemove = { false },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.DEEPSEEK)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_REMOVE_TEST_TAG).performClick()

        onNodeWithText("Configurar DeepSeek").assertIsDisplayed()
    }

    /**
     * Issue #125: trocar a chave de uma fonte já ligada não mexe no interruptor.
     * Reafirmá-lo regravaria a preferência, dispararia uma segunda coleta e
     * trocaria o aviso de "chave de API salva" pelo de "APIs monitoradas".
     */
    @Test
    fun `SettingsDialogContent rotates a key without re-enabling the source`() = runDesktopComposeUiTest {
        var savedKey: String? = null
        var toggleCalls = 0

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.MINIMAX),
                    configuredApiKeys = setOf(ApiSource.MINIMAX),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> toggleCalls += 1 },
                    onApiKeySave = { api, key ->
                        savedKey = "$api:$key"
                        true
                    },
                    initialTab = SettingsTab.APIS
                )
            }
        }

        onNodeWithTag(apiSelectorEditKeyTestTag(ApiSource.MINIMAX)).performScrollTo().performClick()
        onNodeWithTag(API_KEY_DIALOG_FIELD_TEST_TAG).performTextReplacement("minimax-rotated")
        onNodeWithText("Salvar").performClick()

        assertEquals("MINIMAX:minimax-rotated", savedKey)
        assertEquals(0, toggleCalls)
        onNodeWithTag(apiSelectorSwitchTestTag(ApiSource.MINIMAX)).performScrollTo().assertIsOn()
    }

    /** "Reduzir animações" mora em Aparência, ao lado da escala da interface. */
    @Test
    fun `SettingsDialogContent emits the reduced motion change`() = runDesktopComposeUiTest {
        var enabled: Boolean? = null

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    reducedMotion = false,
                    onReducedMotionChange = { value -> enabled = value },
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        onNodeWithText("Reduzir animações").assertExists()
        onNodeWithTag(REDUCED_MOTION_SWITCH_TEST_TAG).performScrollTo().performClick()

        assertEquals(true, enabled)
    }

    /** Contas Codex extras (issue #329): seção própria na aba Contas, abaixo das da Anthropic. */
    @Test
    fun `SettingsDialogContent lists extra Codex accounts and emits their actions`() = runDesktopComposeUiTest {
        var toggled: Pair<String, Boolean>? = null
        var added = false

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.CODEX),
                    autoStartEnabled = false,
                    initialTab = SettingsTab.ACCOUNTS,
                    codexAccounts = CodexAccountsSettings(
                        profiles = listOf(CodexProfileUiModel("codex-work", "work", "C:/codex-work", enabled = true)),
                        error = "Sem auth.json em C:/vazio.",
                        onAdd = { added = true },
                        onToggle = { id, checked -> toggled = id to checked }
                    ),
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        onNodeWithText("Contas Codex extras").assertExists()
        onNodeWithText("C:/codex-work").assertExists()
        onNodeWithText("Sem auth.json em C:/vazio.").assertExists()
        onNodeWithTag(codexAccountSwitchTestTag("codex-work")).performScrollTo().performClick()
        onNodeWithTag(CODEX_ACCOUNTS_ADD_TEST_TAG).performScrollTo().performClick()

        assertEquals("codex-work" to false, toggled)
        assertEquals(true, added)
    }

    /** Anel de uso da bandeja (issue #328): na aba Sistema (#399). */
    @Test
    fun `SettingsDialogContent emits the tray usage ring change`() = runDesktopComposeUiTest {
        var enabled: Boolean? = null

        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    trayUsageRing = false,
                    onTrayUsageRingChange = { value -> enabled = value },
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    initialTab = SettingsTab.SYSTEM
                )
            }
        }

        onNodeWithText("Anel de uso na bandeja").assertExists()
        onNodeWithTag(TRAY_USAGE_RING_SWITCH_TEST_TAG).performScrollTo().performClick()

        assertEquals(true, enabled)
    }

    @Test
    fun `SettingsDialogContent displays localized controls in EN`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX),
                    autoStartEnabled = false,
                    windowOpacityPercent = 75,
                    uiScalePercent = 115,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = listOf(
                        AnthropicProfileUiModel(
                            id = "default",
                            label = "Personal",
                            path = "C:\\Users\\test\\.claude",
                            enabled = true,
                            removable = false,
                            identityLabel = "personal@example.com",
                            status = AnthropicProfileUiStatus.READY
                        )
                    )
                )
            }
        }

        // A aba Aparência é a que abre (#399, X1); o resto do diálogo só existe
        // depois do clique na aba correspondente.
        onAllNodesWithText("System Startup").assertCountEquals(0)
        onNodeWithText("Window opacity").assertIsDisplayed()
        // Por tag: "75%" também é rótulo de limiar no cartão de alertas.
        onNodeWithTag(WINDOW_OPACITY_VALUE_TEST_TAG).assertTextEquals("75%")
        onNodeWithText("Interface size").assertIsDisplayed()
        // Mesma razão da tag de opacidade: "115%" também aparece como limiar.
        onNodeWithTag(UI_SCALE_VALUE_TEST_TAG).assertTextEquals("115%")
        onNodeWithText("Language").assertIsDisplayed()

        onNodeWithTag(settingsTabTestTag(SettingsTab.SYSTEM)).performClick()
        onNodeWithText("System Startup").assertIsDisplayed()
        onNodeWithText("Updates").assertIsDisplayed()
        onNodeWithText("Diagnostics").assertIsDisplayed()
        onAllNodesWithText("Window opacity").assertCountEquals(0)

        onNodeWithTag(settingsTabTestTag(SettingsTab.APIS)).performClick()
        onNodeWithText("Monitored APIs").assertIsDisplayed()
        onNodeWithText("OpenCode Zen Free").performScrollTo().assertIsDisplayed()
        onNodeWithText("OpenCode Go").performScrollTo().assertIsDisplayed()
        onNodeWithText("Kilo Free").performScrollTo().assertIsDisplayed()
        onNodeWithText("Gemini CLI").performScrollTo().assertIsDisplayed()
        onNodeWithText("Cursor").performScrollTo().assertIsDisplayed()
        onNodeWithText("Antigravity CLI").performScrollTo().assertIsDisplayed()

        onNodeWithTag(settingsTabTestTag(SettingsTab.ACCOUNTS)).performClick()
        onNodeWithText("Anthropic accounts").assertIsDisplayed()
        onNodeWithText("personal@example.com").assertIsDisplayed()

        onAllNodesWithText("Close").assertCountEquals(0)
    }

    /**
     * Prévia da barra HUD (#399, X10): o próprio notch, só na aba Aparência, e
     * sem gesto — o ponteiro sobre ela não abre balão.
     */
    @Test
    fun `appearance tab shows a static HUD preview`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    appearancePreview = {
                        SettingsHudPreview(
                            accounts = ScreenshotFixtures.hudAccounts,
                            fallbackLabel = "Carregando",
                            windowOpacityPercent = 80,
                            windowOpacitySupported = true,
                            language = AppLanguage.PT
                        )
                    }
                )
            }
        }

        onNodeWithText("Prévia da barra HUD").assertIsDisplayed()
        onNodeWithTag(SETTINGS_HUD_PREVIEW_TEST_TAG).assertIsDisplayed().performMouseInput { moveTo(center) }
        waitForIdle()
        onAllNodesWithTag(HUD_BALLOON_TEST_TAG).assertCountEquals(0)

        onNodeWithTag(settingsTabTestTag(SettingsTab.SYSTEM)).performClick()
        onAllNodesWithTag(SETTINGS_HUD_PREVIEW_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `SettingsDialogContent shows one tab at a time`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        // O conteúdo das outras abas não está apenas fora da vista: ele não está
        // na composição. Sem isso as abas seriam decoração sobre a mesma coluna.
        onNodeWithText("Window opacity").assertIsDisplayed()
        onAllNodesWithText("System Startup").assertCountEquals(0)
        onAllNodesWithText("Monitored APIs").assertCountEquals(0)
        onAllNodesWithText("Anthropic accounts").assertCountEquals(0)

        onNodeWithTag(settingsTabTestTag(SettingsTab.TEAM)).performClick()
        onAllNodesWithText("Window opacity").assertCountEquals(0)
    }

    @Test
    fun `SettingsDialogContent expands Anthropic profile editor only after Edit click`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                var expandedProfileId by remember { mutableStateOf<String?>(null) }
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.EN,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    anthropicProfiles = listOf(
                        AnthropicProfileUiModel(
                            id = "default",
                            label = "Personal",
                            path = "C:\\Users\\test\\.claude",
                            enabled = true,
                            removable = false,
                            identityLabel = "personal@example.com",
                            status = AnthropicProfileUiStatus.READY
                        )
                    ),
                    expandedProfileId = expandedProfileId,
                    onToggleProfileExpanded = { profileId ->
                        expandedProfileId = if (expandedProfileId == profileId) null else profileId
                    },
                    // As contas moram na aba própria; o teste é sobre o editor do
                    // perfil, não sobre a navegação entre abas.
                    initialTab = SettingsTab.ACCOUNTS
                )
            }
        }

        // Colapsado por padrão: identidade visível, campo de edição do apelido ainda não.
        onNodeWithText("personal@example.com").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Label").assertCountEquals(0)

        onNodeWithContentDescription("Edit").performScrollTo().performClick()

        // Expandido após clicar em "Editar": campo de edição do apelido aparece.
        onNodeWithText("Label").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `SettingsDialogContent hosts its own toast area`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = AppLanguage.PT,
                    enabledApis = setOf(ApiSource.ANTHROPIC),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> }
                )
            }
        }

        // O diálogo é uma janela separada: o SnackbarHost do dashboard não
        // desenha por cima dela, então o aviso de "salvo" precisa deste host.
        // A exibição em si é do Material3 e não é reencenada aqui — o teste do
        // conteúdo da mensagem é `SettingsToastMessageTest`, em commonTest.
        onNodeWithTag(SETTINGS_TOAST_HOST_TEST_TAG).assertExists()
    }
}
