package com.usagemonitor.data.diagnostics

import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.data.datasource.restrictToOwnerReadWrite
import com.usagemonitor.domain.entity.breadcrumbFailureReasonOf
import com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Severidade de uma linha do `app.log`.
 *
 * A trilha de passos (`BreadcrumbCategory`) responde "o que o usuário estava
 * fazendo"; este log responde "o que deu errado e quão grave", inclusive quando
 * ninguém estava olhando a tela (issue #416).
 */
internal enum class AppLogLevel {
    INFO,
    WARN,
    ERROR,

    /** Falha que derrubou uma thread sem ninguém para tratá-la. */
    FATAL;

    val wireValue: String
        get() = when (this) {
            INFO -> "info"
            WARN -> "warn"
            ERROR -> "error"
            FATAL -> "fatal"
        }
}

@Serializable
private data class AppLogLineDto(
    val ts: String,
    val pid: Long,
    val version: String,
    val level: String,
    val source: String,
    val thread: String,
    val message: String,
    val error: String? = null,
    val stack: List<String>? = null,
    val causes: List<String>? = null
)

/**
 * O log do app em `~/.usage-monitor/diagnostics/app.log` (issue #416).
 *
 * **Existe porque os outros registros dependem de alguém.** A trilha de passos
 * só sai do disco pelo relatório de bug, que é aberto pela interface; quando a
 * interface é o defeito, o relato chega com uma captura de tela e nada mais. Este
 * arquivo recebe o que o processo vê sozinho — falha não tratada, travamento do
 * EDT, `stderr` da JVM — e é coletado sem janela (`--diagnostics`).
 *
 * **Mesmo modelo de escrita dos registros vizinhos** (`StartupDiagnostics`,
 * `LocalBreadcrumbRecorder`): síncrona sob lock, [restrictToOwnerReadWrite], e
 * `runCatching` que nunca derruba quem chamou. Síncrona de propósito: a linha
 * mais importante é a de quem está caindo, e trabalho enfileirado para outra
 * thread não chega ao disco quando a JVM está de saída. O volume é baixo — falha
 * e travamento, não cada coleta —, então o append no chamador custa pouco.
 *
 * **Rotação por tamanho, não corte por linha:** a linha aqui carrega pilha, e um
 * corte de 200 linhas apagaria o travamento de ontem em uma tarde ruim. O
 * arquivo vira `app.1.log` ao passar de [maxBytes], e o mais antigo de
 * [keptFiles] some. Medir o tamanho é um `stat`; ler o arquivo inteiro a cada
 * linha, como o corte por linha faz, não.
 *
 * **Privacidade:** a mensagem passa por `sanitizeBreadcrumbMessage` — a mesma
 * fábrica da trilha, que redige caminho absoluto, e-mail e segredo antes de
 * cortar. A exceção entra pela classe e pela mensagem redigida
 * (`breadcrumbFailureReasonOf`); a pilha, por `StackTraceElement.toString()`,
 * que dá `pacote.Classe.metodo(Arquivo.kt:42)`, sem caminho. Nunca prompt,
 * resposta, token ou corpo de resposta HTTP.
 */
internal class AppLogFile(
    private val logFile: File = defaultLogFile(),
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val keptFiles: Int = DEFAULT_KEPT_FILES,
    private val json: Json = Json { encodeDefaults = false },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val pid: Long = ProcessHandle.current().pid(),
    private val version: String = CURRENT_APP_VERSION
) {
    // Lock do processo, não da instância: quem grava de longe da injeção (a janela
    // da HUD) cria a própria instância, e duas instâncias girando o mesmo arquivo
    // ao mesmo tempo perderiam linhas.
    private val lock = PROCESS_LOCK

    fun log(
        level: AppLogLevel,
        source: String,
        message: String,
        error: Throwable? = null,
        threadName: String = Thread.currentThread().name
    ) {
        // Montar a linha também fica dentro do `runCatching`: uma exceção cujo
        // `message` lança não pode virar a segunda falha do dia.
        runCatching {
            val line = AppLogLineDto(
                ts = Instant.fromEpochMilliseconds(nowMillis()).toString(),
                pid = pid,
                version = version,
                level = level.wireValue,
                source = source,
                thread = threadName,
                message = sanitizeBreadcrumbMessage(message),
                error = error?.let(::breadcrumbFailureReasonOf),
                stack = error?.let(::stackOf),
                causes = error?.let(::causesOf)
            )
            appendLine(json.encodeToString(line))
        }
    }

    fun info(source: String, message: String) = log(AppLogLevel.INFO, source, message)

    fun warn(source: String, message: String, error: Throwable? = null) =
        log(AppLogLevel.WARN, source, message, error)

    fun error(source: String, message: String, error: Throwable? = null) =
        log(AppLogLevel.ERROR, source, message, error)

    /** Todos os arquivos do log, do atual ao mais antigo, que existem no disco. */
    fun files(): List<File> = (0 until keptFiles).map(::fileAt).filter(File::exists)

    private fun appendLine(line: String) {
        synchronized(lock) {
            logFile.parentFile?.mkdirs()
            rotateIfNeeded()
            logFile.appendText("$line\n")
            restrictToOwnerReadWrite(logFile.toPath())
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() < maxBytes) {
            return
        }
        val oldest = fileAt(keptFiles - 1)
        if (oldest.exists()) {
            oldest.delete()
        }
        for (index in keptFiles - 2 downTo 0) {
            val current = fileAt(index)
            if (current.exists()) {
                current.renameTo(fileAt(index + 1))
            }
        }
    }

    /** `app.log`, `app.1.log`, `app.2.log`… — o índice 0 é o arquivo em uso. */
    private fun fileAt(index: Int): File {
        if (index == 0) {
            return logFile
        }
        val base = logFile.nameWithoutExtension
        val extension = logFile.extension
        val name = if (extension.isEmpty()) "$base.$index" else "$base.$index.$extension"
        return File(logFile.parentFile, name)
    }

    private fun stackOf(error: Throwable): List<String> =
        error.stackTrace.take(STACK_FRAMES).map(StackTraceElement::toString)

    /**
     * A cadeia de causas, só classe e mensagem redigida. A causa costuma ser o
     * fato (o `IOException` debaixo do `IllegalStateException`), e sem ela a
     * linha diria só quem embrulhou a falha.
     */
    private fun causesOf(error: Throwable): List<String>? {
        val causes = generateSequence(error.cause) { cause -> cause.cause?.takeIf { it !== cause } }
            .take(MAX_CAUSES)
            .map(::breadcrumbFailureReasonOf)
            .toList()
        return causes.ifEmpty { null }
    }

    internal companion object {
        const val DEFAULT_MAX_BYTES = 1_000_000L
        const val DEFAULT_KEPT_FILES = 3

        /** Quadros do topo da pilha: o bastante para achar a origem sem virar despejo. */
        const val STACK_FRAMES = 25
        const val MAX_CAUSES = 4

        private val PROCESS_LOCK = Any()

        fun defaultLogFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/diagnostics/app.log")
        }
    }
}
