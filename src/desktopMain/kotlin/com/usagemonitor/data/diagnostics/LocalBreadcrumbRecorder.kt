package com.usagemonitor.data.diagnostics

import com.usagemonitor.StartupDiagnostics
import com.usagemonitor.data.datasource.restrictToOwnerReadWrite
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.normalizeBreadcrumbMessage
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class BreadcrumbLineDto(
    val ts: String,
    val category: String,
    val message: String
)

/**
 * A trilha em `~/.usage-monitor/diagnostics/breadcrumbs.jsonl`.
 *
 * **Modelado no [StartupDiagnostics]**, e não num desenho novo: mesmo lock,
 * mesmo corte antes do append, mesmo [restrictToOwnerReadWrite], mesmo
 * `runCatching` que nunca derruba quem chamou. Os dois arquivos são a mesma
 * coisa — uma linha JSON por evento, num diretório só de diagnóstico —, e um
 * segundo desenho para o mesmo tipo de arquivo só daria duas coisas para manter.
 *
 * O corte vem **das constantes daquele arquivo**, não de números repetidos aqui:
 * dois cortes para o mesmo tipo de arquivo seriam dois donos da mesma decisão.
 *
 * **Sempre ligado**, sem variável de ambiente — ao contrário dos recorders de
 * créditos da Anthropic e do Codex, que gravam corpo de resposta a cada coleta.
 * Aqui a linha é curta e a pergunta é a mesma do registro de arranque:
 * diagnóstico que exige variável configurada **antes** do fato não serve para
 * investigar o defeito que já aconteceu.
 *
 * **A escrita é síncrona, de propósito.** O caminho crítico é o handler de
 * exceção não tratada: ali a JVM está de saída, e um passo enfileirado para
 * outra thread não chega ao disco. Uma segunda via assíncrona para os passos de
 * navegação também reordenaria a trilha, que é justamente o que ela existe para
 * preservar.
 *
 * Metadado de uso apenas: nada de prompt, resposta, corpo de resposta HTTP ou
 * credencial.
 */
class LocalBreadcrumbRecorder(
    private val breadcrumbsFile: File = defaultBreadcrumbsFile(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : BreadcrumbRecorder {

    private val lock = Any()

    override fun record(category: BreadcrumbCategory, message: String) {
        val line = BreadcrumbLineDto(
            ts = Instant.fromEpochMilliseconds(nowMillis()).toString(),
            category = category.wireValue,
            // A normalização é do domain e vale aqui também: quem chama pode ter
            // interpolado uma mensagem de exceção de três linhas.
            message = normalizeBreadcrumbMessage(message)
        )

        // Anotar o passo não pode virar a segunda falha do dia -- muito menos a
        // que derruba o app enquanto ele tenta explicar a primeira.
        runCatching { appendLine(json.encodeToString(line)) }
    }

    override fun read(limit: Int): List<Breadcrumb> {
        if (limit <= 0) {
            return emptyList()
        }
        return runCatching {
            synchronized(lock) {
                if (!breadcrumbsFile.exists()) {
                    emptyList()
                } else {
                    // Linha ilegível é pulada, não interrompe a leitura: um
                    // arquivo com uma linha truncada por um desligamento abrupto
                    // é exatamente o caso em que o relatório mais importa.
                    breadcrumbsFile.readLines().takeLast(limit).mapNotNull(::parseLine)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun parseLine(line: String): Breadcrumb? {
        val dto = runCatching { json.decodeFromString<BreadcrumbLineDto>(line) }.getOrNull()
            ?: return null
        val at = runCatching { Instant.parse(dto.ts) }.getOrNull() ?: return null
        // Categoria desconhecida é linha escrita por uma versão mais nova do app;
        // inventar um valor para ela seria afirmar algo que não foi gravado.
        val category = BreadcrumbCategory.fromWireValue(dto.category) ?: return null
        return Breadcrumb(at = at, category = category, message = dto.message)
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            breadcrumbsFile.parentFile?.mkdirs()
            trimIfNeeded()
            breadcrumbsFile.appendText("$line\n")
            restrictToOwnerReadWrite(breadcrumbsFile.toPath())
        }
    }

    // O corte acontece ANTES do append, entao o limite superior real e
    // MAX_LINES + 1 -- igual ao do registro de arranque.
    private fun trimIfNeeded() {
        if (!breadcrumbsFile.exists()) {
            return
        }

        val lines = breadcrumbsFile.readLines()
        if (lines.size <= StartupDiagnostics.MAX_LINES) {
            return
        }

        val kept = lines.takeLast(StartupDiagnostics.KEPT_LINES)
        breadcrumbsFile.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        fun defaultBreadcrumbsFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/diagnostics/breadcrumbs.jsonl")
        }
    }
}
