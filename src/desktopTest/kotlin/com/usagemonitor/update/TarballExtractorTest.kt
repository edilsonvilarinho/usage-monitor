package com.usagemonitor.update

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * As guardas da extração.
 *
 * O tar é construído aqui, entrada a entrada, porque o que se quer exercitar são
 * os arquivos que **não** existem em release nenhuma: symlink apontando para
 * fora, `..` no nome, FIFO, duplicata. Um fixture gravado no repositório não
 * poderia conter nenhum deles sem ser um arquivo malicioso versionado.
 */
class TarballExtractorTest {

    private val workDirectory: File = Files.createTempDirectory("usage-monitor-tar").toFile()
    private val archive = File(workDirectory, "usage-monitor_39.0.0_linux_x64.tar.gz")
    private val staging = File(workDirectory, "39.0.0.staging")

    private val appliedModes = mutableListOf<Pair<String, Int>>()

    @AfterTest
    fun cleanUp() {
        workDirectory.deleteRecursively()
    }

    @Test
    fun `a well formed archive is extracted whole`() {
        writeArchive(validEntries())

        val inspection = extractor().extract(archive, staging)

        assertEquals(5, inspection.entryCount)
        // 10 bytes do launcher + 12 do jar. Diretório não conta tamanho.
        assertEquals(22L, inspection.expandedBytes)
        assertTrue(File(staging, "Usage Monitor/bin/Usage Monitor").isFile)
        assertTrue(File(staging, "Usage Monitor/lib/app.jar").isFile)
        assertTrue(File(staging, "Usage Monitor/lib").isDirectory)
    }

    /**
     * `chmod -R +x` daria bit de execução aos ~180 arquivos de dados do
     * app-image. O modo vem do tar, arquivo a arquivo.
     */
    @Test
    fun `unix modes come from the archive, not from a blanket chmod`() {
        writeArchive(validEntries())

        extractor().extract(archive, staging)

        assertEquals(0b111_101_101, modeOf("Usage Monitor/bin/Usage Monitor"))
        assertEquals(0b110_100_100, modeOf("Usage Monitor/lib/app.jar"))
    }

    private fun modeOf(relativePath: String): Int {
        val suffix = relativePath.replace('/', File.separatorChar)
        return appliedModes.first { it.first.endsWith(suffix) }.second
    }

