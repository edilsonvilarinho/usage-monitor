package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateArchitecture
import com.usagemonitor.domain.entity.AppUpdateArtifact
import com.usagemonitor.domain.entity.AppUpdateArtifactKind
import com.usagemonitor.domain.entity.AppUpdatePlatform
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Os testes usam `runBlocking` e não `runTest` de propósito: o teto de tempo por
 * requisição é medido com atraso **real** no engine, e o relógio virtual do
 * `runTest` pula exatamente o que se quer medir.
 */
class UpdateArtifactDownloaderTest {

    private val updatesDirectory: File = Files.createTempDirectory("usage-monitor-updates").toFile()
    private val payload: ByteArray = ByteArray(200_000) { index -> (index % 251).toByte() }

    @AfterTest
    fun cleanUp() {
        updatesDirectory.deleteRecursively()
    }

    @Test
    fun `downloads, verifies and moves the artifact into place`() = runBlocking {
        var requests = 0
        val downloader = downloader { requests++; respondFull() }

        val file = downloader.download(artifact())

        assertEquals(1, requests)
        assertEquals(File(updatesDirectory, ASSET_NAME), file)
        assertContentEquals(payload, file.readBytes())
        assertFalse(File(updatesDirectory, "$ASSET_NAME.part").exists(), "o parcial deve sumir")
    }

    @Test
    fun `reports progress up to the declared total`() = runBlocking {
        val downloader = downloader { respondFull() }
        val seen = mutableListOf<Pair<Long, Long?>>()

        downloader.download(artifact()) { downloaded, total -> seen += downloaded to total }

        assertTrue(seen.isNotEmpty())
        assertEquals(payload.size.toLong() to payload.size.toLong(), seen.last())
        // Progresso monotônico: um valor que anda para trás na tela é pior que
        // nenhum.
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
    }

    /**
     * Regressão do defeito que matava a funcionalidade: o cliente compartilhado
     * do app tem `requestTimeoutMillis = 20_000`, e no Ktor esse teto cobre a
     * leitura do corpo inteiro. Aqui o cliente está configurado com 300 ms e o
     * engine demora 1.200 ms — sem a sobrescrita por requisição, este teste
     * falha.
     */
    @Test
    fun `per-request timeout override survives a body slower than the client timeout`() = runBlocking {
        val downloader = downloader(clientRequestTimeoutMillis = 300) {
            delay(1_200)
            respondFull()
        }

        val file = downloader.download(artifact())

        assertContentEquals(payload, file.readBytes())
    }

    @Test
    fun `resumes from the partial file when the server honors the range`() = runBlocking {
        val alreadyOnDisk = 120_000
        writePartial(alreadyOnDisk)
        var seenRange: String? = null

        val downloader = downloader { request ->
            seenRange = request.headers["Range"]
            respond(
                ByteReadChannel(payload.copyOfRange(alreadyOnDisk, payload.size)),
                HttpStatusCode.PartialContent
            )
        }

        val file = downloader.download(artifact())

        assertEquals("bytes=$alreadyOnDisk-", seenRange)
        assertContentEquals(payload, file.readBytes())
    }

    @Test
    fun `rewrites from zero when the server ignores the range and answers 200`() = runBlocking {
        writePartial(120_000)

        val downloader = downloader { respondFull() }

        val file = downloader.download(artifact())

        // Anexar um corpo inteiro ao parcial produziria um arquivo remendado.
        assertContentEquals(payload, file.readBytes())
    }

    @Test
    fun `discards a partial bigger than the declared size instead of resuming it`() = runBlocking {
        val partFile = File(updatesDirectory, "$ASSET_NAME.part")
        updatesDirectory.mkdirs()
        partFile.writeBytes(ByteArray(payload.size + 4_096))
        var seenRange: String? = "not-read"

        val downloader = downloader { request ->
            seenRange = request.headers["Range"]
            respondFull()
        }

        val file = downloader.download(artifact())

        assertEquals(null, seenRange, "parcial de outra release não pode virar retomada")
        assertContentEquals(payload, file.readBytes())
    }

    @Test
    fun `does not touch the network when the final file is already valid`() = runBlocking {
        updatesDirectory.mkdirs()
        File(updatesDirectory, ASSET_NAME).writeBytes(payload)
        var requests = 0

        val downloader = downloader { requests++; respondFull() }

        val file = downloader.download(artifact())

        assertEquals(0, requests)
        assertContentEquals(payload, file.readBytes())
    }

    @Test
    fun `redownloads when the file on disk does not match the digest`() = runBlocking {
        updatesDirectory.mkdirs()
        File(updatesDirectory, ASSET_NAME).writeBytes(ByteArray(payload.size) { 7 })
        var requests = 0

        val downloader = downloader { requests++; respondFull() }

        val file = downloader.download(artifact())

        assertEquals(1, requests)
        assertContentEquals(payload, file.readBytes())
    }

    @Test
    fun `sha-256 mismatch fails and deletes the partial`() = runBlocking {
        val downloader = downloader { respondFull() }

        assertFailsWith<IllegalStateException> {
            downloader.download(artifact(sha256 = "00".repeat(32)))
        }

        // Manter o parcial faria a tentativa seguinte retomar de cima do que já
        // se sabe corrompido, para sempre.
        assertFalse(File(updatesDirectory, "$ASSET_NAME.part").exists())
        assertFalse(File(updatesDirectory, ASSET_NAME).exists())
    }

