package com.usagemonitor.update

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/** Teto de entradas. O tar real da v37.0.0 tem 197. */
internal const val MAX_TARBALL_ENTRIES = 10_000

/** Teto do conteúdo expandido. O tar real expande para ~330 MB. */
internal const val MAX_TARBALL_EXPANDED_BYTES = 1L shl 30

/**
 * Folga exigida além do conteúdo expandido.
 *
 * Encher o disco no meio de uma extração não deixa só a atualização pela metade:
 * o `usage-history.db` e o `team.json` moram no mesmo home.
 */
internal const val TARBALL_FREE_SPACE_SLACK_BYTES = 64L * 1024 * 1024

/**
 * O que a inspeção do arquivo apurou antes de escrever qualquer byte.
 */
internal data class TarballInspection(
    val entryCount: Int,
    val expandedBytes: Long
)

/**
 * Extrai o `.tar.gz` da release para o staging, com todas as guardas.
 *
 * **Duas passadas, e não uma.** A primeira lê o arquivo inteiro sem escrever
 * nada e reprova tudo que não serve; só então a segunda extrai. Uma passada só
 * deixaria metade da árvore no disco antes de encontrar a entrada ruim, e o
 * staging pela metade é exatamente o estado que o mecanismo não sabe
 * distinguir de uma extração completa. Descomprimir 125 MB duas vezes custa
 * segundos; recuperar de um staging parcial custa uma versão.
 *
 * Nada aqui usa o binário `tar`: o update não pode depender do que estiver no
 * `PATH` da máquina.
 */
