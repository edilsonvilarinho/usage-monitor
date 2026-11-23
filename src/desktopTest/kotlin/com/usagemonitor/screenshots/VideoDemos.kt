package com.usagemonitor.screenshots

/**
 * Demos gravadas só para o vídeo de lançamento (#633). Ficam fora do `HelpTopic`
 * (nenhum valor novo no enum): o id é o nome da pasta de quadros.
 */
internal object VideoDemos {

    val recorders: Map<String, (HelpMediaOptions) -> Unit> = mapOf(
        "video-hud-floating" to ::recordHudFloating,
        "video-hud-minimize" to ::recordHudMinimize,
        "video-hud-reorder" to ::recordHudReorder,
        "video-codex-sessions" to ::recordCodexSessions,
        "video-analysis-all" to ::recordAnalysisAll
    )

    val ids: Set<String> get() = recorders.keys

    /** Ids pedidos que não são nem do catálogo da ajuda nem desta lista. */
    fun unknownTopics(requested: Set<String>, catalogIds: Set<String>): Set<String> =
        requested - catalogIds - ids
}
