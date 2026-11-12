package com.usagemonitor

import androidx.compose.ui.unit.Dp
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens

/*
 * Geometria da conta minimizada (#576): anel de 28 dp com a cota em foco ao lado,
 * sem palavra nem pílula de estado. Vive fora de HudNotchGeometry.kt para não
 * estourar o limite de linhas do arquivo.
 */

/** Largura do item minimizado na faixa horizontal, e na lateral como espessura: anel, vão e cota em foco. */
internal fun minimizedRingWithFocusWidth(account: HudAccount): Dp =
    AppGargantuaTokens.minimizedSize + HUD_RING_TEXT_GAP + stripLineWidth(account.focusLine)

/** Altura do item minimizado na lateral: o maior entre o anel e a linha da cota em foco. */
internal fun minimizedRingWithFocusHeight(account: HudAccount): Dp =
    maxOf(AppGargantuaTokens.minimizedSize, stripLineHeight(account.focusLine))