internal class TarballExtractor(
    private val expectedRootName: String = LINUX_APP_DIRECTORY_NAME,
    private val requiredLauncherPath: String =
        "$LINUX_APP_DIRECTORY_NAME/$LINUX_APP_LAUNCHER_RELATIVE_PATH",
    private val maxEntries: Int = MAX_TARBALL_ENTRIES,
    private val maxExpandedBytes: Long = MAX_TARBALL_EXPANDED_BYTES,
    private val freeSpaceSlackBytes: Long = TARBALL_FREE_SPACE_SLACK_BYTES,
    /**
     * Espaço livre do volume de destino. Injetável porque a alternativa seria
     * encher o disco de quem roda a suíte para exercitar a guarda.
     */
    private val freeSpaceProvider: (File) -> Long = { it.usableSpace },
    /**
     * Aplicação do modo Unix. Injetável pela mesma razão do probe de permissão
     * do layout: `Files.setPosixFilePermissions` **lança**
     * `UnsupportedOperationException` no Windows, e um teste que dependesse dela
     * não afirmaria nada na máquina em que a suíte roda.
     */
    private val modeApplier: (File, Int) -> Unit = ::applyPosixFileMode
) {

    /**
     * Lê o arquivo inteiro e reprova o que não serve. Não escreve nada.
     *
     * Separada de [extract] porque é ela que decide, e um teste que a chame
     * sozinha prova a decisão sem produzir arquivo nenhum.
     */
    fun inspect(archive: File): TarballInspection {
        if (!archive.isFile) {
            throw IllegalStateException("Update archive ${archive.name} is not a file.")
        }

        var entryCount = 0
        var expandedBytes = 0L
        var sawLauncher = false
        val seenNames = HashSet<String>()

        openTar(archive).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entryCount += 1
                if (entryCount > maxEntries) {
                    throw IllegalStateException(
                        "Update archive has more than $maxEntries entries."
                    )
                }

                val name = normalizedEntryName(entry)
                if (!seenNames.add(name)) {
                    // Duplicata não é redundância: a segunda entrada sobrescreve
                    // a primeira, e é assim que um arquivo já conferido vira
                    // outro sem que a contagem ou o tamanho mudem.
                    throw IllegalStateException("Update archive repeats the entry '$name'.")
                }

                requireSupportedType(entry, name)
                requireExpectedRoot(name)

                if (entry.isFile) {
                    expandedBytes += entry.size
                    if (expandedBytes > maxExpandedBytes) {
                        throw IllegalStateException(
                            "Update archive expands to more than $maxExpandedBytes bytes."
                        )
                    }
                    if (name == requiredLauncherPath) {
                        sawLauncher = true
                    }
                }
            }
        }

        if (entryCount == 0) {
            throw IllegalStateException("Update archive is empty.")
        }
        if (!sawLauncher) {
            // Sem o launcher a árvore promovida não roda, e a falha só
            // apareceria depois do swap — quando o app já não está de pé para
            // reportá-la.
            throw IllegalStateException(
                "Update archive does not contain '$requiredLauncherPath'."
            )
        }

        return TarballInspection(entryCount = entryCount, expandedBytes = expandedBytes)
    }

    /**
     * Inspeciona, confere espaço e extrai para [targetDirectory].
     *
     * O destino tem de **não existir**: extrair por cima de um staging anterior
     * misturaria duas versões, e a mistura passa em toda contagem.
     */
    fun extract(archive: File, targetDirectory: File): TarballInspection {
        if (targetDirectory.exists()) {
            throw IllegalStateException(
                "Staging directory ${targetDirectory.name} already exists."
            )
        }

        val inspection = inspect(archive)

        val volume = targetDirectory.parentFile ?: targetDirectory
        volume.mkdirs()
        val required = inspection.expandedBytes + freeSpaceSlackBytes
        val available = freeSpaceProvider(volume)
        if (available in 1 until required) {
            // Zero é "não sei": `usableSpace` devolve 0 para caminho que não
            // existe, e recusar por isso reprovaria toda primeira instalação.
            throw IllegalStateException(
                "Not enough free space for the update: $required bytes needed, $available available."
            )
        }

        if (!targetDirectory.mkdirs()) {
            throw IllegalStateException(
                "Could not create the staging directory ${targetDirectory.absolutePath}."
            )
        }

        val targetRoot = targetDirectory.canonicalFile
        openTar(archive).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = normalizedEntryName(entry)
                val destination = resolveInside(targetRoot, name)

                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                // O modo vem do tar. Um `chmod -R +x` daria bit de execução a
                // toda a árvore, inclusive aos ~180 arquivos de dados.
                modeApplier(destination, entry.mode)
            }
        }

        return inspection
    }

    private fun openTar(archive: File): TarArchiveInputStream {
        return TarArchiveInputStream(GZIPInputStream(BufferedInputStream(archive.inputStream())))
    }

    /**
     * Rejeita tudo que não seja arquivo comum ou diretório.
     *
     * Symlink e hardlink saem porque um link apontando para fora do staging
     * transforma a escrita seguinte — ou a leitura do próprio app — num acesso a
     * caminho arbitrário. FIFO e device saem porque nada no `.tar.gz` de um
     * app-image tem motivo para ser um deles, e abrir um FIFO para escrita
     * bloqueia até alguém abrir a outra ponta: a extração ficaria pendurada
     * para sempre, sem erro nenhum.
     */
    private fun requireSupportedType(entry: TarArchiveEntry, name: String) {
        val unsupported = entry.isSymbolicLink ||
            entry.isLink ||
            entry.isFIFO ||
            entry.isCharacterDevice ||
            entry.isBlockDevice ||
            (!entry.isFile && !entry.isDirectory)
        if (unsupported) {
            throw IllegalStateException("Update archive entry '$name' is not a regular file or directory.")
        }
    }

    private fun requireExpectedRoot(name: String) {
        val firstSegment = name.substringBefore('/')
        if (firstSegment != expectedRootName) {
            throw IllegalStateException(
                "Update archive entry '$name' is outside the expected '$expectedRootName' root."
            )
        }
    }

    private fun normalizedEntryName(entry: TarArchiveEntry): String =
        normalizedTarEntryName(entry.name)

    /**
     * Resolve o destino e afirma que ele ficou **dentro** do staging.
     *
     * Redundante com a validação de nome de propósito: são duas perguntas
     * diferentes — uma sobre o texto que veio no arquivo, outra sobre o caminho
     * que o sistema de arquivos produziu — e a segunda é a que pega o caso que a
     * primeira não previu.
     */
    private fun resolveInside(targetRoot: File, name: String): File {
        val destination = File(targetRoot, name.replace('/', File.separatorChar))
        val canonical = destination.canonicalFile
        val prefix = targetRoot.path + File.separator
        if (!canonical.path.startsWith(prefix)) {
            throw IllegalStateException("Update archive entry '$name' resolves outside the staging directory.")
        }
        return destination
    }
}

