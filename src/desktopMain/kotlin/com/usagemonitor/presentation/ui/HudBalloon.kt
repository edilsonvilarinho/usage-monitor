package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_APP_BALLOON_ACTIONS
import com.usagemonitor.HUD_APP_BALLOON_STATUS
import com.usagemonitor.HUD_APP_BALLOON_UPDATE_BANNER
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
import com.usagemonitor.hudSessionBannerHeight
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AccountEmojiGlyph
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.GargantuaJetFrame
import com.usagemonitor.presentation.ui.components.drawGargantuaJet
import com.usagemonitor.presentation.ui.components.accentColorFor
import com.usagemonitor.presentation.ui.components.appDepth
import com.usagemonitor.presentation.ui.components.appSheen
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppChrome
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

/** A seção de sinais de sessão CLI do balão de uma conta (issue #265). */
internal const val HUD_BALLOON_SESSION_SIGNALS_TAG = "hudBalloonSessionSignals"

/** O botão da ação da atualização no balão da engrenagem ("Reiniciar o app e atualizar"). */
internal const val HUD_APP_BALLOON_UPDATE_ACTION_TAG = "hudAppBalloonUpdateAction"

/** O banner da atualização pendente no balão da engrenagem (issue #291). */
internal const val HUD_APP_BALLOON_UPDATE_BANNER_TAG = "hudAppBalloonUpdateBanner"

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
    bodyHeight: Dp,
    reveal: () -> GargantuaJetFrame = { GargantuaJetFrame.Settled },
    beamOrigin: () -> Float = { 0f }
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
                val tail = tailCenter()
                val frame = reveal()
                val tailPath = hudBalloonTailPath(edge, size.width, size.height, tail, HUD_BALLOON_GAP.toPx(), HUD_BALLOON_TAIL_BASE.toPx())
                if (frame.settled) {
                    drawContent()
                    drawPath(tailPath, surface)
                    return@drawWithContent
                }
                // B3 · jato relativístico: o balão se desdobra ao longo da borda a
                // partir da linha do feixe, e o feixe sai do anel por cima dele.
                val shown = hudBalloonRevealRect(edge, size.width, size.height, tail, frame.unfold)
                clipRect(shown.left, shown.top, shown.right, shown.bottom) {
                    this@drawWithContent.drawContent()
                    drawPath(tailPath, surface)
                }
                val origin = -beamOrigin()
                val depth = if (edge.isHorizontal) size.height else size.width
                drawGargantuaJet(
                    from = hudBalloonPoint(edge, size.width, size.height, tail, origin),
                    to = hudBalloonPoint(edge, size.width, size.height, tail, origin + (depth - origin) * frame.beamEnd),
                    alpha = frame.beamAlpha,
                    width = HUD_JET_WIDTH.toPx(),
                    flashRadius = HUD_JET_FLASH.toPx()
                )
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
    val path = Path()
    // Pontos de controle do `clip-path` do Codenotch (32 × 36), normalizados.
    fun point(x: Float, y: Float): Offset = hudBalloonPoint(edge, width, height, center - half + y * base, depth - x * depth)
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

/**
 * Um ponto da caixa do balão dado em ([along] da borda, [depth] a partir do lado
 * do notch). Profundidade negativa sai da caixa em direção ao notch — é por onde
 * o feixe do jato vem do anel.
 */
internal fun hudBalloonPoint(edge: HudEdge, width: Float, height: Float, along: Float, depth: Float): Offset = when (edge) {
    HudEdge.TOP -> Offset(along, depth)
    HudEdge.BOTTOM -> Offset(along, height - depth)
    HudEdge.LEFT -> Offset(depth, along)
    HudEdge.RIGHT -> Offset(width - depth, along)
}

/**
 * O trecho visível do balão desdobrando (B3): a profundidade inteira e, ao longo
 * da borda, [unfold] de cada lado a partir da linha do feixe em [tailCenter].
 * Com `unfold = 1` é a caixa inteira; com `0`, largura zero sobre a linha.
 */
internal fun hudBalloonRevealRect(edge: HudEdge, width: Float, height: Float, tailCenter: Float, unfold: Float): Rect {
    val along = if (edge.isHorizontal) width else height
    val line = tailCenter.coerceIn(0f, along)
    val shown = unfold.coerceIn(0f, 1f)
    val start = line - line * shown
    val end = line + (along - line) * shown
    return if (edge.isHorizontal) Rect(start, 0f, end, height) else Rect(0f, start, width, end)
}

