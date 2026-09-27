package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AccountEmoji
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.rememberInfiniteTransition
import com.usagemonitor.presentation.ui.theme.appTween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.appSpring
import androidx.compose.animation.core.VisibilityThreshold
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes

/**
 * Âncoras estruturais do card.
 *
 * O que os testes deste card observam é, na maioria, texto de dado — e texto de
 * interface não muda nesta refatoração, então esses asserts sobrevivem a ela.
 * O que **não** sobrevive é assert que depende de onde a coisa está: os dois
 * testes de empilhamento comparam a posição do rótulo da cota, e o rótulo vai
 * deixar de ser o nó externo do bloco quando a cota virar linha. Daí a âncora
 * ser o bloco, não o texto dentro dele.
 */
const val API_USAGE_CARD_TAG_PREFIX = "apiUsageCard:"
const val API_USAGE_CARD_HEADER_TAG = "apiUsageCardHeader"
const val API_USAGE_CARD_ACTIONS_TAG = "apiUsageCardActions"

/** Badge de estado do cabeçalho: ponto e palavra do pior risco entre as cotas. */
const val API_USAGE_CARD_STATUS_TAG = "apiUsageCardStatus"
const val API_USAGE_CARD_STATUS_HINT_TAG = "apiUsageCardStatusHint"
const val QUOTA_BLOCK_TAG_PREFIX = "quotaBlock:"
const val QUOTA_PROGRESS_TRACK_TAG_PREFIX = "quotaProgress:"
/** O rótulo da cota é único dentro de um card: é a chave da série. */
fun quotaBlockTag(label: String): String = "$QUOTA_BLOCK_TAG_PREFIX$label"

/** Âncora de teste da barra da cota expandida; não altera a semântica visual. */
fun quotaProgressTrackTag(label: String): String = "$QUOTA_PROGRESS_TRACK_TAG_PREFIX$label"

/** O emoji da conta no cabeçalho do card (issue #287). */
const val API_USAGE_CARD_EMOJI_TAG = "apiUsageCardEmoji"

fun apiUsageCardTag(apiName: String): String = "$API_USAGE_CARD_TAG_PREFIX$apiName"

/** Opacidade do número de uma janela já vencida — o dado é real, mas velho. */
internal const val STALE_QUOTA_ALPHA = 0.45f

// Entrada do card: o fade é longo o bastante para a grade ler como cascata com
// o atraso de `AppMotion.stagger`, e a subida e a escala andam por mola.
private const val CARD_ENTER_FADE_MS = AppMotion.slow

/** O conteúdo novo espera a saída do antigo começar, para não se sobreporem cheios. */
private const val MINIMIZE_FADE_DELAY_MS = 50

