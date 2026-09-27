package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_EMOJI_BADGE_OVERSHOOT
import com.usagemonitor.HUD_EMOJI_BADGE_SIZE
import com.usagemonitor.HUD_ITEM_GAP
import com.usagemonitor.HUD_NOTCH_PADDING_ACROSS
import com.usagemonitor.HUD_NOTCH_PADDING_ALONG
import com.usagemonitor.HUD_NOTCH_SHOULDER
import com.usagemonitor.HUD_RING_GAP
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.HUD_RING_TEXT_GAP
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AccountEmojiGlyph
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.AppUsageRing
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appSpring

/** O anel "pressionado" enquanto a conta recoleta. */
private const val RING_REFRESH_SCALE = 0.9f

/** A marca gira uma volta por segundo, o ritmo do glifo de recarga do card. */
private const val RING_REFRESH_TURN_MILLIS = 1_000

/** A faixa de anéis, do tamanho do notch. */
@Composable
internal fun HudRingStrip(
    accounts: List<HudAccount>,
    edge: HudEdge,
    fallbackLabel: String,
    fallbackTone: AppTone,
    size: DpSize,
    compact: Boolean,
    language: AppLanguage,
    onRingHovered: (Int) -> Unit,
    onRingRefresh: (Int) -> Unit,
    onItemPlaced: (Int, LayoutCoordinates) -> Unit,
    onRingPlaced: (Int, LayoutCoordinates) -> Unit
) {
    val items: @Composable () -> Unit = {
        if (accounts.isEmpty()) {
            AppStatusIndicator(label = fallbackLabel, tone = fallbackTone)
        } else {
            accounts.forEachIndexed { index, account ->
                HudRingItem(
                    account = account,
                    vertical = !edge.isHorizontal,
                    compact = compact,
                    language = language,
                    onHovered = { onRingHovered(index) },
                    onRefresh = { onRingRefresh(index) },
                    onItemPlaced = { coordinates -> onItemPlaced(index, coordinates) },
                    onPlaced = { coordinates -> onRingPlaced(index, coordinates) }
                )
            }
        }
        // A faixa é só das contas. A contagem até a próxima coleta mora no balão
        // da engrenagem (issue #269): com a cadência de 60 s ela reiniciava a
        // cada minuto na borda da tela. A atualização pendente também não entra
        // (issue #291) — é o ponto da engrenagem.
    }
    if (edge.isHorizontal) {
        Row(
            modifier = Modifier
                .size(size)
                .padding(horizontal = HUD_NOTCH_SHOULDER + HUD_NOTCH_PADDING_ALONG, vertical = HUD_NOTCH_PADDING_ACROSS),
            horizontalArrangement = Arrangement.spacedBy(HUD_ITEM_GAP, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) { items() }
    } else {
        Column(
            modifier = Modifier
                .size(size)
                .padding(horizontal = HUD_NOTCH_PADDING_ACROSS, vertical = HUD_NOTCH_SHOULDER + HUD_NOTCH_PADDING_ALONG),
            verticalArrangement = Arrangement.spacedBy(HUD_ITEM_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { items() }
    }
}

/** A pílula de estado de cada conta; os testes medem ela contra a geometria. */
internal const val HUD_STATUS_PILL_TEST_TAG = "hud-status-pill"

@Composable
private fun HudRingItem(
    account: HudAccount,
    vertical: Boolean,
    /** A célula do Codenotch: anel e percentual embaixo, sem a palavra. */
    compact: Boolean,
    language: AppLanguage,
    onHovered: () -> Unit,
    onRefresh: () -> Unit,
    onItemPlaced: (LayoutCoordinates) -> Unit,
    onPlaced: (LayoutCoordinates) -> Unit
) {
    // Coletando, o anel fica pressionado — o `refreshRing` do Codenotch — e a
    // marca gira, só com a política contínua.
    val pressScale by animateFloatAsState(
        targetValue = if (account.refreshing) RING_REFRESH_SCALE else 1f,
        animationSpec = appSpring(AppMotion.Springs.SNAPPY),
        label = "hudRingRefreshScale"
    )
    val policy = LocalAppMotionPolicy.current
    val markTurn = if (account.refreshing && policy.continuous) {
        val transition = rememberInfiniteTransition(label = "hudRingRefresh")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(RING_REFRESH_TURN_MILLIS, easing = LinearEasing)),
            label = "hudRingRefreshAngle"
        )
        angle
    } else {
        0f
    }
    val refreshLabel = hudRefreshAccountLabel(account, language)
    // A ação é **declarada** na semântica, não instalada: um `clickable` aqui
    // consumiria o `down` e o arrasto pelo corpo nunca começaria.
    val itemModifier = Modifier
        .onGloballyPositioned(onItemPlaced)
        .semantics {
            onClick(label = refreshLabel) {
                onRefresh()
                true
            }
        }
    // O anel sob o ponteiro escolhe a conta do balão.
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val currentOnHovered by rememberUpdatedState(onHovered)
    LaunchedEffect(isHovered) {
        if (isHovered) currentOnHovered()
    }
    val description = hudRingDescription(account, language)
    val ring: @Composable () -> Unit = {
        // A marca do fornecedor no centro do anel, como no Codenotch: a conta se
        // reconhece antes de ler o nome, que o notch recolhido nem mostra. Na
        // cor do texto e não no acento — em volta dela já estão os arcos, e o
        // acento ali competiria com a cor de risco deles. **Exceção: a cor que o
        // usuário deu à conta** (issue #275). Com duas contas Claude, o miolo é
        // o único ponto do notch recolhido que diz qual é qual, e ali a escolha
        // é dele — um marcador à parte mudaria a geometria e dividiria espaço
        // com a órbita de sessão ativa.
        Box(
            modifier = Modifier
                .onGloballyPositioned(onPlaced)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
            contentAlignment = Alignment.Center
        ) {
            AppUsageRing(
                arcs = account.rings.map { quota -> AppRingArc(quota.fraction, quota.tone, quota.hasForecast) },
                description = description,
                size = HUD_RING_SIZE,
                stroke = HUD_RING_STROKE,
                gap = HUD_RING_GAP,
                active = account.sessionActive,
                attention = account.needsAttention,
                attentionIndex = account.attentionRingIndex
            )
            AppProviderMark(
                source = account.source,
                tint = account.accountAccent?.current ?: MaterialTheme.colorScheme.onSurface,
                size = hudRingMarkSize(account.rings.size),
                modifier = Modifier.graphicsLayer { rotationZ = markTurn }
            )
            // O emoji da conta (issue #287), selo no canto de cima à direita do
            // anel. Passa só `HUD_EMOJI_BADGE_OVERSHOOT` para fora dele, dentro do
            // respiro do notch: é selo, não item, e não mexe em `hudNotchSizes`.
            // Fica de pé em toda borda, como o texto do balão.
            val emoji = account.accountEmoji
            if (emoji != null) {
                AccountEmojiGlyph(
                    emoji = emoji,
                    size = HUD_EMOJI_BADGE_SIZE,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = HUD_EMOJI_BADGE_OVERSHOOT, y = -HUD_EMOJI_BADGE_OVERSHOOT)
                        .testTag(HUD_ACCOUNT_EMOJI_TEST_TAG)
                )
            }
        }
    }
    // Uma linha por anel, com a janela (#286); compacta, só a cota em foco. As
    // linhas são desenhadas no tamanho que `stripLineWidth`/`stripLineHeight`
    // medem — a costura com a geometria.
    val lines: @Composable () -> Unit = {
        val stripLines = if (compact) listOf(account.focusLine) else account.stripLines
        Column(horizontalAlignment = if (vertical || compact) Alignment.CenterHorizontally else Alignment.Start) {
            stripLines.forEach { line -> HudStripLineText(line) }
        }
    }
    // A palavra do estado em pílula tonal (issue #322): solta, ela tinha o
    // mesmo peso dos percentuais ao lado e o notch lia "flat". A largura e a
    // altura saem de `statusPillWidth`/`statusPillHeight`, a costura com a
    // geometria.
    val word: @Composable () -> Unit = {
        AppStatusPill(
            label = account.statusLabel,
            tone = account.tone,
            // Na coluna vertical "Sem projeção" quebra em duas linhas; alinhadas
            // à esquerda elas destoavam do anel e dos percentuais, centrados.
            textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
            maxLines = if (vertical) 2 else 1,
            modifier = Modifier.testTag(HUD_STATUS_PILL_TEST_TAG)
        )
    }
    if (vertical || compact) {
        Column(modifier = itemModifier.hoverable(hover), horizontalAlignment = Alignment.CenterHorizontally) {
            ring()
            lines()
            // Compacto, a palavra fica no balão e na descrição do anel: com
            // contas demais ela é o que fazia a faixa atravessar a tela.
            if (!compact) word()
        }
    } else {
        Row(
            modifier = itemModifier.hoverable(hover),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HUD_RING_TEXT_GAP)
        ) {
            ring()
            Column {
                lines()
                word()
            }
        }
    }
}

/**
 * "7d 72%": a janela no tom secundário e o número no do texto. A cor do risco
 * fica no arco e na palavra — aqui ela diria o estado só pela cor. Sem rótulo é
 * o percentual de sempre, em `labelMedium`.
 */
@Composable
private fun HudStripLineText(line: HudStripLine) {
    val label = line.label
    if (label == null) {
        Text(
            text = line.percentText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        return
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(label) }
            append(" ")
            append(line.percentText)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

/**
 * A marca cabe no miolo que os arcos deixam livre: cada arco come um traço e um
 * vão de cada lado. 70% do miolo deixa ar entre a marca e o arco de dentro.
 *
 * A sessão ativa não entra na conta: a órbita dela gira por fora do anel. Quando
 * ela morava por dentro, a marca da conta trabalhando caía de 14dp para 8dp.
 */
internal fun hudRingMarkSize(arcs: Int): Dp {
    val used = (HUD_RING_STROKE + HUD_RING_GAP) * 2 * arcs.coerceIn(1, 3)
    return ((HUD_RING_SIZE - used) * 0.7f).coerceAtLeast(6.dp)
}
