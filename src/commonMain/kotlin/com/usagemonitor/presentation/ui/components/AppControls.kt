package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.MutableTransitionState
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.VisibilityThreshold
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween
import com.usagemonitor.presentation.ui.theme.AppChrome
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Primitivas de controle.
 *
 * O desenho de todas é o mesmo retângulo de raio 6 e borda de 1dp: os botões
 * circulares pesados do card e os campos de 56dp do Material saem de cena. A
 * altura de referência é 28dp — a mesma linha de base do rótulo ao lado.
 */

/** Altura de botão e campo. Um valor, para os controles alinharem entre si. */
private val CONTROL_HEIGHT = AppChrome.control

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
    val container = animatedButtonContainer(colors, interactionSource, enabled, "appButtonContainer")

    // Texto não encolhe na pressão: escalar uma camada com Plex Mono borra o
    // traço durante a transição. Quem responde ao clique aqui é a camada de
    // pressão, um degrau acima do hover.
    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(AppBorderWidth, colors.border.copy(alpha = colors.border.alpha * alpha), AppShapes.small)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
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
    val container = animatedButtonContainer(colors, interactionSource, enabled, "appIconButtonContainer")

    Box(
        modifier = modifier
            .size(ICON_BUTTON_SIZE)
            .appPressScale(interactionSource, enabled)
            .clip(AppShapes.small)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(AppBorderWidth, colors.border.copy(alpha = colors.border.alpha * alpha), AppShapes.small)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
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
    val interactionSource = remember { MutableInteractionSource() }
    val focusRing = animatedFocusRing(interactionSource)
    // A moldura entra por `decorationBox`, e o [modifier] do chamador fica no
    // próprio `BasicTextField`: é ele que carrega o foco e a ação de digitar.
    // Com a decoração por fora, uma `testTag` do chamador cairia num `Box` sem
    // `RequestFocus` e `performTextInput` falharia.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = interactionSource,
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
                    .border(focusRing.width, focusRing.color, AppShapes.small)
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
 * Campo de texto de várias linhas.
 *
 * Irmão do [AppTextField] e não um parâmetro dele: os dois têm alturas, alinhamento
 * vertical e papéis diferentes — aquele é filtro, URL, apelido e chave, este é
 * texto que a pessoa escreve. Um `singleLine` configurável faria a mesma
 * primitiva responder a duas perguntas e deixaria a altura mínima sem dono.
 *
 * O [modifier] do chamador desce até o `BasicTextField` pelo mesmo motivo do
 * campo de uma linha: é ele que carrega a `testTag` e o `RequestFocus` que o
 * `performTextInput` exige.
 */
@Composable
fun AppTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val interactionSource = remember { MutableInteractionSource() }
    val focusRing = animatedFocusRing(interactionSource)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minHeight = TEXT_AREA_HEIGHT),
        enabled = enabled,
        singleLine = false,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(AppShapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .border(focusRing.width, focusRing.color, AppShapes.small)
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                // Topo, e não centro: texto que cresce para baixo tem de começar
                // sempre no mesmo lugar, senão a primeira linha se move enquanto
                // a pessoa digita.
                contentAlignment = Alignment.TopStart
            ) {
                if (value.isEmpty() && placeholder != null) {
                    // Fora da árvore semântica: o `BasicTextField` mescla os
                    // descendentes, e sem isto um campo vazio passa a "conter" o
                    // texto de exemplo.
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
                innerTextField()
            }
        }
    )
}

/** Altura mínima do campo de várias linhas: cerca de cinco linhas de `bodySmall`. */
private val TEXT_AREA_HEIGHT = 96.dp

