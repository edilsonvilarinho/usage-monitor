package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_APP_BALLOON_ACTIONS
import com.usagemonitor.HUD_APP_BALLOON_CAPTION
import com.usagemonitor.HUD_APP_BALLOON_MODE_ROW
import com.usagemonitor.HUD_APP_BALLOON_UPDATE_TITLE
import com.usagemonitor.HUD_APP_BALLOON_UPDATE_TITLE_LINES
import com.usagemonitor.HUD_BALLOON_ACTIONS
import com.usagemonitor.HUD_BALLOON_BAR_ROW
import com.usagemonitor.HUD_BALLOON_FOOTER
import com.usagemonitor.HUD_BALLOON_GAP
import com.usagemonitor.HUD_BALLOON_GROUP_HEADER
import com.usagemonitor.HUD_BALLOON_GROUP_PADDING
import com.usagemonitor.HUD_BALLOON_HEADER
import com.usagemonitor.HUD_BALLOON_PADDING
import com.usagemonitor.HUD_BALLOON_QUOTA_DETAIL
import com.usagemonitor.HUD_BALLOON_QUOTA_TITLE
import com.usagemonitor.HUD_BALLOON_SECTION_GAP
import com.usagemonitor.HUD_BALLOON_TAIL_BASE
import com.usagemonitor.HUD_BALLOON_WIDTH
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudBalloonHeight
import com.usagemonitor.hudQuotaRuns
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.WindowMode
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.accentColorFor
import com.usagemonitor.presentation.ui.components.appDepth
import com.usagemonitor.presentation.ui.components.appSheen
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders

/** O balão aberto: é por ele que testes acham qual conta está sendo detalhada. */
internal const val HUD_BALLOON_TEST_TAG = "hudBalloon"

/** A coluna de linhas do balão de conta, cuja altura a geometria soma. */
internal const val HUD_BALLOON_CONTENT_TEST_TAG = "hudBalloonContent"

/**
 * Prefixo do glifo de legenda de cada cota no balão, seguido da posição do anel
 * (0 é o de fora). Cota além dos anéis não tem glifo.
 */
internal const val HUD_BALLOON_RING_LEGEND_TAG_PREFIX = "hudBalloonRingLegend_"

/** A linha de ação da atualização no balão da engrenagem ("Reiniciar o app e atualizar →"). */
internal const val HUD_APP_BALLOON_UPDATE_ACTION_TAG = "hudAppBalloonUpdateAction"

/**
 * A caixa inteira do balão na orientação de [edge]: o corpo e, do lado do notch,
 * a faixa de [HUD_BALLOON_GAP] por onde a cauda passa.
 */
internal fun hudBalloonBoxSize(edge: HudEdge, bodyHeight: Dp): DpSize {
    return if (edge.isHorizontal) {
        DpSize(HUD_BALLOON_WIDTH, bodyHeight + HUD_BALLOON_GAP)
    } else {
        DpSize(HUD_BALLOON_WIDTH + HUD_BALLOON_GAP, bodyHeight)
    }
}

/**
 * O balão de **uma** conta, o do anel sob o ponteiro, como o card do Codenotch.
 *
 * Cabeçalho com marca, título e estado; por cota o título e o reinício, a barra
 * e "87% usado · 13% restante"; as cotas de um mesmo grupo (Antigravity) numa
 * caixa sob o nome dele; e, embaixo, o plano e a origem — "Plus · via Codex".
 *
 * Todas as linhas têm altura fixa, e o corpo usa `requiredSize` com a altura de
 * [hudBalloonHeight]: a janela é dimensionada antes de existir composição, e a
 * soma das linhas daqui é a mesma de lá (`HudNotchTest` afirma).
 *
 * A cauda sai do lado do notch e aponta para o centro do anel: [tailCenter] é a
 * posição dela ao longo da borda, em pixels da caixa. É lambda para ser lida no
 * desenho — o balão deslizando de um anel para outro não recompõe o conteúdo.
 */
