package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HudNotch
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Registro das demos de vídeo (#633): ids fixos, fora do `HelpTopic`, e `--topic` misto com o catálogo. */
class VideoDemosTest {

    @Test
    fun `o registro de video lista as demos de hud`() {
        assertEquals(
            setOf(
                "video-hud-minimize", "video-hud-floating", "video-hud-reorder",
                "video-codex-sessions", "video-analysis-all"
            ),
            VideoDemos.ids
        )
    }

    @Test
    fun `topico fora do catalogo e do registro e apontado`() {
        val catalog = setOf("dashboard", "history")
        val requested = setOf("dashboard", "video-hud-minimize", "video-nao-existe")
        assertEquals(setOf("video-nao-existe"), VideoDemos.unknownTopics(requested, catalog))
    }

    @Test
    fun `topic aceita lista mista de catalogo e video`() {
        val options = parseHelpMediaArgs(arrayOf("--frames", "--topic=video-hud-floating,dashboard"))
        assertEquals(setOf("video-hud-floating", "dashboard"), options.topics)
    }

    private fun alphaAt(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) ushr 24

    /** Grava a demo em quadros PNG e devolve o primeiro quadro. */
    private fun firstFrameOf(id: String): BufferedImage {
        val dir = Files.createTempDirectory("video-demo-alpha").toFile()
        try {
            val options = HelpMediaOptions(dir, AppLanguage.PT, HelpMediaFormat.FRAMES, setOf(id))
            VideoDemos.recorders.getValue(id)(options)
            return ImageIO.read(File(options.framesDir(id), "000.png"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun assertTransparentCorners(id: String, frame: BufferedImage) {
        val last = listOf(0, frame.width - 1) to listOf(0, frame.height - 1)
        for (x in last.first) {
            for (y in last.second) {
                assertEquals(0, alphaAt(frame, x, y), "$id: o canto ($x, $y) tem de ser transparente")
            }
        }
    }

    /**
     * Minimize e reorder gravam centenas de MB de quadros a 4x e estouram o heap de 1 GB dos testes; o
     * gravador transparente é verificado com o notch real numa cena do mesmo formato, e o encanamento
     * `transparent` das demos fica provado pela flutuante (abaixo) e pela geração real.
     */
    @Test
    fun `o gravador transparente deixa o canto sem alfa e o notch opaco`() {
        val sizes = hudNotchSizes(
            accounts = ScreenshotFixtures.hudAccountsIn(AppLanguage.PT), edge = HudEdge.TOP,
            fallbackLabel = "Carregando", hasUpdateIndicator = false
        )
        val margin = VideoDemoPace.SCENE_MARGIN_DP
        val width = ceil(sizes.expanded.width.value).toInt() + 2 * margin
        val height = ceil(sizes.expanded.height.value).toInt() + 2 * margin
        val recorder = SceneRecorder(width, height, 10L, downsample = false, scale = 4, transparent = true)
        try {
            recorder.setContent {
                Box(Modifier.fillMaxSize().padding(margin.dp), contentAlignment = Alignment.TopCenter) {
                    HudNotch(
                        accounts = ScreenshotFixtures.hudAccountsIn(AppLanguage.PT), edge = HudEdge.TOP, sizes = sizes,
                        fallbackLabel = "Carregando", expanded = true, language = AppLanguage.PT
                    )
                }
            }
            recorder.hold(10L)
            val frame = recorder.frames.first().image
            assertTransparentCorners("notch", frame)
            val notchY = (margin + 8) * 4
            assertEquals(255, alphaAt(frame, frame.width / 2, notchY), "o notch tem de ser opaco")
        } finally {
            recorder.close()
        }
    }

    @Test
    fun `o quadro da hud flutuante tem fundo transparente e pixels opacos da hud`() {
        val frame = firstFrameOf("video-hud-floating")
        assertTransparentCorners("video-hud-floating", frame)
        var opaque = 0
        for (y in 0 until frame.height step 4) {
            for (x in 0 until frame.width step 4) {
                if (alphaAt(frame, x, y) == 255) opaque++
            }
        }
        assertTrue(opaque > 0, "a HUD flutuante tem de desenhar pixels opacos")
    }
}
