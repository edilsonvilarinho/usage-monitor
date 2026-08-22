package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateArtifact
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Baixa e confere um artefato de release.
 *
 * Separado de [WindowsAppUpdateInstaller] porque não tem nada de Windows: é
 * rede, disco e checksum. Testá-lo exige um `MockEngine` e um diretório
 * temporário; testar o instalador exige registro e processo.
 */
class UpdateArtifactDownloader(
    private val httpClient: HttpClient,
    private val updatesDirectory: File
) {

    /**
     * Devolve o arquivo íntegro, baixando o que faltar.
     *
     * Arquivo final já presente e íntegro devolve sem tocar na rede: é o que
     * impede o laço de verificação de rebaixar 120 MB a cada passada.
     */
    suspend fun download(
        artifact: AppUpdateArtifact,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): File {
        validate(artifact)

        updatesDirectory.mkdirs()
        val finalFile = File(updatesDirectory, artifact.assetName)
        val partFile = File(updatesDirectory, "${artifact.assetName}.part")

        if (finalFile.isFile && verifies(finalFile, artifact)) {
            onProgress(finalFile.length(), artifact.sizeBytes ?: finalFile.length())
            return finalFile
        }

        // Parcial maior que o artefato inteiro não é retomada, é lixo de outra
        // release com o mesmo nome de asset.
        val declaredSize = artifact.sizeBytes
        if (declaredSize != null && partFile.isFile && partFile.length() >= declaredSize) {
            partFile.delete()
        }

        val resumeFrom = if (partFile.isFile) partFile.length() else 0L
        writeBody(artifact, partFile, resumeFrom, onProgress)

        if (!verifies(partFile, artifact)) {
            // Falha de integridade apaga o parcial. Mantê-lo faria a tentativa
            // seguinte retomar de cima do que já se sabe corrompido, para sempre.
            // Interrupção de rede é outra coisa: ali o parcial fica.
            partFile.delete()
            throw IllegalStateException(
                "Update artifact ${artifact.assetName} failed size or SHA-256 verification."
            )
        }

        moveAtomically(partFile, finalFile)
        return finalFile
    }

    /**
     * Apaga de `updates/` tudo que não seja o artefato indicado.
     *
     * Sem isso o diretório cresce ~120 MB por versão baixada, indefinidamente.
     */
    fun prune(keepAssetName: String) {
        val keep = setOf(keepAssetName, "$keepAssetName.part")
        updatesDirectory.listFiles()
            ?.filter { it.isFile && it.name !in keep }
            ?.forEach { it.delete() }
    }

    private suspend fun writeBody(
        artifact: AppUpdateArtifact,
        partFile: File,
        resumeFrom: Long,
        onProgress: (Long, Long?) -> Unit
    ) {
        val response = httpClient.get(artifact.downloadUrl) {
            // O cliente compartilhado do app tem requestTimeoutMillis = 20 s, o
            // que é adequado para uma resposta de API e impossível para 120 MB:
            // no Ktor o requestTimeout cobre a leitura do corpo inteiro. O teto
            // por requisição é o que existe aqui; o socketTimeout continua
            // finito, senão uma conexão morta ficaria pendurada para sempre.
            timeout {
                requestTimeoutMillis = INFINITE_REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
            }
            if (resumeFrom > 0) {
                header(HttpHeaders.Range, "bytes=$resumeFrom-")
            }
        }

        // Servidor que ignora o Range responde 200 com o corpo inteiro: anexar
        // isso ao parcial produziria um arquivo remendado que só o SHA-256
        // pegaria, depois de baixar tudo de novo.
        val appending = resumeFrom > 0 && response.status == HttpStatusCode.PartialContent
        if (!appending && response.status != HttpStatusCode.OK) {
            throw IllegalStateException(
                "Update download for ${artifact.assetName} returned HTTP ${response.status.value}."
            )
        }

        val startOffset = if (appending) resumeFrom else 0L
        val expectedTotal = artifact.sizeBytes
        var written = startOffset

        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        RandomAccessFile(partFile, "rw").use { output ->
            output.setLength(startOffset)
            output.seek(startOffset)
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) {
                    break
                }
                if (read == 0) {
                    continue
                }
                output.write(buffer, 0, read)
                written += read
                onProgress(written, expectedTotal)
            }
        }
    }

    private fun verifies(file: File, artifact: AppUpdateArtifact): Boolean {
        val expectedSize = artifact.sizeBytes
        if (expectedSize != null && file.length() != expectedSize) {
            return false
        }
        val expectedSha256 = artifact.sha256 ?: return false
        return sha256Of(file).equals(expectedSha256, ignoreCase = true)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            value.toString(16).padStart(2, '0')
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validate(artifact: AppUpdateArtifact) {
        if (artifact.sha256 == null) {
            throw IllegalStateException("Update artifact ${artifact.assetName} has no SHA-256 digest.")
        }
        if (artifact.assetName.isBlank() ||
            artifact.assetName.contains('/') ||
            artifact.assetName.contains('\\') ||
            artifact.assetName.contains("..")
        ) {
            // O nome do asset vira caminho no disco: separador ou ".." ali
            // escreveria fora de updates/.
            throw IllegalStateException("Update asset name is not a plain file name.")
        }

        val uri = URI(artifact.downloadUrl)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme != "https" || host !in TRUSTED_DOWNLOAD_HOSTS) {
            throw IllegalStateException("Update URL is not a trusted HTTPS GitHub URL.")
        }
    }

    private companion object {
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

        /** `HttpTimeoutConfig.INFINITE_TIMEOUT_MS` do Ktor é este valor. */
        const val INFINITE_REQUEST_TIMEOUT_MILLIS = Long.MAX_VALUE
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        const val SOCKET_TIMEOUT_MILLIS = 60_000L

        /**
         * Só a URL **inicial** passa por aqui: o GitHub redireciona o download
         * para `objects.githubusercontent.com`, e o Ktor segue o redirecionamento
         * sozinho. Acrescentar o host de destino a esta lista faria a allowlist
         * parecer cobrir o que ela não cobre. Quem barra artefato trocado é o
         * SHA-256, que vem da API por TLS — a allowlist só garante que a cadeia
         * começa no GitHub.
         */
        val TRUSTED_DOWNLOAD_HOSTS = setOf("github.com", "www.github.com")
    }
}
