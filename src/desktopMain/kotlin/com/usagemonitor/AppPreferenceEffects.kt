package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.viewmodel.recordFailure
import com.usagemonitor.update.ensureLinuxMenuIconCurrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Os efeitos das preferências que rodam fora de qualquer tela: a leitura real do
 * autostart, a gravação com debounce da escala e da opacidade, os avisos de
 * "salvo" que saem dessas gravações e a arrumação dos cards quando uma conta
 * aparece ou some.
 */
@Composable
internal fun AppPreferenceEffects(
    shell: AppShellState,
    settings: PreferencesSettings,
    availableTargets: List<UsageTargetKey>,
    feedback: AppSettingsFeedback,
    breadcrumbs: BreadcrumbRecorder
) {
    LaunchedEffect(availableTargets) {
        shell.keepCardsOf(availableTargets)
    }
    AutoStartResolution(shell, settings, breadcrumbs)
    OpacityPersistence(shell, settings)
    UiScalePersistence(shell, settings)

    // Quem grava é o coletor com debounce; o aviso sai daqui, onde o toast
    // existe. A geração inicial não conta: ninguém mexeu em nada.
    LaunchedEffect(shell.opacitySaveGeneration) {
        if (shell.opacitySaveGeneration > 0) {
            feedback.showToast(SettingsToast.Saved(SettingsField.WINDOW_OPACITY))
        }
    }
    LaunchedEffect(shell.uiScaleSaveGeneration) {
        if (shell.uiScaleSaveGeneration > 0) {
            feedback.showToast(SettingsToast.Saved(SettingsField.UI_SCALE))
        }
    }
}

/**
 * O valor inicial do autostart vem das preferências (leitura em memória); a
 * confirmação do sistema — que pode envolver `reg query`, bloqueante — chega
 * depois, fora da thread da interface, para não atrasar o primeiro quadro.
 */
@Composable
private fun AutoStartResolution(shell: AppShellState, settings: PreferencesSettings, breadcrumbs: BreadcrumbRecorder) {
    LaunchedEffect(settings) {
        if (AutoStartManager.isAutoStartSupported()) {
            val resolved = withContext(Dispatchers.IO) { AutoStartManager.isAutoStartEnabled() }
            shell.applyResolvedAutoStart(resolved)
            // Migração por baixo: instalação anterior tem a entrada de
            // inicialização sem o argumento de origem, e sem ele todo arranque por
            // autostart seria registrado como manual.
            val migrationResult = runCatching {
                withContext(Dispatchers.IO) { AutoStartManager.ensureAutoStartCommandCurrent() }
            }.onFailure { error ->
                breadcrumbs.recordFailure("atualizar comando de inicialização", error)
            }.getOrNull()
            if (migrationResult is AutoStartResult.Failure) {
                breadcrumbs.recordFailure("atualizar comando de inicialização", migrationResult.reason)
            }
        }
        // Mesmo motivo, para a entrada de MENU (issue #133): fora do `if` porque
        // a checagem é sobre autostart e esta é sobre Linux, e
        // `ensureLinuxMenuIconCurrent()` se protege sozinha nas outras plataformas.
        withContext(Dispatchers.IO) { ensureLinuxMenuIconCurrent() }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun OpacityPersistence(shell: AppShellState, settings: PreferencesSettings) {
    LaunchedEffect(settings) {
        snapshotFlow { shell.windowOpacityPercent }
            .distinctUntilChanged()
            // A primeira emissão é o valor que acabou de ser lido do registro:
            // regravá-lo não muda nada e faria o app subir avisando "salvo".
            .drop(1)
            .debounce(250.milliseconds)
            .collect { percent ->
                persistWindowOpacityPercent(settings, percent)
                // O aviso sai depois da gravação: no callback do slider seria um
                // toast por pixel arrastado.
                shell.opacitySaveGeneration += 1
            }
    }
}

/**
 * Grava a escala no commit, não a cada tique do slider: o conteúdo já escala ao
 * vivo pela densidade. A HUD e as janelas modais se dimensionam pela própria
 * geometria, então não há moldura para redimensionar aqui.
 */
@OptIn(FlowPreview::class)
@Composable
private fun UiScalePersistence(shell: AppShellState, settings: PreferencesSettings) {
    // Primeira execução depois da atualização que subiu a escala default de 100
    // para 115: gravar a escala fecha a porta, porque a chave passa a existir.
    LaunchedEffect(settings) {
        if (!hasPersistedUiScale(settings)) {
            persistUiScalePercent(settings, shell.uiScalePercent)
        }
    }
    LaunchedEffect(settings) {
        snapshotFlow { shell.uiScalePercent }
            .distinctUntilChanged()
            .drop(1)
            .debounce(250.milliseconds)
            .collect { percent ->
                persistUiScalePercent(settings, percent)
                shell.uiScaleSaveGeneration += 1
            }
    }
}
