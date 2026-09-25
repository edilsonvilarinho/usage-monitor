package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.usagemonitor.presentation.ui.theme.AccountEmoji

/**
 * O emoji de uma conta (issue #287) numa caixa de [size], com o glifo medido em
 * **dp** e não em sp.
 *
 * Em sp o glifo cresceria com a escala de fonte do sistema e sairia da caixa,
 * e a caixa é que a geometria da HUD conta. A escala da interface já troca a
 * densidade, que é o que faz dp e sp crescerem juntos.
 *
 * Decorativo na semântica: o nome da conta está sempre escrito ao lado, como na
 * [AppProviderMark]. A cor não vem do tema: o emoji é colorido pela própria fonte.
 */
@Composable
fun AccountEmojiGlyph(
    emoji: AccountEmoji,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val fontSize = with(LocalDensity.current) { (size * ACCOUNT_EMOJI_GLYPH_FRACTION).toSp() }
    Box(
        modifier = modifier.size(size).clearAndSetSemantics { },
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

/** O glifo ocupa 80% da caixa: o desenho das fontes de emoji já traz respiro próprio. */
private const val ACCOUNT_EMOJI_GLYPH_FRACTION = 0.8f
