package com.usagemonitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.MAX_WINDOW_OPACITY_PERCENT
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudNotch

/** A prévia não tem texto próprio para buscar; a tag a acha em qualquer idioma. */
internal const val SETTINGS_HUD_PREVIEW_TEST_TAG = "settingsHudPreview"

/**
 * Prévia da barra HUD na aba Aparência (issue #399, direção X10).
 *
 * É o próprio [HudNotch], parado e fechado, com as contas da última leitura: tema
 * e tamanho da interface chegam pelo `AppTheme` da janela de Configurações — a
 * mesma preferência que a HUD recebe —, e a opacidade é aplicada aqui, porque na
 * HUD ela é da janela AWT e não da composição.
 *
 * **A geometria é a da HUD** ([hudNotchSizes]), nunca medida: o mesmo critério que
 * dimensiona a janela dela. A largura disponível é o `maxAlong`, como a borda da
 * tela é para a HUD — conta demais vira a faixa compacta, igual lá.
 *
 * Sem gesto: uma camada por cima recebe o ponteiro, e nem o hover abre balão nem
 * o clique recoleta. A prévia mostra, não comanda.
 */
@Composable
internal fun SettingsHudPreview(
    accounts: List<HudAccount>,
    fallbackLabel: String,
    windowOpacityPercent: Int,
    windowOpacitySupported: Boolean,
    language: AppLanguage
) {
    // Onde a plataforma não aplica opacidade à HUD, a prévia também não aplica:
    // prometeria um efeito que a barra real não tem.
    val opacity = if (windowOpacitySupported) windowOpacityPercent else MAX_WINDOW_OPACITY_PERCENT
    val description = if (language == AppLanguage.PT) "Prévia da barra HUD" else "HUD bar preview"
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .testTag(SETTINGS_HUD_PREVIEW_TEST_TAG)
            .semantics { contentDescription = description }
    ) {
        val sizes = remember(accounts, fallbackLabel, maxWidth) {
            hudNotchSizes(
                accounts = accounts,
                edge = HudEdge.TOP,
                fallbackLabel = fallbackLabel,
                hasUpdateIndicator = false,
                maxAlong = maxWidth
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(sizes.collapsed.height + PREVIEW_SHADOW_ROOM),
            contentAlignment = Alignment.TopCenter
        ) {
            HudNotch(
                accounts = accounts,
                edge = HudEdge.TOP,
                sizes = sizes,
                fallbackLabel = fallbackLabel,
                language = language,
                modifier = Modifier.alpha(opacity / MAX_WINDOW_OPACITY_PERCENT.toFloat())
            )
            // Irmã por cima: o teste de toque do Compose entrega o ponteiro só à
            // de cima, e o notch embaixo não recebe nem o hover.
            Box(modifier = Modifier.matchParentSize().pointerInput(Unit) { })
        }
    }
}

/** O respiro da sombra do notch, o mesmo que as capturas dão à cena. */
private val PREVIEW_SHADOW_ROOM = 16.dp
