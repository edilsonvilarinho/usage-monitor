package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.AppLanguage
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * As demos da ajuda servem também ao tour em vídeo (#505): o mesmo roteiro grava GIF em português para a janela de
 * ajuda e, com `--frames --language=en`, uma sequência de PNG a 2× em inglês para o vídeo. O padrão continua sendo o
 * da ajuda — `gradlew.bat generateHelpMedia` sem argumento novo grava exatamente o que gravava.
 */
class HelpMediaOptionsTest {

    @Test
    fun `without flags it records portuguese gifs of every topic into the help folder`() {
        val options = parseHelpMediaArgs(emptyArray())

        assertEquals(File("src/desktopMain/resources/help"), options.outputDir)
        assertEquals(AppLanguage.PT, options.language)
        assertEquals(HelpMediaFormat.GIF, options.format)
        assertNull(options.topics)
    }

    @Test
    fun `the video flags pick the language and the frame sequence`() {
        val options = parseHelpMediaArgs(arrayOf("build/video-media", "--frames", "--language=en"))

        assertEquals(File("build/video-media"), options.outputDir)
        assertEquals(AppLanguage.EN, options.language)
        assertEquals(HelpMediaFormat.FRAMES, options.format)
    }

    @Test
    fun `topic filter keeps the comma list and history keeps its shortcut`() {
        assertEquals(setOf("history", "budget"), parseHelpMediaArgs(arrayOf("out", "--topic=history,budget")).topics)
        assertEquals(setOf("history"), parseHelpMediaArgs(arrayOf("out", "--history")).topics)
    }

    @Test
    fun `an unknown language fails instead of silently recording portuguese`() {
        val failure = kotlin.runCatching { parseHelpMediaArgs(arrayOf("out", "--language=es")) }.exceptionOrNull()

        assertEquals("Idioma desconhecido: es (use pt ou en)", failure?.message)
    }

    @Test
    fun `frames land in a folder per language and topic`() {
        val options = parseHelpMediaArgs(arrayOf("build/video-media", "--frames", "--language=en"))

        assertEquals(File("build/video-media/en/history"), options.framesDir("history"))
    }

    @Test
    fun `the frame sequence keeps every frame at full size with its duration`() {
        val dir = Files.createTempDirectory("frames").toFile()
        val frames = listOf(GifFrame(solid(40, 20, 0xFF0000), 100), GifFrame(solid(40, 20, 0x00FF00), 1_800))

        FrameSequenceWriter.write(dir, frames)

        assertEquals(listOf("000.png", "001.png", "frames.json"), dir.list()!!.sorted())
        val second = ImageIO.read(File(dir, "001.png"))
        assertEquals(40 to 20, second.width to second.height)
        assertEquals(0x00FF00, second.getRGB(5, 5) and 0xFFFFFF)
        assertEquals(
            """[{"file":"000.png","delayMillis":100},{"file":"001.png","delayMillis":1800}]""",
            File(dir, "frames.json").readText()
        )
    }

    @Test
    fun `rewriting a sequence drops the frames of the previous take`() {
        val dir = Files.createTempDirectory("frames").toFile()
        FrameSequenceWriter.write(dir, List(3) { GifFrame(solid(4, 4, 0), 100) })

        FrameSequenceWriter.write(dir, listOf(GifFrame(solid(4, 4, 0), 100)))

        assertEquals(listOf("000.png", "frames.json"), dir.list()!!.sorted())
    }

    private fun solid(width: Int, height: Int, rgb: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, rgb)
        return image
    }
}
