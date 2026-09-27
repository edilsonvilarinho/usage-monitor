package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween

/** Uma opção do menu suspenso. */
data class AppMenuOption(
    val label: String,
    val testTag: String? = null
)

/**
 * Menu suspenso: escolhe **uma** opção de uma lista curta, ancorado num
 * controle.
 *
 * É o irmão de [AppSegmentedControl] para quando as opções não cabem na barra:
 * o segmentado mostra todas o tempo todo e custa a largura de todas, o menu
 * mostra uma e as demais sob demanda. Numa barra de estado de 30dp com seis
 * ações, o segmentado com três rótulos não cabe — foi por isso que esta
 * primitiva passou a existir (issue #187).
 *
 * **É `Popup` com a superfície deste sistema, e não o `DropdownMenu` do
 * Material.** Aquele traz a própria superfície, o próprio raio, a própria
 * animação de entrada e a própria altura de item, e nenhum dos quatro é o
 * deste sistema — vesti-lo por fora deixaria dois desenhos de menu no mesmo
 * app, um deles invisível no código. Aqui a anatomia é a de sempre: recorte,
 * fundo `surface`, borda de 1dp e a elevação `raised`, que é o patamar que o
 * token descreve como "tooltip e menu suspenso".
 *
 * **O item selecionado carrega marca e realce, nunca só o realce.** O contêiner
 * `surfaceVariant` é o mesmo de [AppSegmentedControl] e do item de navegação
 * das Configurações — não há um segundo desenho de seleção neste app —, e a
 * marca ao lado do rótulo é o que impede a cor de informar sozinha. O espaço da
 * marca é reservado em todas as linhas, senão o rótulo da selecionada andaria
 * para o lado ao trocar de opção.
 *
 * **Ele abre para cima quando não cabe abaixo.** O consumidor de hoje é o
 * rodapé, que é a última linha da janela: um menu que só soubesse abrir para
 * baixo nasceria fora dela. Popup no Compose Desktop é camada **dentro** da
 * janela, recortada pelos limites dela — a #164 pagou isso —, e por isso a
 * posição é presa à janela nos dois eixos.
 *
 * **Entra e sai.** A primeira versão recusava animação de entrada ("menu não é
 * lugar de transição avulsa"), e o menu surgia e sumia num quadro — o que mais
 * contribuía para a tela ler como sem fluidez, justo no controle que o usuário
 * aciona para trocar a janela inteira de modo. Entrada: escala de 0,96 a 1 pela
 * mola `EXPRESSIVE` mais fade, **a partir da borda que encosta na âncora** —
 * menu que abre para cima cresce de baixo. Saída: só fade, em 90ms. O `Popup`
 * continua composto até a saída terminar; antes disso ele sumia junto com o
 * `expanded`, e não havia saída para animar.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    options: List<AppMenuOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    val enterAlpha = appTween<Float>(AppMotion.fast)
    val exitAlpha = appTween<Float>(AppMotion.exit, AppMotion.exitEasing)
    val enterScale = appSpring<Float>(AppMotion.Springs.EXPRESSIVE, visibilityThreshold = 0.001f)
    val exitScale = appTween<Float>(AppMotion.exit, AppMotion.exitEasing)

    Box(modifier = modifier) {
        anchor()

        // Fechado e parado: nada composto. Fechando, o popup fica até a saída
        // terminar.
        if (!visibility.currentState && !visibility.targetState) {
            return@Box
        }

        val gapPx = with(LocalDensity.current) { AppSpacing.xs.roundToPx() }
        val positionProvider = remember(gapPx) { AppMenuPositionProvider(gapPx) }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            val transition = rememberTransition(visibility, label = "appMenu")
            val alpha by transition.animateFloat(
                transitionSpec = { if (targetState) enterAlpha else exitAlpha },
                label = "appMenuAlpha"
            ) { shown -> if (shown) 1f else 0f }
            val scale by transition.animateFloat(
                transitionSpec = { if (targetState) enterScale else exitScale },
                label = "appMenuScale"
            ) { shown -> if (shown) 1f else MENU_ENTER_SCALE }
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                        // Cresce a partir da borda que encosta na âncora. A
                        // posição é resolvida no layout do popup, antes deste
                        // desenho, e por isso a leitura aqui já é a do quadro.
                        transformOrigin = TransformOrigin(
                            0.5f,
                            if (positionProvider.opensUpward) 1f else 0f
                        )
                    }
                    .appDepth(AppDepth.OVERLAY, AppShapes.small)
                    .clip(AppShapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
                    .width(IntrinsicSize.Max)
            ) {
                options.forEachIndexed { index, option ->
                    AppMenuItem(
                        option = option,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppMenuItem(
    option: AppMenuOption,
    selected: Boolean,
    onClick: () -> Unit
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = if (hovered && !selected) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        container
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .background(background)
            .hoverable(interactionSource)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            .then(
                if (option.testTag == null) Modifier else Modifier.testTag(option.testTag)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        // O espaço da marca existe em todas as linhas: sem ele o rótulo da
        // selecionada andaria para o lado a cada troca de opção.
        Box(modifier = Modifier.size(MENU_MARK_SIZE), contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1
                )
            }
        }

        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1
        )
    }
}

/** Lado da coluna da marca de seleção do menu. */
private val MENU_MARK_SIZE = 12.dp

/**
 * Onde o menu abre: acima da âncora quando cabe, abaixo quando não, sempre
 * dentro da janela.
 *
 * O alinhamento horizontal é pela **direita** da âncora porque o consumidor de
 * hoje mora no canto direito de uma barra de estado; à esquerda, o menu sairia
 * por cima do conteúdo que a âncora não pertence. A posição final é presa aos
 * limites da janela nos dois eixos: popup aqui é camada dentro dela, e o que
 * passar do limite não é rolado, é recortado.
 */
/** Escala de partida da entrada: o bastante para ler como "saiu da âncora". */
private const val MENU_ENTER_SCALE = 0.96f

private class AppMenuPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    /**
     * Para que lado o menu abriu na última posição calculada. Campo comum, não
     * estado: é lido no desenho do mesmo quadro, depois do layout que o escreve,
     * e como estado ele recomporia o menu a cada posicionamento.
     */
    var opensUpward: Boolean = false
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val below = anchorBounds.bottom + gapPx
        opensUpward = above >= 0
        val y = if (opensUpward) {
            above
        } else {
            below.coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        }

        return IntOffset(x, y)
    }
}
