package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.math.roundToInt

/** Largura da coluna de navegação: cabe "Configurações" sem quebrar. */
private val SETTINGS_NAV_WIDTH = 150.dp

/**
 * Trilho de navegação vertical de um diálogo com seções.
 *
 * Irmão do [AppTabs] e não uma variante dele: aba troca o que a tela mostra numa
 * faixa horizontal sublinhada, e este troca a seção de uma janela alta, numa
 * coluna que **não rola**. A coluna é o controle; conteúdo que rola não pode
 * tirá-la da vista. Largura fixa pelo mesmo motivo: item selecionado não pode
 * mudar a largura do trilho, ou a lista inteira se mexe a cada clique.
 *
 * Reaproveita [AppTab] como item porque a forma é a mesma — rótulo mais a
 * `testTag` que a suíte observa —, e um segundo tipo para os mesmos dois campos
 * só daria duas coisas para manter em sincronia.
 *
 * O item selecionado ganha `surfaceVariant`, **sem borda**: aqui a superfície
 * marca a seleção, e um anel em volta de cada item transformaria o trilho numa
 * pilha de caixas. É por isso que ele não usa [Modifier.appSurfaceBlock], que
 * sempre desenha a borda.
 */
@Composable
fun AppSettingsNav(
    items: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    width: Dp = SETTINGS_NAV_WIDTH
) {
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (header != null) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            )
        }
        // O realce é um bloco só, que desliza entre as seções, desenhado atrás
        // da coluna de itens -- mesma origem das posições que eles publicam.
        val indicator = rememberSlidingIndicatorState()
        val span = animatedIndicatorSpan(indicator, selectedIndex)
        val density = LocalDensity.current
        Box(modifier = Modifier.fillMaxWidth()) {
            if (span != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, span.start.roundToInt()) }
                        .fillMaxWidth()
                        .height(with(density) { span.size.toDp() })
                        .clip(AppShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { index, item ->
                    AppSettingsNavItem(
                        label = item.label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = if (item.testTag == null) {
                            Modifier.reportIndicatorSpan(indicator, index, vertical = true)
                        } else {
                            Modifier.testTag(item.testTag).reportIndicatorSpan(indicator, index, vertical = true)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSettingsNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // O fundo do selecionado é o bloco deslizante do [AppSettingsNav]; aqui só
    // a cor do texto acompanha.
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appSettingsNavContent"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .appModalRevealRow()
            .clip(AppShapes.small)
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
