package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
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
