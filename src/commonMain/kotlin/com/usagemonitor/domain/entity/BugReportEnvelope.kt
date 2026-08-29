package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * O pacote de diagnóstico inteiro: o que o usuário descreveu, o que a máquina é
 * e os passos que levaram até ali.
 *
 * A versão do app não é campo daqui — ela mora em [BugReportMachineInfo], que é
 * o único dono dela.
 *
 * **Nada sai da máquina por conta própria.** Este tipo só produz texto; quem
 * grava em disco e quem abre o navegador são camadas de fora, e quem publica é o
 * usuário.
 */
data class BugReportEnvelope(
    val description: String,
    val machineInfo: BugReportMachineInfo,
    val capturedAt: Instant,
    val breadcrumbs: List<Breadcrumb>
) {

    /**
     * O pacote completo, em JSON.
     *
     * **Montado à mão, sem `kotlinx.serialization`.** O envelope mora no domain,
     * e o domain não importa biblioteca externa — foi por essa regra que o
     * `UsageExporter` foi parar em `data`. Aqui o documento é uma árvore de
     * quatro campos e um vetor de objetos de três campos: um escapador coberto
     * por teste custa menos que arrastar a entidade para fora do domain só para
     * serializá-la.
     *
     * Indentado porque é um arquivo que uma pessoa vai abrir e ler antes de
     * arrastá-lo para dentro de uma issue.
     */
    fun toJson(): String {
        val builder = StringBuilder()
        builder.append("{\n")
        builder.append("  ").append(jsonField("capturedAt", capturedAt.toString())).append(",\n")
        builder.append("  ").append(jsonField("description", description)).append(",\n")
        builder.append("  \"machine\": {\n")
        builder.append("    ").append(jsonField("app", machineInfo.appVersion)).append(",\n")
        builder.append("    ").append(jsonField("osName", machineInfo.osName)).append(",\n")
        builder.append("    ").append(jsonField("osVersion", machineInfo.osVersion)).append(",\n")
        builder.append("    ").append(jsonField("osArch", machineInfo.osArch)).append(",\n")
        builder.append("    ").append(jsonField("jvm", machineInfo.javaVersion)).append(",\n")
        builder.append("    ").append(jsonField("language", machineInfo.language.name)).append(",\n")
        builder.append("    \"uiScalePercent\": ").append(machineInfo.uiScalePercent).append(",\n")
        builder.append("    ").append(jsonField("screenResolution", machineInfo.screenResolution)).append(",\n")
        builder.append("    ").append(jsonField("timeZone", machineInfo.timeZoneId)).append("\n")
        builder.append("  },\n")
        builder.append("  \"breadcrumbs\": [")
        if (breadcrumbs.isEmpty()) {
            builder.append("]\n")
        } else {
            builder.append("\n")
            breadcrumbs.forEachIndexed { index, breadcrumb ->
                builder.append("    {")
                builder.append(jsonField("at", breadcrumb.at.toString())).append(", ")
                builder.append(jsonField("category", breadcrumb.category.wireValue)).append(", ")
                builder.append(jsonField("message", breadcrumb.message))
                builder.append("}")
                if (index < breadcrumbs.lastIndex) {
                    builder.append(",")
                }
                builder.append("\n")
            }
            builder.append("  ]\n")
        }
        builder.append("}\n")
        return builder.toString()
    }

    /**
     * O corpo pré-preenchido da issue.
     *
     * É um **resumo**, não o pacote: o pacote completo é o JSON, que o usuário
     * anexa. Este texto viaja dentro de uma URL, e URL tem limite prático —
     * navegador e servidor cortam sem avisar, e um corpo cortado no meio de uma
     * linha some sem deixar rastro de que foi cortado.
     *
     * Dois cortes, nesta ordem:
     * 1. os últimos [MAX_ISSUE_BODY_BREADCRUMBS] passos, porque o que explica a
     *    falha é o que aconteceu **perto** dela;
     * 2. se ainda assim o texto passar de [MAX_ISSUE_BODY_LENGTH], os passos vão
     *    saindo do mais antigo para o mais novo, e só em último caso o texto
     *    inteiro é cortado com marca.
     *
     * A ordem importa: cortar o texto antes de cortar a trilha jogaria fora o
     * fim do documento, que é justamente onde estão os passos recentes.
     *
     * **Sempre em português**, mesmo com a interface em inglês: a triagem
     * acontece no repositório upstream, cujas issues são escritas em português, e
     * um corpo que muda de idioma com a preferência do usuário daria ao mantenedor
     * dois formatos para ler.
     */
    fun toGithubIssueBody(): String {
        val recent = breadcrumbs.takeLast(MAX_ISSUE_BODY_BREADCRUMBS)
        var kept = recent.size
        while (true) {
            val body = renderIssueBody(recent.takeLast(kept))
            if (body.length <= MAX_ISSUE_BODY_LENGTH) {
                return body
            }
            if (kept == 0) {
                // Descrição sozinha maior que o teto: corta o texto e diz que
                // cortou. Silêncio aqui faria o leitor tomar meia frase por
                // frase inteira.
                return body.take(MAX_ISSUE_BODY_LENGTH - BODY_TRUNCATION_NOTICE.length) +
                    BODY_TRUNCATION_NOTICE
            }
            kept -= 1
        }
    }

    private fun renderIssueBody(shownBreadcrumbs: List<Breadcrumb>): String {
        val builder = StringBuilder()
        builder.append("## O que aconteceu\n\n")
        builder.append(description.trim().ifEmpty { "(sem descrição)" }).append("\n\n")

        builder.append("## Ambiente\n\n")
        machineInfo.summaryLines().forEach { line ->
            builder.append("- ").append(line).append("\n")
        }
        builder.append("- Relatório gerado em: ").append(capturedAt).append("\n\n")

        builder.append("## Trilha de eventos")
        if (shownBreadcrumbs.size < breadcrumbs.size) {
            builder.append(" (últimos ").append(shownBreadcrumbs.size)
            builder.append(" de ").append(breadcrumbs.size).append(")")
        }
        builder.append("\n\n")
        if (shownBreadcrumbs.isEmpty()) {
            builder.append("(trilha vazia)\n\n")
        } else {
            builder.append("```\n")
            shownBreadcrumbs.forEach { breadcrumb ->
                builder.append(breadcrumb.at).append("  ")
                builder.append(breadcrumb.category.wireValue).append("  ")
                builder.append(breadcrumb.message).append("\n")
            }
            builder.append("```\n\n")
        }

        builder.append(ATTACHMENT_HINT)
        return builder.toString()
    }

    companion object {
        /**
         * Quantos passos entram no corpo da issue.
         *
         * Trinta cobrem a sequência que leva a uma falha sem transformar o corpo
         * num despejo de log — o restante está no JSON anexado, que não tem
         * limite de URL.
         */
        const val MAX_ISSUE_BODY_BREADCRUMBS = 30

        /**
         * Teto do corpo **antes** do percent-encoding.
         *
         * Seis mil caracteres deixam folga confortável dentro do limite prático
         * de URL de navegador mesmo no pior caso de encoding (três bytes por
         * caractere acentuado), e sobra espaço para o título e para o resto da
         * query string.
         */
        const val MAX_ISSUE_BODY_LENGTH = 6_000
    }
}

