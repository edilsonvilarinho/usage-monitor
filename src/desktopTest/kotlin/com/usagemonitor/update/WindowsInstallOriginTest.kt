package com.usagemonitor.update

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os caminhos abaixo são os reais desta instalação de referência, lidos da chave
 * `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\Usage Monitor`:
 * `InstallLocation = C:\Users\edils\AppData\Local\Usage Monitor`, com o
 * `Usage Monitor.exe` na raiz dela.
 */
class WindowsInstallOriginTest {

    private val installLocation = """C:\Users\someone\AppData\Local\Usage Monitor"""
    private val installedExecutable = """C:\Users\someone\AppData\Local\Usage Monitor\Usage Monitor.exe"""

    @Test
    fun `executable inside the registered install location is the NSIS install`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.NSIS_PER_USER, origin)
    }

    @Test
    fun `any of the two executable sources is enough`() {
        // Nenhuma das duas fontes é garantida; aceitar qualquer uma torna a
        // detecção independente de qual delas o runtime preenche.
        val fromJpackageOnly = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = listOf(installedExecutable, """C:\java\bin\java.exe"""),
            hasNsisUninstaller = true
        )
        val fromProcessOnly = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = listOf("""C:\java\bin\java.exe""", installedExecutable),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.NSIS_PER_USER, fromJpackageOnly)
        assertEquals(WindowsInstallOrigin.NSIS_PER_USER, fromProcessOnly)
    }

    @Test
    fun `casing and trailing separator do not change the answer`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = """c:\users\someone\appdata\local\USAGE MONITOR\""",
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = true
        )

        // O sistema de arquivos do Windows não distingue maiúsculas, e o registro
        // guarda o que o instalador escreveu.
        assertEquals(WindowsInstallOrigin.NSIS_PER_USER, origin)
    }

    @Test
    fun `quoted registry value is accepted`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = "\"$installLocation\"",
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.NSIS_PER_USER, origin)
    }

    @Test
    fun `executable outside the registered location is unmanaged`() {
        // A chave sobrevive a uma instalacao removida a mao; sozinha ela passaria
        // a autorizar a atualizacao de uma copia qualquer da pasta.
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = listOf("""D:\portable\Usage Monitor\Usage Monitor.exe"""),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }

    @Test
    fun `missing registry key is unmanaged`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = null,
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }

    @Test
    fun `blank registry value is unmanaged`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = "   ",
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }

    @Test
    fun `no executable candidate at all is unmanaged`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = emptyList(),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }

    @Test
    fun `runtime launcher one level below the install location is unmanaged`() {
        // Conservador de proposito: se o processo nao e o launcher da raiz, nao
        // ha prova de que esta instalacao e a que o registro descreve.
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = listOf("""C:\Users\someone\AppData\Local\Usage Monitor\runtime\bin\java.exe"""),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }

    @Test
    fun `the registered location without the NSIS uninstaller is unmanaged`() {
        // Este e o estado real medido em 2026-08-24 numa maquina que veio do NSIS
        // e migrou para o MSI: a chave HKCU do NSIS continua la em 23.0.0, o
        // produto ativo e o MSI em 37.0.0, e os dois usam o MESMO diretorio. As
        // duas primeiras condicoes batem por acidente, e sem esta terceira o
        // resolvedor autorizava a atualizacao automatica de uma arvore gerenciada
        // pelo Windows Installer.
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = true,
            installLocation = installLocation,
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = false
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }

    @Test
    fun `outside windows the origin is always unmanaged`() {
        val origin = WindowsInstallOriginResolver.resolve(
            isWindows = false,
            installLocation = installLocation,
            executableCandidates = listOf(installedExecutable),
            hasNsisUninstaller = true
        )

        assertEquals(WindowsInstallOrigin.UNMANAGED, origin)
    }
}