@Composable
internal fun HudBalloon(
    edge: HudEdge,
    tailCenter: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    bodyHeight: Dp
) {
    val surface = MaterialTheme.colorScheme.surface
    val box = hudBalloonBoxSize(edge, bodyHeight)
    val bodyPadding = when (edge) {
        HudEdge.TOP -> Modifier.padding(top = HUD_BALLOON_GAP)
        HudEdge.BOTTOM -> Modifier.padding(bottom = HUD_BALLOON_GAP)
        HudEdge.LEFT -> Modifier.padding(start = HUD_BALLOON_GAP)
        HudEdge.RIGHT -> Modifier.padding(end = HUD_BALLOON_GAP)
    }
    val ladder = AppSurfaceLadders.current
    Box(
        modifier = modifier
            .requiredSize(box)
            .testTag(HUD_BALLOON_TEST_TAG)
            // A cauda é desenhada **depois** do corpo: ela cobre o trecho da borda
            // do corpo onde encosta, e os dois leem como uma forma só.
            .drawWithContent {
                drawContent()
                drawPath(hudBalloonTailPath(edge, size.width, size.height, tailCenter(), HUD_BALLOON_GAP.toPx(), HUD_BALLOON_TAIL_BASE.toPx()), surface)
            }
    ) {
        Box(
            modifier = bodyPadding
                .requiredSize(HUD_BALLOON_WIDTH, bodyHeight)
                .appDepth(AppDepth.OVERLAY, AppShapes.large)
                .clip(AppShapes.large)
                .background(surface)
                .appSheen()
                .border(1.dp, ladder.borderTop, AppShapes.large)
                .padding(HUD_BALLOON_PADDING)
        ) {
            content()
        }
    }
}

/**
 * A cunha curva do Codenotch (`TooltipTail`): ombros que saem tangentes ao corpo
 * e ponta no notch. Desenhada em (ao longo, a partir do notch) e levada à caixa
 * de cada borda; a ponta encosta no lado do notch e a base entra 1px no corpo,
 * para não sobrar fresta.
 */
internal fun hudBalloonTailPath(
    edge: HudEdge,
    width: Float,
    height: Float,
    tailCenter: Float,
    gap: Float,
    base: Float
): Path {
    val along = if (edge.isHorizontal) width else height
    val half = base / 2
    val center = tailCenter.coerceIn(half + CORNER_CLEARANCE_PX, (along - half - CORNER_CLEARANCE_PX).coerceAtLeast(half))
    val depth = gap + 1f
    val map: (Float, Float) -> Offset = when (edge) {
        HudEdge.TOP -> { a, c -> Offset(a, c) }
        HudEdge.BOTTOM -> { a, c -> Offset(a, height - c) }
        HudEdge.LEFT -> { a, c -> Offset(c, a) }
        HudEdge.RIGHT -> { a, c -> Offset(width - c, a) }
    }
    val path = Path()
    // Pontos de controle do `clip-path` do Codenotch (32 × 36), normalizados.
    fun point(x: Float, y: Float): Offset = map(center - half + y * base, depth - x * depth)
    val start = point(0f, 0f)
    path.moveTo(start.x, start.y)
    val c1 = point(0f, 0.25f)
    val c2 = point(0.58f, 0.38f)
    val tip = point(1f, 0.5f)
    path.cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
    val c3 = point(0.58f, 0.62f)
    val c4 = point(0f, 0.75f)
    val end = point(0f, 1f)
    path.cubicTo(c3.x, c3.y, c4.x, c4.y, end.x, end.y)
    path.close()
    return path
}

/** A cauda não encosta no canto arredondado do corpo. */
private const val CORNER_CLEARANCE_PX = 12f

