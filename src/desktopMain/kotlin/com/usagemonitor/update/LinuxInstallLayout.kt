package com.usagemonitor.update

import java.io.File

/** Diretório da aplicação dentro da raiz de dados XDG. */
internal const val LINUX_INSTALL_DIRECTORY_NAME = "usage-monitor"

/**
 * Marcador que autoriza qualquer escrita nesta árvore.
 *
 * Sem ele, o script de troca **aborta sem tocar em nada**. É a única defesa
 * contra apontar o mecanismo para um diretório que o app não criou.
 */
internal const val LINUX_MANAGED_MARKER_NAME = ".usage-monitor-managed"

/**
 * Ponteiro da versão ativa. **Arquivo de texto com a versão, não symlink.**
 *
 * Com symlink, o swap atômico exigiria `mv -T`, que é extensão GNU e não POSIX:
 * `mv current.next current`, com `current` sendo symlink para diretório, move o
 * arquivo **para dentro** do diretório apontado — o oposto do pretendido.
 * Arquivo regular faz `rename(2)` puro, e o rollback é reescrever a versão
 * anterior.
 */
internal const val LINUX_CURRENT_FILE_NAME = "current"

internal const val LINUX_VERSIONS_DIRECTORY_NAME = "versions"

/**
 * Staging da extração — e **não** o diretório de download.
 *
 * O tarball continua em `~/.usage-monitor/updates`, onde `UpdateArtifactDownloader`
 * baixa e onde a poda da issue #87 já governa. A extração vem para cá porque a
 * promoção de `<versão>.staging` para `versions/<versão>` é um `rename`, e
 * `rename(2)` exige o **mesmo filesystem** — que os dois homes não são
 * garantidamente.
 */
internal const val LINUX_UPDATES_DIRECTORY_NAME = "updates"

/** Sufixo do staging. Fora de `versions/` para uma extração parcial nunca virar versão. */
internal const val LINUX_STAGING_SUFFIX = ".staging"

/** Raiz única exigida dentro do `.tar.gz`, conferida na v37.0.0. */
internal const val LINUX_APP_DIRECTORY_NAME = "Usage Monitor"

/** Launcher do jpackage dentro da árvore da versão, em modo `0755`. */
internal const val LINUX_APP_LAUNCHER_RELATIVE_PATH = "bin/Usage Monitor"

/**
 * Launcher estável, o único caminho que o `.desktop` e os atalhos conhecem.
 *
 * `~/.local/bin` e não a raiz XDG de dados: são coisas diferentes na convenção,
 * e o `PATH` do usuário já costuma trazer aquele.
 */
internal const val LINUX_STABLE_LAUNCHER_RELATIVE_PATH = ".local/bin/usage-monitor"

/**
 * Onde vive uma instalação Linux gerenciada.
 *
 * **Os caminhos são texto com `/`, não `File(parent, child)`.** O separador do
 * `File` é o da máquina que roda o código, e a suíte roda no Windows: montar os
 * caminhos com `File` produziria `C:\...\versions\39.0.0` nos testes e um
 * contrato que ninguém consegue afirmar. Aqui a forma é sempre POSIX, que é a
 * forma da plataforma de destino.
 *
 * A classe não toca o disco além de ler `current`: quem decide se a instalação é
 * gerenciada é [LinuxInstallOriginResolver], com leitores injetados.
 */
internal class LinuxInstallLayout(val rootPath: String) {

    val root: File get() = File(rootPath)

    val markerPath: String = "$rootPath/$LINUX_MANAGED_MARKER_NAME"
    val currentPath: String = "$rootPath/$LINUX_CURRENT_FILE_NAME"
    val versionsPath: String = "$rootPath/$LINUX_VERSIONS_DIRECTORY_NAME"
    val updatesPath: String = "$rootPath/$LINUX_UPDATES_DIRECTORY_NAME"

    val markerFile: File get() = File(markerPath)
    val currentFile: File get() = File(currentPath)
    val versionsDirectory: File get() = File(versionsPath)
    val updatesDirectory: File get() = File(updatesPath)

    /** `versions/<versão>` — a árvore instalada, uma por versão retida. */
    fun versionPath(version: String): String = "$versionsPath/$version"

    /** `versions/<versão>/Usage Monitor` — a raiz única que o tarball traz. */
    fun appPath(version: String): String = "${versionPath(version)}/$LINUX_APP_DIRECTORY_NAME"

    /** O executável que o launcher estável vai chamar. */
    fun launcherPath(version: String): String =
        "${appPath(version)}/$LINUX_APP_LAUNCHER_RELATIVE_PATH"

    /** `updates/<versão>.staging` — extração ainda não promovida. */
    fun stagingPath(version: String): String =
        "$updatesPath/$version$LINUX_STAGING_SUFFIX"

    /**
     * Versão ativa, ou `null` quando o ponteiro não existe, não é legível ou
     * traz algo que não é versão.
     *
     * A validação não é zelo: o conteúdo de `current` vira **segmento de
     * caminho**, e um `../..` ali apontaria a execução para fora da raiz.
     */
    fun readCurrentVersion(): String? {
        val raw = runCatching {
            if (!currentFile.isFile) null else currentFile.readText()
        }.getOrNull() ?: return null

        val trimmed = raw.trim()
        return if (isValidLinuxVersionName(trimmed)) trimmed else null
    }

