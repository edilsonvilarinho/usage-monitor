package com.usagemonitor.help

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
}