/**
 * Interruptor de 30 × 17.
 *
 * O botão anda por mola rápida e trilho, borda e botão mudam de cor **juntos**,
 * no mesmo tween. Antes só o trilho e a posição animavam: borda e botão trocavam
 * de cor no primeiro quadro, e o interruptor chegava verde de um lado enquanto o
 * botão ainda estava no outro. Tudo termina — nada aqui pode virar animação
 * infinita, que travaria o `waitForIdle` dos testes de componente.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Verde, não o azul de `primary`: ligado é um estado — o mesmo "ok" do
    // indicador de estado e da barra de progresso saudável —, e o azul deste
    // sistema é a cor de informação, que já é a linha do gráfico e o realce de
    // seleção. Com ele aqui, um interruptor ligado lia como item selecionado.
    val accent = AppTone.OK.color()
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val knobOffset by animateDpAsState(
        targetValue = if (checked) SWITCH_WIDTH - SWITCH_KNOB - SWITCH_PADDING * 2 else 0.dp,
        animationSpec = appSpring(AppMotion.Springs.SNAPPY, visibilityThreshold = Dp.VisibilityThreshold),
        label = "appSwitchKnob"
    )
    val track by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.30f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = appTween(AppMotion.fast),
        label = "appSwitchTrack"
    )
    val border by animateColorAsState(
        targetValue = if (checked) accent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = appTween(AppMotion.fast),
        label = "appSwitchBorder"
    )
    val knob by animateColorAsState(
        targetValue = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = appTween(AppMotion.fast),
        label = "appSwitchKnobColor"
    )
    val shape: Shape = RoundedCornerShape(SWITCH_HEIGHT / 2)

    Box(
        modifier = modifier
            .width(SWITCH_WIDTH)
            .height(SWITCH_HEIGHT)
            .clip(shape)
            .background(track.copy(alpha = track.alpha * alpha))
            .border(AppBorderWidth, border.copy(alpha = alpha), shape)
            // `toggleable` com `Role.Switch`, e não `clickable`: com o clique
            // simples o nó não publica `ToggleableState` nenhum, e o estado do
            // interruptor fica **invisível** para leitor de tela e para teste de
            // componente — que só conseguia afirmar o clique, nunca o valor. É a
            // mesma armadilha do `contentDescription` em botão de ícone.
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
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
    // O fundo do segmento escolhido é um polegar só, que desliza por trás dos
    // rótulos: trocar de "5h" para "30 dias" mostra o caminho entre os dois em
    // vez de apagar um e acender o outro no mesmo quadro.
    val indicator = rememberSlidingIndicatorState()
    val span = animatedIndicatorSpan(indicator, selectedIndex)
    val density = LocalDensity.current
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val thumb = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .height(CONTROL_HEIGHT)
    ) {
        if (span != null) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(span.start.roundToInt(), 0) }
                    .width(with(density) { span.size.toDp() })
                    .fillMaxHeight()
                    .background(thumb.copy(alpha = thumb.alpha * alpha))
            )
        }
        Row(modifier = Modifier.fillMaxHeight()) {
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
                    onClick = { onSelect(index) },
                    modifier = Modifier.reportIndicatorSpan(indicator, index)
                )
            }
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
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appToggleChipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appToggleChipBorder"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appToggleChipContent"
    )
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

/**
 * Opção de cor: amostra redonda e rótulo, **uma** escolha entre várias (issue
 * #275, a cor de cada conta Claude).
 *
 * Não é [AppToggleChip]: aquele liga ou desliga uma restrição, este escolhe uma
 * entre N — é `selectable` com `Role.RadioButton`, que é o que `assertIsSelected`
 * observa. A opção escolhida carrega **marca além do realce**, com o espaço da
 * marca reservado em todas: cor nunca informa sozinha, e sem a reserva o rótulo
 * andaria para o lado a cada troca. [swatch] nulo é a opção "Padrão", desenhada
 * como anel vazio — não há cor própria a mostrar.
 */