/** Espessura do feixe e raio do clarão na origem, iguais ao protótipo B3. */
private val HUD_JET_WIDTH = 1.6.dp
private val HUD_JET_FLASH = 8.dp

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
                // A cor da conta quando há (issue #275), o acento da fonte quando não.
                tint = account.accountAccent?.current ?: accentColorFor(source = account.source, accents = AppAccents.current),
                size = BALLOON_MARK_SIZE
            )
            // O emoji da conta (issue #287) ao lado da marca, como no anel.
            val emoji = account.accountEmoji
            if (emoji != null) {
                AccountEmojiGlyph(emoji = emoji, size = BALLOON_MARK_SIZE)
            }
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
        // Sinais de sessão CLI (issue #265), depois das cotas: são da conta, mas
        // não são cota, e a palavra do cabeçalho continua sendo só do risco dela.
        if (account.sessionSignals.isNotEmpty()) {
            Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
            // F10: um aviso, como o da atualização no balão da engrenagem. O tom
            // do pior sinal fica só na barra de 2dp; a frase de cada sinal diz o
            // que ele é, uma por linha.
            AppBanner(
                title = if (language == AppLanguage.PT) "Sessões CLI" else "CLI sessions",
                description = account.sessionSignals.joinToString("\n") { signal -> signal.text },
                tone = hudSessionSignalsTone(account.sessionSignals),
                modifier = Modifier
                    .height(hudSessionBannerHeight(account.sessionSignals.size))
                    .testTag(HUD_BALLOON_SESSION_SIGNALS_TAG)
            )
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
    val usedLeft = quota.usedLeft
    if (usedLeft == null) {
        // Saldo e atividade observada não têm teto: ali a linha é o valor do card.
        Text(
            text = quota.percentText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(HUD_BALLOON_QUOTA_DETAIL)
        )
        return
    }
    // A linha continua uma só para o leitor de tela: "7% usado · 93% restante".
    // O usado fica à esquerda (acompanhando a barra) e o restante à direita.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUD_BALLOON_QUOTA_DETAIL)
            .clearAndSetSemantics { contentDescription = usedLeft.text },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = usedLeft.used,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = usedLeft.left,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** A coluna do balão da engrenagem. */
internal const val HUD_APP_BALLOON_CONTENT_TEST_TAG = "hudAppBalloonContent"

/** A versão instalada, exibida no cabeçalho do balão da engrenagem. */
internal const val HUD_APP_BALLOON_VERSION_TEST_TAG = "hudAppBalloonVersion"

/**
 * O balão da engrenagem: tudo o que o rodapé do modo padrão oferece, para quem
 * está na barra HUD e não tem rodapé.
 *
 * Título com a contagem até a próxima coleta; a fileira de ações do rodapé, a **mesma**
 * ([actions] recebe o `FooterActionGroup`), com os mesmos ícones e descrições; e a
 * atualização pendente, quando há — um `AppBanner` e, com [onUpdateAction], a
 * **mesma** ação da faixa do modo padrão ("Reiniciar o app e atualizar") como
 * `AppButton`. Era frase colorida solta e um rótulo com seta que só o hover
 * revelava clicável (issue #291). Ela mora aqui e não no notch: o balão é aberto
 * de propósito e o rótulo diz o que o clique faz, e no notch seria clique de
 * rotina reiniciando o app (#225).
 *
 * Alturas fixas, somadas por `hudAppBalloonHeight`, como no balão de conta.
 */
@Composable
internal fun HudAppBalloonContent(
    language: AppLanguage,
    appVersion: String,
    countdown: (@Composable () -> Unit)?,
    updateIndicator: HudUpdateIndicator?,
    actions: @Composable () -> Unit,
    onUpdateAction: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(HUD_APP_BALLOON_CONTENT_TEST_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(HUD_BALLOON_HEADER),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Usage Monitor",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            // A versão mora ao lado do nome, e a contagem sozinha na linha de baixo.
            // Dividindo a linha com "próxima coleta em", sobravam ~50dp para a versão
            // e "v41.6.0-beta.2" saía cortada em "v41.6.0…". `HudNotchTextFitTest` mede.
            Text(
                text = "v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag(HUD_APP_BALLOON_VERSION_TEST_TAG)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(HUD_APP_BALLOON_STATUS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (countdown != null) {
                Text(
                    text = if (language == AppLanguage.PT) "Próxima coleta em" else "Next fetch in",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                countdown()
            }
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
            // A cor fica só na barra de 2dp do banner; o texto é o do sistema.
            AppBanner(
                title = indicator.headline,
                description = indicator.detail,
                tone = indicator.tone,
                modifier = Modifier.height(HUD_APP_BALLOON_UPDATE_BANNER).testTag(HUD_APP_BALLOON_UPDATE_BANNER_TAG)
            )
            // O botão fica abaixo do aviso, e não dentro dele como o F10 desenhava:
            // "Reiniciar o app e atualizar" não cabe na largura interna do aviso
            // (192dp) a partir de 105% de escala, e o rótulo não encurta — ele diz
            // o que reinicia. `HudNotchTextFitTest` mede.
            val actionLabel = indicator.actionLabel
            if (actionLabel != null && onUpdateAction != null) {
                Spacer(Modifier.height(HUD_BALLOON_SECTION_GAP))
                AppButton(
                    label = actionLabel,
                    onClick = onUpdateAction,
                    modifier = Modifier.height(AppChrome.control).testTag(HUD_APP_BALLOON_UPDATE_ACTION_TAG)
                )
            }
        }
    }
}

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
