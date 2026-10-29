package com.usagemonitor.terminal

import org.junit.Assume
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path

// Windows sem SeCreateSymbolicLinkPrivilege (sem Modo de Desenvolvedor nem admin) recusa o link com
// FileSystemException; o caso vira ignorado, nunca falha nem verde vazio (#519). Caminho que ja existe
// e defeito do proprio teste e propaga.
internal fun createSymbolicLinkOrSkip(
    link: Path,
    target: Path,
    create: (Path, Path) -> Unit = { from, to -> Files.createSymbolicLink(from, to) },
) {
    try {
        create(link, target)
    } catch (exception: FileAlreadyExistsException) {
        throw exception
    } catch (exception: FileSystemException) {
        Assume.assumeNoException("symbolic link refused by the environment", exception)
    } catch (exception: UnsupportedOperationException) {
        Assume.assumeNoException("symbolic links unsupported by the file system", exception)
    }
}
