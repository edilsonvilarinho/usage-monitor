package com.usagemonitor.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Molde de `WindowsInstallOriginTest`: leitores injetados, nenhum acesso ao
 * disco nem ao processo real.
 */
class LinuxInstallOriginTest {

    private val root = "/home/edils/.local/share/usage-monitor"
    private val managedExecutable = "$root/versions/39.0.0/Usage Monitor/bin/Usage Monitor"

    @Test
    fun `marker plus an executable inside versions is a managed install`() {
        assertEquals(
            LinuxInstallOrigin.MANAGED_XDG,
            resolve(candidates = listOf(managedExecutable), hasMarker = true)
        )
    }

    /**
     * O marcador sobrevive a uma instalação apagada à mão. Sozinho ele passaria
     * a autorizar a atualização de uma cópia qualquer da pasta.
     */
    @Test
    fun `the marker alone does not authorize an executable from elsewhere`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf("/home/edils/Downloads/Usage Monitor/bin/Usage Monitor"), hasMarker = true)
        )
    }

    @Test
    fun `an executable inside versions without the marker is not managed`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf(managedExecutable), hasMarker = false)
        )
    }

    /**
     * Um `.rpm` em `/opt` pode conviver com um marcador deixado por uma
     * instalação XDG anterior. A recusa não pode depender do formato exato do
     * caminho de `versions/`.
     */
    @Test
    fun `a package manager path is refused even with the marker present`() {
        listOf(
            "/opt/usage-monitor/bin/usage-monitor",
            "/opt/Usage Monitor/bin/Usage Monitor",
            "/usr/bin/usage-monitor",
            "/usr/local/bin/usage-monitor"
        ).forEach { candidate ->
            assertEquals(
                LinuxInstallOrigin.UNMANAGED,
                resolve(candidates = listOf(candidate), hasMarker = true),
                "aceitou '$candidate'"
            )
        }
    }

    /**
     * Duas fontes para o executável porque nenhuma das duas é garantida: basta
     * uma delas apontar para dentro de `versions/`.
     */
    @Test
    fun `either executable source is enough`() {
        assertEquals(
            LinuxInstallOrigin.MANAGED_XDG,
            resolve(candidates = listOf("/nao/sei", managedExecutable), hasMarker = true)
        )
    }

    @Test
    fun `outside linux nothing is managed`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            LinuxInstallOriginResolver.resolve(
                isLinux = false,
                rootPath = root,
                executableCandidates = listOf(managedExecutable),
                hasMarker = true
            )
        )
    }

    @Test
    fun `without a resolvable root nothing is managed`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            LinuxInstallOriginResolver.resolve(
                isLinux = true,
                rootPath = null,
                executableCandidates = listOf(managedExecutable),
                hasMarker = true
            )
        )
    }

    @Test
    fun `no executable candidate is not managed`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = emptyList(), hasMarker = true)
        )
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf("   ", "\"\""), hasMarker = true)
        )
    }

    /**
     * `versions` como **prefixo de nome** não conta: `versionsX` é outro
     * diretório, e a comparação é por segmento inteiro por causa da barra.
     */
    @Test
    fun `a sibling directory whose name starts with versions is not inside it`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf("$root/versions-antigo/39.0.0/x"), hasMarker = true)
        )
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf("$root/versions"), hasMarker = true)
        )
    }

    /**
     * `File.normalize()` usaria o separador do Windows e devolveria o caminho
     * intacto, e este teste passaria sem provar nada.
     */
    @Test
    fun `a dot dot segment cannot fake being inside versions`() {
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf("$root/versions/../../../etc/passwd"), hasMarker = true)
        )
        assertEquals(
            LinuxInstallOrigin.UNMANAGED,
            resolve(candidates = listOf("$root/versions/39.0.0/../../../opt/x"), hasMarker = true)
        )
    }

    @Test
    fun `redundant separators and dots do not change the answer`() {
        assertEquals(
            LinuxInstallOrigin.MANAGED_XDG,
            resolve(
                candidates = listOf("$root//versions/./39.0.0/Usage Monitor/bin/Usage Monitor"),
                hasMarker = true
            )
        )
    }

    @Test
    fun `posix normalization is textual and platform independent`() {
        assertEquals("/home/edils/x", normalizePosixPath("/home/edils/y/../x"))
        assertEquals("/home/edils", normalizePosixPath("/home//edils/"))
        assertEquals("/", normalizePosixPath("/"))
        assertEquals("/", normalizePosixPath("/home/../.."))
        assertEquals("../x", normalizePosixPath("../x"))
        assertEquals("x", normalizePosixPath("./x"))
    }

    @Test
    fun `package manager detection ignores quotes and redundant segments`() {
        assertTrue(isLinuxPackageManagerPath("\"/opt/usage-monitor/bin/usage-monitor\""))
        assertTrue(isLinuxPackageManagerPath("/usr/share/../bin/usage-monitor"))
        assertFalse(isLinuxPackageManagerPath("$root/versions/39.0.0/x"))
        assertFalse(isLinuxPackageManagerPath("/home/edils/opt/x"))
    }

    private fun resolve(candidates: List<String>, hasMarker: Boolean): LinuxInstallOrigin {
        return LinuxInstallOriginResolver.resolve(
            isLinux = true,
            rootPath = root,
            executableCandidates = candidates,
            hasMarker = hasMarker
        )
    }
}
