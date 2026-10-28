package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/** O menu da bandeja nos dois idiomas, item a item. */
class TrayMenuLabelsTest {

    @Test
    fun `every tray menu item has its own text in each language`() {
        val items = listOf(TrayMenuLabels::open, TrayMenuLabels::refreshNow, TrayMenuLabels::settings, TrayMenuLabels::help, TrayMenuLabels::quit)

        assertEquals(
            listOf("Abrir", "Atualizar agora", "Configurações", "Ajuda", "Sair"),
            items.map { label -> label(AppLanguage.PT) }
        )
        assertEquals(
            listOf("Open", "Refresh now", "Settings", "Help", "Quit"),
            items.map { label -> label(AppLanguage.EN) }
        )
    }
}
