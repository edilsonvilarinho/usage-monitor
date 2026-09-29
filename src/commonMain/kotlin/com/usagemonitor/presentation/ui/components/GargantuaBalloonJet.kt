package com.usagemonitor.presentation.ui.components

/**
 * Um quadro do jato relativístico (B3) que abre e fecha o balão da HUD. Tudo em
 * fração: [unfold] `1` é o balão inteiro; o feixe vai do centro do anel
 * ([beamEnd] `0`) até a borda de lá do balão (`1`) e só desenha com
 * [beamAlpha] acima de zero.
 */
data class GargantuaJetFrame(
    /** Quanto o balão já se desdobrou ao longo da borda, a partir da linha do feixe. */
    val unfold: Float = 1f,
    /** Ponta do feixe: `0` no centro do anel, `1` na borda de lá do balão. */
    val beamEnd: Float = 0f,
    val beamAlpha: Float = 0f
) {
    val settled: Boolean get() = this == Settled

    companion object {
        val Settled = GargantuaJetFrame()
    }
}

/**
 * Abrir: o feixe sai do anel e atravessa o balão (0–35%), o balão se desdobra
 * para os dois lados a partir da linha dele (22–85%) e o feixe se apaga no fim
 * (60–100%). [progress] em `0..1`.
 */
fun gargantuaJetOpenFrame(progress: Float): GargantuaJetFrame {
    val t = progress.coerceIn(0f, 1f)
    if (t >= 1f) return GargantuaJetFrame.Settled
    return GargantuaJetFrame(
        unfold = easeInOut(span(t, 0.22f, 0.85f)),
        beamEnd = easeOut(span(t, 0f, 0.35f)),
        beamAlpha = 1f - span(t, 0.6f, 1f)
    )
}

/**
 * Fechar: o balão dobra de volta para a linha do feixe (0–60%) e o feixe é
 * recolhido para dentro do anel (45–100%). [progress] em `0..1`.
 */
fun gargantuaJetCloseFrame(progress: Float): GargantuaJetFrame {
    val t = progress.coerceIn(0f, 1f)
    val retract = span(t, 0.45f, 1f)
    return GargantuaJetFrame(
        unfold = 1f - easeIn(span(t, 0f, 0.6f)),
        beamEnd = 1f - easeIn(retract),
        beamAlpha = if (t < 0.3f || t >= 1f) 0f else 1f - span(t, 0.8f, 1f)
    )
}
