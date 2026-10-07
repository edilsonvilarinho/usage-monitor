package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DEFAULT_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MAX_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppThemePreset

const val SETTINGS_TOAST_HOST_TEST_TAG = "settingsToastHost"
const val API_KEY_DIALOG_FIELD_TEST_TAG = "apiKeyDialogField"
const val API_KEY_DIALOG_REMOVE_TEST_TAG = "apiKeyDialogRemove"
const val API_KEY_DIALOG_TEST_TEST_TAG = "apiKeyDialogTest"
const val API_KEY_DIALOG_RESULT_TEST_TAG = "apiKeyDialogResult"
const val WINDOW_OPACITY_VALUE_TEST_TAG = "windowOpacityValue"

/** Mesma razão da tag de opacidade: "115%" também é rótulo de chip no cartão de alertas. */
const val UI_SCALE_VALUE_TEST_TAG = "uiScaleValue"

/** O rótulo é traduzido; buscar por texto amarraria o teste ao idioma. */
const val REDUCED_MOTION_SWITCH_TEST_TAG = "reducedMotionSwitch"
const val TRAY_USAGE_RING_SWITCH_TEST_TAG = "trayUsageRingSwitch"
const val AUTO_UPDATE_SWITCH_TEST_TAG = "autoUpdateSwitch"
const val AUTO_UPDATE_TEXT_BLOCK_TEST_TAG = "autoUpdateTextBlock"
const val AUTO_UPDATE_RECEIPT_TEST_TAG = "autoUpdateReceipt"
const val AUTO_UPDATE_FEED_OVERRIDE_TEST_TAG = "autoUpdateFeedOverride"
const val BETA_UPDATES_SWITCH_TEST_TAG = "betaUpdatesSwitch"
const val THEME_PRESET_TEST_TAG_PREFIX = "themePreset_"

/** O rótulo é traduzido; buscar por texto amarraria o teste ao idioma. */
const val REPORT_BUG_BUTTON_TEST_TAG = "reportBugButton"

/**
 * Seções das Configurações, uma por aba.
 *
 * Enum próprio, e não um valor a mais em algum enum existente: os `when`
 * exaustivos de `AppLanguage` e companhia não têm nada a ver com esta escolha.
 * A ordem de declaração é a ordem das abas na tela.
 *
 * `APPEARANCE` e `SYSTEM` substituíram `GENERAL` (issue #399, direção X1) — exceção
 * aprovada à regra de não criar valor em enum existente: o enum não é persistido
 * nem serializado, e os dois `when` que o percorrem são deste arquivo.
 */
enum class SettingsTab { APPEARANCE, SYSTEM, ALERTS, APIS, ACCOUNTS, TEAM, NETWORK }

/** Marcado por aba: o rótulo é traduzido e buscar por texto amarraria o teste ao idioma. */
fun settingsTabTestTag(tab: SettingsTab): String = "settingsTab_${tab.name}"

internal fun settingsTabLabel(tab: SettingsTab, language: AppLanguage): String {
    val isPt = language == AppLanguage.PT
    return when (tab) {
        SettingsTab.APPEARANCE -> if (isPt) "Aparência" else "Appearance"
        SettingsTab.SYSTEM -> if (isPt) "Sistema" else "System"
        SettingsTab.ALERTS -> if (isPt) "Alertas" else "Alerts"
        SettingsTab.APIS -> if (isPt) "APIs" else "APIs"
        SettingsTab.ACCOUNTS -> if (isPt) "Contas" else "Accounts"
        SettingsTab.TEAM -> if (isPt) "Time" else "Team"
        SettingsTab.NETWORK -> if (isPt) "Rede" else "Network"
    }
}

enum class AnthropicProfileUiStatus { READY, INCOMPLETE, INVALID, DUPLICATE }

data class AnthropicProfileUiModel(
    val id: String,
    val label: String,
    val path: String,
    val enabled: Boolean,
    val removable: Boolean,
    val identityLabel: String?,
    val status: AnthropicProfileUiStatus,
    val detail: String? = null,
    /** A cor escolhida para a conta (issue #275); `null` é o acento da Anthropic. */
    val color: AccountAccent? = null,
    /** O emoji escolhido para a conta (issue #287); `null` é nenhum. */
    val emoji: AccountEmoji? = null
)

