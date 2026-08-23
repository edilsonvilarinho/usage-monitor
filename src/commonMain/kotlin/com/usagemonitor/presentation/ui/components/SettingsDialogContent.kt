package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme
import com.usagemonitor.domain.entity.DEFAULT_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MAX_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MAX_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.MIN_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MIN_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.UI_SCALE_STEP_PERCENT
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val SETTINGS_TOAST_HOST_TEST_TAG = "settingsToastHost"
const val WINDOW_OPACITY_VALUE_TEST_TAG = "windowOpacityValue"

/** Mesma razão da tag de opacidade: "115%" também é rótulo de chip no cartão de alertas. */
const val UI_SCALE_VALUE_TEST_TAG = "uiScaleValue"

/** O rótulo é traduzido; buscar por texto amarraria o teste ao idioma. */
const val CARDS_ONLY_MODE_SWITCH_TEST_TAG = "cardsOnlyModeSwitch"

/**
 * Seções das Configurações, uma por aba.
 *
 * Enum próprio, e não um valor a mais em algum enum existente: os `when`
 * exaustivos de `AppLanguage` e companhia não têm nada a ver com esta escolha.
 * A ordem de declaração é a ordem das abas na tela.
 */
enum class SettingsTab { GENERAL, ALERTS, APIS, ACCOUNTS, TEAM }

/** Marcado por aba: o rótulo é traduzido e buscar por texto amarraria o teste ao idioma. */
fun settingsTabTestTag(tab: SettingsTab): String = "settingsTab_${tab.name}"