@Composable
fun ApiUsageCard(
    source: ApiSource,
    apiName: String,
    quotas: List<QuotaInfo>,
    accountContext: UsageAccountContext? = null,
    notices: Set<ApiUsageNotice> = emptySet(),
    /** Plano da conta ("Max 20x"); `null` quando o fornecedor não informa. */
    planLabel: String? = null,
    /**
     * A cor da conta (issue #275), já resolvida por `accountAccentColor`; `null`
     * é o acento da fonte. Vale só onde o acento já aparece: o marcador e a marca.
     */
    accent: Color? = null,
    /** O emoji da conta (issue #287), ao lado da marca do fornecedor; `null` é nenhum. */
    emoji: AccountEmoji? = null,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary> = emptyMap(),
    showUsageDetails: Boolean,
    isRefreshing: Boolean,
    isMinimized: Boolean = false,
    isBeingDragged: Boolean = false,
    isDragTarget: Boolean = false,
    language: AppLanguage,
    animationDelayMillis: Int,
    animateEntrance: Boolean = true,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit = {},
    /** Só os cards Anthropic recebem: sessões do Claude Code pertencem a uma conta. */
    onOpenCliSessions: (() -> Unit)? = null,
    /** O card Codex recebe as sessões do Codex CLI da conta exibida nele. */
    onOpenCodexCliSessions: (() -> Unit)? = null,
    /**
     * Só os cards Anthropic de contas marcadas como parte do time recebem, e só
     * com a integração ligada. Nulo esconde o botão — quem não usa a integração
     * não ganha um botão que não leva a lugar nenhum.
     */
    onOpenTeamUsage: (() -> Unit)? = null,
    /**
     * Abre a janela de quem está conectado agora nesta conta.
     *
     * Mesma condição de [onOpenTeamUsage]: nulo esconde o botão.
     */
    onOpenTeamPresence: (() -> Unit)? = null,
    /**
     * Sessões desta máquina, nesta conta, com interação nos últimos minutos e
     * veredito laranja ou vermelho. Vazio deixa o botão como qualquer outro.
     */
    cliSessionPulse: SessionPulse = SessionPulse.EMPTY,
    /** Mesmo semáforo, para as sessões de todo o time nesta conta. */
    teamSessionPulse: SessionPulse = SessionPulse.EMPTY,
    onToggleMinimized: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    /**
     * Instante contra o qual o vencimento das janelas de cota é medido.
     *
     * É um valor, não um relógio: quem avança o tempo é a `DashboardScreen`, o
     * único ponto stateful da tela. Assim o card segue sem corrotina própria.
     */
    now: Instant = Clock.System.now(),
    modifier: Modifier = Modifier
) {
    val orderedQuotas = orderQuotasForCard(quotas)

    var visible by remember(source, animateEntrance) { mutableStateOf(!animateEntrance) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    LaunchedEffect(source, animateEntrance) {
        if (!animateEntrance) {
            visible = true
            return@LaunchedEffect
        }
        visible = false
        delay(animationDelayMillis.toLong())
        visible = true
    }

    // Tudo pela política de motion: com "Reduzir animações" o card nasce no
    // lugar. Fade por tween enfático; escala e subida por mola, que é o que
    // deixa o levantar do arrasto acompanhar a mão sem parar seco.
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(CARD_ENTER_FADE_MS, AppMotion.emphasizedEasing),
        label = "cardAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = when {
            isBeingDragged -> 1.02f
            isDragTarget -> 0.99f
            visible -> 1f
            else -> 0.96f
        },
        animationSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = 0.001f),
        label = "cardScale"
    )
    val cardOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 18.dp,
        animationSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = Dp.VisibilityThreshold),
        label = "cardOffsetY"
    )
    // A profundidade diz o que está sobre o quê: em repouso o card está sobre o
    // fundo, com o ponteiro em cima ele sobe um patamar e 1dp, e arrastado ele
    // flutua sobre os outros. O alvo do arrasto afunda para o plano -- é o vão
    // que vai receber o card. Mola sem rebote: sombra que passa do alvo e volta
    // lê como tremor.
    val cardDepth = when {
        isBeingDragged -> AppDepth.DIALOG
        isDragTarget -> AppDepth.FLAT
        isHovered -> AppDepth.RAISED
        else -> AppDepth.CARD
    }
    val cardKeyShadow by animateDpAsState(
        targetValue = cardDepth.key,
        animationSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = Dp.VisibilityThreshold),
        label = "cardKeyShadow"
    )
    val cardAmbientShadow by animateDpAsState(
        targetValue = cardDepth.ambient,
        animationSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = Dp.VisibilityThreshold),
        label = "cardAmbientShadow"
    )
    val cardLift by animateDpAsState(
        targetValue = if (isHovered && !isBeingDragged) (-1).dp else 0.dp,
        animationSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = Dp.VisibilityThreshold),
        label = "cardLift"
    )
    // O rastro que varria o card durante a coleta era `rememberInfiniteTransition`
    // — animação sem fim, a mesma classe de coisa que trava o `waitForIdle` dos
    // testes de componente. O estado de coleta agora se lê no rótulo do botão,
    // que já dizia "Atualizando…", e na opacidade das cotas.

    // O fundo do card não muda no hover: quem diz "o ponteiro está aqui" é a
    // subida de patamar. Trocar para `surfaceVariant` também apagava o hover das
    // linhas de cota, que usam aquele mesmo tom.
    val cardBackground = cardContainerColor()

    // Era o único `Card()` do Material que restava na aplicação. A superfície
    // agora sai de `appSurfaceBlock` — o mesmo recorte, fundo e borda de 1dp que
    // `AppDataSurface` aplica —, e a cor animada do hover entra por parâmetro:
    // hover neste sistema é troca de superfície neutra, não mudança de cor.
    //
    // Sombra só enquanto o card está sendo arrastado, que é quando ele de fato
    // flutua sobre os outros; com `cardElevation` em zero o `shadow` não desenha
    // nada. Em repouso quem o separa do fundo é a borda de 1dp — e era a sombra
    // em toda superfície que fazia o dashboard ler como uma pilha de blocos de
    // mesmo peso.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(apiUsageCardTag(apiName))
            .hoverable(hoverInteraction)
            .pointerInput(source) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
                translationY = (cardOffsetY + cardLift).toPx()
            }
            .appDepth(key = cardKeyShadow, ambient = cardAmbientShadow, shape = AppShapes.medium)
            .appSurfaceBlock(shape = AppShapes.medium, color = cardBackground, sheen = true)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.medium)
        ) {
            val density = resolveApiUsageCardDensity(maxWidth)
            // Abaixo do piso o popup de cota cobre o card inteiro. Ver
            // `shouldShowQuotaTooltip`.
            val showQuotaTooltip = shouldShowQuotaTooltip(maxWidth)
            val stackCompactQuotas = shouldStackCompactQuotas(
                cardWidth = maxWidth,
                quotaCount = orderedQuotas.size
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                ApiUsageCardHeader(
                    source = source,
                    apiName = apiName,
                    accountContext = accountContext,
                    notices = notices,
                    planLabel = planLabel,
                    accent = accent,
                    emoji = emoji,
                    quotas = orderedQuotas,
                    riskByQuotaKey = riskByQuotaKey,
                    now = now,
                    isRefreshing = isRefreshing,
                    isMinimized = isMinimized,
                    language = language,
                    density = density,
                    onRefresh = onRefresh,
                    onToggleMinimized = onToggleMinimized
                )

                AppDivider()

                ApiUsageCardQuotaContent(
                    source = source,
                    quotas = orderedQuotas,
                    isMinimized = isMinimized,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    riskByQuotaKey = riskByQuotaKey,
                    density = density,
                    stackCompactQuotas = stackCompactQuotas,
                    showQuotaTooltip = showQuotaTooltip,
                    now = now
                )

            ApiUsageCardNavigationBar(
                language = language,
                density = density,
                cliSessionPulse = cliSessionPulse,
                teamSessionPulse = teamSessionPulse,
                onOpenHistory = onOpenHistory,
                onOpenCliSessions = onOpenCliSessions,
                onOpenCodexCliSessions = onOpenCodexCliSessions,
                onOpenTeamUsage = onOpenTeamUsage,
                onOpenTeamPresence = onOpenTeamPresence
            )
            }
        }
    }
}

