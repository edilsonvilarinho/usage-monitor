package com.usagemonitor.screenshots

/**
 * Ritmo das demos de vídeo (#633), em milissegundos: um lugar só para todas. O vídeo anterior
 * ficou rápido e cortado; o ritmo é regra, e o cursor nunca teleporta (todo deslocamento usa [CURSOR_TRAVEL]).
 */
internal object VideoDemoPace {
    /** Tela parada antes da primeira ação. */
    const val INTRO_HOLD = 2_500L

    /** O cursor anda até o alvo. */
    const val CURSOR_TRAVEL = 1_400L

    /** Cursor parado sobre o alvo antes do clique. */
    const val DWELL_BEFORE_CLICK = 900L

    /** O resultado de cada passo na tela. */
    const val STEP_HOLD = 2_500L

    /** Fim da demo, com o estado final parado. */
    const val OUTRO_HOLD = 3_000L

    /** Piso das animações do app (voo, abertura, arrasto): quadros suficientes para ler o movimento. */
    const val MIN_ANIMATION = 1_600L

    /** Margem da cena em volta de tudo o que a demo usa: nada aparece cortado. */
    const val SCENE_MARGIN_DP = 48
}
