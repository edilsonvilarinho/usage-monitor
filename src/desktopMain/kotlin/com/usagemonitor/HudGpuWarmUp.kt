package com.usagemonitor

import kotlinx.coroutines.delay

/*
 * Aquecimento da GPU da HUD (#627). Na primeira expansão depois do arranque, o
 * Direct3D paga um custo de primeira execução e a animação engasga; o aquecimento
 * abre e fecha a janela com o conteúdo quase transparente, antes de o usuário
 * chegar. Mesmos ciclos, tempos e alfa do protótipo medido.
 */

/** Alfa do conteúdo durante o aquecimento: quase invisível, mas percorre o caminho de desenho real. */
internal const val HUD_GPU_WARM_UP_ALPHA = 0.01f

/** Atraso depois do arranque antes do primeiro ciclo. */
internal const val HUD_GPU_WARM_UP_START_DELAY_MILLIS = 3_000L

/** Tempo com a HUD expandida em cada ciclo. */
internal const val HUD_GPU_WARM_UP_OPEN_MILLIS = 600L

/** Tempo com a HUD recolhida em cada ciclo. */
internal const val HUD_GPU_WARM_UP_CLOSE_MILLIS = 400L

private const val HUD_GPU_WARM_UP_WINDOWS_CYCLES = 3

/**
 * Quantos ciclos aquecer nesta plataforma. Só Windows, que é a única medida; com
 * "Reduzir animações" não há tween, então não há o que aquecer.
 */
internal fun hudGpuWarmUpCycles(platform: AutoStartManager.Platform, motionReduced: Boolean): Int {
    if (motionReduced) return 0
    return when (platform) {
        AutoStartManager.Platform.WINDOWS -> HUD_GPU_WARM_UP_WINDOWS_CYCLES
        AutoStartManager.Platform.LINUX,
        AutoStartManager.Platform.MACOS,
        AutoStartManager.Platform.OTHER -> 0
    }
}

/** Como o aquecimento terminou: nunca rodou, rodou inteiro, ou o uso real o interrompeu. */
internal enum class HudGpuWarmUpOutcome {
    SKIPPED,
    COMPLETED,
    INTERRUPTED
}

/** As ações da HUD que o aquecimento dirige. Quem implementa é a janela, em A04. */
internal interface HudGpuWarmUpTarget {
    fun setWarming(warming: Boolean)
    fun setWindowOpen(open: Boolean)
    fun setExpanded(expanded: Boolean)
    suspend fun awaitFrame()
}

/**
 * Roda o aquecimento. `interrupted` é checado antes de cada passo: uso real
 * (hover ou arrasto) para tudo. Sobre a janela e a expansão, só o caminho normal
 * as desfaz; no uso real e no cancelamento elas ficam como estavam, porque quem
 * as conduz agora é o dono do hover.
 *
 * O aquecimento sempre termina desligado: o `finally` cobre o fim, o uso real e
 * o cancelamento sem que cada saída repita a chamada.
 */
internal suspend fun runHudGpuWarmUp(
    cycles: Int,
    target: HudGpuWarmUpTarget,
    interrupted: () -> Boolean
): HudGpuWarmUpOutcome {
    if (cycles <= 0) return HudGpuWarmUpOutcome.SKIPPED
    try {
        return warmUpSteps(cycles, target, interrupted)
    } finally {
        target.setWarming(false)
    }
}

private suspend fun warmUpSteps(
    cycles: Int,
    target: HudGpuWarmUpTarget,
    interrupted: () -> Boolean
): HudGpuWarmUpOutcome {
    if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
    delay(HUD_GPU_WARM_UP_START_DELAY_MILLIS)
    if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
    target.setWarming(true)
    repeat(cycles) {
        if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
        target.setWindowOpen(true)
        if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
        target.awaitFrame()
        if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
        target.setExpanded(true)
        if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
        delay(HUD_GPU_WARM_UP_OPEN_MILLIS)
        if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
        target.setExpanded(false)
        if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
        delay(HUD_GPU_WARM_UP_CLOSE_MILLIS)
    }
    if (interrupted()) return HudGpuWarmUpOutcome.INTERRUPTED
    target.setWindowOpen(false)
    return HudGpuWarmUpOutcome.COMPLETED
}