private const val BODY_TRUNCATION_NOTICE = "\n\n_(corpo truncado; o pacote completo está no arquivo anexo)_"

private const val ATTACHMENT_HINT =
    "---\n\nAnexe aqui o arquivo `.json` que o Usage Monitor salvou " +
        "(e a captura da janela, se você marcou a opção) antes de publicar.\n"

private fun jsonField(name: String, value: String?): String {
    val rendered = if (value == null) "null" else "\"${escapeJsonString(value)}\""
    return "\"$name\": $rendered"
}

/**
 * Escapa uma string para JSON.
 *
 * Os caracteres de controle vão para `\u00XX` e não são descartados: a descrição
 * é texto colado pelo usuário, e um caractere invisível no meio dela produziria
 * um arquivo que nenhum parser abre — que é a pior forma de perder um relatório,
 * porque a falha só aparece do outro lado.
 *
 * Só três formas curtas — quebra de linha, retorno e tabulação. As de backspace
 * e form feed ficam de fora de propósito: elas são igualmente válidas na forma
 * `\u00XX`, ninguém as lê no arquivo, e cada caso a menos no `when` é um caso a
 * menos para errar.
 */
internal fun escapeJsonString(value: String): String {
    val builder = StringBuilder(value.length + 8)
    value.forEach { character ->
        when (character) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> if (character < ' ') {
                builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                builder.append(character)
            }
        }
    }
    return builder.toString()
}
