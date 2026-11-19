package com.usagemonitor

/**
 * Parâmetros do buraco negro do modo flutuante, em dp, exceto os marcados. Padrões dobrados na A08e1 (teste real do dono:
 * o app desenhava os números do protótipo em 1x). O JSON de origem, `prototipos/issue-619/parametros-dono.json`, ainda traz os valores de 1x.
 */
internal data class HudOrbitParams(
    val holeD: Float = 64f,
    /** Inclinação do disco de acreção, em porcentagem: ry = rx * tilt / 100. */
    val tiltPercent: Float = 29f,
    /** Espessura do disco, em porcentagem. */
    val diskWidthPercent: Float = 3f,
    /** Segundos para uma volta do disco. */
    val diskSpinSeconds: Float = 3.5f,
)