/**
 * As cotas do card, aberto ou minimizado. A troca entre os dois é a única
 * animação de tamanho do card.
 */
@Composable
private fun ApiUsageCardQuotaContent(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    isMinimized: Boolean,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    stackCompactQuotas: Boolean,
    showQuotaTooltip: Boolean,
    now: Instant
) {

    // Um dono só para o tamanho: esta transição. Havia também um
    // `animateContentSize` no card inteiro, e as duas animações de
    // tamanho aninhadas faziam o card esticar em dois tempos ao
    // minimizar. A mola do tamanho é a mesma do resto do card.
    val minimizeFadeIn = appTween<Float>(AppMotion.normal, AppMotion.emphasizedEasing, delayMillis = MINIMIZE_FADE_DELAY_MS)
    val minimizeFadeOut = appTween<Float>(AppMotion.exit, AppMotion.exitEasing)
    val minimizeScaleIn = appSpring<Float>(AppMotion.Springs.GENTLE, visibilityThreshold = 0.001f)
    val minimizeScaleOut = appTween<Float>(AppMotion.exit, AppMotion.exitEasing)
    val minimizeSize = appSpring<IntSize>(AppMotion.Springs.GENTLE, visibilityThreshold = IntSize.VisibilityThreshold)
    AnimatedContent(
        targetState = isMinimized,
        transitionSpec = {
            (fadeIn(minimizeFadeIn) + scaleIn(minimizeScaleIn, initialScale = 0.97f))
                .togetherWith(fadeOut(minimizeFadeOut) + scaleOut(minimizeScaleOut, targetScale = 0.98f))
                .using(SizeTransform(clip = false) { _, _ -> minimizeSize })
        },
        label = "cardLayoutMode"
    ) { minimized ->
        // Só a cota expandida é linha de tabela e traz a própria
        // divisória de ponta a ponta. Badge e resumo do OpenCode são
        // blocos, e bloco encostado na borda não tem onde respirar:
        // esses dois recebem o padding do card.
        val blockPadding = Modifier.padding(
            horizontal = density.contentHorizontalPadding,
            vertical = density.contentVerticalPadding
        )
        if (source.isObservedActivitySource()) {
            ObservedUsageSummary(
                source = source,
                quotas = quotas,
                language = language,
                compact = minimized,
                modifier = blockPadding
            )
        } else if (minimized) {
            CompactQuotaSummary(
                source = source,
                quotas = quotas,
                showUsageDetails = showUsageDetails,
                language = language,
                riskByQuotaKey = riskByQuotaKey,
                density = density,
                stacked = stackCompactQuotas,
                showTooltip = showQuotaTooltip,
                now = now,
                modifier = blockPadding
            )
        } else {
            ExpandedQuotaSummary(
                quotas = quotas,
                showUsageDetails = showUsageDetails,
                language = language,
                riskByQuotaKey = riskByQuotaKey,
                density = density,
                showTooltip = showQuotaTooltip,
                now = now
            )
        }
    }
}
