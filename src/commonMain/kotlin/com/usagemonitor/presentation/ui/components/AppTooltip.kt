package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * A bolha de uma tooltip: superfície `raised`, raio 6, borda de 1dp e 2dp de
 * sombra.
 *
 * Existe porque a anatomia estava escrita por extenso em quatro lugares — a
 * tooltip de texto aqui, a de métricas do card, a do gráfico de turnos e a do
 * gráfico de histórico — e as quatro flutuam sobre o mesmo tipo de conteúdo.
 * Duas tooltips sobre o mesmo gráfico em alturas diferentes é o defeito que a
 * repetição produz sozinha.
 *
 * **Patamar [AppDepth.RAISED], não o de menu.** O menu cobre a janela; a bolha
 * cobre um ponto do gráfico. A sombra é a do sistema ([appDepth]), em duas
 * camadas, e não a `shadowElevation` do Material, que tem outra curva e outra
 * cor. O `tonalElevation` fica: é ele que dá à bolha o tom um pouco acima do
 * `surfaceVariant` que a separa do gráfico.
 *
 * Só o conteúdo é do chamador: cada bolha tem o próprio `padding` e a própria
 * largura máxima, e é por isso que isto é superfície e não contêiner.
 */
@Composable
fun AppTooltipSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.small,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.appDepth(AppDepth.RAISED, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = TOOLTIP_TONAL_ELEVATION,
        shadowElevation = 0.dp,
        border = BorderStroke(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

/**
 * Tooltip de texto simples.
 *
 * Persistente como a `HoverTooltipBox` dos gráficos, e pelo mesmo motivo: aqui
 * a tooltip explica, e explicação de duas linhas que some ao mover o ponteiro
 * não chega a ser lida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            AppTooltipSurface {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                )
            }
        },
        state = rememberTooltipState(isPersistent = true)
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/** O tom que a bolha já tinha; a sombra saiu do Material para [appDepth]. */
private val TOOLTIP_TONAL_ELEVATION = 2.dp
