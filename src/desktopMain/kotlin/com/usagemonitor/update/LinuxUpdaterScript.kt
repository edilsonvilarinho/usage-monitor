package com.usagemonitor.update

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/** Recurso versionado, empacotado no jar. */
internal const val LINUX_UPDATER_RESOURCE_PATH = "/update/linux-updater.sh"

internal const val LINUX_UPDATER_SCRIPT_NAME = "linux-updater.sh"

/**
 * Escreve o `linux-updater.sh` no disco, em modo `0700`.
 *
 * O script é **recurso do app** e não texto montado em tempo de execução: é
 * assim que ele fica versionado com o código, revisável no diff e testável como
 * arquivo. Montá-lo por concatenação faria cada caminho virar interpolação, que
 * é exatamente o que a passagem por argumento existe para evitar.
 *
 * `0700` porque ele é executado e não deve ser legível nem gravável por mais
 * ninguém: um script executado no encerramento do app, com permissão de escrita
 * para o grupo, é um caminho de execução de código aberto a quem estiver na
 * mesma máquina.
 *
 * **Um script sobrado é apagado antes**, e não sobrescrito: o `rm -f "$0"` da
 * última linha pode não ter rodado, e reescrever por cima manteria a permissão e
 * o inode antigos.
 */
internal fun materializeLinuxUpdaterScript(
    directory: File,
    modeApplier: (File) -> Unit = ::applyOwnerOnlyExecutableMode
): File {
    val script = File(directory, LINUX_UPDATER_SCRIPT_NAME)
    val body = LinuxUpdaterScriptResource::class.java.getResourceAsStream(LINUX_UPDATER_RESOURCE_PATH)
        ?.use { input -> input.readBytes() }
        ?: throw IllegalStateException("Resource $LINUX_UPDATER_RESOURCE_PATH is missing from the build.")

    directory.mkdirs()
    script.delete()
    script.writeBytes(body)
    modeApplier(script)
    return script
}

/** Âncora de classloader. O `object` não tem estado: só empresta o pacote. */
private object LinuxUpdaterScriptResource

/**
 * Comando exato do updater.
 *
 * `/bin/sh` explícito e argumentos **separados**: o `ProcessBuilder` entrega
 * cada elemento como um `argv[]`, então nenhum caminho passa por interpretação
 * de shell. Montar uma linha só — ainda que com aspas — devolveria ao shell a
 * chance de reinterpretar espaço, apóstrofo e cifrão que vêm de nome de pasta.
 *
 * [previousVersion] nula vira `"-"` e não string vazia: argumento vazio existe
 * na contagem de `$#` mas some numa leitura desatenta, e o script confere `$#`.
 */
internal fun linuxUpdaterCommand(
    script: File,
    rootPath: String,
    version: String,
    previousVersion: String?,
    previousPid: Long,
    ackToken: String,
    launcherPath: String,
    ackFilePath: String,
    receiptFilePath: String,
    /**
     * O mesmo arquivo que [launchDetachedProcess] já usa para a saída do
     * script — passado por argumento porque o script não tem outro jeito de
     * conhecê-lo, e sem ele a versão relançada (passo 6, `--update-ack`) e o
     * relançamento de rollback (passo 9) escreviam em `/dev/null`. Um crash
     * ali não deixava rastro nenhum: foi assim que o health-timeout medido ao
     * vivo numa Bazzite real (issue #118) ficou sem causa por várias
     * tentativas — o processo relançado morria antes do ACK, e não havia
     * onde olhar.
     */
    logFilePath: String
): List<String> {
    return listOf(
        "/bin/sh",
        script.absolutePath,
        rootPath,
        version,
        previousVersion?.takeIf { it.isNotBlank() } ?: "-",
        previousPid.toString(),
        ackToken,
        launcherPath,
        ackFilePath,
        receiptFilePath,
        logFilePath
    )
}

/**
 * `0700`. Falha engolida pelo mesmo motivo de [applyPosixFileMode]: no Windows a
 * chamada lança, e ali este script nunca é executado.
 */
internal fun applyOwnerOnlyExecutableMode(file: File) {
    runCatching {
        Files.setPosixFilePermissions(
            file.toPath(),
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            )
        )
    }
}
