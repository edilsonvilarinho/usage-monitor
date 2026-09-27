package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.MAX_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MAX_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.MIN_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MIN_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.UI_SCALE_STEP_PERCENT
import kotlin.math.roundToInt

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
