package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Folga interna da pílula. `internal` pelo mesmo motivo de [STATUS_DOT_SIZE]: a
 * geometria da barra HUD dimensiona a janela antes de existir composição e
 * precisa saber quanto a pílula ocupa além do texto — um segundo `6.dp` lá seria
 * um segundo dono do mesmo valor.
 */
internal val STATUS_PILL_PADDING_HORIZONTAL = 6.dp
internal val STATUS_PILL_PADDING_VERTICAL = 2.dp

/**
 * Opacidade do tom sobre a superfície. Baixa de propósito: o texto é escrito no
 * próprio tom, e um fundo mais cheio derruba o contraste dele abaixo de AA
 * (`AppStatusPillContrastTest`). Medido: com 0,14 o verde do tema claro dava
 * 4,17:1; 0,08 é o maior valor em que os três tons passam nos dois temas (pior
 * caso 4,53:1, o mesmo verde).
 */
internal const val STATUS_PILL_TINT_ALPHA = 0.08f

/**
 * Indicador de estado em pílula: ponto e palavra sobre um fundo tingido do tom
 * (issue #322).
 *
 * [AppStatusIndicator] é ponto e palavra soltos no fundo, e na barra HUD — onde o
 * estado é a única coisa que o notch recolhido diz além dos números — a palavra
 * lia "sem graça", da mesma altura e peso dos percentuais ao lado. O fundo
 * tingido dá peso de selo sem trazer cor nova: é o mesmo tom do ponto e do texto,
 * só com opacidade baixa sobre a superfície.
 *
 * Cor continua não informando sozinha: a palavra está sempre escrita.
 * Raio 4, o `extraSmall` da escala; nunca pílula de raio cheio, que passaria do
 * teto de 10 da escala e leria como botão.
 */
@Composable
fun AppStatusPill(
    label: String,
    tone: AppTone,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start
) {
    val color = tone.color()
    Row(
        modifier = modifier
            .clip(AppShapes.extraSmall)
            .background(color.copy(alpha = STATUS_PILL_TINT_ALPHA))
            .padding(horizontal = STATUS_PILL_PADDING_HORIZONTAL, vertical = STATUS_PILL_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AppStatusDot(tone = tone)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}

/**
 * Selo do canal beta (issue #355): a pílula com a palavra "Beta" no tom de
 * atenção. Um dono só para o texto e o tom, porque a janela de novidades e o
 * rodapé o mostram, e dois desenhos para o mesmo dado obrigariam a reaprender a
 * ler. "Beta" é igual nos dois idiomas.
 */
@Composable
fun BetaReleasePill(modifier: Modifier = Modifier) {
    AppStatusPill(label = BETA_RELEASE_LABEL, tone = AppTone.WARNING, modifier = modifier)
}

internal const val BETA_RELEASE_LABEL = "Beta"

/** O fundo que a pílula pinta sobre [surface]; puro, para o teste de contraste. */
internal fun statusPillBackground(tone: Color, surface: Color): Color {
    return tone.copy(alpha = STATUS_PILL_TINT_ALPHA).compositeOver(surface)
}
