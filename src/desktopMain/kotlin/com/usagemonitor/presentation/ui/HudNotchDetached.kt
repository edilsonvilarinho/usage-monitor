package com.usagemonitor.presentation.ui

import androidx.compose.ui.graphics.Shape
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppShapes

/**
 * A descrição semântica do buraco do modo flutuante (#619, A10b): é o repouso da barra destacada, e o que o
 * leitor de tela anuncia quando ela está fechada.
 */
internal fun hudFloatingHoleDescription(language: AppLanguage): String =
    localized(language, "Usage Monitor — passe o mouse para abrir", "Usage Monitor — hover to open")

/**
 * A forma do corpo: a barra encaixada usa [HudNotchShape] (lado reto na borda); a destacada tem cantos
 * arredondados nos 4 lados, com o raio do token [AppShapes.large] (A10b).
 */
internal fun hudNotchShape(edge: HudEdge, detached: Boolean): Shape =
    if (detached) AppShapes.large else HudNotchShape(edge)
