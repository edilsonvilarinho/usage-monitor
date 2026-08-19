package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Primitivas de controle.
 *
 * O desenho de todas é o mesmo retângulo de raio 6 e borda de 1dp: os botões
 * circulares pesados do card e os campos de 56dp do Material saem de cena. A
 * altura de referência é 28dp — a mesma linha de base do rótulo ao lado.
 */

/** Altura de botão e campo. Um valor, para os controles alinharem entre si. */
private val CONTROL_HEIGHT = 28.dp

/** Lado do botão de ícone: quadrado, não círculo. */
private val ICON_BUTTON_SIZE = 26.dp

private val SWITCH_WIDTH = 30.dp
private val SWITCH_HEIGHT = 17.dp
private val SWITCH_KNOB = 11.dp
private val SWITCH_PADDING = 2.dp

/** Opacidade de controle desabilitado: legível, e claramente inativo. */
private const val DISABLED_ALPHA = 0.42f

/**
 * Peso visual de um botão.
 *
 * [PRIMARY] é a inversão fundo/texto e existe **uma vez por tela** — é a ação
 * que a tela propõe. [DANGER] não é vermelho cheio: só a borda e o texto
 * mudam, porque um retângulo vermelho sólido numa lista de linhas puxa mais
 * atenção que o dado.
 */
enum class AppButtonTone { DEFAULT, PRIMARY, GHOST, DANGER }

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AppButtonTone = AppButtonTone.DEFAULT,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null
) {
    val colors = buttonColors(tone)
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val container by animateColorAsState(
        targetValue = if (hovered && enabled) colors.hover else colors.container,
        animationSpec = tween(AppMotion.fast),
        label = "appButtonContainer"
    )

    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(AppBorderWidth, colors.border.copy(alpha = colors.border.alpha * alpha), AppShapes.small)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        if (leading != null) {
            leading()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.content.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Botão só de ícone.
 *
 * [contentDescription] é obrigatório e não tem default: rótulo que virou ícone
 * precisa continuar dizendo a mesma coisa para quem lê a árvore semântica — e
 * é por ele que os testes de componente encontram a ação.
 */
@Composable
fun AppIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AppButtonTone = AppButtonTone.GHOST,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    val colors = buttonColors(tone)
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val container by animateColorAsState(
        targetValue = if (hovered && enabled) colors.hover else colors.container,
        animationSpec = tween(AppMotion.fast),
        label = "appIconButtonContainer"
    )

    Box(
        modifier = modifier
            .size(ICON_BUTTON_SIZE)
            .clip(AppShapes.small)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(AppBorderWidth, colors.border.copy(alpha = colors.border.alpha * alpha), AppShapes.small)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            // `contentDescription` na semântica, e não só `onClickLabel`: o
            // rótulo do clique descreve a **ação** para o leitor de tela, mas
            // não é o que `onNodeWithContentDescription` encontra — e é assim
            // que as suítes localizam toda ação que virou ícone neste app.
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.labelLarge
        ) {
            icon()
        }
    }
}

/**
 * Campo de texto de uma linha.
 *
 * `BasicTextField` e não `OutlinedTextField`: o do Material tem 56dp de altura
 * mínima e um rótulo flutuante, e ambos brigam com uma barra de controles de
 * 34dp. O que se ganha do Material aqui — cursor, seleção, teclado — o
 * `BasicTextField` já traz.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    /** A chave de time é mascarada até o usuário pedir para revelá-la. */
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    // A moldura entra por `decorationBox`, e o [modifier] do chamador fica no
    // próprio `BasicTextField`: é ele que carrega o foco e a ação de digitar.
    // Com a decoração por fora, uma `testTag` do chamador cairia num `Box` sem
    // `RequestFocus` e `performTextInput` falharia.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = CONTROL_HEIGHT),
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(AppShapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder != null) {
                    // Fora da árvore semântica: o `BasicTextField` mescla os
                    // descendentes, e o placeholder acabava dentro do texto do
                    // próprio campo — um campo vazio passava a "conter" o texto
                    // de exemplo, e um `onNodeWithText` do exemplo encontrava
                    // dois nós.
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * Interruptor de 30 × 17.
 *
 * A transição do botão é de 120ms e **termina**: `animateDpAsState` chega ao
 * alvo e para. Nada aqui pode virar animação infinita, que travaria o
 * `waitForIdle` dos testes de componente.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val accent = MaterialTheme.colorScheme.primary
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val knobOffset by animateDpAsState(
        targetValue = if (checked) SWITCH_WIDTH - SWITCH_KNOB - SWITCH_PADDING * 2 else 0.dp,
        animationSpec = tween(AppMotion.fast),
        label = "appSwitchKnob"
    )
    val track by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.30f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(AppMotion.fast),
        label = "appSwitchTrack"
    )
    val border = if (checked) accent else MaterialTheme.colorScheme.outlineVariant
    val knob = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val shape: Shape = RoundedCornerShape(SWITCH_HEIGHT / 2)

    Box(
        modifier = modifier
            .width(SWITCH_WIDTH)
            .height(SWITCH_HEIGHT)
            .clip(shape)
            .background(track.copy(alpha = track.alpha * alpha))
            .border(AppBorderWidth, border.copy(alpha = alpha), shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(SWITCH_PADDING),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(SWITCH_KNOB)
                .clip(RoundedCornerShape(SWITCH_KNOB / 2))
                .background(knob.copy(alpha = alpha))
        )
    }
}

