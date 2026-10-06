package com.usagemonitor.data.datasource

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Grava [content] em [target] como os arquivos de segredo do app
 * (`team.json`, `api-keys.json`): intermediário de nome único, permissão só do
 * dono e troca atômica. Usado pelos arquivos do acesso web (#388) e do bot (#387).
 */
internal fun writeSecretFile(target: File, content: String) {
    val parentDir = target.parentFile ?: throw IllegalStateException("Diretório pai de ${target.name} não encontrado.")
    parentDir.mkdirs()
    val tempFile = Files.createTempFile(parentDir.toPath(), target.name, ".tmp").toFile()
    try {
        Files.writeString(tempFile.toPath(), content)
        restrictToOwnerReadWrite(tempFile.toPath())
        try {
            Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        restrictToOwnerReadWrite(target.toPath())
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}

/**
 * Lê [file] com [decode]; arquivo ilegível vai para `.corrupt` e devolve `null`,
 * a mesma regra de `LocalTeamSettingsDataSource`: nunca sobrescrever o original.
 */
internal fun <T> readSecretFile(file: File, decode: (String) -> T): T? {
    if (!file.isFile) {
        return null
    }
    return runCatching { decode(file.readText()) }.getOrElse {
        runCatching {
            Files.move(file.toPath(), File(file.parentFile, "${file.name}.corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        null
    }
}
