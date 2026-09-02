package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppTheme
import java.awt.image.BufferedImage

/**
 * Gravador de quadros de uma cena Compose renderizada offscreen.
 *
 * Extraído do gerador do tour para servir também às demos da ajuda
 * (issue #184): os dois gravam a mesma coisa — quadros de composables reais com
 * dados sintéticos — em tamanhos diferentes. Duas cópias da mesma máquina de
 * relógio duplo divergiriam justamente no ponto que é difícil de acertar.
 *
 * Os dois relógios avançam juntos de propósito: os `delay` dos composables
 * esperam **tempo real** (rodam em `Dispatchers.Unconfined`), enquanto as
 * animações consomem o `nanoTime` passado a `render`. Avançar só um deixa a
 * cena em branco.
 */
internal class SceneRecorder(
    widthDp: Int,
    heightDp: Int,
    private val frameMillis: Long = FRAME_MILLIS
) {

    val frames = mutableListOf<GifFrame>()

    @OptIn(ExperimentalComposeUiApi::class)
    private val scene = ImageComposeScene(
        width = widthDp * SCALE,
        height = heightDp * SCALE,
        density = Density(SCALE.toFloat())
    )

    private var sceneNanos = 0L

    @OptIn(ExperimentalComposeUiApi::class)
    fun setContent(content: @Composable () -> Unit) {
        scene.setContent {
            AppTheme(isDark = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    content()
                }
            }
        }
        scene.render(0L)
    }

    /** Grava [durationMillis] de movimento, com [onFrame] recebendo 0..1. */
    fun animate(durationMillis: Long, onFrame: (Float) -> Unit) {
        val count = (durationMillis / frameMillis).toInt().coerceAtLeast(1)
        for (index in 1..count) {
            onFrame(index.toFloat() / count)
            frames += GifFrame(step(), frameMillis.toInt())
        }
    }

    /** Uma pausa: [SETTLE_FRAMES] quadros normais e um quadro longo. */
    fun hold(durationMillis: Long) {
        repeat(SETTLE_FRAMES) {
            frames += GifFrame(step(), frameMillis.toInt())
        }
        frames += GifFrame(step(), durationMillis.toInt())
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun close() = scene.close()

    /** Um quadro adiante. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun step(): BufferedImage {
        Thread.sleep(frameMillis)
        sceneNanos += frameMillis * 1_000_000L
        // A mutação de estado veio de fora de qualquer snapshot; sem isto o
        // recompositor só a enxergaria no quadro seguinte, ou em nenhum.
        Snapshot.sendApplyNotifications()
        return scene.render(sceneNanos).toBufferedImage().downsampleByTwo()
    }

    companion object {

        /** Supersampling: a cena renderiza em 2x e cada quadro é reduzido à metade. */
        const val SCALE = 2

        /** 10 quadros por segundo — o suficiente para fade e spinner não picotarem. */
        const val FRAME_MILLIS = 100L

        /**
         * Quadros gravados no fim de um movimento antes da pausa longa.
         *
         * A pausa é um único quadro com espera longa; sem estes dois, a animação
         * que ainda estava assentando seria cortada no meio.
         */
        const val SETTLE_FRAMES = 2
    }
}

/** Suavização de entrada e saída dos movimentos gravados. */
internal fun smoothStep(progress: Float): Float = progress * progress * (3f - 2f * progress)

/**
 * Mede o conteúdo em [contentHeight] e o desloca em [pan] dentro da cena.
 *
 * É um `Layout` na mão porque os dois modificadores prontos falham aqui:
 * `height` é coagido pelas constraints do pai e nunca passaria da altura da
 * cena, e `requiredHeight` mede alto mas **centraliza** o que sobra — o topo da
 * tela sumia e o deslocamento revelava vazio no rodapé.
 */
@Composable
internal fun PannedViewport(contentHeight: Dp, pan: Dp, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) { measurables, constraints ->
        val height = contentHeight.roundToPx()
        val placeables = measurables.map { measurable ->
            measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = -pan.roundToPx()
            placeables.forEach { placeable -> placeable.place(0, top) }
        }
    }
}

/**
 * Ponteiro sintético de um roteiro gravado.
 *
 * Um dono só para os dois geradores: o tour e as demos da ajuda desenham o mesmo
 * ponteiro, com a mesma onda de clique, e duas cópias da mesma interpolação
 * divergiriam sem ninguém perceber — o vídeo continuaria saindo.
 */
internal class CursorTrack {

    var pose by mutableStateOf(TourCursorPose(x = 0.dp, y = 0.dp, visible = false))
}

/** Leva o ponteiro até (x, y), com entrada e saída suaves. */
internal fun SceneRecorder.moveCursor(cursor: CursorTrack, x: Dp, y: Dp, durationMillis: Long) {
    val fromX = cursor.pose.x
    val fromY = cursor.pose.y
    animate(durationMillis) { progress ->
        val eased = smoothStep(progress)
        cursor.pose = cursor.pose.copy(
            x = fromX + (x - fromX) * eased,
            y = fromY + (y - fromY) * eased,
            visible = true,
            clickProgress = null
        )
    }
}

/** Onda do clique; [action] dispara no meio dela, não no fim. */
internal fun SceneRecorder.click(cursor: CursorTrack, action: () -> Unit) {
    var fired = false
    animate(CLICK_MILLIS) { progress ->
        if (!fired && progress >= 0.35f) {
            action()
            fired = true
        }
        cursor.pose = cursor.pose.copy(clickProgress = progress)
    }
    cursor.pose = cursor.pose.copy(clickProgress = null)
}

/** Interpola um valor de deslocamento; quem o guarda é o estado do roteiro. */
internal fun SceneRecorder.panValue(from: Dp, to: Dp, durationMillis: Long, apply: (Dp) -> Unit) {
    animate(durationMillis) { progress ->
        apply(from + (to - from) * smoothStep(progress))
    }
}

private const val CLICK_MILLIS = 400L
