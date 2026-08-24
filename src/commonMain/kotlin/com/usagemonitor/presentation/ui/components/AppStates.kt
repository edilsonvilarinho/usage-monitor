package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Primitivas de estado: aviso, vazio, carregando, erro, indicador e barra.
 *
 * Todas obedecem à mesma regra: **cor nunca informa sozinha**. Todo estado
 * carrega ponto e palavra, e a cor apenas reforça — é o que mantém a tela
 * legível para quem não distingue as matizes.
 */

/**
 * Severidade de um estado.
 *
 * Os valores saem de `AppAccents` e do `ColorScheme`, não de constantes novas:
 * o verde de "normal" é o mesmo `cacheRead` dos gráficos e o vermelho de
 * "crítico" é o mesmo `error`. Dois vermelhos para a mesma ideia é como uma
 * paleta começa a virar decoração.
 */
enum class AppTone { NEUTRAL, OK, WARNING, CRITICAL, INFO }

@Composable
@ReadOnlyComposable
fun AppTone.color(): Color {
    val accents = AppAccents.current
    return when (this) {
        AppTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        AppTone.OK -> accents.cacheRead
        AppTone.WARNING -> accents.cacheWrite
        AppTone.CRITICAL -> MaterialTheme.colorScheme.error
        AppTone.INFO -> MaterialTheme.colorScheme.primary
    }
}

private val STATUS_DOT_SIZE = 6.dp
private val TRACK_HEIGHT = 4.dp
private val BANNER_BAR_WIDTH = 2.dp
private val STATE_PADDING = 44.dp
private val SKELETON_HEIGHT = 10.dp

/**
 * Indicador de estado: ponto e palavra.
 *
 * [AppTone.NEUTRAL] desenha o ponto **vazado** em vez de preenchido — sem cor
 * para distinguir, o contorno é o que separa "desconectado" de "conectado" numa
 * captura em tons de cinza.
 */
@Composable
fun AppStatusIndicator(
    label: String,
    tone: AppTone,
    modifier: Modifier = Modifier
) {
    val color = tone.color()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        val dot = Modifier.size(STATUS_DOT_SIZE).clip(CircleShape)
        Box(
            modifier = if (tone == AppTone.NEUTRAL) {
                dot.border(AppBorderWidth, color, CircleShape)
            } else {
                dot.background(color)
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * Barra de progresso de 4dp.
 *
 * [fraction] é recortada em 0..1 aqui e não na chamada: cota estourada devolve
 * mais de 100%, e uma barra desenhada além da própria largura sangra sobre a
 * linha vizinha.
 *
 * **A borda de 1dp é fundo mais padding, nunca `Modifier.border`.** O `border`
 * do Compose arredonda o traço para cima (`ceil(width.toPx())`) e o pinta
 * **depois** do conteúdo: num trilho de 4dp o anel vira 2px a partir de
 * densidade 1,05 e cobre os 4px inteiros, apagando o preenchimento — a barra
 * ficava cinza com a cota em 37% nas escalas de 105% e 110% (issue #83). Fundo
 * mais padding usa `roundToPx`, que acompanha a altura do trilho, e é também o
 * `box-sizing: border-box` que o protótipo especifica, em que a cor nunca fica
 * por baixo do anel.
 */
@Composable
fun AppProgressTrack(
    fraction: Float,
    tone: AppTone,
    modifier: Modifier = Modifier
) {
    val safe = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            // O clip é o `overflow: hidden` do protótipo: ele recorta os dois
            // filhos e dispensa clip interno.
            .clip(AppShapes.extraSmall)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(AppBorderWidth)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(safe)
                .background(tone.color())
        )
    }
}

/**
 * Aviso: barra de severidade, título, descrição e ação.
 *
 * A cor entra pela barra de 2dp à esquerda e não pelo fundo. Fundo colorido em
 * banner empilhado — e esta app empilha um por alvo — vira faixa de cor no topo
 * da janela, competindo com os dados que o aviso manda olhar.
 */
@Composable
fun AppBanner(
    title: String,
    tone: AppTone,
    modifier: Modifier = Modifier,
    description: String? = null,
    /**
     * Terceira linha, opcional.
     *
     * Existe porque o aviso de sessão saturada tem três coisas a dizer e as três
     * são observadas por teste: o veredito, o número que o gerou e o que fazer a
     * respeito. Emendá-las em um texto só faria o assert por texto do conselho
     * deixar de casar — foi assim que a necessidade apareceu.
     */
    detail: String? = null,
    action: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .width(BANNER_BAR_WIDTH)
                .height(if (description == null && detail == null) 16.dp else 32.dp)
                .clip(AppShapes.extraSmall)
                .background(tone.color())
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                content = action
            )
        }
    }
}

/** Lista vazia: uma frase centrada, sem ilustração e sem ação inventada. */
@Composable
fun AppEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: @Composable (ColumnScope.() -> Unit)? = null
) {
    AppCenteredState(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * Carregando: esqueleto **estático**.
 *
 * Sem shimmer. `ShimmerBox` é a única animação infinita da app e continua onde
 * está, mas não se replica: animação sem fim trava o `waitForIdle` dos testes
 * de componente, e cada tela nova que a copiasse tornaria uma suíte inteira
 * impossível de escrever.
 */
@Composable
fun AppLoadingState(
    message: String,
    modifier: Modifier = Modifier,
    lines: Int = 4
) {
    // Larguras fixas e desiguais: um bloco de barras idênticas lê como tabela
    // vazia, não como texto por chegar.
    val widths = listOf(0.38f, 0.82f, 0.64f, 0.74f, 0.56f, 0.88f)
    Column(
        modifier = modifier.fillMaxWidth().padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        repeat(lines) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widths[index % widths.size])
                    .height(SKELETON_HEIGHT)
                    .clip(AppShapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.extraSmall)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.xs)
        )
    }
}

/** Erro: a mensagem, o detalhe quando existe, e o caminho de volta. */
@Composable
fun AppErrorState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    AppCenteredState(modifier = modifier) {
        AppStatusIndicator(label = message, tone = AppTone.CRITICAL)
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (retryLabel != null && onRetry != null) {
            AppButton(label = retryLabel, onClick = onRetry)
        }
    }
}

@Composable
private fun AppCenteredState(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = STATE_PADDING,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content
    )
}
