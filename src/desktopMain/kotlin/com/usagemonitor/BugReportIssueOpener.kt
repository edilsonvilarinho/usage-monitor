package com.usagemonitor

import com.usagemonitor.domain.entity.BugReportEnvelope
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Repositório para onde os relatórios vão.
 *
 * Constante única e **sem override por variável de ambiente**: build de fork
 * continua abrindo o upstream, que é onde as issues são triadas. Um override
 * mandaria o relatório para um repositório que ninguém lê, e o usuário não teria
 * como saber.
 */
const val BUG_REPORT_REPOSITORY_URL = "https://github.com/edilsonvilarinho/usage-monitor"

/** Caminho de nova issue; separado da constante acima para o validador conferir os dois. */
private const val NEW_ISSUE_PATH = "/edilsonvilarinho/usage-monitor/issues/new"

/**
 * Teto do resumo que vira título.
 *
 * Título longo é cortado pela própria lista de issues do GitHub, então cortar
 * aqui é escolher **onde** ele é cortado em vez de deixar a decisão para a
 * largura da tela de quem lê.
 */
internal const val MAX_ISSUE_TITLE_SUMMARY = 80

/**
 * Título da issue: prefixo fixo + a primeira linha da descrição.
 *
 * A primeira linha, e não a descrição inteira: quem escreve um relatório começa
 * dizendo o que houve e depois detalha, e o título é o resumo, não o texto.
 */
internal fun bugReportIssueTitle(description: String): String {
    val firstLine = description.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    val summary = firstLine.ifEmpty { "sem descrição" }
    val capped = if (summary.length <= MAX_ISSUE_TITLE_SUMMARY) {
        summary
    } else {
        summary.take(MAX_ISSUE_TITLE_SUMMARY - 3) + "..."
    }
    return "Relatório de bug: $capped"
}

/**
 * A URL de nova issue com título e corpo pré-preenchidos.
 *
 * O corpo já chega truncado de [BugReportEnvelope.toGithubIssueBody]; aqui ele só
 * é codificado. Truncar depois do percent-encoding cortaria no meio de uma
 * sequência `%C3%A7` e produziria um caractere quebrado.
 */
internal fun bugReportIssueUrl(envelope: BugReportEnvelope): String {
    val title = encode(bugReportIssueTitle(envelope.description))
    val body = encode(envelope.toGithubIssueBody())
    return "$BUG_REPORT_REPOSITORY_URL/issues/new?title=$title&body=$body"
}

private fun encode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
}

/**
 * Abre a issue pré-preenchida no navegador.
 *
 * Valida host e caminho pelo mesmo motivo do abridor de release: o que vai para o
 * navegador é montado com texto do usuário, e um validador na saída é o que
 * garante que nenhuma montagem futura consiga apontar para outro lugar.
 *
 * As três costuras são as mesmas do abridor de release — `Desktop.browse` abriria
 * um navegador de verdade durante a suíte.
 */
class BugReportIssueOpener(
    private val processLauncher: (List<String>, File?) -> Process = ::launchBrowserProcess,
    private val desktopBrowser: (URI) -> Boolean = ::browseWithDesktop,
    private val osNameProvider: () -> String = { System.getProperty("os.name").orEmpty() }
) {

    fun open(issueUrl: String): Result<Unit> {
        return Result.runCatching {
            val uri = URI(issueUrl)
            validate(uri)
            openInBrowser(
                uri = uri,
                desktopBrowser = desktopBrowser,
                processLauncher = processLauncher,
                osNameProvider = osNameProvider
            )
        }
    }

    private fun validate(uri: URI) {
        if (uri.scheme?.lowercase() != "https") {
            throw IllegalStateException("URL de issue inválida: apenas HTTPS é aceito.")
        }
        if (uri.host?.lowercase() !in TRUSTED_HOSTS) {
            throw IllegalStateException("URL de issue inválida: host não confiável.")
        }
        if (!uri.path.orEmpty().equals(NEW_ISSUE_PATH, ignoreCase = true)) {
            throw IllegalStateException("URL de issue inválida: caminho fora da nova issue esperada.")
        }
    }

    private companion object {
        val TRUSTED_HOSTS = setOf("github.com", "www.github.com")
    }
}
