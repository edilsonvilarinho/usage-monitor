package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
internal fun HudAccountBalloonContent(account: HudAccount, language: AppLanguage) {
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
        hudQuotaRuns(account.quotas).forEach { run ->
            val group = run.group
            if (group == null) {
                run.quotas.forEach { quota ->
                    Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
                    HudBalloonQuota(quota, language)
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
                        HudBalloonQuota(quota, language)
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
    }
}

@Composable
private fun HudBalloonQuota(quota: HudQuota, language: AppLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_QUOTA_TITLE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

/** "Reinicia ter 21h00" / "Resets Tue 21h00" — o "Resets at 20:19" do Codenotch. */
internal fun hudResetCaption(reset: String, language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Reinicia $reset" else "Resets $reset"
}

private val BALLOON_MARK_SIZE = 14.dp
