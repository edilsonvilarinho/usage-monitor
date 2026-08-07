package com.usagemonitor.data.datasource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Restringe o ficheiro a leitura/escrita apenas pelo dono (equivalente a chmod 600).
 * Sem efeito em sistemas de ficheiros sem suporte a atributos POSIX (ex.: Windows/NTFS),
 * onde a ACL herdada do perfil do utilizador já limita o acesso a outros perfis.
 */
internal fun restrictToOwnerReadWrite(path: Path) {
    if (!Files.exists(path)) {
        return
    }
    if (!path.fileSystem.supportedFileAttributeViews().contains("posix")) {
        return
    }
    runCatching {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
    }
}
