package com.usagemonitor.domain.entity

/**
 * Uma conta Codex extra monitorada (issue #329): um diretório no formato do
 * `CODEX_HOME`, com `auth.json` e `cap_sid`, que o próprio CLI mantém renovado.
 *
 * A conta padrão (`~/.codex`) **não** tem perfil: o alvo dela continua sendo
 * `UsageTargetKey(CODEX)` sem `profileId`, e por isso a ordem dos cards, o
 * backoff persistido e o cache gravados antes desta versão continuam valendo.
 */
data class CodexProfileRef(
    val id: String,
    val label: String
) {
    init {
        require(id.isNotBlank()) { "O identificador do perfil Codex não pode ser vazio." }
        require(label.isNotBlank()) { "O nome do perfil Codex não pode ser vazio." }
    }
}
