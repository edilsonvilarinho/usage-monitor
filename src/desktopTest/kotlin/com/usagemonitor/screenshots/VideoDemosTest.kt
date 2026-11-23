package com.usagemonitor.screenshots

import kotlin.test.Test
import kotlin.test.assertEquals

/** Registro das demos de vídeo (#633): ids fixos, fora do `HelpTopic`, e `--topic` misto com o catálogo. */
class VideoDemosTest {

    @Test
    fun `o registro de video lista as demos de hud`() {
        assertEquals(setOf("video-hud-minimize", "video-hud-floating", "video-hud-reorder"), VideoDemos.ids)
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
}
