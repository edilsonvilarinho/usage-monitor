package com.usagemonitor.help

import androidx.compose.ui.graphics.toPixelMap
import com.usagemonitor.screenshots.GifEncoder
import com.usagemonitor.screenshots.GifFrame
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O tocador é testado contra um GIF gerado pelo **mesmo codificador** que grava
 * as demos (`GifEncoder`): paleta global, quadros delta e índice transparente.
 * Um GIF de outra origem provaria que o Skia decodifica GIF, que ninguém duvida;
 * o que precisa de prova é que ele decodifica *o nosso*.
 */
class HelpMediaPlayerTest {

    @Test
    fun `reads every frame of a clip written by the app's own encoder`() {
        val clip = assertNotNull(decodeHelpMedia(encodedClip()))

        try {
            assertEquals(3, clip.frameCount)
        } finally {
            clip.close()
        }
    }

    /** A espera de cada quadro é o que faz a demo ter o ritmo com que foi gravada. */
    @Test
    fun `keeps the per-frame delay of the recording`() {
        val clip = assertNotNull(decodeHelpMedia(encodedClip()))

        try {
            assertEquals(100L, clip.frameDelayMillis(0))
            assertEquals(200L, clip.frameDelayMillis(1))
            assertEquals(300L, clip.frameDelayMillis(2))
        } finally {
            clip.close()
        }
    }

    /**
     * O terceiro quadro deste GIF é um retângulo delta sobre o segundo. Se a
     * composição do quadro anterior não acontecesse, ele viria transparente ou
     * repetindo o primeiro — que é exatamente o defeito silencioso de decodificar
     * quadro a quadro sem blending.
     */
    @Test
    fun `composes delta frames over the previous one`() {
        val clip = assertNotNull(decodeHelpMedia(encodedClip()))

        try {
            val first = clip.frameAt(0).toPixelMap()[0, 0]
            val second = clip.frameAt(1).toPixelMap()[0, 0]
            val third = clip.frameAt(2).toPixelMap()[0, 0]

            assertTrue(first != second, "quadro 1 e 2 saíram iguais")
            assertTrue(second != third, "quadro 2 e 3 saíram iguais")
        } finally {
            clip.close()
        }
    }

    /** Voltar ao início do laço não pode depender do quadro que está no bitmap. */
    @Test
    fun `replays the first frame after wrapping around`() {
        val clip = assertNotNull(decodeHelpMedia(encodedClip()))

        try {
            val firstPass = clip.frameAt(0).toPixelMap()[0, 0]
            clip.frameAt(1)
            clip.frameAt(2)
            val secondPass = clip.frameAt(0).toPixelMap()[0, 0]

            assertEquals(firstPass, secondPass)
        } finally {
            clip.close()
        }
    }

    /** Arquivo corrompido degrada para "sem demo", nunca derruba a janela. */
    @Test
    fun `returns null for bytes that are not a gif`() {
        assertNull(decodeHelpMedia(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
        assertNull(decodeHelpMedia(ByteArray(0)))
    }

    /** Recurso que não veio na instalação também é "sem demo". */
    @Test
    fun `returns null for a media id with no resource`() {
        assertNull(readHelpMediaBytes("this-demo-does-not-exist"))
    }

    /**
     * Três quadros de cores distintas, com esperas diferentes. As cores são
     * afastadas de propósito: o codificador quantiza numa paleta de 255 cores, e
     * tons vizinhos poderiam cair no mesmo índice e apagar a diferença que o
     * teste mede.
     */
    private fun encodedClip(): ByteArray {
        val frames = listOf(
            GifFrame(solid(0xFFFF0000.toInt()), 100),
            GifFrame(solid(0xFF00FF00.toInt()), 200),
            GifFrame(solid(0xFF0000FF.toInt()), 300)
        )

        val file = File.createTempFile("help-media", ".gif")
        try {
            GifEncoder.write(file, frames)
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun solid(color: Int): BufferedImage {
        val image = BufferedImage(32, 20, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = java.awt.Color(color, true)
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
