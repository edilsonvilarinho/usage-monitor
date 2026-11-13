package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens

/**
 * O emoji de uma conta (issue #287) numa caixa de [size], com o glifo medido em
 * **dp** e não em sp.
 *
 * Em sp o glifo cresceria com a escala de fonte do sistema e sairia da caixa,
 * e a caixa é que a geometria da HUD conta. A escala da interface já troca a
 * densidade, que é o que faz dp e sp crescerem juntos.
 *
 * Com [moon] (#585), a caixa vira lua: disco `core` com fio de 1 dp em `gold`
 * atrás do glifo. Só a HUD usa; as outras telas mantêm o emoji puro.
 *
 * Decorativo na semântica: o nome da conta está sempre escrito ao lado, como na
 * [AppProviderMark]. A cor não vem do tema: o emoji é colorido pela própria fonte.
 */
@Composable
fun AccountEmojiGlyph(
    emoji: AccountEmoji,
    size: Dp,
    modifier: Modifier = Modifier,
    moon: Boolean = false
) {
    val fontSize = with(LocalDensity.current) { (size * ACCOUNT_EMOJI_GLYPH_FRACTION).toSp() }
    Box(
        modifier = modifier.size(size).moonDisk(moon).clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji.glyph,
            style = TextStyle(fontSize = fontSize, lineHeight = fontSize),
            maxLines = 1,
            softWrap = false,
            // Fontes de emoji desenham um pouco além da linha; sem isto o glifo
            // era cortado embaixo na caixa justa.
            modifier = Modifier.wrapContentSize(unbounded = true)
        )
    }
}

/** Disco da lua atrás do glifo: `core` cheio e fio de 1 dp em `gold` por dentro da borda. */
private fun Modifier.moonDisk(enabled: Boolean): Modifier =
    if (!enabled) this else drawBehind {
        val rim = MOON_RIM.toPx()
        val radius = size.minDimension / 2f
        drawCircle(color = AppGargantuaTokens.core, radius = radius)
        drawCircle(
            color = AppGargantuaTokens.gold,
            radius = radius - rim / 2f,
            style = Stroke(width = rim)
        )
    }

/** O glifo ocupa 80% da caixa: o desenho das fontes de emoji já traz respiro próprio. */
private const val ACCOUNT_EMOJI_GLYPH_FRACTION = 0.8f

/** Fio da lua (#585): 1 dp. */
private val MOON_RIM: Dp = 1.dp
