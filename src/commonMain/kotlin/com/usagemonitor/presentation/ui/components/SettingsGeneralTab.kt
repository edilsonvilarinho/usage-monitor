package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppThemePreset

@Composable
internal fun GeneralSettingsTab(
    currentTheme: AppThemePreset,
    currentLanguage: AppLanguage,
    autoStartEnabled: Boolean,
    alwaysOnTopEnabled: Boolean,
    hudMode: Boolean,
    windowOpacityPercent: Int,
    windowOpacityEnabled: Boolean,
    uiScalePercent: Int,
    reducedMotion: Boolean,
    autoUpdateEnabled: Boolean,
    autoUpdateSupport: AppUpdateSupport,
    autoUpdatePlatform: AppUpdatePlatform?,
    lastUpdateReceipt: AppUpdateReceipt?,
    autoUpdateFeedOverride: String?,
    onThemeChange: (AppThemePreset) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAlwaysOnTopChange: (Boolean) -> Unit,
    onHudModeChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onWindowOpacityChange: (Int) -> Unit,
    onUiScaleChange: (Int) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onReportBug: () -> Unit,
    trayUsageRing: Boolean = false,
    onTrayUsageRingChange: (Boolean) -> Unit = {},
    receiveBetaUpdates: Boolean = false,
    onReceiveBetaUpdatesChange: (Boolean) -> Unit = {}
) {
    val isPt = currentLanguage == AppLanguage.PT

    // Dois painéis nomeados no lugar de uma coluna de controles empilhados: com
    // sete opções seguidas sem divisória nem título, achar uma delas era ler a
    // lista inteira. Aparência é o que a janela mostra; Sistema é o que ela faz
    // fora dela.
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Aparência" else "Appearance") }
    ) {
        AppDataRow {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isPt) "Tema" else "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isPt) {
                        "Escolha uma das treze paletas claras ou treze escuras."
                    } else {
                        "Choose one of thirteen light or thirteen dark palettes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ThemePresetPicker(
                    selected = currentTheme,
                    language = currentLanguage,
                    onSelect = onThemeChange,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
            }
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
        // Em Aparência e não em Sistema: é sobre como a janela se desenha, a
        // mesma pergunta da escala logo acima.
        ReducedMotionToggle(
            enabled = reducedMotion,
            language = currentLanguage,
            onToggle = onReducedMotionChange,
            showDivider = false
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
        // Junto de "iniciar com o sistema": as duas descrevem o que o app faz
        // sem ninguém pedir.
        AutoUpdateToggle(
            enabled = autoUpdateEnabled,
            support = autoUpdateSupport,
            platform = autoUpdatePlatform,
            language = currentLanguage,
            lastReceipt = lastUpdateReceipt,
            feedUrlOverride = autoUpdateFeedOverride,
            onToggle = onAutoUpdateChange
        )
        // Logo abaixo: escolhe *quais* versões a linha de cima baixa.
        BetaUpdatesToggle(
            enabled = receiveBetaUpdates,
            language = currentLanguage,
            onToggle = onReceiveBetaUpdatesChange
        )
        AlwaysOnTopToggle(
            enabled = alwaysOnTopEnabled,
            language = currentLanguage,
            onToggle = onAlwaysOnTopChange
        )
        // Ao lado de "manter sempre visível": as duas são propriedades da
        // moldura da janela, não do conteúdo dela.
        HudModeToggle(
            enabled = hudMode,
            language = currentLanguage,
            onToggle = onHudModeChange
        )
        // Fecha a seção: é o que o app mostra fora da janela, na bandeja, e
        // não mais uma moldura dela.
        TrayUsageRingToggle(
            enabled = trayUsageRing,
            language = currentLanguage,
            onToggle = onTrayUsageRingChange,
            showDivider = false
        )
    }

    // Seção própria, e não mais uma linha em "Sistema": aquelas são
    // interruptores de comportamento contínuo do app, e esta é uma ação que o
    // usuário dispara uma vez. A ação vai no `trailing` do cabeçalho porque age
    // sobre a seção inteira -- mesmo lugar do "Adicionar" da aba Contas.
    //
    // `PRIMARY` porque a aba Geral não tinha nenhum botão primário: `PRIMARY` é
    // uma por tela, e esta é a única ação que a tela propõe.
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (isPt) "Diagnóstico" else "Diagnostics",
                trailing = {
                    AppButton(
                        label = if (isPt) "Reportar um bug" else "Report a bug",
                        tone = AppButtonTone.PRIMARY,
                        onClick = onReportBug,
                        modifier = Modifier.testTag(REPORT_BUG_BUTTON_TEST_TAG)
                    )
                }
            )
        }
    ) {
        AppDataRow(showDivider = false) {
            Text(
                text = if (isPt) {
                    "O app guarda uma trilha dos últimos passos e dos erros em " +
                        "~/.usage-monitor/diagnostics. Ao reportar, você revisa o pacote antes de " +
                        "publicá-lo: nada é enviado automaticamente."
                } else {
                    "The app keeps a trail of the last steps and errors in " +
                        "~/.usage-monitor/diagnostics. When reporting, you review the package " +
                        "before publishing it: nothing is sent automatically."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
