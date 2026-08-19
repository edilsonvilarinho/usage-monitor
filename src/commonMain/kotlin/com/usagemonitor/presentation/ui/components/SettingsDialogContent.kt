package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme
import com.usagemonitor.domain.entity.MAX_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.MIN_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val SETTINGS_TOAST_HOST_TEST_TAG = "settingsToastHost"
const val WINDOW_OPACITY_VALUE_TEST_TAG = "windowOpacityValue"

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
    windowOpacityPercent: Int = MAX_WINDOW_OPACITY_PERCENT,
    windowOpacityEnabled: Boolean = true,
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAlwaysOnTopChange: (Boolean) -> Unit = {},
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
                        windowOpacityPercent = windowOpacityPercent,
                        windowOpacityEnabled = windowOpacityEnabled,
                        onThemeToggle = onThemeToggle,
                        onLanguageChange = onLanguageChange,
                        onAutoStartChange = onAutoStartChange,
                        onAlwaysOnTopChange = onAlwaysOnTopChange,
                        onWindowOpacityChange = onWindowOpacityChange
                    )

                    SettingsTab.ALERTS -> SettingsSectionCard {
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

                    SettingsTab.TEAM -> SettingsSectionCard {
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

@Composable
private fun GeneralSettingsTab(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    autoStartEnabled: Boolean,
    alwaysOnTopEnabled: Boolean,
    windowOpacityPercent: Int,
    windowOpacityEnabled: Boolean,
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAlwaysOnTopChange: (Boolean) -> Unit,
    onWindowOpacityChange: (Int) -> Unit
) {
    SettingsSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeToggle(
                isDark = currentTheme == AppTheme.DARK,
                language = currentLanguage,
                onToggle = onThemeToggle
            )

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

            WindowOpacitySlider(
                percent = windowOpacityPercent,
                language = currentLanguage,
                enabled = windowOpacityEnabled,
                onPercentChange = onWindowOpacityChange
            )

            Text(
                text = if (currentLanguage == AppLanguage.PT) "Idioma" else "Language",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            LanguageSelector(
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonitoredApisTab(
    currentLanguage: AppLanguage,
    enabledApis: Set<ApiSource>,
    onApiToggle: (ApiSource, Boolean) -> Unit
) {
    SettingsSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (currentLanguage == AppLanguage.PT) "APIs monitoradas" else "Monitored APIs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiSource.entries.forEach { api ->
                    ApiCheckboxRow(
                        api = api,
                        language = currentLanguage,
                        isChecked = api in enabledApis,
                        onCheckedChange = { checked -> onApiToggle(api, checked) }
                    )
                }
            }
        }
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
    SettingsSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentLanguage == AppLanguage.PT) "Contas Anthropic" else "Anthropic accounts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
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
private fun SettingsSectionCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.card)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

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
    val statusColor = if (profile.status == AnthropicProfileUiStatus.READY) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val editLabel = if (language == AppLanguage.PT) "Editar" else "Edit"
    val collapseLabel = if (language == AppLanguage.PT) "Recolher" else "Collapse"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.label,
                        style = MaterialTheme.typography.bodyMedium,
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
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                AppSwitch(
                    checked = profile.enabled,
                    onCheckedChange = { checked -> onToggle(profile.id, checked) }
                )
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.Edit,
                        contentDescription = if (expanded) collapseLabel else editLabel
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
                    color = statusColor
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

@Composable
fun ThemeToggle(
    isDark: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        isDark && language == AppLanguage.PT -> "🌙 Escuro"
        isDark -> "🌙 Dark"
        language == AppLanguage.PT -> "☀️ Claro"
        else -> "☀️ Light"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.toggleable(
            value = isDark,
            role = Role.Switch,
            onValueChange = { onToggle() }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        // Sem callback próprio: quem alterna é a linha inteira, que já carrega o
        // `toggleable` com `Role.Switch`. Um segundo alvo de clique aqui dentro
        // daria dois caminhos para a mesma ação.
        AppSwitch(
            checked = isDark,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun AutoStartToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (language == AppLanguage.PT) "Inicialização com Sistema" else "System Startup"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) }
        )
    }
}

@Composable
fun AlwaysOnTopToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (language == AppLanguage.PT) "Manter sempre visível" else "Always on top"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) }
        )
    }
}

@Composable
fun WindowOpacitySlider(
    percent: Int,
    language: AppLanguage = AppLanguage.PT,
    enabled: Boolean = true,
    onPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (language == AppLanguage.PT) "Opacidade da janela" else "Window opacity"
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Marcado porque "75%" também é rótulo de chip no cartão de
                // alertas: buscar pelo texto encontraria os dois.
                modifier = Modifier.testTag(WINDOW_OPACITY_VALUE_TEST_TAG)
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { value -> onPercentChange(value.roundToInt()) },
            valueRange = MIN_WINDOW_OPACITY_PERCENT.toFloat()..MAX_WINDOW_OPACITY_PERCENT.toFloat(),
            // Sem steps: 51 tick marks desenhados na trilha só poluiriam. A granularidade
            // de 1 ponto percentual já vem do roundToInt e do valor Int devolvido pelo estado.
            steps = 0,
            enabled = enabled,
            // Trilha na cor da superfície e polegar na cor do texto: o azul cheio
            // do Material era o elemento mais forte da tela de Configurações, para
            // ajustar transparência de janela.
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (!enabled) {
            Text(
                text = if (language == AppLanguage.PT) {
                    "Transparência não suportada neste sistema."
                } else {
                    "Transparency is not supported on this system."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RefreshControl(
    secondsUntilRefresh: Int,
    language: AppLanguage,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val countdownText = if (language == AppLanguage.PT) {
        "Próxima atualização: ${formatRefreshCountdown(secondsUntilRefresh)}"
    } else {
        "Next update: ${formatRefreshCountdown(secondsUntilRefresh)}"
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = countdownText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .widthIn(max = 140.dp)
                .padding(end = 4.dp)
        )
        IconButton(onClick = onRefresh) {
            Text(
                text = "↻",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
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