internal fun settingsTabLabel(tab: SettingsTab, language: AppLanguage): String {
    val isPt = language == AppLanguage.PT
    return when (tab) {
        SettingsTab.GENERAL -> if (isPt) "Geral" else "General"
        SettingsTab.ALERTS -> if (isPt) "Alertas" else "Alerts"
        SettingsTab.APIS -> if (isPt) "APIs" else "APIs"
        SettingsTab.ACCOUNTS -> if (isPt) "Contas" else "Accounts"
        SettingsTab.TEAM -> if (isPt) "Time" else "Team"
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
    val detail: String? = null
)

@Composable
fun SettingsDialogContent(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    enabledApis: Set<ApiSource>,
    autoStartEnabled: Boolean,
    alwaysOnTopEnabled: Boolean = false,
    cardsOnlyMode: Boolean = false,
    windowOpacityPercent: Int = MAX_WINDOW_OPACITY_PERCENT,
    windowOpacityEnabled: Boolean = true,
    uiScalePercent: Int = DEFAULT_UI_SCALE_PERCENT,
    onUiScaleChange: (Int) -> Unit = {},
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAlwaysOnTopChange: (Boolean) -> Unit = {},
    /** Default vazio para não arrastar os geradores de captura e os testes de componente. */
    onCardsOnlyModeChange: (Boolean) -> Unit = {},
    onWindowOpacityChange: (Int) -> Unit = {},
    alertSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT,
    onAlertSettingsChange: (UsageAlertSettings) -> Unit = {},
    monthlyBudgetText: String = "",
    onMonthlyBudgetCommit: (String) -> Unit = {},
    onApiToggle: (ApiSource, Boolean) -> Unit,
    anthropicProfiles: List<AnthropicProfileUiModel> = emptyList(),
    onAnthropicProfileToggle: (String, Boolean) -> Unit = { _, _ -> },
    onAnthropicProfileRename: (String, String) -> Unit = { _, _ -> },
    onAddAnthropicProfile: () -> Unit = {},
    onRemoveAnthropicProfile: (String) -> Unit = {},
    onRescanAnthropicProfiles: () -> Unit = {},
    expandedProfileId: String? = null,
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
    teamAdminConnection: TeamConnectionUiState = TeamConnectionUiState(),
    onTeamAdminTokenChange: (String) -> Unit = {},
    onTeamValidateAdminToken: () -> Unit = {},
    onTeamOpenKeysManager: () -> Unit = {},
    onTeamExitAdminMode: () -> Unit = {},
    toastEvent: SettingsToastEvent? = null,
    /** Aba aberta ao entrar; existe para os geradores de captura escolherem a seção. */
    initialTab: SettingsTab = SettingsTab.GENERAL,
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

    // Evento que já existia quando o diálogo abriu é de uma edição anterior —
    // reexibi-lo faria a tela abrir avisando algo que o usuário nem acabou de
    // fazer.
    val staleToastId = remember { toastEvent?.id }

    // Host próprio: o diálogo é uma janela separada e o SnackbarHost do
    // dashboard não desenha por cima dela. O `dismiss` antes de mostrar impede
    // que mexer em vários controles seguidos enfileire avisos e o usuário fique
    // assistindo à fila esvaziar depois de já ter parado.
    LaunchedEffect(toastEvent?.id) {
        val event = toastEvent ?: return@LaunchedEffect
        if (event.id == staleToastId) {
            return@LaunchedEffect
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = settingsToastMessage(event.toast, currentLanguage),
            duration = SnackbarDuration.Short
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // A navegação fica à esquerda e não rola: ela é o controle, e o
            // conteúdo rolando não pode tirá-la da vista.
            SettingsSideNav(
                selected = selectedTab,
                language = currentLanguage,
                onSelect = { tab -> selectedTab = tab }
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
                when (selectedTab) {
                    SettingsTab.GENERAL -> GeneralSettingsTab(
                        currentTheme = currentTheme,
                        currentLanguage = currentLanguage,
                        autoStartEnabled = autoStartEnabled,
                        alwaysOnTopEnabled = alwaysOnTopEnabled,
                        cardsOnlyMode = cardsOnlyMode,
                        windowOpacityPercent = windowOpacityPercent,
                        windowOpacityEnabled = windowOpacityEnabled,
                        uiScalePercent = uiScalePercent,
                        onThemeToggle = onThemeToggle,
                        onLanguageChange = onLanguageChange,
                        onAutoStartChange = onAutoStartChange,
                        onAlwaysOnTopChange = onAlwaysOnTopChange,
                        onCardsOnlyModeChange = onCardsOnlyModeChange,
                        onWindowOpacityChange = onWindowOpacityChange,
                        onUiScaleChange = onUiScaleChange
                    )

                    SettingsTab.ALERTS -> {
                        AlertSettingsSection(
                            settings = alertSettings,
                            language = currentLanguage,
                            onSettingsChange = onAlertSettingsChange,
                            budgetText = monthlyBudgetText,
                            onBudgetCommit = onMonthlyBudgetCommit
                        )
                    }

                    SettingsTab.APIS -> MonitoredApisTab(
                        currentLanguage = currentLanguage,
                        enabledApis = enabledApis,
                        onApiToggle = onApiToggle
                    )

                    SettingsTab.ACCOUNTS -> AnthropicAccountsTab(
                        currentLanguage = currentLanguage,
                        anthropicProfiles = anthropicProfiles,
                        expandedProfileId = expandedProfileId,
                        onAnthropicProfileToggle = onAnthropicProfileToggle,
                        onAnthropicProfileRename = onAnthropicProfileRename,
                        onAddAnthropicProfile = onAddAnthropicProfile,
                        onRemoveAnthropicProfile = onRemoveAnthropicProfile,
                        onRescanAnthropicProfiles = onRescanAnthropicProfiles,
                        onToggleProfileExpanded = onToggleProfileExpanded
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
                            adminConnection = teamAdminConnection,
                            onAdminTokenChange = onTeamAdminTokenChange,
                            onValidateAdminToken = onTeamValidateAdminToken,
                            onOpenKeysManager = onTeamOpenKeysManager,
                            onExitAdminMode = onTeamExitAdminMode
                        )
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
 * Linha de opção: rótulo em mono, descrição em sans, controle à direita.
 *
 * A divisão entre as duas famílias é por papel: o rótulo é rótulo — largura fixa
 * de dígito, mesma classe do cabeçalho de coluna — e a descrição é texto corrido,
 * que é onde a sans existe. As duas estavam em `bodySmall`, ou seja, as duas em
 * sans, e o rótulo lia como mais uma frase.
 */
@Composable
private fun SettingsOptionRow(
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

@Composable
private fun GeneralSettingsTab(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    autoStartEnabled: Boolean,
    alwaysOnTopEnabled: Boolean,
    cardsOnlyMode: Boolean,
    windowOpacityPercent: Int,
    windowOpacityEnabled: Boolean,
    uiScalePercent: Int,
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAlwaysOnTopChange: (Boolean) -> Unit,
    onCardsOnlyModeChange: (Boolean) -> Unit,
    onWindowOpacityChange: (Int) -> Unit,
    onUiScaleChange: (Int) -> Unit
) {
    val isPt = currentLanguage == AppLanguage.PT

    // Dois painéis nomeados no lugar de uma coluna de controles empilhados: com
    // sete opções seguidas sem divisória nem título, achar uma delas era ler a
    // lista inteira. Aparência é o que a janela mostra; Sistema é o que ela faz
    // fora dela.
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Aparência" else "Appearance") }
    ) {
        SettingsOptionRow(label = if (isPt) "Tema" else "Theme") {
            ThemeToggle(
                isDark = currentTheme == AppTheme.DARK,
                language = currentLanguage,
                onToggle = onThemeToggle
            )
        }
        SettingsOptionRow(label = if (isPt) "Idioma" else "Language") {
            LanguageSelector(
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange
            )
        }
        WindowOpacitySlider(
            percent = windowOpacityPercent,
            language = currentLanguage,
            enabled = windowOpacityEnabled,
            onPercentChange = onWindowOpacityChange
        )
        UiScaleSlider(
            percent = uiScalePercent,
            language = currentLanguage,
            onPercentChange = onUiScaleChange
        )
    }

    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Sistema" else "System") }
    ) {
        AutoStartToggle(
            enabled = autoStartEnabled,
            language = currentLanguage,
            onToggle = onAutoStartChange
        )
        AlwaysOnTopToggle(
            enabled = alwaysOnTopEnabled,
            language = currentLanguage,
            onToggle = onAlwaysOnTopChange
        )
        // Ao lado de "manter sempre visível": as duas são propriedades da
        // moldura da janela, não do conteúdo dela.
        CardsOnlyModeToggle(
            enabled = cardsOnlyMode,
            language = currentLanguage,
            onToggle = onCardsOnlyModeChange,
            showDivider = false
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonitoredApisTab(
    currentLanguage: AppLanguage,
    enabledApis: Set<ApiSource>,
    onApiToggle: (ApiSource, Boolean) -> Unit
) {
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (currentLanguage == AppLanguage.PT) "APIs monitoradas" else "Monitored APIs"
            )
        }
    ) {
        ApiSelector(
            enabledApis = enabledApis,
            language = currentLanguage,
            onToggle = onApiToggle
        )
    }
}

@Composable
private fun AnthropicAccountsTab(
    currentLanguage: AppLanguage,
    anthropicProfiles: List<AnthropicProfileUiModel>,
    expandedProfileId: String?,
    onAnthropicProfileToggle: (String, Boolean) -> Unit,
    onAnthropicProfileRename: (String, String) -> Unit,
    onAddAnthropicProfile: () -> Unit,
    onRemoveAnthropicProfile: (String) -> Unit,
    onRescanAnthropicProfiles: () -> Unit,
    onToggleProfileExpanded: (String) -> Unit
) {
    // As duas ações vão para o `trailing` do cabeçalho, como no protótipo: elas
    // agem sobre a lista inteira, e no corpo competiam com as linhas de perfil.
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (currentLanguage == AppLanguage.PT) "Contas Anthropic" else "Anthropic accounts",
                trailing = {
                    AppButton(
                        label = if (currentLanguage == AppLanguage.PT) "Redetectar" else "Rescan",
                        onClick = onRescanAnthropicProfiles,
                        tone = AppButtonTone.GHOST
                    )
                    AppButton(
                        label = if (currentLanguage == AppLanguage.PT) "Adicionar" else "Add",
                        onClick = onAddAnthropicProfile,
                        tone = AppButtonTone.PRIMARY
                    )
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            if (anthropicProfiles.isEmpty()) {
                Text(
                    text = if (currentLanguage == AppLanguage.PT) {
                        "Nenhum perfil Anthropic detectado."
                    } else {
                        "No Anthropic profile detected."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                anthropicProfiles.forEach { profile ->
                    key(profile.id) {
                        AnthropicProfileRow(
                            profile = profile,
                            language = currentLanguage,
                            expanded = profile.id == expandedProfileId,
                            onToggle = onAnthropicProfileToggle,
                            onRename = onAnthropicProfileRename,
                            onRemove = onRemoveAnthropicProfile,
                            onToggleExpanded = { onToggleProfileExpanded(profile.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Navegação lateral das Configurações.
 *
 * Era uma fileira de chips presa no topo. Cinco chips numa janela estreita
 * quebravam em duas linhas e empurravam o conteúdo para baixo; na lateral eles
 * ocupam largura fixa e a lista cresce sem mexer no que está sendo lido.
 *
 * Continua sendo o **enum existente**: nenhuma seção nova, nenhum valor novo em
 * `SettingsTab`, e a `testTag` de cada uma é a mesma.
 */
@Composable
private fun SettingsSideNav(
    selected: SettingsTab,
    language: AppLanguage,
    onSelect: (SettingsTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(SETTINGS_NAV_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (language == AppLanguage.PT) "Seções" else "Sections",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
        SettingsTab.entries.forEach { tab ->
            SettingsNavItem(
                label = settingsTabLabel(tab, language),
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag(settingsTabTestTag(tab))
            )
        }
    }
}

@Composable
private fun SettingsNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .background(container)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1
        )
    }
}

/** Largura da coluna de navegação: cabe "Configurações" sem quebrar. */
private val SETTINGS_NAV_WIDTH = 150.dp

@Composable
private fun AnthropicProfileRow(
    profile: AnthropicProfileUiModel,
    language: AppLanguage,
    expanded: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onToggleExpanded: () -> Unit
) {
    val statusText = when (profile.status) {
        AnthropicProfileUiStatus.READY -> if (language == AppLanguage.PT) "Pronto" else "Ready"
        AnthropicProfileUiStatus.INCOMPLETE -> if (language == AppLanguage.PT) "Incompleto" else "Incomplete"
        AnthropicProfileUiStatus.INVALID -> if (language == AppLanguage.PT) "Inválido" else "Invalid"
        AnthropicProfileUiStatus.DUPLICATE -> if (language == AppLanguage.PT) "Conta duplicada" else "Duplicate account"
    }
    val statusTone = if (profile.status == AnthropicProfileUiStatus.READY) {
        AppTone.OK
    } else {
        AppTone.CRITICAL
    }
    val editLabel = if (language == AppLanguage.PT) "Editar" else "Edit"
    val collapseLabel = if (language == AppLanguage.PT) "Recolher" else "Collapse"

    // Linha de dados, não bloco em `surfaceVariant`: aquele é o realce de hover
    // das listas, e com ele como fundo fixo passar o mouse deixava de dar
    // retorno. O estado do perfil vira ponto e palavra, como no resto do app.
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            AppDataRow(showDivider = false, horizontalPadding = 0.dp) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val identity = profile.identityLabel
                    if (identity != null) {
                        Text(
                            text = identity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AppStatusIndicator(label = statusText, tone = statusTone)
                AppSwitch(
                    checked = profile.enabled,
                    onCheckedChange = { checked -> onToggle(profile.id, checked) }
                )
                AppIconButton(
                    contentDescription = if (expanded) collapseLabel else editLabel,
                    onClick = onToggleExpanded
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                DebouncedTextField(
                    value = profile.label,
                    label = if (language == AppLanguage.PT) "Apelido" else "Label",
                    onCommit = { newLabel -> onRename(profile.id, newLabel) }
                )
                Text(
                    text = profile.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = listOfNotNull(statusText, profile.detail).joinToString(" — "),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusTone.color()
                )
                if (profile.removable) {
                    AppButton(
                        label = if (language == AppLanguage.PT) "Remover do monitor" else "Remove from monitor",
                        onClick = { onRemove(profile.id) },
                        tone = AppButtonTone.GHOST
                    )
                }
            }
        }
    }
}

/**
 * Seletor de tema: segmentado de duas opções, como no protótipo.
 *
 * Era um rótulo com emoji ao lado de um interruptor, e a forma mentia sobre a
 * natureza da escolha: interruptor diz ligado/desligado, e tema é uma escolha
 * entre duas alternativas — a mesma pergunta que o seletor de idioma logo
 * abaixo já respondia com um segmentado. Era também o único emoji da interface.
 */
@Composable
fun ThemeToggle(
    isDark: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT
    val options = listOf(
        AppSegment(label = if (isPt) "Escuro" else "Dark"),
        AppSegment(label = if (isPt) "Claro" else "Light")
    )
    AppSegmentedControl(
        options = options,
        selectedIndex = if (isDark) 0 else 1,
        // O callback do app alterna, não escolhe: clicar na opção já ativa não
        // pode inverter o tema.
        onSelect = { index -> if ((index == 0) != isDark) onToggle() },
        modifier = modifier
    )
}

@Composable
fun AutoStartToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Inicialização com Sistema" else "System Startup",
        description = if (isPt) {
            "Registra a aplicação na inicialização do usuário atual."
        } else {
            "Registers the app to launch with the current user session."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(checked = enabled, onCheckedChange = { onToggle(it) })
    }
}

@Composable
fun AlwaysOnTopToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Manter sempre visível" else "Always on top",
        description = if (isPt) {
            "Mantém a janela acima das demais."
        } else {
            "Keeps the window above the others."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(checked = enabled, onCheckedChange = { onToggle(it) })
    }
}

/**
 * Modo somente cards: esconde a barra de título e o rodapé da janela.
 *
 * O texto de apoio não é decoração. Ligado, o modo tira da tela o botão de
 * fechar e a engrenagem das configurações, e quem não souber como voltar fica
 * com um app que não consegue desligar — as três saídas têm de estar escritas
 * onde o interruptor é acionado.
 */
@Composable
fun CardsOnlyModeToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Somente os cards" else "Cards only",
        description = if (isPt) {
            "Esconde a barra de título e o rodapé. Para voltar: Ctrl+Shift+M, o ícone na bandeja ou a faixa que aparece ao passar o mouse no topo da janela."
        } else {
            "Hides the title bar and the footer. To return: Ctrl+Shift+M, the tray icon, or the strip that appears when hovering the top of the window."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) },
            modifier = Modifier.testTag(CARDS_ONLY_MODE_SWITCH_TEST_TAG)
        )
    }
}

/**
 * Trilha e polegar de um controle deslizante, no desenho do sistema.
 *
 * O `Slider` do Material tem trilha de 16dp de altura, indicadores de parada
 * desenhados nela e um polegar em cápsula — três coisas que este sistema visual
 * não tem em lugar nenhum. Os dois slots trocam só o desenho: a semântica de
 * progresso, que é o que `SetProgress` dos testes exercita, continua vindo do
 * próprio `Slider`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSliderTrack(state: SliderState) {
    val span = state.valueRange.endInclusive - state.valueRange.start
    val fraction = if (span > 0f) (state.value - state.valueRange.start) / span else 0f
    AppProgressTrack(fraction = fraction, tone = AppTone.NEUTRAL)
}

@Composable
private fun AppSliderThumb() {
    Box(
        modifier = Modifier
            .size(SLIDER_THUMB_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface)
    )
}

/** Lado do polegar: alvo de arrasto sem virar a peça mais pesada da tela. */
private val SLIDER_THUMB_SIZE = 12.dp

/** Largura reservada ao controle deslizante dentro da linha de opção. */
private val SLIDER_WIDTH = 180.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindowOpacitySlider(
    percent: Int,
    language: AppLanguage = AppLanguage.PT,
    enabled: Boolean = true,
    onPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Opacidade da janela" else "Window opacity",
        description = if (enabled) {
            null
        } else if (isPt) {
            "Transparência não suportada neste sistema."
        } else {
            "Transparency is not supported on this system."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            // Marcado porque 75% também é rótulo de chip no cartão de alertas:
            // buscar pelo texto encontraria os dois.
            modifier = Modifier.testTag(WINDOW_OPACITY_VALUE_TEST_TAG)
        )
        Slider(
            value = percent.toFloat(),
            onValueChange = { value -> onPercentChange(value.roundToInt()) },
            valueRange = MIN_WINDOW_OPACITY_PERCENT.toFloat()..MAX_WINDOW_OPACITY_PERCENT.toFloat(),
            // Sem steps: 51 indicadores de parada na trilha só poluiriam. A
            // granularidade de 1 ponto percentual já vem do roundToInt e do
            // valor Int devolvido pelo estado.
            steps = 0,
            enabled = enabled,
            track = { state -> AppSliderTrack(state) },
            thumb = { AppSliderThumb() },
            modifier = Modifier.width(SLIDER_WIDTH)
        )
    }
}

/**
 * Escala global da interface.
 *
 * Mesma anatomia do [WindowOpacitySlider] logo acima, porque os dois respondem à
 * mesma pergunta sobre a própria janela. A diferença é a granularidade: os
 * `steps` prendem o valor à grade de [UI_SCALE_STEP_PERCENT], já que a distância
 * entre 113% e 114% não é visível e só multiplicaria gravações.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiScaleSlider(
    percent: Int,
    language: AppLanguage = AppLanguage.PT,
    onPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Tamanho da interface" else "Interface size",
        description = if (isPt) {
            "Vale para todas as janelas do app."
        } else {
            "Applies to every window of the app."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(UI_SCALE_VALUE_TEST_TAG)
        )
        Slider(
            value = percent.toFloat(),
            onValueChange = { value -> onPercentChange(snapUiScalePercent(value)) },
            valueRange = MIN_UI_SCALE_PERCENT.toFloat()..MAX_UI_SCALE_PERCENT.toFloat(),
            // Pontos intermediários da grade de 5, sem contar as duas pontas.
            steps = (MAX_UI_SCALE_PERCENT - MIN_UI_SCALE_PERCENT) / UI_SCALE_STEP_PERCENT - 1,
            track = { state -> AppSliderTrack(state) },
            thumb = { AppSliderThumb() },
            modifier = Modifier.width(SLIDER_WIDTH)
        )
    }
}

/** Prende o valor do slider à grade de [UI_SCALE_STEP_PERCENT] dentro da faixa. */
internal fun snapUiScalePercent(value: Float): Int {
    val steps = (value / UI_SCALE_STEP_PERCENT).roundToInt()
    return (steps * UI_SCALE_STEP_PERCENT).coerceIn(MIN_UI_SCALE_PERCENT, MAX_UI_SCALE_PERCENT)
}

@Composable
fun LanguageSelector(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    // Segmentado: idioma é uma escolha entre alternativas mutuamente exclusivas,
    // que é exatamente o que este controle diz. Dois botões de texto lado a lado
    // deixavam a diferença entre escolhido e não escolhido só na cor.
    AppSegmentedControl(
        options = AppLanguage.entries.map { language -> AppSegment(label = language.name) },
        selectedIndex = AppLanguage.entries.indexOf(currentLanguage),
        onSelect = { index -> onLanguageChange(AppLanguage.entries[index]) },
        modifier = modifier
    )
}
