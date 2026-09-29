package com.usagemonitor.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta cenográfica da HUD; dourado não representa consumo nem estado. */
object AppGargantuaTokens {
    val size = 64.dp
    val night = Color(0xFF080B12)
    val core = Color(0xFF030508)
    val gold = Color(0xFFE8AE63)
    val hot = Color(0xFFFFF0CB)
    val dust = Color(0xFF8C643F)
    /** Lado que se afasta do observador: o disco escurece para brasa. */
    val ember = Color(0xFFB4501E)
    val mark = Color(0xFFF2EDED)
    const val orbitMillis = 14_000
    const val refreshMillis = 4_000
    /** Coletando: período de cada onda gravitacional (R1). */
    const val rippleMillis = 1_300
    /** Coleta concluída: a onda final (R1). */
    const val refreshWaveMillis = 800
    const val attentionMillis = 3_200
    const val activeMillis = 2_600
    /** Uma passada de luz ao longo do arco de quota, do início até a ponta. */
    const val flowMillis = 2_800
    /** Nascimento ao ativar uma API ou iniciar o app (S1, onda de choque). */
    const val birthMillis = 1_100
    /** Colapso ao desativar uma API (C2, colapso com clarão). */
    const val collapseMillis = 480
    /** Abertura do balão de uma conta ou da engrenagem (B3, jato relativístico). */
    const val jetOpenMillis = 520
    /** Fechamento do balão: dobra de volta e o feixe recolhe (B3). */
    const val jetCloseMillis = 240
    /** Dado novo: cada caractere que mudou rola pelo horizonte (D5). */
    const val rollMillis = 480
    /** Cascata entre os caracteres que rolam, da esquerda para a direita (D5). */
    const val rollStaggerMillis = 50
    const val diskTilt = -12f
}
