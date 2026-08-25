package com.usagemonitor.update

import com.usagemonitor.AutoStartManager
import java.io.File

/**
 * De onde veio esta instalação no Windows.
 *
 * A atualização automática executa o `UsageMonitor-Setup-<v>.exe` em silêncio, e
 * esse instalador escreve numa pasta per-user, num par de chaves HKCU e nos
 * atalhos. Fazer isso por cima de uma instalação que **não** foi criada por ele
 * produz uma cópia paralela: a versão nova em `%LOCALAPPDATA%\Usage Monitor`, os
 * atalhos do MSI ainda apontando para a pasta antiga e um registro do Windows
 * Installer capaz de reparar ou remover arquivos da versão nova. Por isso o
 * resolvedor existe, e por isso o default é não atualizar.
 */
enum class WindowsInstallOrigin {
    /** Instalada pelo `UsageMonitor-Setup-<v>.exe`: pode se atualizar sozinha. */
    NSIS_PER_USER,

    /**
     * Qualquer outra coisa — MSI, cópia manual da pasta, `gradlew run`.
     *
     * Os casos não são separados de propósito: distinguir o MSI exigiria varrer
     * as chaves do Windows Installer por `DisplayName`, o que é lento e frágil, e
     * a resposta seria a mesma para todos eles. Afirmar "instalado pelo MSI" sem
     * ter como provar seria pior que dizer "não foi por este instalador".
     */
    UNMANAGED
}

object WindowsInstallOriginResolver {

    /**
     * Resolve a origem a partir do estado real da máquina.
     *
     * Duas fontes para o executável em execução porque nenhuma das duas é
     * garantida: `jpackage.app-path` é a propriedade que o launcher do jpackage
     * injeta, e `ProcessHandle` devolve a imagem do processo — num app-image o
     * launcher nativo carrega a libjvm no mesmo processo, então as duas tendem a
     * apontar para o mesmo `.exe`. Aceitar qualquer uma das duas torna a
     * detecção independente de qual delas o runtime preenche.
     */
    fun current(): WindowsInstallOrigin {
        val installLocation = AutoStartManager.readWindowsInstallLocationOrNull()
        return resolve(
            isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win"),
            installLocation = installLocation,
            executableCandidates = listOfNotNull(
                System.getProperty("jpackage.app-path"),
                ProcessHandle.current().info().command().orElse(null)
            ),
            hasNsisUninstaller = hasNsisUninstallerIn(installLocation)
        )
    }

    /**
     * Checagem de **sistema de arquivos**, e não de registro: não abre `reg.exe`
     * nem varre o banco do Windows Installer, que é lento e frágil. O
     * `Uninstall.exe` é escrito pelo `WriteUninstaller` do NSIS e não existe num
     * app-image do jpackage — desinstalação de MSI é por `msiexec`.
     */
    private fun hasNsisUninstallerIn(installLocation: String?): Boolean {
        val directory = installLocation?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return false
        return File(directory, UNINSTALLER_FILE_NAME).isFile
    }

    private const val UNINSTALLER_FILE_NAME = "Uninstall.exe"

    /**
     * Função pura, para o teste não depender do registro nem do processo real.
     *
     * São **três** condições, e nenhuma delas basta sozinha:
     *
     * 1. o `InstallLocation` da chave HKCU que o NSIS escreve existe;
     * 2. ele é **o diretório onde o executável em execução está** — a chave
     *    sobrevive a uma instalação removida à mão e passaria a autorizar a
     *    atualização de uma cópia qualquer da pasta;
     * 3. esse diretório contém o `Uninstall.exe` do NSIS.
     *
     * A terceira condição existe porque até a v37 o release publicava também um
     * MSI, e os **dois instaladores gravavam no mesmo diretório**. Numa máquina
     * que veio do NSIS e migrou para o MSI, as duas primeiras condições batem —
     * por acidente — e o resolvedor devolvia `NSIS_PER_USER` para uma instalação
     * que é MSI, que é exatamente o caso que ele existe para recusar. O
     * `Uninstall.exe` é o que distingue: o NSIS sempre o escreve, o app-image do
     * jpackage nunca o tem.
     */
    internal fun resolve(
        isWindows: Boolean,
        installLocation: String?,
        executableCandidates: List<String>,
        hasNsisUninstaller: Boolean
    ): WindowsInstallOrigin {
        if (!isWindows) {
            return WindowsInstallOrigin.UNMANAGED
        }

        if (!hasNsisUninstaller) {
            return WindowsInstallOrigin.UNMANAGED
        }

        val installDirectory = normalizedDirectory(installLocation)
            ?: return WindowsInstallOrigin.UNMANAGED

        val matches = executableCandidates.any { candidate ->
            normalizedDirectory(File(candidate.trim().trim('"')).parent) == installDirectory
        }

        return if (matches) WindowsInstallOrigin.NSIS_PER_USER else WindowsInstallOrigin.UNMANAGED
    }

    /**
     * Caminho absoluto normalizado e em caixa baixa. Caixa baixa porque o
     * sistema de arquivos do Windows não distingue maiúsculas, e o registro
     * guarda o que o instalador escreveu — comparar por igualdade exata daria
     * `UNMANAGED` para a mesma pasta escrita com outra grafia.
     */
    private fun normalizedDirectory(path: String?): String? {
        val trimmed = path?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return null
        return File(trimmed).absoluteFile.normalize().path.trimEnd('\\', '/').lowercase()
    }
}