@Composable
fun AppSwatchChip(
    label: String,
    swatch: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        animationSpec = appTween(AppMotion.normal),
        label = "appSwatchChipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = appTween(AppMotion.normal),
        label = "appSwatchChipBorder"
    )
    val content = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container)
            .border(AppBorderWidth, border, AppShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .clip(CircleShape)
                .then(
                    if (swatch != null) {
                        Modifier.background(swatch)
                    } else {
                        Modifier.border(AppBorderWidth, ring, CircleShape)
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1
        )
        Box(modifier = Modifier.width(SWATCH_MARK_WIDTH), contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

private val SWATCH_SIZE = 10.dp

/**
 * Opção de glifo: um emoji (ou uma palavra curta) em vez de amostra e rótulo,
 * **uma** escolha entre várias (issue #287, o emoji de cada conta Claude).
 *
 * O contrato é o de [AppSwatchChip] — `selectable` com `Role.RadioButton`, marca
 * além do realce e o espaço da marca reservado em todas —, sem o rótulo: o glifo
 * é o conteúdo, e dezesseis opções com nome escrito tomariam três linhas da aba.
 * O nome vai em [description], que é a semântica da opção: é por ele que leitor
 * de tela e testes chegam a ela.
 */
@Composable
fun AppGlyphChip(
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        animationSpec = appTween(AppMotion.normal),
        label = "appGlyphChipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = appTween(AppMotion.normal),
        label = "appGlyphChipBorder"
    )
    val content = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container)
            .border(AppBorderWidth, border, AppShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        glyph()
        Box(modifier = Modifier.width(SWATCH_MARK_WIDTH), contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

private val SWATCH_MARK_WIDTH = 10.dp

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    // O fundo do escolhido é o polegar deslizante do [AppSegmentedControl].
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appSegmentContent"
    )

    val tagged = if (option.testTag != null) modifier.testTag(option.testTag) else modifier
    Box(
        modifier = tagged
            // Preenche a altura do controle, como os divisores já fazem. Sem isto o
            // segmento tem a altura do texto, sobra uma faixa do fundo do pai dentro
            // da borda e o canto arredondado do clip do `Row` come o último
            // segmento — que na tela se lê como botão cortado.
            .fillMaxHeight()
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

/**
 * Repouso → hover → pressão, em tween. A pressão é a camada de pressão do
 * [AppSurfaceLadder] somada ao hover: um degrau acima dele em qualquer tom de
 * botão, sem uma terceira cor por tom.
 */
@Composable
private fun animatedButtonContainer(
    colors: ButtonColors,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    label: String
): Color {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val ladder = AppSurfaceLadders.current
    val target = when {
        !enabled -> colors.container
        pressed -> ladder.pressedLayer.compositeOver(colors.hover)
        hovered -> colors.hover
        else -> colors.container
    }
    val container by animateColorAsState(
        targetValue = target,
        animationSpec = appTween(AppMotion.fast),
        label = label
    )
    return container
}

/**
 * Pressão por escala, **só em superfície sem texto** — botão de ícone, ação do
 * card, HUD. Escalar texto em Plex Mono borra o traço durante a transição, e
 * por isso o botão com rótulo responde só com a camada. Mola `SNAPPY`: a
 * pressão tem de acompanhar o dedo, e sem rebote para não tremer.
 */
@Composable
fun Modifier.appPressScale(interactionSource: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = appSpring(AppMotion.Springs.SNAPPY, visibilityThreshold = 0.001f),
        label = "appPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private const val PRESSED_SCALE = 0.96f

@Immutable
private data class FocusRing(val width: Dp, val color: Color)

/**
 * O anel de foco que o design system já exigia e o código não tinha: 2dp em
 * `--info`, por dentro do campo. Sem ele não havia como saber qual campo
 * recebia a digitação num formulário de três — a aba Rede tem cinco.
 */
@Composable
private fun animatedFocusRing(interactionSource: MutableInteractionSource): FocusRing {
    val focused by interactionSource.collectIsFocusedAsState()
    val color by animateColorAsState(
        targetValue = if (focused) AppTone.INFO.color() else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = appTween(AppMotion.fast),
        label = "appFocusRingColor"
    )
    val width by animateDpAsState(
        targetValue = if (focused) FOCUS_RING_WIDTH else AppBorderWidth,
        animationSpec = appTween(AppMotion.fast),
        label = "appFocusRingWidth"
    )
    return FocusRing(width, color)
}

private val FOCUS_RING_WIDTH = 2.dp

/** Espaço reservado para o `RowScope` de quem compõe uma barra de ações. */
@Composable
fun RowScope.AppSpacer() {
    Box(modifier = Modifier.weight(1f))
}

/** O tom que a bolha já tinha; a sombra saiu do Material para [appDepth]. */
private val TOOLTIP_TONAL_ELEVATION = 2.dp
