package com.usagemonitor.update

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Layout da instalação Linux gerenciada.
 *
 * A suíte roda no Windows, então nada aqui depende do separador da máquina nem
 * de permissão real de diretório: os caminhos são texto POSIX e os probes de
 * disco são injetados. Um teste de permissão escrito contra o disco do Windows
 * passaria sem medir coisa nenhuma.
 */
class LinuxInstallLayoutTest {

    private val tempDirectory: File = Files.createTempDirectory("usage-monitor-layout").toFile()

    @AfterTest
    fun cleanUp() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun `absent XDG_DATA_HOME falls back to the default under the home`() {
        assertEquals(
            "/home/edils/.local/share/usage-monitor",
            resolveLinuxInstallRoot(xdgDataHome = null, userHome = "/home/edils")
        )
    }

    @Test
    fun `an absolute XDG_DATA_HOME is honored`() {
        assertEquals(
            "/data/xdg/usage-monitor",
            resolveLinuxInstallRoot(xdgDataHome = "/data/xdg", userHome = "/home/edils")
        )
    }

    /**
     * A especificação XDG diz que caminho relativo nessas variáveis é
     * **inválido e deve ser ignorado** — e não resolvido contra o diretório
     * corrente. Resolver instalaria a árvore onde quer que o app tenha sido
     * lançado.
     */
    @Test
    fun `a relative XDG_DATA_HOME is ignored, not resolved`() {
        assertEquals(
            "/home/edils/.local/share/usage-monitor",
            resolveLinuxInstallRoot(xdgDataHome = "relativo/share", userHome = "/home/edils")
        )
        assertEquals(
            "/home/edils/.local/share/usage-monitor",
            resolveLinuxInstallRoot(xdgDataHome = "   ", userHome = "/home/edils")
        )
    }

    @Test
    fun `a trailing slash does not double up in the path`() {
        assertEquals(
            "/data/xdg/usage-monitor",
            resolveLinuxInstallRoot(xdgDataHome = "/data/xdg/", userHome = "/home/edils")
        )
        assertEquals(
            "/home/edils/.local/share/usage-monitor",
            resolveLinuxInstallRoot(xdgDataHome = null, userHome = "/home/edils/")
        )
    }

    @Test
    fun `without a home and without the variable there is no root`() {
        assertNull(resolveLinuxInstallRoot(xdgDataHome = null, userHome = null))
        assertNull(resolveLinuxInstallRoot(xdgDataHome = "nao/absoluto", userHome = ""))
        assertNull(resolveLinuxInstallLayout(xdgDataHome = null, userHome = null))
    }

    @Test
    fun `the layout names every path with posix separators`() {
        val layout = LinuxInstallLayout("/home/edils/.local/share/usage-monitor")

        assertEquals("/home/edils/.local/share/usage-monitor/.usage-monitor-managed", layout.markerPath)
        assertEquals("/home/edils/.local/share/usage-monitor/current", layout.currentPath)
        assertEquals("/home/edils/.local/share/usage-monitor/versions", layout.versionsPath)
        assertEquals("/home/edils/.local/share/usage-monitor/updates", layout.updatesPath)
        assertEquals(
            "/home/edils/.local/share/usage-monitor/versions/39.0.0",
            layout.versionPath("39.0.0")
        )
        assertEquals(
            "/home/edils/.local/share/usage-monitor/versions/39.0.0/Usage Monitor",
            layout.appPath("39.0.0")
        )
        assertEquals(
            "/home/edils/.local/share/usage-monitor/versions/39.0.0/Usage Monitor/bin/Usage Monitor",
            layout.launcherPath("39.0.0")
        )
        assertEquals(
            "/home/edils/.local/share/usage-monitor/updates/39.0.0.staging",
            layout.stagingPath("39.0.0")
        )
    }

    /**
     * O staging fica **fora** de `versions/`: uma extração interrompida ali
     * dentro seria indistinguível de uma versão instalada.
     */
    @Test
    fun `staging never lands inside the versions directory`() {
        val layout = LinuxInstallLayout("/root")

        assertFalse(layout.stagingPath("39.0.0").startsWith("${layout.versionsPath}/"))
    }

