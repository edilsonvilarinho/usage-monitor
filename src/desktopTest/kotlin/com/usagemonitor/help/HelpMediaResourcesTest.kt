package com.usagemonitor.help

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpTopic
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A costura entre o catálogo e o que está no classpath.
 *
 * Tópico cujo `mediaId` não tem arquivo degrada em silêncio: a janela mostra o
 * estado "demonstração indisponível" e ninguém descobre que a gravação ficou
 * para trás — que é exatamente o que acontece quando se acrescenta um tópico e
 * se esquece de rodar `gradlew generateHelpMedia`.
 */
class HelpMediaResourcesTest {

    @Test
    fun `every topic ships a demo that decodes`() {
        for (topic in HelpTopic.entries) {
            val mediaId = HelpCatalog.mediaId(topic)
            val bytes = assertNotNull(
                readHelpMediaBytes(mediaId),
                "sem recurso help/$mediaId.gif para $topic"
            )

            val clip = assertNotNull(
                decodeHelpMedia(bytes),
                "help/$mediaId.gif não decodifica"
            )
            try {
                assertTrue(clip.frameCount > 1, "help/$mediaId.gif tem um quadro só")
            } finally {
                clip.close()
            }
        }
    }

    /**
     * O laço de reprodução decodifica os quadros **em sequência**, reaproveitando
     * o bitmap anterior como `priorFrame`. É o caminho que a janela percorre e o
     * único que os testes de componente não alcançam — laço infinito trava o
     * `waitForIdle`, e é por isso que ele mora fora do composable.
     */
    @Test
    fun `plays every frame of every demo in order`() {
        for (topic in HelpTopic.entries) {
            val mediaId = HelpCatalog.mediaId(topic)
            val bytes = assertNotNull(readHelpMediaBytes(mediaId))
            val clip = assertNotNull(decodeHelpMedia(bytes))

            try {
                var changed = 0
                var previous = sampleOf(clip.frameAt(0).toPixelMap())
                for (index in 1 until clip.frameCount) {
                    assertTrue(
                        clip.frameDelayMillis(index) > 0L,
                        "quadro $index de $mediaId sem espera"
                    )
                    val current = sampleOf(clip.frameAt(index).toPixelMap())
                    if (current != previous) {
                        changed++
                    }
                    previous = current
                }
                // Gravação em que nada muda em quadro nenhum é uma imagem parada
                // vendida como demo — o gerador teria produzido um laço vazio.
                assertTrue(changed > 0, "nenhum quadro de $mediaId difere do anterior")
            } finally {
                clip.close()
            }
        }
    }

    /**
     * Assinatura do quadro por amostragem em grade.
     *
     * Dois pixels de canto não servem: no dashboard o movimento é o botão de
     * atualizar de um card, e os cantos ficam idênticos do primeiro ao último
     * quadro — o teste acusou "nada muda" numa gravação em que muda.
     */
    private fun sampleOf(pixels: PixelMap): List<Int> {
        val samples = mutableListOf<Int>()
        var y = 0
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                samples += pixels[x, y].hashCode()
                x += SAMPLE_STRIDE
            }
            y += SAMPLE_STRIDE
        }
        return samples
    }

    private companion object {

        /** Passo da amostragem, em pixels. Cobre a cena sem ler 420 mil pixels por quadro. */
        const val SAMPLE_STRIDE = 16
    }
}
