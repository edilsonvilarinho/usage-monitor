package com.usagemonitor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.normalizeCardOrder
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppThemePreset

/**
 * As preferências de aparência e de moldura que o app inteiro lê: escala, tema,
 * idioma, movimento, opacidade e a arrumação das contas na barra HUD.
 *
 * Cada mudança passa por um método, que grava e aplica a regra de negócio no
 * mesmo lugar — regra espalhada em lambdas locais de `runUsageMonitor` podia
 * ser contornada por qualquer trecho que escrevesse direto no `var`.
 *
 * A escala e a opacidade são exceção: o controle deslizante muda o valor a cada
 * pixel, e quem grava é o coletor com debounce de `AppPreferenceEffects`.
 */
@Stable
internal class AppShellState(
    private val settings: PreferencesSettings,
    initialTargets: List<UsageTargetKey>
) {
    /** Escala da interface; o valor persistido acompanha pelo coletor com debounce. */
    var uiScalePercent by mutableStateOf(readPersistedUiScalePercent(settings))
        private set

    /** Sobe a cada gravação da escala; é o que faz o aviso de "salvo" sair uma vez. */
    var uiScaleSaveGeneration by mutableStateOf(0)

    var reducedMotion by mutableStateOf(readPersistedReducedMotion(settings))

    var trayUsageRing by mutableStateOf(readPersistedTrayUsageRing(settings))
        private set

    /**
     * A política de movimento de **todas** as janelas: cada `Window` tem
     * composição própria, e a que não receber isto fica na política estática sem
     * erro nenhum — a armadilha da escala.
     */
    val motion: AppMotionPolicy
        get() = AppMotionPolicy.forPreference(reducedMotion)

    var themePreset by mutableStateOf(readPersistedThemePreset(settings))
        private set

    var language by mutableStateOf(storedLanguage(settings))
        private set

    /** O que estava gravado no arranque; a leitura real do sistema chega depois. */
    val storedAutoStartPreference: Boolean = settings.getBoolean(AUTO_START_KEY, false)

    var autoStartEnabled by mutableStateOf(storedAutoStartPreference)
        private set

    val windowOpacitySupported: Boolean = isWindowOpacitySupported()

    var windowOpacityPercent by mutableStateOf(readPersistedWindowOpacityPercent(settings))
        private set

    /** Sobe a cada gravação da opacidade, pela mesma razão de [uiScaleSaveGeneration]. */
    var opacitySaveGeneration by mutableStateOf(0)

    var monthlyBudgetMicros by mutableStateOf(readPersistedBudgetMicros(settings))
        private set

    var cardOrder by mutableStateOf(
        normalizeCardOrder(readUsageTargetCollection(settings, CARD_ORDER_KEY), initialTargets)
    )
        private set

    fun changeUiScale(percent: Int) {
        uiScalePercent = clampUiScalePercent(percent)
    }

    fun changeReducedMotion(enabled: Boolean) {
        reducedMotion = enabled
        persistReducedMotion(settings, enabled)
    }

    fun changeTrayUsageRing(enabled: Boolean) {
        trayUsageRing = enabled
        persistTrayUsageRing(settings, enabled)
    }

    fun changeTheme(preset: AppThemePreset) {
        themePreset = preset
        persistThemePreset(settings, preset)
    }

    fun changeLanguage(selected: AppLanguage) {
        language = selected
        settings.putString(LANGUAGE_KEY, selected.name)
    }

    /**
     * A leitura real do sistema, que chega depois do primeiro quadro. Só grava
     * quando ela diverge do que estava gravado no arranque.
     */
    fun applyResolvedAutoStart(resolved: Boolean) {
        if (resolved != storedAutoStartPreference) {
            settings.putBoolean(AUTO_START_KEY, resolved)
        }
        autoStartEnabled = resolved
    }

    /** O estado que o sistema realmente tem, gravado como a preferência. */
    fun recordAutoStart(enabled: Boolean) {
        autoStartEnabled = enabled
        settings.putBoolean(AUTO_START_KEY, enabled)
    }

    fun changeWindowOpacity(percent: Int) {
        windowOpacityPercent = clampWindowOpacityPercent(percent)
    }

    fun changeMonthlyBudget(micros: Long) {
        monthlyBudgetMicros = micros
        persistBudgetMicros(settings, micros)
    }

    /**
     * Conta que sumiu sai da ordem; conta nova entra nela. A ordem é a que o
     * arrasto do dashboard gravava e que a barra HUD segue lendo.
     */
    fun keepCardsOf(availableTargets: List<UsageTargetKey>) {
        cardOrder = normalizeCardOrder(cardOrder, availableTargets)
        writeUsageTargetCollection(settings, CARD_ORDER_KEY, cardOrder)
    }
}