    @Test
    fun `the current pointer is read and trimmed`() {
        val layout = LinuxInstallLayout(tempDirectory.absolutePath)
        layout.currentFile.writeText("  39.0.0\n")

        assertEquals("39.0.0", layout.readCurrentVersion())
    }

    @Test
    fun `an absent pointer is not a version`() {
        val layout = LinuxInstallLayout(File(tempDirectory, "vazio").absolutePath)

        assertNull(layout.readCurrentVersion())
    }

    /**
     * O conteúdo de `current` vira **segmento de caminho**. Aceitá-lo cru
     * apontaria a execução para fora da raiz gerenciada.
     */
    @Test
    fun `a pointer that is not a version is refused`() {
        val layout = LinuxInstallLayout(tempDirectory.absolutePath)

        listOf("../..", "39.0.0/../../etc", "", "latest", "/absoluto", "39.0.0 39.0.1").forEach { value ->
            layout.currentFile.writeText(value)
            assertNull(layout.readCurrentVersion(), "aceitou '$value' como versão")
        }
    }

    @Test
    fun `version names accept up to four numeric components`() {
        listOf("1", "1.2", "39.0.0", "39.0.0.1").forEach { value ->
            assertTrue(isValidLinuxVersionName(value), "recusou '$value'")
        }
        listOf("", "v39", "39.0.0-rc1", "39..0", "39.0.0.1.2", "..", "39/0").forEach { value ->
            assertFalse(isValidLinuxVersionName(value), "aceitou '$value'")
        }
    }

    /**
     * O probe é injetado porque `setWritable(false)` num diretório do Windows é
     * inerte: o teste real mediria o `canWrite` de sempre e passaria.
     */
    @Test
    fun `a root without write permission is reported as such`() {
        val layout = LinuxInstallLayout("/data/xdg/usage-monitor")

        assertFalse(layout.isRootWritable { false })
        assertTrue(layout.isRootWritable { true })
    }

    @Test
    fun `the marker decides whether the tree is managed`() {
        val layout = LinuxInstallLayout(tempDirectory.absolutePath)

        assertFalse(layout.hasMarker())
        layout.markerFile.writeText("")
        assertTrue(layout.hasMarker())
    }

    @Test
    fun `the stable launcher lives under the home, not under XDG_DATA_HOME`() {
        assertEquals(
            "/home/edils/.local/bin/usage-monitor",
            resolveLinuxStableLauncherPath(userHome = "/home/edils")
        )
        // XDG_DATA_HOME apontado para outro disco não muda onde ficam os
        // executáveis do usuário: são convenções independentes.
        assertNull(resolveLinuxStableLauncherPath(userHome = null))
    }

    @Test
    fun `the stable launcher execs the tree named by current`() {
        val script = buildLinuxStableLauncherScript("/home/edils/.local/share/usage-monitor")

        assertTrue(script.startsWith("#!/bin/sh\n"))
        assertTrue(script.contains("root='/home/edils/.local/share/usage-monitor'"))
        assertTrue(script.contains("version=\$(cat \"\$root/current\")"))
        // `exec` e não subshell: o processo do app substitui o do launcher, ou o
        // `.desktop` e a bandeja enxergariam um `sh` como sendo o aplicativo.
        assertTrue(
            script.contains(
                "exec \"\$root/versions/\$version/Usage Monitor/bin/Usage Monitor\" \"\$@\""
            )
        )
    }

    /**
     * O caminho do `$HOME` é texto do usuário. Um apóstrofo num nome de conta
     * basta para transformar interpolação ingênua em execução de comando.
     */
    @Test
    fun `a quote in the home path cannot escape the launcher string`() {
        val script = buildLinuxStableLauncherScript("/home/d'arcy/.local/share/usage-monitor")

        assertTrue(script.contains("""root='/home/d'\''arcy/.local/share/usage-monitor'"""))
        assertEquals("""'; rm -rf /; echo '\''x'""", quoteForPosixShell("; rm -rf /; echo 'x"))
    }
}