/** O conteúdo do balão de uma conta. */
@Composable
internal fun HudAccountBalloonContent(
    account: HudAccount,
    language: AppLanguage,
    /** Os botões do card desta conta; a fileira tem a altura reservada mesmo vazia. */
    actions: (@Composable (HudAccount) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(HUD_BALLOON_CONTENT_TEST_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_HEADER),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppProviderMark(
                source = account.source,
                tint = accentColorFor(source = account.source, accents = AppAccents.current),
                size = BALLOON_MARK_SIZE
            )
            Text(
                text = account.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AppStatusIndicator(label = account.statusLabel, tone = account.tone)
        }
        val rings = account.rings
        // A posição do anel de cada cota, pela identidade: duas cotas iguais em
        // valor continuam sendo dois anéis.
        val ringOf = { quota: HudQuota -> rings.indexOfFirst { ring -> ring === quota } }
        hudQuotaRuns(account.quotas).forEach { run ->
            val group = run.group
            if (group == null) {
                run.quotas.forEach { quota ->
                    Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
                    HudBalloonQuota(quota, language, ringOf(quota), rings.size)
                }
            } else {
                Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
                Text(
                    text = group,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(HUD_BALLOON_GROUP_HEADER)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.medium)
                        .padding(horizontal = HUD_BALLOON_GROUP_PADDING, vertical = HUD_BALLOON_GROUP_PADDING)
                ) {
                    run.quotas.forEachIndexed { index, quota ->
                        if (index > 0) Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
                        HudBalloonQuota(quota, language, ringOf(quota), rings.size)
                    }
                }
            }
        }
        account.detailLine?.let { detail ->
            Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(HUD_BALLOON_FOOTER)
            )
        }
        Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
        Row(
            modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_ACTIONS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions?.invoke(account)
        }
    }
}

@Composable
private fun HudBalloonQuota(quota: HudQuota, language: AppLanguage, ringIndex: Int, ringCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_QUOTA_TITLE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Com um anel só não há qual apontar; a cota além dos anéis não tem um.
        if (ringCount > 1 && ringIndex >= 0) {
            HudRingLegendGlyph(position = ringIndex, count = ringCount)
        }
        Text(
            text = quota.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Sem reset não se imprime nada no lugar, nem traço: saldo que não
        // expira é o caso comum.
        quota.resetText?.let { reset ->
            Text(
                text = hudResetCaption(reset, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_BAR_ROW),
        contentAlignment = Alignment.Center
    ) {
        AppProgressTrack(fraction = quota.fraction, tone = quota.tone)
    }
    // Saldo e atividade observada não têm teto: ali a linha é o valor do card.
    Text(
        text = quota.usedLeftText ?: quota.percentText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(HUD_BALLOON_QUOTA_DETAIL)
    )
}

/** A coluna do balão da engrenagem. */
internal const val HUD_APP_BALLOON_CONTENT_TEST_TAG = "hudAppBalloonContent"

/** Prefixo das linhas de modo de janela do balão da engrenagem, seguido do nome do modo. */
internal const val HUD_APP_BALLOON_MODE_TAG_PREFIX = "hudAppBalloonMode_"

/**
 * O balão da engrenagem: tudo o que o rodapé do modo padrão oferece, para quem
 * está na barra HUD e não tem rodapé.
 *
 * Título com a contagem até a próxima coleta; os três modos de janela em linhas,
 * o corrente marcado — **em linhas e não no menu do rodapé**, porque aquele é um
 * `Popup`, e popup no Compose Desktop é recortado pela própria janela, que aqui
 * é do tamanho do balão —; a fileira de ações do rodapé, a **mesma**
 * ([actions] recebe o `FooterActionGroup`), com os mesmos ícones e descrições; e a
 * atualização pendente, quando há — a frase e, com [onUpdateAction], a **mesma**
 * ação da faixa do modo padrão ("Reiniciar o app e atualizar"). Ela mora aqui e
 * não no ícone do notch: o balão é aberto de propósito e o rótulo diz o que o
 * clique faz, e no notch seria clique de rotina reiniciando o app (#225).
 *
 * Alturas fixas, somadas por `hudAppBalloonHeight`, como no balão de conta.
 */
@Composable
internal fun HudAppBalloonContent(
    language: AppLanguage,
    countdown: (@Composable () -> Unit)?,
    updateIndicator: HudUpdateIndicator?,
    onWindowModeChange: (WindowMode) -> Unit,
    actions: @Composable () -> Unit,
    onUpdateAction: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(HUD_APP_BALLOON_CONTENT_TEST_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_HEADER),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Usage Monitor",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            countdown?.invoke()
        }
        Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
        Text(
            text = if (language == AppLanguage.PT) "Modo de janela" else "Window mode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.height(HUD_APP_BALLOON_CAPTION)
        )
        WindowMode.entries.forEach { mode ->
            HudModeRow(
                label = mode.label(language),
                selected = mode == WindowMode.HUD,
                testTag = HUD_APP_BALLOON_MODE_TAG_PREFIX + mode.name,
                onClick = { onWindowModeChange(mode) }
            )
        }
        Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
        Box(
            modifier = Modifier.fillMaxWidth().height(HUD_APP_BALLOON_ACTIONS),
            contentAlignment = Alignment.CenterStart
        ) {
            actions()
        }
        updateIndicator?.let { indicator ->
            Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
            Text(
                text = indicator.description,
                style = MaterialTheme.typography.labelSmall,
                color = indicator.tone.color(),
                maxLines = HUD_APP_BALLOON_UPDATE_TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(HUD_APP_BALLOON_UPDATE_TITLE)
            )
            val actionLabel = indicator.actionLabel
            if (actionLabel != null && onUpdateAction != null) {
                HudUpdateActionRow(
                    label = actionLabel,
                    tone = indicator.tone,
                    onClick = onUpdateAction
                )
            }
        }
    }
}

