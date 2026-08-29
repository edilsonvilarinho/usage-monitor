package com.usagemonitor.domain.entity

/**
 * O que a máquina é, para quem vai ler o relatório.
 *
 * **Sem hostname, sem usuário do sistema e sem IP** — e isto não é omissão por
 * esquecimento. O pacote vira o corpo de uma issue **pública** no GitHub: as três
 * identificam a pessoa e nenhuma ajuda a diagnosticar o app. Ao acrescentar campo
 * aqui, a pergunta é "isto explica um defeito ou identifica quem o reportou?".
 *
 * A **versão do app mora aqui**, e não também no envelope: os dois carregariam a
 * mesma string, e dois donos do mesmo valor divergem no dia em que um deles for
 * preenchido de outro lugar.
 *
 * Os campos anuláveis são os que a JVM pode não saber responder — um ambiente
 * headless não tem resolução de tela. Nulo é "não medido"; a linha some do
 * resumo em vez de afirmar "desconhecida", que ocuparia espaço sem informar.
 */
data class BugReportMachineInfo(
    val osName: String,
    val osVersion: String,
    val osArch: String,
    val javaVersion: String,
    val appVersion: String,
    val language: AppLanguage,
    val uiScalePercent: Int,
    /** `"1920x1080"`, ou nulo em ambiente sem tela. */
    val screenResolution: String? = null,
    /** Identificador IANA (`"America/Sao_Paulo"`), ou nulo se a JVM não o resolver. */
    val timeZoneId: String? = null
) {

    /**
     * As linhas do resumo, na ordem em que são lidas.
     *
     * Função única e não um bloco de texto montado na tela: o corpo da issue e a
     * prévia do diálogo mostram a mesma coisa, e duas montagens divergiriam
     * exatamente no campo que alguém acrescentasse depois.
     */
    fun summaryLines(): List<String> {
        val lines = mutableListOf(
            "App: $appVersion",
            "OS: $osName $osVersion ($osArch)",
            "JVM: $javaVersion",
            "Idioma: ${language.name}",
            "Escala da interface: $uiScalePercent%"
        )
        if (screenResolution != null) {
            lines += "Resolução: $screenResolution"
        }
        if (timeZoneId != null) {
            lines += "Fuso: $timeZoneId"
        }
        return lines
    }
}