/** Prefixo da opção de cor de um perfil, seguido do id do perfil e do nome da cor (ou `DEFAULT`). */
const val ACCOUNT_COLOR_OPTION_TEST_TAG_PREFIX = "accountColorOption_"

/** Prefixo da opção de emoji de um perfil, seguido do id do perfil e do nome do emoji (ou `NONE`). */
const val ACCOUNT_EMOJI_OPTION_TEST_TAG_PREFIX = "accountEmojiOption_"

@Composable
fun SettingsDialogContent(
    currentTheme: AppThemePreset,
    currentLanguage: AppLanguage,
    enabledApis: Set<ApiSource>,
    configuredApiKeys: Set<ApiSource> = emptySet(),
    autoStartEnabled: Boolean,
    windowOpacityPercent: Int = MAX_WINDOW_OPACITY_PERCENT,
    windowOpacityEnabled: Boolean = true,
    uiScalePercent: Int = DEFAULT_UI_SCALE_PERCENT,
    onUiScaleChange: (Int) -> Unit = {},
    reducedMotion: Boolean = false,
    /** Default vazio pela mesma razão de [onAutoUpdateChange]. */
    onReducedMotionChange: (Boolean) -> Unit = {},
    trayUsageRing: Boolean = false,
    onTrayUsageRingChange: (Boolean) -> Unit = {},
    /** Abre o diálogo de relatório de bug. Default vazio: os geradores de captura não o abrem. */
    onReportBug: () -> Unit = {},
    onThemeChange: (AppThemePreset) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    autoUpdateEnabled: Boolean = false,
    /**
     * Default `UNAVAILABLE`: quem não passa a origem não tem o mecanismo, e o
     * interruptor aparece desabilitado com o motivo em vez de prometer algo.
     */
    autoUpdateSupport: AppUpdateSupport = AppUpdateSupport.UNAVAILABLE,
    /**
     * Plataforma em execução. `null` é "não reconhecida", e não um default de
     * conveniência: dois dos motivos de indisponibilidade nomeiam o instalador,
     * e quem não sabe onde está não pode nomeá-lo.
     */
    autoUpdatePlatform: AppUpdatePlatform? = null,
    lastUpdateReceipt: AppUpdateReceipt? = null,
    autoUpdateFeedOverride: String? = null,
    onAutoUpdateChange: (Boolean) -> Unit = {},
    /** Canal beta (issue #355). Default desligado, como a preferência. */
    receiveBetaUpdates: Boolean = false,
    onReceiveBetaUpdatesChange: (Boolean) -> Unit = {},
    onWindowOpacityChange: (Int) -> Unit = {},
    alertSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT,
    onAlertSettingsChange: (UsageAlertSettings) -> Unit = {},
    monthlyBudgetText: String = "",
    onMonthlyBudgetCommit: (String) -> Unit = {},
    onApiToggle: (ApiSource, Boolean) -> Unit,
    onApiKeySave: (ApiSource, String) -> Boolean = { _, _ -> false },
    /**
     * Apaga a chave da fonte e devolve se a gravação foi feita. Espelha
     * `onApiKeySave`: o diálogo só fecha quando a camada de dados confirma, e um
     * `false` mantém a tela aberta com o aviso de falha.
     */
    onApiKeyRemove: (ApiSource) -> Boolean = { false },
    /**
     * Veredito do "Testar chave" (issue #204). Um estado só, e não um por fonte:
     * o diálogo é modal e só existe uma fonte em edição de cada vez.
     */
    apiKeyCheck: ApiKeyCheckUiState = ApiKeyCheckUiState(),
    /** Recebe a fonte e a chave candidata — a digitada, ou vazia para usar a guardada. */
    onApiKeyTest: (ApiSource, String) -> Unit = { _, _ -> },
    /** Apaga o veredito: ele descreve a chave anterior, não a que está no campo agora. */
    onApiKeyCheckReset: () -> Unit = {},
    anthropicProfiles: List<AnthropicProfileUiModel> = emptyList(),
    onAnthropicProfileToggle: (String, Boolean) -> Unit = { _, _ -> },
    onAnthropicProfileRename: (String, String) -> Unit = { _, _ -> },
    onAnthropicProfileColorChange: (String, AccountAccent?) -> Unit = { _, _ -> },
    onAnthropicProfileEmojiChange: (String, AccountEmoji?) -> Unit = { _, _ -> },
    onAddAnthropicProfile: () -> Unit = {},
    onRemoveAnthropicProfile: (String) -> Unit = {},
    onRescanAnthropicProfiles: () -> Unit = {},
    expandedProfileId: String? = null,
    codexAccounts: CodexAccountsSettings = CodexAccountsSettings(),
    onToggleProfileExpanded: (String) -> Unit = {},
    teamSettings: TeamIntegrationSettings = TeamIntegrationSettings(),
    teamConnection: TeamConnectionUiState = TeamConnectionUiState(),
    onTeamEnabledChange: (Boolean) -> Unit = {},
    onTeamServerUrlChange: (String) -> Unit = {},
    onTeamApiKeyChange: (String) -> Unit = {},
    onTeamAliasChange: (String) -> Unit = {},
    onTeamProfileParticipationChange: (String, Boolean) -> Unit = { _, _ -> },
    onTeamTestConnection: () -> Unit = {},
    teamSyncFailureMessage: String? = null,
    /** Contas recusadas pelo servidor, por `profileId`. Aponta a linha errada. */
    teamRejectedProfiles: Map<String, String> = emptyMap(),
    teamAdminConnection: TeamConnectionUiState = TeamConnectionUiState(),
    onTeamAdminTokenChange: (String) -> Unit = {},
    onTeamValidateAdminToken: () -> Unit = {},
    onTeamOpenKeysManager: () -> Unit = {},
    onTeamExitAdminMode: () -> Unit = {},
    proxySettings: ProxySettings = ProxySettings(),
    proxyConnection: ProxyConnectionUiState = ProxyConnectionUiState(),
    onProxyUseEnvironmentChange: (Boolean) -> Unit = {},
    onProxyHostChange: (String) -> Unit = {},
    onProxyPortChange: (String) -> Unit = {},
    onProxyUsernameChange: (String) -> Unit = {},
    onProxyPasswordChange: (String) -> Unit = {},
    onProxyTestConnection: () -> Unit = {},
    /** Acesso web local (#388); `null` esconde a seção (geradores de captura, testes). */
    webAccess: WebAccessSectionModel? = null,
    /** Bot do Telegram (#387); `null` esconde a seção. */
    telegramBot: TelegramBotSectionModel? = null,
    toastEvent: SettingsToastEvent? = null,
    /** Prévia da barra HUD na aba Aparência (#399, X10); `null` esconde o painel. */
    appearancePreview: (@Composable () -> Unit)? = null,
    /** Aba aberta ao entrar; existe para os geradores de captura escolherem a seção. */
    initialTab: SettingsTab = SettingsTab.APPEARANCE,
    modifier: Modifier = Modifier
) {
    // A aba mora num `remember` do próprio diálogo: ele é uma janela separada e
    // nenhuma outra parte do app precisa saber qual seção está aberta — mesmo
    // critério que o filtro e a página do resumo por eixo seguem.
    var selectedTab by remember { mutableStateOf(initialTab) }

    // Um estado de rolagem por aba, começando no topo: reaproveitar o mesmo faria
    // a aba curta abrir rolada pela posição que a aba longa deixou para trás.
    val scrollState = remember(selectedTab) { ScrollState(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsToastEffect(toastEvent = toastEvent, snackbarHostState = snackbarHostState, language = currentLanguage)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // A navegação fica à esquerda e não rola: ela é o controle, e o
            // conteúdo rolando não pode tirá-la da vista.
            AppSettingsNav(
                items = SettingsTab.entries.map { tab ->
                    AppTab(
                        label = settingsTabLabel(tab, currentLanguage),
                        testTag = settingsTabTestTag(tab)
                    )
                },
                selectedIndex = SettingsTab.entries.indexOf(selectedTab),
                onSelect = { index -> selectedTab = SettingsTab.entries[index] },
                header = if (currentLanguage == AppLanguage.PT) "Seções" else "Sections"
            )
            AppVerticalDivider()

            // A barra de rolagem mora dentro da área rolável, e não sobre o
            // diálogo inteiro: fora dela ficaria por cima da navegação.
            Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                // A aba nova entra com o mesmo fade que as telas usam ao trocar de
                // estado; só a escolhida continua composta -- a saída da anterior
                // dura 120ms e some. A `Column` interna existe porque as abas
                // emitem vários painéis e contam com o `spacedBy` do pai.
                AppStateCrossfade(
                    state = selectedTab,
                    key = { tab -> tab },
                    label = "settingsTab",
                    // A troca de seção não repete o E9: é navegação, e o dado já
                    // está pronto. Os filamentos da abertura continuam.
                    revealOnChange = false
                ) { tab ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        when (tab) {
                            SettingsTab.APPEARANCE -> AppearanceSettingsTab(
                                currentTheme = currentTheme,
                                currentLanguage = currentLanguage,
                                windowOpacityPercent = windowOpacityPercent,
                                windowOpacityEnabled = windowOpacityEnabled,
                                uiScalePercent = uiScalePercent,
                                reducedMotion = reducedMotion,
                                onThemeChange = onThemeChange,
                                onLanguageChange = onLanguageChange,
                                onWindowOpacityChange = onWindowOpacityChange,
                                onUiScaleChange = onUiScaleChange,
                                onReducedMotionChange = onReducedMotionChange,
                                preview = appearancePreview
                            )

                            SettingsTab.SYSTEM -> SystemSettingsTab(
                                currentLanguage = currentLanguage,
                                autoStartEnabled = autoStartEnabled,
                                autoUpdateEnabled = autoUpdateEnabled,
                                autoUpdateSupport = autoUpdateSupport,
                                autoUpdatePlatform = autoUpdatePlatform,
                                lastUpdateReceipt = lastUpdateReceipt,
                                autoUpdateFeedOverride = autoUpdateFeedOverride,
                                onAutoStartChange = onAutoStartChange,
                                onAutoUpdateChange = onAutoUpdateChange,
                                onReportBug = onReportBug,
                                trayUsageRing = trayUsageRing,
                                onTrayUsageRingChange = onTrayUsageRingChange,
                                receiveBetaUpdates = receiveBetaUpdates,
                                onReceiveBetaUpdatesChange = onReceiveBetaUpdatesChange
                            )

                            SettingsTab.ALERTS -> {
                                AlertSettingsSection(
                                    settings = alertSettings,
                                    language = currentLanguage,
                                    onSettingsChange = onAlertSettingsChange,
                                    budgetText = monthlyBudgetText,
                                    onBudgetCommit = onMonthlyBudgetCommit
                                )
                                telegramBot?.let { model -> TelegramBotSection(model, currentLanguage) }
                            }

                            SettingsTab.APIS -> MonitoredApisTab(
                                currentLanguage = currentLanguage,
                                enabledApis = enabledApis,
                                configuredApiKeys = configuredApiKeys,
                                onApiToggle = onApiToggle,
                                onApiKeySave = onApiKeySave,
                                onApiKeyRemove = onApiKeyRemove,
                                apiKeyCheck = apiKeyCheck,
                                onApiKeyTest = onApiKeyTest,
                                onApiKeyCheckReset = onApiKeyCheckReset
                            )

                            SettingsTab.ACCOUNTS -> AnthropicAccountsTab(
                                currentLanguage = currentLanguage,
                                anthropicProfiles = anthropicProfiles,
                                expandedProfileId = expandedProfileId,
                                onAnthropicProfileToggle = onAnthropicProfileToggle,
                                onAnthropicProfileRename = onAnthropicProfileRename,
                                onAnthropicProfileColorChange = onAnthropicProfileColorChange,
                                onAnthropicProfileEmojiChange = onAnthropicProfileEmojiChange,
                                onAddAnthropicProfile = onAddAnthropicProfile,
                                onRemoveAnthropicProfile = onRemoveAnthropicProfile,
                                onRescanAnthropicProfiles = onRescanAnthropicProfiles,
                                onToggleProfileExpanded = onToggleProfileExpanded,
                                codexAccounts = codexAccounts
                            )

                            SettingsTab.TEAM -> {
                                TeamIntegrationSection(
                                    settings = teamSettings,
                                    language = currentLanguage,
                                    profiles = anthropicProfiles,
                                    connection = teamConnection,
                                    onEnabledChange = onTeamEnabledChange,
                                    onServerUrlChange = onTeamServerUrlChange,
                                    onApiKeyChange = onTeamApiKeyChange,
                                    onAliasChange = onTeamAliasChange,
                                    onProfileParticipationChange = onTeamProfileParticipationChange,
                                    onTestConnection = onTeamTestConnection,
                                    syncFailureMessage = teamSyncFailureMessage,
                                    rejectedProfiles = teamRejectedProfiles,
                                    adminConnection = teamAdminConnection,
                                    onAdminTokenChange = onTeamAdminTokenChange,
                                    onValidateAdminToken = onTeamValidateAdminToken,
                                    onOpenKeysManager = onTeamOpenKeysManager,
                                    onExitAdminMode = onTeamExitAdminMode
                                )
                            }

                            SettingsTab.NETWORK -> {
                                NetworkSettingsSection(
                                    settings = proxySettings,
                                    language = currentLanguage,
                                    connection = proxyConnection,
                                    onUseEnvironmentProxyChange = onProxyUseEnvironmentChange,
                                    onHostChange = onProxyHostChange,
                                    onPortChange = onProxyPortChange,
                                    onUsernameChange = onProxyUsernameChange,
                                    onPasswordChange = onProxyPasswordChange,
                                    onTestConnection = onProxyTestConnection
                                )
                                webAccess?.let { model -> WebAccessSection(model, currentLanguage) }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(SETTINGS_TOAST_HOST_TEST_TAG)
            )
            }
        }
    }
}

/**
 * O aviso curto das Configurações. Saiu de [SettingsDialogContent], que passou do
 * limite de 300 linhas com o canal beta (issue #355).
 *
 * Host próprio: o diálogo é uma janela separada e o SnackbarHost do dashboard não
 * desenha por cima dela. O `dismiss` antes de mostrar impede que mexer em vários
 * controles seguidos enfileire avisos e o usuário fique assistindo à fila
 * esvaziar depois de já ter parado.
 */
@Composable
private fun SettingsToastEffect(
    toastEvent: SettingsToastEvent?,
    snackbarHostState: SnackbarHostState,
    language: AppLanguage
) {
    // Evento que já existia quando o diálogo abriu é de uma edição anterior —
    // reexibi-lo faria a tela abrir avisando algo que o usuário nem acabou de
    // fazer.
    val staleToastId = remember { toastEvent?.id }

    LaunchedEffect(toastEvent?.id) {
        val event = toastEvent ?: return@LaunchedEffect
        if (event.id == staleToastId) {
            return@LaunchedEffect
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = settingsToastMessage(event.toast, language),
            duration = SnackbarDuration.Short
        )
    }
}

/**
 * Linha de opção: rótulo em mono, descrição em sans, controle à direita.
 *
 * A divisão entre as duas famílias é por papel: o rótulo é rótulo — largura fixa
 * de dígito, mesma classe do cabeçalho de coluna — e a descrição é texto corrido,
 * que é onde a sans existe. As duas estavam em `bodySmall`, ou seja, as duas em
 * sans, e o rótulo lia como mais uma frase.
 */
@Composable
internal fun SettingsOptionRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    showDivider: Boolean = true,
    control: @Composable RowScope.() -> Unit
) {
    AppDataRow(modifier = modifier, showDivider = showDivider) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        control()
    }
}
