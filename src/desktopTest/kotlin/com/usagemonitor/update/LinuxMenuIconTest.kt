package com.usagemonitor.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxMenuIconTest {

    private val stableIconPath = "/home/edils/.local/share/usage-monitor/icon.png"

    @Test
    fun `an absent entry does not need repair`() {
        assertFalse(linuxMenuIconNeedsRepair(desktopEntryText = null, stableIconPath = stableIconPath))
    }

    @Test
    fun `an entry already pointing at the stable icon does not need repair`() {
        val entry = "[Desktop Entry]\nName=Usage Monitor\nIcon=$stableIconPath\n"

        assertFalse(linuxMenuIconNeedsRepair(entry, stableIconPath))
    }

    @Test
    fun `an entry pointing into the versioned tree needs repair`() {
        val entry = "[Desktop Entry]\n" +
            "Name=Usage Monitor\n" +
            "Icon=/home/edils/.local/share/usage-monitor/versions/38.0.2/Usage Monitor/lib/Usage Monitor.png\n"

        assertTrue(linuxMenuIconNeedsRepair(entry, stableIconPath))
    }

    @Test
    fun `an entry with no icon line at all needs repair`() {
        // Cobre a instalação onde o png não existia no momento em que o
        // instalador rodou -- sem esta condição ela nunca ganharia ícone.
        val entry = "[Desktop Entry]\nName=Usage Monitor\nExec=usage-monitor\n"

        assertTrue(linuxMenuIconNeedsRepair(entry, stableIconPath))
    }

    @Test
    fun `rewriting replaces only the icon line and preserves everything else`() {
        val entry = "[Desktop Entry]\n" +
            "Type=Application\n" +
            "Name=Usage Monitor\n" +
            "Icon=/home/edils/.local/share/usage-monitor/versions/38.0.2/Usage Monitor/lib/Usage Monitor.png\n" +
            "Exec=\"/home/edils/.local/bin/usage-monitor\"\n" +
            "Terminal=false\n" +
            "Categories=Utility;Monitor;\n" +
            "Comment=Anotação que alguém escreveu à mão\n"

        val rewritten = rewriteLinuxMenuIconLine(entry, stableIconPath)

        assertEquals(
            "[Desktop Entry]\n" +
                "Type=Application\n" +
                "Name=Usage Monitor\n" +
                "Exec=\"/home/edils/.local/bin/usage-monitor\"\n" +
                "Terminal=false\n" +
                "Categories=Utility;Monitor;\n" +
                "Comment=Anotação que alguém escreveu à mão\n" +
                "Icon=$stableIconPath\n",
            rewritten
        )
    }

    @Test
    fun `rewriting an entry without any icon line just appends one`() {
        val entry = "[Desktop Entry]\nName=Usage Monitor\nExec=usage-monitor\n"

        val rewritten = rewriteLinuxMenuIconLine(entry, stableIconPath)

        assertEquals(
            "[Desktop Entry]\nName=Usage Monitor\nExec=usage-monitor\nIcon=$stableIconPath\n",
            rewritten
        )
    }
}