    @Test
    fun `size mismatch fails before the file is published`() = runBlocking {
        val downloader = downloader { respondFull() }

        assertFailsWith<IllegalStateException> {
            downloader.download(artifact(sizeBytes = payload.size + 1L))
        }

        assertFalse(File(updatesDirectory, ASSET_NAME).exists())
    }

    @Test
    fun `http error fails without publishing anything`() = runBlocking {
        val downloader = downloader { respond("nope", HttpStatusCode.NotFound) }

        assertFailsWith<IllegalStateException> { downloader.download(artifact()) }

        assertFalse(File(updatesDirectory, ASSET_NAME).exists())
    }

    @Test
    fun `artifact without a digest is rejected before any request`() = runBlocking {
        var requests = 0
        val downloader = downloader { requests++; respondFull() }

        assertFailsWith<IllegalStateException> { downloader.download(artifact(sha256 = null)) }

        assertEquals(0, requests)
    }

    @Test
    fun `non-https url is rejected before any request`() = runBlocking {
        var requests = 0
        val downloader = downloader { requests++; respondFull() }

        assertFailsWith<IllegalStateException> {
            downloader.download(artifact(url = "http://github.com/o/r/releases/download/v1/$ASSET_NAME"))
        }

        assertEquals(0, requests)
    }

    @Test
    fun `untrusted host is rejected before any request`() = runBlocking {
        var requests = 0
        val downloader = downloader { requests++; respondFull() }

        assertFailsWith<IllegalStateException> {
            downloader.download(artifact(url = "https://evil.test/o/r/releases/download/v1/$ASSET_NAME"))
        }

        assertEquals(0, requests)
    }

    @Test
    fun `asset name with a path separator is rejected before any request`() = runBlocking {
        var requests = 0
        val downloader = downloader { requests++; respondFull() }

        assertFailsWith<IllegalStateException> {
            downloader.download(artifact(assetName = "../../evil.exe"))
        }

        assertEquals(0, requests)
    }

    @Test
    fun `prune keeps the current artifact and removes the rest`() {
        updatesDirectory.mkdirs()
        File(updatesDirectory, ASSET_NAME).writeBytes(payload)
        File(updatesDirectory, "$ASSET_NAME.part").writeBytes(ByteArray(10))
        File(updatesDirectory, "UsageMonitor-Setup-36.0.0.exe").writeBytes(ByteArray(10))
        File(updatesDirectory, "UsageMonitor-Setup-35.0.0.exe.part").writeBytes(ByteArray(10))

        downloader { respondFull() }.prune(ASSET_NAME)

        val remaining = updatesDirectory.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf(ASSET_NAME, "$ASSET_NAME.part"), remaining)
    }

    @Test
    fun `pruning without a name to keep empties the directory`() {
        updatesDirectory.mkdirs()
        File(updatesDirectory, ASSET_NAME).writeBytes(payload)
        File(updatesDirectory, "$ASSET_NAME.part").writeBytes(ByteArray(10))
        File(updatesDirectory, "UsageMonitor-Setup-36.0.0.exe").writeBytes(ByteArray(10))

        // É a poda de abertura: o recibo já disse que este artefato foi
        // aplicado, então não há nome a preservar.
        pruneUpdateArtifacts(updatesDirectory, keepAssetName = null)

        assertEquals(emptyList(), updatesDirectory.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `pruning an absent directory is not an error`() {
        val absent = File(updatesDirectory, "nao-existe")

        // A poda roda em toda abertura, inclusive na de quem nunca baixou nada.
        pruneUpdateArtifacts(absent, keepAssetName = null)

        assertEquals(false, absent.exists())
    }

    // --- infraestrutura -----------------------------------------------------

    private fun MockRequestHandleScope.respondFull(): HttpResponseData {
        return respond(
            ByteReadChannel(payload),
            HttpStatusCode.OK,
            headersOf("Content-Length", payload.size.toString())
        )
    }

    private fun writePartial(byteCount: Int) {
        updatesDirectory.mkdirs()
        File(updatesDirectory, "$ASSET_NAME.part").writeBytes(payload.copyOfRange(0, byteCount))
    }

    private fun downloader(
        clientRequestTimeoutMillis: Long = 20_000,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): UpdateArtifactDownloader {
        val client = HttpClient(MockEngine { request -> handler(request) }) {
            // Mesmo desenho do cliente de produção: um teto curto, adequado a
            // resposta de API e impossível para 120 MB.
            install(HttpTimeout) {
                requestTimeoutMillis = clientRequestTimeoutMillis
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = clientRequestTimeoutMillis
            }
        }
        return UpdateArtifactDownloader(httpClient = client, updatesDirectory = updatesDirectory)
    }

    private fun artifact(
        assetName: String = ASSET_NAME,
        url: String = "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v40.0.0/$ASSET_NAME",
        sizeBytes: Long? = payload.size.toLong(),
        sha256: String? = sha256Hex(payload)
    ): AppUpdateArtifact {
        return AppUpdateArtifact(
            assetName = assetName,
            downloadUrl = url,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            platform = AppUpdatePlatform.WINDOWS,
            architecture = AppUpdateArchitecture.X64,
            kind = AppUpdateArtifactKind.WINDOWS_NSIS
        )
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            value.toString(16).padStart(2, '0')
        }
    }

    private companion object {
        const val ASSET_NAME = "UsageMonitor-Setup-40.0.0.exe"
    }
}
