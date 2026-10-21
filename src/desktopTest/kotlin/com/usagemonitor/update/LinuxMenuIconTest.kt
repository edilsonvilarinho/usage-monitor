package com.usagemonitor.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LinuxMenuIconTest {

    private val stableIconPath = "/home/edils/.local/share/usage-monitor/icon-0123456789ab.png"

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

    @Test
    fun `the icon file name is content addressed`() {
        // SHA-256 de "abc", conferível com `printf abc | sha256sum`.
        assertEquals("icon-ba7816bf8f01.png", linuxMenuIconFileName("abc".encodeToByteArray()))
    }

    @Test
    fun `the same bytes give the same name and new bytes give a new one`() {
        val oldIcon = byteArrayOf(1, 2, 3)

        assertEquals(linuxMenuIconFileName(oldIcon), linuxMenuIconFileName(byteArrayOf(1, 2, 3)))
        assertNotEquals(linuxMenuIconFileName(oldIcon), linuxMenuIconFileName(byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `a new icon name makes the entry pointing at the previous copy need repair`() {
        // É isto que faz a troca da marca chegar ao menu (issue #432): o
        // nome novo deixa de bater com o `Icon=` gravado.
        val previousCopy = "/home/edils/.local/share/usage-monitor/icon.png"
        val entry = "[Desktop Entry]\nName=Usage Monitor\nIcon=$previousCopy\n"

        assertTrue(linuxMenuIconNeedsRepair(entry, stableIconPath))
    }

    @Test
    fun `stale copies are the legacy name and other hashes only`() {
        val existing = listOf(
            "icon-0123456789ab.png",
            "icon.png",
            "icon-ffffffffffff.png",
            "current",
            ".usage-monitor-managed",
            "versions",
            "icon-0123456789ab.png.next",
            "icon-nothex000000.png",
            "meu-icone.png"
        )

        assertEquals(
            listOf("icon-ffffffffffff.png", "icon.png"),
            staleLinuxMenuIconFileNames(existing, currentFileName = "icon-0123456789ab.png")
        )
    }

    @Test
    fun `nothing is stale when only the current copy exists`() {
        assertEquals(
            emptyList(),
            staleLinuxMenuIconFileNames(listOf("icon-0123456789ab.png", "current"), "icon-0123456789ab.png")
        )
    }
}
