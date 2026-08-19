package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

// 10dp é o teto da escala de raios; 16 vinha da escala antiga, que ia até 28.
// `applyWindowShape` continua reagindo a `componentResized`, senão a máscara
// ficaria com o tamanho da janela anterior depois de qualquer redimensionamento.
private val WindowCornerRadius = 10.dp

/** Altura da barra de título das seis janelas. */
private val TITLE_BAR_HEIGHT = 34.dp

private fun WindowScope.applyWindowShape(density: androidx.compose.ui.unit.Density, cornerRadius: Dp) {
    val arcDiameter = with(density) { cornerRadius.toPx() * 2 }
    window.shape = java.awt.geom.RoundRectangle2D.Float(
        0f, 0f,
        window.width.toFloat(),
        window.height.toFloat(),
        arcDiameter,
        arcDiameter
    )
}

@Composable
fun WindowScope.DesktopWindowFrame(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isMaximized = windowState.placement == WindowPlacement.Maximized

    DisposableEffect(isMaximized, density) {
        if (isMaximized) {
            window.shape = null
        } else {
            applyWindowShape(density, WindowCornerRadius)
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent) {
                    applyWindowShape(density, WindowCornerRadius)
                }
            }
            window.addComponentListener(listener)
            return@DisposableEffect onDispose { window.removeComponentListener(listener) }
        }
        onDispose {}
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopTitleBar(
                title = title,
                iconPainter = iconPainter,
                windowState = windowState,
                onCloseRequest = onCloseRequest
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

@Composable
fun WindowScope.DesktopDialogFrame(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState? = null,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isMaximized = windowState?.placement == WindowPlacement.Maximized

    DisposableEffect(density, isMaximized) {
        if (isMaximized) {
            window.shape = null
        } else {
            applyWindowShape(density, WindowCornerRadius)
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent) {
                    applyWindowShape(density, WindowCornerRadius)
                }
            }
            window.addComponentListener(listener)
            return@DisposableEffect onDispose { window.removeComponentListener(listener) }
        }
        onDispose {}
    }

    var entered by remember(title) { mutableStateOf(false) }

    LaunchedEffect(title) {
        entered = false
        entered = true
    }

    val frameScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = tween(durationMillis = AppMotion.normal, easing = AppMotion.enterEasing),
        label = "dialogFrameScale"
    )
    val frameOffsetY by animateDpAsState(
        targetValue = if (entered) 0.dp else 14.dp,
        animationSpec = tween(durationMillis = AppMotion.normal, easing = AppMotion.enterEasing),
        label = "dialogFrameOffsetY"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = frameScale
                scaleY = frameScale
                translationY = frameOffsetY.toPx()
                transformOrigin = TransformOrigin(0.5f, 0.3f)
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopDialogTitleBar(
                title = title,
                iconPainter = iconPainter,
                windowState = windowState,
                onCloseRequest = onCloseRequest
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun WindowScope.DesktopTitleBar(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState,
    onCloseRequest: () -> Unit
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 34dp: a barra é cromo, e oito dp a menos por janela são oito
                // dp a mais de dado em seis janelas.
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleBarButton(
                    label = "—",
                    onClick = { windowState.isMinimized = true }
                )
                TitleBarButton(
                    label = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                    onClick = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                    }
                )
                TitleBarButton(
                    label = "×",
                    hoverColor = Color(0xFFC62828),
                    onClick = onCloseRequest
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun WindowScope.DesktopDialogTitleBar(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState?,
    onCloseRequest: () -> Unit
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 34dp: a barra é cromo, e oito dp a menos por janela são oito
                // dp a mais de dado em seis janelas.
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (windowState != null) {
                    TitleBarButton(
                        label = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                        onClick = {
                            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                        }
                    )
                }

                TitleBarButton(
                    label = "×",
                    hoverColor = Color(0xFFC62828),
                    onClick = onCloseRequest
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun TitleBarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hoverColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
) {
    val defaultColor = Color.Transparent
    val textColor = MaterialTheme.colorScheme.onSurface
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(
        modifier = modifier
            // Retângulo que preenche a altura da barra, como numa janela de
            // sistema: o botão arredondado flutuando dentro dela era o único
            // lugar do app onde um controle não encostava na própria moldura.
            .width(40.dp)
            .height(TITLE_BAR_HEIGHT - 1.dp)
            .clip(AppShapes.extraSmall)
            .background(if (isHovered) hoverColor else defaultColor)
            .hoverable(hoverInteraction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}
