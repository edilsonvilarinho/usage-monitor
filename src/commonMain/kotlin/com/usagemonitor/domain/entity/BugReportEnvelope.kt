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
}

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