/**
 * Nome de entrada em forma POSIX conferida.
 *
 * Caminho absoluto, `..`, barra invertida e nome vazio são recusados **aqui** e
 * não na escrita: a checagem por caminho canônico da segunda passada é a rede de
 * segurança, e depender dela sozinha significaria descobrir o problema com
 * metade da árvore já no disco.
 *
 * **Função top-level e não método privado** porque é a única forma de exercitar
 * a guarda de barra invertida: o `TarArchiveEntry` do Commons Compress
 * **converte `\` em `/`** ao ser construído numa máquina Windows, e a suíte roda
 * no Windows — um teste que montasse o tar com esse nome nunca chegaria a
 * exercitar a checagem. Medido: o caso reprovava com a mensagem de `..`.
 */
internal fun normalizedTarEntryName(rawName: String): String {
    val raw = rawName.trim()
    if (raw.isEmpty()) {
        throw IllegalStateException("Update archive contains an entry with an empty name.")
    }
    if (raw.startsWith("/")) {
        throw IllegalStateException("Update archive entry '$raw' has an absolute path.")
    }
    if (raw.contains('\\')) {
        // Barra invertida é caractere válido num nome POSIX e vira separador no
        // Windows: aceitá-la deixaria a checagem de `..` olhando para os
        // segmentos errados.
        throw IllegalStateException("Update archive entry '$raw' contains a backslash.")
    }
    if (raw.split('/').any { it == ".." }) {
        throw IllegalStateException("Update archive entry '$raw' escapes the staging directory.")
    }

    return raw.trimEnd('/')
}

/**
 * Aplica o modo Unix do tar ao arquivo extraído.
 *
 * No Windows `Files.setPosixFilePermissions` lança `UnsupportedOperationException`,
 * e isso não é erro: a extração de um tarball Linux numa máquina Windows só
 * acontece em teste. A falha é engolida, e quem prova que o modo certo chegou
 * até aqui é o applier injetado da suíte.
 */
internal fun applyPosixFileMode(file: File, mode: Int) {
    runCatching {
        Files.setPosixFilePermissions(file.toPath(), posixPermissionsOf(mode))
    }
}

/** Os nove bits de permissão do modo Unix. */
internal fun posixPermissionsOf(mode: Int): Set<PosixFilePermission> {
    val permissions = HashSet<PosixFilePermission>()
    if (mode and 0b100_000_000 != 0) permissions.add(PosixFilePermission.OWNER_READ)
    if (mode and 0b010_000_000 != 0) permissions.add(PosixFilePermission.OWNER_WRITE)
    if (mode and 0b001_000_000 != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE)
    if (mode and 0b000_100_000 != 0) permissions.add(PosixFilePermission.GROUP_READ)
    if (mode and 0b000_010_000 != 0) permissions.add(PosixFilePermission.GROUP_WRITE)
    if (mode and 0b000_001_000 != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE)
    if (mode and 0b000_000_100 != 0) permissions.add(PosixFilePermission.OTHERS_READ)
    if (mode and 0b000_000_010 != 0) permissions.add(PosixFilePermission.OTHERS_WRITE)
    if (mode and 0b000_000_001 != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE)
    return permissions
}