    @Test
    fun `a dot dot segment is refused`() {
        writeArchive(
            validEntries() + fileEntry("Usage Monitor/../../etc/passwd", "x", 0b110_100_100)
        )

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("escapes the staging directory"), error.message!!)
    }

    @Test
    fun `an absolute path is refused`() {
        writeArchive(validEntries() + fileEntry("/etc/passwd", "x", 0b110_100_100))

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("absolute path"), error.message!!)
    }

    /**
     * Barra invertida é caractere válido num nome POSIX e vira separador no
     * Windows: aceitá-la deixaria a checagem de `..` olhando para os segmentos
     * errados.
     *
     * O caso ataca a função direto, e não o arquivo: o `TarArchiveEntry` do
     * Commons Compress **converte `\` em `/`** ao ser construído no Windows, e a
     * suíte roda no Windows — montar o tar com esse nome nunca exercitaria a
     * checagem. Medido: o caso reprovava com a mensagem de `..`.
     */
    @Test
    fun `a backslash in the entry name is refused`() {
        val error = assertFailsWith<IllegalStateException> {
            normalizedTarEntryName("""Usage Monitor\..\..\x""")
        }
        assertTrue(error.message!!.contains("backslash"), error.message!!)
    }

    @Test
    fun `entry names are validated before anything is written`() {
        assertEquals("Usage Monitor/bin", normalizedTarEntryName("  Usage Monitor/bin/  "))
        assertFailsWith<IllegalStateException> { normalizedTarEntryName("   ") }
        assertFailsWith<IllegalStateException> { normalizedTarEntryName("/etc/passwd") }
        assertFailsWith<IllegalStateException> { normalizedTarEntryName("a/../../b") }
        // `..` como parte de um nome não é o segmento `..`.
        assertEquals("Usage Monitor/..x", normalizedTarEntryName("Usage Monitor/..x"))
    }

    @Test
    fun `a symlink is refused`() {
        writeArchive(validEntries() + linkEntry("Usage Monitor/atalho", "/etc/passwd", TarConstants.LF_SYMLINK))

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("not a regular file or directory"), error.message!!)
    }

    @Test
    fun `a hard link is refused`() {
        writeArchive(
            validEntries() + linkEntry("Usage Monitor/copia", "Usage Monitor/lib/app.jar", TarConstants.LF_LINK)
        )

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("not a regular file or directory"), error.message!!)
    }

    /**
     * Abrir um FIFO para escrita bloqueia até alguém abrir a outra ponta: a
     * extração ficaria pendurada para sempre, sem erro nenhum.
     */
    @Test
    fun `a fifo is refused`() {
        writeArchive(validEntries() + linkEntry("Usage Monitor/cano", "", TarConstants.LF_FIFO))

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("not a regular file or directory"), error.message!!)
    }

    /**
     * A segunda entrada sobrescreve a primeira: é assim que um arquivo já
     * conferido vira outro sem que contagem ou tamanho mudem.
     */
    @Test
    fun `a repeated entry is refused`() {
        writeArchive(validEntries() + fileEntry("Usage Monitor/lib/app.jar", "outro", 0b110_100_100))

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("repeats the entry"), error.message!!)
    }

    @Test
    fun `a second root is refused`() {
        writeArchive(validEntries() + fileEntry("Outra Raiz/x", "x", 0b110_100_100))

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("outside the expected"), error.message!!)
    }

    /**
     * Sem o launcher a árvore promovida não roda, e a falha só apareceria depois
     * do swap — quando o app já não está de pé para reportá-la.
     */
    @Test
    fun `an archive without the launcher is refused`() {
        writeArchive(
            listOf(
                directoryEntry("Usage Monitor/"),
                fileEntry("Usage Monitor/lib/app.jar", "jar", 0b110_100_100)
            )
        )

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("does not contain"), error.message!!)
    }

    @Test
    fun `an empty archive is refused`() {
        writeArchive(emptyList())

        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("empty"), error.message!!)
    }

    @Test
    fun `the entry ceiling stops the archive`() {
        writeArchive(validEntries())

        val error = assertFailsWith<IllegalStateException> {
            extractor(maxEntries = 3).inspect(archive)
        }
        assertTrue(error.message!!.contains("more than 3 entries"), error.message!!)
    }

    @Test
    fun `the expanded size ceiling stops the archive`() {
        writeArchive(validEntries())

        val error = assertFailsWith<IllegalStateException> {
            extractor(maxExpandedBytes = 5).inspect(archive)
        }
        assertTrue(error.message!!.contains("expands to more than"), error.message!!)
    }

    /** Os tetos default são parte do contrato, não detalhe da instância de teste. */
    @Test
    fun `the shipped ceilings are the ones the plan states`() {
        assertEquals(10_000, MAX_TARBALL_ENTRIES)
        assertEquals(1024L * 1024 * 1024, MAX_TARBALL_EXPANDED_BYTES)
        assertEquals(64L * 1024 * 1024, TARBALL_FREE_SPACE_SLACK_BYTES)
    }

    /**
     * Encher o disco no meio da extração não deixa só a atualização pela metade:
     * o `usage-history.db` e o `team.json` moram no mesmo home.
     */
    @Test
    fun `not enough free space stops the extraction before it starts`() {
        writeArchive(validEntries())

        val error = assertFailsWith<IllegalStateException> {
            extractor(freeSpace = 1_000).extract(archive, staging)
        }
        assertTrue(error.message!!.contains("Not enough free space"), error.message!!)
        assertFalse(staging.exists(), "o staging não pode existir depois de uma recusa")
    }

    /** `usableSpace` devolve zero para caminho desconhecido: zero é "não sei". */
    @Test
    fun `an unknown free space does not block the extraction`() {
        writeArchive(validEntries())

        extractor(freeSpace = 0).extract(archive, staging)

        assertTrue(File(staging, "Usage Monitor/bin/Usage Monitor").isFile)
    }

    /**
     * Extrair por cima de um staging anterior misturaria duas versões, e a
     * mistura passa em toda contagem.
     */
    @Test
    fun `an existing staging directory is refused`() {
        writeArchive(validEntries())
        staging.mkdirs()

        val error = assertFailsWith<IllegalStateException> { extractor().extract(archive, staging) }
        assertTrue(error.message!!.contains("already exists"), error.message!!)
    }

    /**
     * A recusa acontece na primeira passada, antes de qualquer escrita: um
     * staging pela metade é indistinguível de uma extração completa.
     */
    @Test
    fun `a rejected archive leaves nothing on disk`() {
        writeArchive(validEntries() + linkEntry("Usage Monitor/atalho", "/etc/passwd", TarConstants.LF_SYMLINK))

        assertFailsWith<IllegalStateException> { extractor().extract(archive, staging) }

        assertFalse(staging.exists())
    }

    @Test
    fun `a missing archive is refused`() {
        val error = assertFailsWith<IllegalStateException> { extractor().inspect(archive) }
        assertTrue(error.message!!.contains("is not a file"), error.message!!)
    }

    @Test
    fun `posix permissions map every mode bit`() {
        assertEquals(9, posixPermissionsOf(0b111_111_111).size)
        assertEquals(0, posixPermissionsOf(0).size)
        assertEquals(
            setOf(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
            ),
            posixPermissionsOf(0b111_101_101)
        )
    }

    // --- fixtures ---

    private fun extractor(
        maxEntries: Int = MAX_TARBALL_ENTRIES,
        maxExpandedBytes: Long = MAX_TARBALL_EXPANDED_BYTES,
        freeSpace: Long = Long.MAX_VALUE
    ): TarballExtractor {
        return TarballExtractor(
            maxEntries = maxEntries,
            maxExpandedBytes = maxExpandedBytes,
            freeSpaceProvider = { freeSpace },
            modeApplier = { file, mode -> appliedModes.add(file.path to mode) }
        )
    }

    private fun validEntries(): List<TarFixture> = listOf(
        directoryEntry("Usage Monitor/"),
        directoryEntry("Usage Monitor/bin/"),
        fileEntry("Usage Monitor/bin/Usage Monitor", "#!/bin/sh\n", 0b111_101_101),
        directoryEntry("Usage Monitor/lib/"),
        fileEntry("Usage Monitor/lib/app.jar", "jar\nconteudo", 0b110_100_100)
    )

    /**
     * O conteúdo viaja ao lado da entrada e não dentro dela: `TarArchiveEntry`
     * compara por **nome**, e um mapa lateral devolveria o conteúdo errado
     * justamente no caso da duplicata — que é um dos que este arquivo existe
     * para exercitar.
     */
    private class TarFixture(val entry: TarArchiveEntry, val content: ByteArray?)

    private fun directoryEntry(name: String): TarFixture {
        return TarFixture(TarArchiveEntry(name, true).apply { mode = 0b111_101_101 }, null)
    }

    private fun fileEntry(name: String, content: String, mode: Int): TarFixture {
        val bytes = content.toByteArray()
        val entry = TarArchiveEntry(name, true).apply {
            this.mode = mode
            size = bytes.size.toLong()
        }
        return TarFixture(entry, bytes)
    }

    private fun linkEntry(name: String, linkTarget: String, type: Byte): TarFixture {
        return TarFixture(TarArchiveEntry(name, type).apply { linkName = linkTarget }, null)
    }

    private fun writeArchive(fixtures: List<TarFixture>) {
        archive.parentFile.mkdirs()
        TarArchiveOutputStream(GZIPOutputStream(archive.outputStream())).use { output ->
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            fixtures.forEach { fixture ->
                output.putArchiveEntry(fixture.entry)
                fixture.content?.let { output.write(it) }
                output.closeArchiveEntry()
            }
        }
    }
}