/**
 * A ação da atualização: rótulo com seta no tom do estado, como a faixa do modo
 * padrão, com a altura, o hover e a pressão de uma linha de modo.
 */
@Composable
private fun HudUpdateActionRow(label: String, tone: AppTone, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ladder = AppSurfaceLadders.current
    val layer = when {
        pressed -> ladder.pressedLayer
        hovered -> ladder.hoverLayer
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUD_APP_BALLOON_MODE_ROW)
            .clip(AppShapes.small)
            .background(layer)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(HUD_APP_BALLOON_UPDATE_ACTION_TAG)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label →",
            style = MaterialTheme.typography.labelMedium,
            color = tone.color(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Uma linha de modo: a marca do corrente num espaço reservado em todas — sem ele
 * o rótulo andaria para o lado a cada troca, a regra do `AppMenu` —, hover e
 * pressão como camadas.
 */
@Composable
private fun HudModeRow(label: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ladder = AppSurfaceLadders.current
    val layer = when {
        pressed -> ladder.pressedLayer
        hovered -> ladder.hoverLayer
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUD_APP_BALLOON_MODE_ROW)
            .clip(AppShapes.small)
            .background(layer)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(testTag)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(MODE_MARK_SIZE), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(MODE_MARK_SIZE)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private val MODE_MARK_SIZE = 14.dp

/** "Reinicia ter 21h00" / "Resets Tue 21h00" — o "Resets at 20:19" do Codenotch. */
internal fun hudResetCaption(reset: String, language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Reinicia $reset" else "Resets $reset"
}

private val BALLOON_MARK_SIZE = 14.dp

/**
 * A legenda do anel (issue #278): os mesmos anéis concêntricos do notch em
 * miniatura, com **só** o da cota aceso. Sem ela o balão listava "Sessão 5h" e
 * "Semanal" e nada dizia qual círculo era qual. Aceso na cor do texto, não no tom
 * de risco: o glifo diz posição, e o estado já está na barra logo abaixo.
 * Decorativo para a semântica — a descrição do anel já diz a posição em palavra.
 */
@Composable
private fun HudRingLegendGlyph(position: Int, count: Int) {
    val lit = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .size(RING_LEGEND_SIZE)
            .testTag(HUD_BALLOON_RING_LEGEND_TAG_PREFIX + position)
    ) {
        val stroke = RING_LEGEND_STROKE.toPx()
        val gap = RING_LEGEND_GAP.toPx()
        repeat(count) { index ->
            val inset = stroke / 2 + index * (stroke + gap)
            val radius = size.minDimension / 2 - inset
            if (radius > 0f) {
                drawCircle(
                    color = if (index == position) lit else dim,
                    radius = radius,
                    style = Stroke(width = stroke)
                )
            }
        }
    }
}

// 14dp: com três anéis em 12 o de dentro sobrava com 0,75dp de raio, um ponto.
private val RING_LEGEND_SIZE = 14.dp
private val RING_LEGEND_STROKE = 1.5.dp
private val RING_LEGEND_GAP = 0.75.dp
