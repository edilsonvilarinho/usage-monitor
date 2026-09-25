package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween

/**
 * Diálogo dentro da janela: pergunta curta, com uma ação que o diálogo propõe e
 * uma que desiste.
 *
 * **Substitui o `AlertDialog` do Material**, pelo mesmo motivo de [AppMenu] não
 * ser o `DropdownMenu`: aquele traz superfície, raio e tipografia próprios, e
 * sobretudo **surge num quadro** — no desktop ele não tem transição nenhuma, e o
 * cartão aparecendo seco sobre um fundo que escurece seco era a abertura "brusca"
 * de que os modais eram acusados. A API é a do `AlertDialog` (título, texto e os
 * dois botões por slot) para a troca ser mecânica nas telas.
 *
 * **Entrada: o fundo escurece por fade e o cartão entra com fade e escala de
 * 0,96 a 1** pela mola `GENTLE` — sem rebote, porque o cartão carrega texto que
 * precisa assentar legível. É a mesma escala de partida do [AppMenu].
 *
 * **A saída é seca**, e é deliberado: quem fecha o diálogo é o chamador, que o
 * tira da composição no mesmo clique que dispara a ação. Segurá-lo composto para
 * animar a saída exigiria o chamador guardar o conteúdo depois de descartá-lo —
 * e o que sai já não interessa (é a regra de [AppMotion.exit]).
 *
 * O `Dialog` da plataforma continua embaixo: é ele que prende o foco e fecha no
 * Esc. O escurecimento dele é desligado e desenhado aqui, porque o dele não anima.
 * Clique fora do cartão pede para fechar, como antes. `scrimColor` ainda é
 * experimental no Compose 1.7; é a única forma de desligar o escurecimento seco.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            scrimColor = Color.Transparent
        )
    ) {
        val visibility = remember { MutableTransitionState(false) }
        visibility.targetState = true
        val transition = rememberTransition(visibility, label = "appDialog")
        val scrimAlphaSpec = appTween<Float>(AppMotion.normal)
        val cardAlphaSpec = appTween<Float>(AppMotion.normal)
        val cardScaleSpec = appSpring<Float>(AppMotion.Springs.GENTLE, visibilityThreshold = 0.001f)
        val scrimAlpha by transition.animateFloat(
            transitionSpec = { scrimAlphaSpec },
            label = "appDialogScrim"
        ) { shown -> if (shown) 1f else 0f }
        val cardAlpha by transition.animateFloat(
            transitionSpec = { cardAlphaSpec },
            label = "appDialogAlpha"
        ) { shown -> if (shown) 1f else 0f }
        val cardScale by transition.animateFloat(
            transitionSpec = { cardScaleSpec },
            label = "appDialogScale"
        ) { shown -> if (shown) 1f else APP_DIALOG_ENTER_SCALE }

        val scrimColor = Color.Black.copy(alpha = APP_DIALOG_SCRIM_ALPHA)
        val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
        var cardBounds by remember { mutableStateOf(Rect.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(APP_DIALOG_SCRIM_TAG)
                .graphicsLayer { alpha = scrimAlpha }
                .background(scrimColor)
                // Clique no fundo fecha, como o `AlertDialog` fazia. Toque cru e
                // não `clickable`: aquele funde a semântica dos descendentes, e o
                // cartão inteiro viraria um nó só para leitor de tela e testes.
                // O fundo cobre a raiz do diálogo, então a posição do toque e a
                // caixa do cartão estão no mesmo sistema de coordenadas.
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        if (!cardBounds.contains(position)) {
                            currentOnDismissRequest()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AppDialogCard(
                modifier = modifier
                    .onGloballyPositioned { coordinates -> cardBounds = coordinates.boundsInRoot() }
                    .graphicsLayer {
                        alpha = cardAlpha
                        scaleX = cardScale
                        scaleY = cardScale
                    },
                title = title,
                text = text,
                confirmButton = confirmButton,
                dismissButton = dismissButton
            )
        }
    }
}

@Composable
private fun AppDialogCard(
    modifier: Modifier,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?
) {
    Column(
        modifier = modifier
            .padding(AppSpacing.lg)
            .widthIn(min = APP_DIALOG_MIN_WIDTH, max = APP_DIALOG_MAX_WIDTH)
            .appDepth(AppDepth.DIALOG, AppShapes.large)
            .clip(AppShapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.large)
            .padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                    title()
                }
            }
        }
        if (text != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    text()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dismissButton != null) {
                dismissButton()
            }
            confirmButton()
        }
    }
}

/** O fundo escurecido; o clique nele pede para fechar. */
const val APP_DIALOG_SCRIM_TAG = "app_dialog_scrim"

/** Mesma escala de partida do [AppMenu]: as duas superfícies nascem do mesmo jeito. */
internal const val APP_DIALOG_ENTER_SCALE = 0.96f

/** Mesmo escurecimento que o `Dialog` da plataforma aplicava. */
private const val APP_DIALOG_SCRIM_ALPHA = 0.32f

/** Os limites de largura do `AlertDialog` que ele substitui. */
private val APP_DIALOG_MIN_WIDTH = 280.dp
private val APP_DIALOG_MAX_WIDTH = 560.dp
