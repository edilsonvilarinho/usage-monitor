package com.usagemonitor.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta cenográfica da HUD; dourado não representa consumo nem estado. */
object AppGargantuaTokens {
    val size = 64.dp
    val night = Color(0xFF080B12)
    val core = Color(0xFF030508)
    /**
     * Corpo da HUD no tema escuro (M1 · horizonte de eventos). Um degrau acima do
     * [core], senão o miolo de cada anel sumiria contra o fundo.
     */
    val horizon = Color(0xFF07080B)
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
    const val collapseMillis = 850
    /**
     * Depois do colapso, a vaga fecha, o notch recolhe e as vizinhas desdobram as
     * linhas (K1). Com 480 ms de colapso e o notch saltando no fim, a saída lia bruta.
     */
    const val departureSettleMillis = 450
    /** Abertura do balão de uma conta ou da engrenagem (B3, jato relativístico). */
    const val jetOpenMillis = 520
    /** Fechamento do balão: dobra de volta e o feixe recolhe (B3). */
    const val jetCloseMillis = 240
    /** Modo recolher (#400): a íris do eclipse abre o notch a partir da faixa (Z2). */
    const val irisOpenMillis = 420
    /** Modo recolher (#400): a íris fecha de volta no meio da faixa (Z2). */
    const val irisCloseMillis = 240
    /** Dado novo: cada caractere que mudou rola pelo horizonte (D5). */
    const val rollMillis = 480
    /** Cascata entre os caracteres que rolam, da esquerda para a direita (D5). */
    const val rollStaggerMillis = 50
    /**
     * Abertura de modal: filamentos de plasma revelam cada linha (E9). Era 680 ms:
     * com a janela já pré-aquecida, a espera que sobrava no clique era a revelação.
     */
    const val filamentOpenMillis = 340
    /** Fechamento de modal: os filamentos recolhem, de baixo para cima (E9). */
    const val filamentCloseMillis = 220
    /**
     * Trilha de cota sem projeção: o anel de detritos fecha o laço nesse ciclo
     * (J7). Na linha central são 8 voltas de 16 s; por dentro, mais voltas.
     */
    const val debrisCycleMillis = 128_000
    /** O anel de fótons da borda da HUD respira entre 85% e 100% (M1). */
    const val horizonBreathMillis = 6_000
    const val diskTilt = -12f
}
