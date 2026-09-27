package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.math.roundToInt

/**
 * Abas com sublinhado, não pílula.
 *
 * Pílula é o que o app usa hoje para aba, para chip de filtro e para janela de
 * tempo ao mesmo tempo — três funções com o mesmo desenho. Aqui a aba troca o
 * conteúdo da tela, e o sublinhado a distingue do controle segmentado, que
 * escolhe um parâmetro do mesmo conteúdo.
 *
 * Recebe rótulos e índice: quem guarda a escolha é a tela, como em todo o resto
 * deste arquivo.
 *
 * O sublinhado é **um só** e desliza de uma aba para a outra
 * ([animatedIndicatorSpan]), em vez de apagar numa e acender na outra no mesmo
 * quadro. Ele mora num `Box` que embrulha só a fileira de abas: é a mesma origem
 * das posições que cada aba publica.
 */
@Composable
fun AppTabs(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val indicator = rememberSlidingIndicatorState()
    val span = animatedIndicatorSpan(indicator, selectedIndex)
    val density = LocalDensity.current
    Column(modifier = modifier) {
        Box {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                tabs.forEachIndexed { index, tab ->
                    AppTabItem(
                        tab = tab,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.reportIndicatorSpan(indicator, index)
                    )
                }
            }
            if (span != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(span.start.roundToInt(), 0) }
                        .width(with(density) { span.size.toDp() })
                        .height(TAB_UNDERLINE_THICKNESS)
                        .background(MaterialTheme.colorScheme.onSurface)
                        .testTag(APP_TABS_INDICATOR_TEST_TAG)
                )
            }
        }
        AppDivider()
    }
}

/** O sublinhado deslizante; os testes medem onde ele parou. */
const val APP_TABS_INDICATOR_TEST_TAG = "appTabsIndicator"

/** Uma aba: rótulo e a `testTag` que a tela usa para encontrá-la. */
data class AppTab(
    val label: String,
    val testTag: String? = null
)

/**
 * O sublinhado **não** é filho da aba. Um `Box(Modifier.fillMaxWidth())` dentro
 * de uma `Column` filha de `Row` faz a coluna inteira esticar até a largura
 * disponível: a primeira aba cobria as outras duas e todo clique caía nela. Por
 * isso ele é desenhado pelo [AppTabs], com a largura que a aba publica.
 */
@Composable
private fun AppTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appTabContent"
    )
    val tagged = if (tab.testTag != null) modifier.testTag(tab.testTag) else modifier

    Text(
        text = tab.label,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        maxLines = 1,
        modifier = tagged
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm)
    )
}

private val TAB_UNDERLINE_THICKNESS = 2.dp