/**
 * Controle segmentado: escolhe um parâmetro do mesmo conteúdo.
 *
 * É o irmão de [AppTabs] e existe separado de propósito. Aba troca **o que** a
 * tela mostra; segmento troca **como** — a janela de tempo, a ordem, o tamanho
 * da página. Desenhá-los igual foi o que fez o app usar pílula para as duas
 * coisas e para chip de filtro ao mesmo tempo.
 */
@Composable
fun AppSegmentedControl(
    options: List<AppSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .height(CONTROL_HEIGHT)
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(AppBorderWidth)
                        .height(CONTROL_HEIGHT)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            AppSegmentItem(
                option = option,
                selected = index == selectedIndex,
                enabled = enabled,
                onClick = { onSelect(index) }
            )
        }
    }
}

/**
 * Chip de alternância: uma restrição ligada ou desligada.
 *
 * Não é [AppSegmentedControl] com duas opções — segmentado diz "ou isto, ou
 * aquilo", e aqui existe um estado só, que está ativo ou não. Carrega
 * `selectable` pela mesma razão do segmentado: é o que `assertIsSelected`
 * observa.
 */
@Composable
fun AppToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha = if (enabled) 1f else DISABLED_ALPHA

    Box(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(AppBorderWidth, border.copy(alpha = alpha), AppShapes.small)
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/** Uma opção do controle segmentado. */
data class AppSegment(
    val label: String,
    val testTag: String? = null
)

@Composable
private fun AppSegmentItem(
    option: AppSegment,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val container = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val tagged = if (option.testTag != null) Modifier.testTag(option.testTag) else Modifier
    Box(
        modifier = tagged
            // Preenche a altura do controle, como os divisores já fazem. Sem isto o
            // segmento tem a altura do texto, sobra uma faixa do fundo do pai dentro
            // da borda e o canto arredondado do clip do `Row` come o último
            // segmento — que na tela se lê como botão cortado.
            .fillMaxHeight()
            .background(container.copy(alpha = container.alpha * alpha))
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(horizontal = AppSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = alpha),
            maxLines = 1
        )
    }
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
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = AppShapes.small,
                tonalElevation = AppElevation.raised,
                shadowElevation = AppElevation.raised,
                border = androidx.compose.foundation.BorderStroke(
                    AppBorderWidth,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
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

@Composable
private fun buttonColors(tone: AppButtonTone): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        AppButtonTone.DEFAULT -> ButtonColors(
            container = scheme.surface,
            hover = scheme.surfaceVariant,
            border = scheme.outlineVariant,
            content = scheme.onSurface
        )
        AppButtonTone.PRIMARY -> ButtonColors(
            container = scheme.onSurface,
            hover = scheme.onSurfaceVariant,
            border = scheme.onSurface,
            content = scheme.surface
        )
        AppButtonTone.GHOST -> ButtonColors(
            container = Color.Transparent,
            hover = scheme.surfaceVariant,
            border = Color.Transparent,
            content = scheme.onSurfaceVariant
        )
        AppButtonTone.DANGER -> ButtonColors(
            container = scheme.surface,
            hover = scheme.surfaceVariant,
            border = scheme.error,
            content = scheme.error
        )
    }
}

private data class ButtonColors(
    val container: Color,
    val hover: Color,
    val border: Color,
    val content: Color
)

/** Espaço reservado para o `RowScope` de quem compõe uma barra de ações. */
@Composable
fun RowScope.AppSpacer() {
    Box(modifier = Modifier.weight(1f))
}