    /**
     * Se a raiz aceita escrita. O probe é injetado porque a suíte roda no
     * Windows, onde `setWritable(false)` num diretório é inerte — um teste de
     * permissão escrito contra o disco real passaria sem medir nada.
     */
    fun isRootWritable(probe: (File) -> Boolean = { it.canWrite() }): Boolean = probe(root)

    /** Marcador presente. Ver [LINUX_MANAGED_MARKER_NAME]. */
    fun hasMarker(probe: (File) -> Boolean = { it.isFile }): Boolean = probe(markerFile)
}

/**
 * Versão aceitável como nome de diretório.
 *
 * Dígitos e pontos, no máximo quatro componentes. Não é validação de semver: é a
 * defesa contra `..`, `/` e nome vazio, porque o valor vem de uma release remota
 * e vira caminho no disco.
 */
internal fun isValidLinuxVersionName(version: String): Boolean {
    return LINUX_VERSION_PATTERN.matches(version)
}

private val LINUX_VERSION_PATTERN = Regex("""^\d+(\.\d+){0,3}$""")

/**
 * Raiz de dados XDG, com a regra da especificação: **variável relativa é
 * inválida e ignorada**, e não normalizada contra o diretório corrente.
 *
 * A checagem é `startsWith("/")` e não `File.isAbsolute`, porque este último
 * responde pela máquina que executa o código — no Windows, `/home/u` não é
 * absoluto — e a pergunta aqui é sobre a plataforma de destino.
 */
internal fun resolveLinuxDataHome(
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
    userHome: String? = System.getProperty("user.home")
): String? {
    val declared = xdgDataHome?.trim()?.takeIf { it.isNotEmpty() }
    if (declared != null && declared.startsWith("/")) {
        return declared.trimEnd('/')
    }

    val home = userHome?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return "${home.trimEnd('/')}/.local/share"
}

/**
 * Raiz da instalação gerenciada, ou `null` quando nem `XDG_DATA_HOME` nem
 * `user.home` dão um caminho utilizável.
 */
internal fun resolveLinuxInstallRoot(
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
    userHome: String? = System.getProperty("user.home")
): String? {
    val dataHome = resolveLinuxDataHome(xdgDataHome, userHome) ?: return null
    return "$dataHome/$LINUX_INSTALL_DIRECTORY_NAME"
}

/** Layout resolvido do ambiente, ou `null` quando não há raiz. */
internal fun resolveLinuxInstallLayout(
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
    userHome: String? = System.getProperty("user.home")
): LinuxInstallLayout? {
    val root = resolveLinuxInstallRoot(xdgDataHome, userHome) ?: return null
    return LinuxInstallLayout(root)
}

/**
 * Caminho do launcher estável. Sai de `user.home` e **não** da raiz XDG: as duas
 * convenções são independentes, e `XDG_DATA_HOME` apontado para outro disco não
 * muda onde ficam os executáveis do usuário.
 */
internal fun resolveLinuxStableLauncherPath(
    userHome: String? = System.getProperty("user.home")
): String? {
    val home = userHome?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return "${home.trimEnd('/')}/$LINUX_STABLE_LAUNCHER_RELATIVE_PATH"
}

/**
 * O launcher estável: lê `current` e executa a árvore daquela versão.
 *
 * A raiz é **interpolada na geração**, e não recalculada pelo script a partir de
 * `XDG_DATA_HOME`. Recalcular colocaria a regra XDG num segundo dono, e as duas
 * respostas divergiriam no dia em que a variável mudasse — com o efeito de o
 * atalho apontar para uma instalação que não existe. Interpolado, o launcher
 * aponta para a instalação que **foi feita**.
 *
 * `exec` e não subshell: o processo do app tem de substituir o do launcher, ou o
 * `.desktop` e a bandeja passariam a enxergar um `sh` como sendo o aplicativo.
 * `"$@"` repassa `--autostart` e o argumento privado do health check.
 */
internal fun buildLinuxStableLauncherScript(rootPath: String): String {
    val quotedRoot = quoteForPosixShell(rootPath)
    return buildString {
        appendLine("#!/bin/sh")
        appendLine("# Gerado pelo Usage Monitor. Lê a versão ativa e executa a árvore dela.")
        appendLine("set -eu")
        appendLine("root=$quotedRoot")
        appendLine("version=\$(cat \"\$root/$LINUX_CURRENT_FILE_NAME\")")
        appendLine(
            "exec \"\$root/$LINUX_VERSIONS_DIRECTORY_NAME/\$version/" +
                "$LINUX_APP_DIRECTORY_NAME/$LINUX_APP_LAUNCHER_RELATIVE_PATH\" \"\$@\""
        )
    }
}

/**
 * Aspas simples do POSIX: dentro delas nada é interpretado, e a única sequência
 * necessária é fechar, escapar a própria aspa e reabrir.
 *
 * O caminho do `$HOME` é texto do usuário — um apóstrofo num nome de conta é
 * suficiente para transformar interpolação ingênua em execução de comando.
 */
internal fun quoteForPosixShell(value: String): String {
    return "'" + value.replace("'", "'\\''") + "'"
}
