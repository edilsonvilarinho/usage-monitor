package com.usagemonitor

import com.usagemonitor.data.datasource.restrictToOwnerReadWrite
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Marcador deixado por uma queda, lido no arranque seguinte.
 *
 * Guarda **o que foi a falha**, não o que a máquina é: o resto do pacote é
 * montado na hora de gerar o relatório, com os valores do momento em que ele foi
 * pedido, e não com os de uma sessão que já acabou.
 */
@Serializable
internal data class PendingCrashMarker(
    val ts: String,
    val thread: String,
    val exception: String,
    val message: String? = null,
    val stackTop: List<String> = emptyList()
)

/**
 * Handler de exceção não tratada.
 *
 * Ao disparar faz três coisas, todas dentro de `runCatching`: anota um passo
 * [BreadcrumbCategory.CRASH] na trilha, grava [PendingCrashMarker] no disco e
 * repassa a exceção para o handler anterior. A ordem importa: a anotação vem
 * antes do marcador porque é ela que fecha a sequência de passos que o relatório
 * vai mostrar, e o repasse vem por último porque o handler anterior é quem pode
 * derrubar o processo.
 *
 * **O que ele NÃO cobre**, e é melhor estar escrito do que descoberto depois:
 * exceção lançada dentro do laço de eventos da AWT não chega aqui — o
 * `EventDispatchThread` a captura e a imprime por conta própria —, e falha dentro
 * de uma coroutine com `SupervisorJob` é entregue ao `CoroutineExceptionHandler`
 * do escopo, não ao handler default da thread. Este handler pega o que derruba
 * uma thread comum, que é o caso em que o app some sem deixar nada.
 *
 * A escrita é síncrona pelo motivo de sempre: a JVM está de saída, e trabalho
 * enfileirado para outra thread não chega ao disco.
 */
class CrashHandler(
    private val breadcrumbs: BreadcrumbRecorder,
    private val markerFile: File = defaultMarkerFile(),
    /**
     * Captura da janela, *best-effort*. Default que não captura: quem não passa
     * um capturer não tem tela, e é assim que o teste roda em CI headless.
     */
    private val screenshots: WindowScreenshotCapturer = NoWindowScreenshotCapturer,
    private val screenshotFile: File = defaultScreenshotFile(),
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : Thread.UncaughtExceptionHandler {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Assume o handler default e guarda o anterior para repassar.
     *
     * Guardar o anterior não é cortesia: sem o repasse, uma queda que hoje
     * aparece no console passaria a não aparecer em lugar nenhum, e o app teria
     * trocado um diagnóstico por outro em vez de somar os dois.
     */
    fun install() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        val exceptionName = error::class.simpleName ?: error::class.qualifiedName ?: "Throwable"
        val stackTop = error.stackTrace.take(STACK_TOP_FRAMES).map { frame -> frame.toString() }

        // A mensagem da exceção entra aqui, ao contrário do que `breadcrumbReasonOf`
        // faz com as falhas engolidas. São dois casos diferentes: aquelas são
        // frequentes, de baixo valor e nunca revisadas; esta é o evento único que
        // motiva o relatório inteiro, e o usuário lê o pacote antes de publicá-lo.
        runCatching {
            breadcrumbs.record(
                BreadcrumbCategory.CRASH,
                listOfNotNull(
                    "$exceptionName em ${thread.name}",
                    error.message,
                    stackTop.firstOrNull()
                ).joinToString(separator = " | ")
            )
        }

        runCatching {
            val marker = PendingCrashMarker(
                ts = Instant.fromEpochMilliseconds(nowMillis()).toString(),
                thread = thread.name,
                exception = exceptionName,
                message = error.message,
                stackTop = stackTop
            )
            markerFile.parentFile?.mkdirs()
            markerFile.writeText(json.encodeToString(marker))
            restrictToOwnerReadWrite(markerFile.toPath())
        }

        // A captura vem DEPOIS do marcador: ela é a parte mais cara e a mais
        // provável de falhar, e o marcador sozinho já entrega o relatório. Na
        // ordem inversa, uma captura que travasse levaria junto o registro da
        // queda.
        runCatching {
            val png = screenshots.capture()
            if (png != null) {
                screenshotFile.parentFile?.mkdirs()
                screenshotFile.writeBytes(png)
                restrictToOwnerReadWrite(screenshotFile.toPath())
            } else if (screenshotFile.exists()) {
                // Imagem de uma queda anterior não pode ser oferecida como sendo
                // desta: ela mostraria uma tela que não é a do defeito.
                screenshotFile.delete()
            }
        }

        // Por último: o handler anterior é quem pode encerrar o processo, e o que
        // vier depois dele não roda.
        previousHandler?.uncaughtException(thread, error)
    }

    companion object {
        /**
         * Quantos quadros do topo entram no marcador.
         *
         * Cinco descrevem a origem da falha sem virar despejo. Nenhum carrega
         * caminho absoluto: `StackTraceElement.toString()` dá
         * `pacote.Classe.metodo(Arquivo.kt:42)`.
         */
        const val STACK_TOP_FRAMES = 5

        fun defaultMarkerFile(): File {
            return diagnosticsFile("pending-crash.json")
        }

        /** Ao lado do marcador, e com o mesmo nome: os dois descrevem uma queda só. */
        fun defaultScreenshotFile(): File {
            return diagnosticsFile("pending-crash.png")
        }

        private fun diagnosticsFile(name: String): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/diagnostics/$name")
        }
    }
}
