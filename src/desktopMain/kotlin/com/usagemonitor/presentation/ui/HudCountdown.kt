package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_COUNTDOWN_GAP
import com.usagemonitor.HUD_COUNTDOWN_ICON
import com.usagemonitor.hudRefreshFraction
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.formatRefreshCountdown
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A contagem até a próxima coleta (issue #185). O tique mora aqui e não em quem
 * chama, e para em zero — é suspensão, não quadro pendente.
 */
@Composable
internal fun HudCountdown(
    nextRefreshAt: Instant,
    description: String,
    /** Com o intervalo, o ícone é o relógio que esvazia; sem ele, o ↻ de sempre. */
    interval: Duration? = null,
    nowProvider: () -> Instant,
    waitNextTick: suspend () -> Unit,
    updatesEnabled: Boolean
) {
    val remainingOf = { (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt() }
    var secondsUntilRefresh by remember(nextRefreshAt) { mutableStateOf(remainingOf()) }

    LaunchedEffect(nextRefreshAt, updatesEnabled) {
        secondsUntilRefresh = remainingOf()
        if (!updatesEnabled) {
            return@LaunchedEffect
        }
        while (true) {
            val remaining = remainingOf()
            secondsUntilRefresh = remaining
            if (remaining <= 0) {
                break
            }
            waitNextTick()
        }
    }

    val icon: @Composable () -> Unit = {
        if (interval == null) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = description,
                modifier = Modifier.size(HUD_COUNTDOWN_ICON),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // A fração sai dos mesmos segundos que o texto mostra, não de um segundo relógio.
            HudCountdownClock(
                fraction = hudRefreshFraction(nextRefreshAt, nextRefreshAt - secondsUntilRefresh.seconds, interval),
                description = description
            )
        }
    }
    val text: @Composable () -> Unit = {
        Text(
            text = formatRefreshCountdown(secondsUntilRefresh),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
    // Ícone e tempo numa linha só também na coluna vertical (#293): empilhados
    // eram duas linhas, e o tempo cabe na largura que a palavra já pede.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HUD_COUNTDOWN_GAP)) {
        icon()
        text()
    }
}

internal const val HUD_COUNTDOWN_CLOCK_TAG = "hudCountdownClock"

/**
 * O relógio da contagem (#293): um timer de cozinha de 12dp. O setor cheio é o
 * que falta até a próxima coleta; ele esvazia no sentido horário a partir das
 * 12h e, na coleta, volta cheio de uma vez. Ao lado do `05:42` o número diz
 * quanto falta e o relógio diz que aquilo é contagem regressiva — o filete solto
 * na borda que o precedeu não dizia nem uma coisa nem outra.
 *
 * O passo de cada segundo desliza em [HUD_CLOCK_STEP_MILLIS], então o setor anda
 * contínuo. São transições finitas, uma por tique, e não animação infinita: o
 * `waitForIdle` dos testes não trava, e "Reduzir animações" vira salto.
 */
@Composable
private fun HudCountdownClock(fraction: Float, description: String) {
    val shown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = appTween(HUD_CLOCK_STEP_MILLIS, LinearEasing),
        label = "hudCountdownClock"
    )
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .size(HUD_COUNTDOWN_ICON)
            .testTag(HUD_COUNTDOWN_CLOCK_TAG)
            .semantics { contentDescription = description }
    ) {
        val stroke = HUD_CLOCK_RING.toPx()
        drawCircle(color = ringColor, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
        // O setor fica recuado do aro: colado nele, o relógio cheio viraria um disco.
        val inset = stroke + HUD_CLOCK_GAP.toPx()
        drawArc(
            color = fillColor,
            startAngle = -90f,
            sweepAngle = 360f * shown,
            useCenter = true,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2)
        )
    }
}

/** Um pouco menos que o tique de 1s: o setor chega antes do passo seguinte. */
private const val HUD_CLOCK_STEP_MILLIS = 900

private val HUD_CLOCK_RING = 1.25.dp

private val HUD_CLOCK_GAP = 1.dp
