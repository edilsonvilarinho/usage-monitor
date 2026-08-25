package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.isExtraCreditsQuota
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.domain.entity.seriesKey
import com.usagemonitor.domain.entity.statusBadgeLabel
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

private const val COMPACT_QUOTA_BADGE_TAG = "compactQuotaBadge"

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

fun apiUsageCardTag(apiName: String): String = "$API_USAGE_CARD_TAG_PREFIX$apiName"

/** Opacidade do número de uma janela já vencida — o dado é real, mas velho. */
private const val STALE_QUOTA_ALPHA = 0.45f

// Durações centralizadas das animações do card (em ms). Mantém legibilidade ao
// alterar timing globalmente sem caçar literais espalhados pelo composable.
private object CardAnimations {
    val EXPAND_DURATION_MS  = AppMotion.slow + AppMotion.normal   // 600ms
    val MINIMIZE_DURATION_MS = AppMotion.normal                   // 250ms
    const val PULSE_DURATION_MS = 1500
}

@Composable
fun ApiUsageCard(
    source: ApiSource,
    apiName: String,
    quotas: List<QuotaInfo>,
    accountContext: UsageAccountContext? = null,
    notices: Set<ApiUsageNotice> = emptySet(),
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary> = emptyMap(),
    showUsageDetails: Boolean,
    isRefreshing: Boolean,
    isMinimized: Boolean = false,
    isBeingDragged: Boolean = false,
    isDragTarget: Boolean = false,
    language: AppLanguage,
    animationDelayMillis: Int,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit = {},
    /** Só os cards Anthropic recebem: sessões do Claude Code pertencem a uma conta. */
    onOpenCliSessions: (() -> Unit)? = null,
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

    var visible by remember(source) { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    LaunchedEffect(source) {
        visible = false
        delay(animationDelayMillis.toLong())
        visible = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = CardAnimations.EXPAND_DURATION_MS),
        label = "cardAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = when {
            isBeingDragged -> 1.02f
            isDragTarget -> 0.99f
            visible -> 1f
            else -> 0.96f
        },
        animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS),
        label = "cardScale"
    )
    val cardOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = CardAnimations.EXPAND_DURATION_MS),
        label = "cardOffsetY"
    )
    val cardElevation by animateDpAsState(
        targetValue = when {
            isBeingDragged -> AppElevation.dialog
            isDragTarget   -> AppElevation.raised
            else           -> AppElevation.card
        },
        animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS),
        label = "cardElevation"
    )
    // O rastro que varria o card durante a coleta era `rememberInfiniteTransition`
    // — animação sem fim, a mesma classe de coisa que trava o `waitForIdle` dos
    // testes de componente. O estado de coleta agora se lê no rótulo do botão,
    // que já dizia "Atualizando…", e na opacidade das cotas.

    val hoverBackground by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.surfaceVariant
                      else           cardContainerColor(),
        animationSpec = tween(durationMillis = AppMotion.fast),
        label = "cardHoverBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(apiUsageCardTag(apiName))
            .hoverable(hoverInteraction)
            .animateContentSize(animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS))
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
                translationY = cardOffsetY.toPx()
            },
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(containerColor = hoverBackground),
        // Sombra só enquanto o card está sendo arrastado, que é quando ele de
        // fato flutua sobre os outros. Em repouso quem o separa do fundo é a
        // borda de 1dp — e era a sombra em toda superfície que fazia o dashboard
        // ler como uma pilha de blocos de mesmo peso.
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = BorderStroke(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant)
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
                // O cabeçalho carrega o próprio padding e o conteúdo abaixo dele
                // encosta na borda: é o `.pbody.flush` do protótipo, onde a
                // divisória de cada cota atravessa o card de ponta a ponta. Com o
                // padding num bloco só em volta de tudo, a divisória parava a 12dp
                // de cada lado e a lista deixava de ler como tabela.
                //
                // `spacedBy` e não `SpaceBetween`: a coluna do título já leva
                // `weight(1f)` e empurra o resto para a direita sozinha, e o
                // arranjo por espaço não deixava vão entre o badge de estado e o
                // primeiro botão — a palavra encostava no ícone.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = density.contentHorizontalPadding,
                            vertical = density.contentVerticalPadding
                        )
                        .testTag(API_USAGE_CARD_HEADER_TAG),
                    horizontalArrangement = Arrangement.spacedBy(density.headerSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(density.headerSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // A identidade da fonte cabe num traço de 2dp. Ela era o
                        // fundo inteiro do card, e com quatro cards abertos o
                        // dashboard virava quatro retângulos coloridos disputando
                        // a atenção que os números deviam ter.
                        AppSourceMarker(
                            color = accentColorFor(source = source, accents = AppAccents.current),
                            height = if (accountContext == null) 18.dp else 28.dp
                        )
                        // Título e conta na mesma coluna, como o `.ptitle`/`.psub`
                        // do protótipo. A conta era uma linha de largura cheia
                        // abaixo do cabeçalho inteiro, alinhada à borda do card e
                        // não ao título de que ela é o subtítulo.
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            HoverTooltipBox(
                                title = apiName,
                                metrics = emptyList()
                            ) {
                                Text(
                                    text = apiName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (accountContext != null) {
                                AccountIdentityLabel(
                                    account = accountContext,
                                    language = language
                                )
                            }
                        }
                        source.statusBadgeLabel(language)?.let { badgeLabel ->
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(AppShapes.small)
                                    .border(
                                        AppBorderWidth,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        AppShapes.small
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // O aviso da fonte sai aqui, no cabeçalho, e não como
                        // banner abaixo das cotas: o cabeçalho é composto tanto
                        // com o card aberto quanto fechado — a garantia que o
                        // aviso de créditos exige — e ocupa a altura que já
                        // existe.
                        if (notices.isNotEmpty()) {
                            CardNoticeHint(
                                notices = notices,
                                language = language,
                                iconSize = density.actionIconSize
                            )
                        }
                    }

                    // Estado da fonte com **ponto e palavra**, ao lado das ações,
                    // como no protótipo. O `RiskSemaphoreDot` de cada cota é só
                    // ponto: sozinho, ele deixa a cor informando o estado, que é
                    // exatamente o que este sistema visual não faz. Aqui a
                    // palavra aparece uma vez, para o card inteiro, e continua
                    // sendo lida com o card minimizado — o cabeçalho é composto
                    // nos dois estados.
                    worstQuotaRisk(
                        quotas = orderedQuotas,
                        riskByQuotaKey = riskByQuotaKey,
                        now = now
                    )?.let { (worstQuota, worstRisk) ->
                        val statusLabel = riskLevelLabel(worstRisk.level, language)
                        Box(modifier = Modifier.testTag(API_USAGE_CARD_STATUS_TAG)) {
                            HoverTooltipBox(
                                title = riskDotTooltipTitle(language),
                                metrics = listOf(
                                    TooltipMetric(
                                        label = if (language == AppLanguage.PT) "Cota" else "Quota",
                                        value = worstQuota.label
                                    ),
                                    TooltipMetric(
                                        label = if (language == AppLanguage.PT) "Status" else "Status",
                                        value = statusLabel
                                    )
                                ),
                                footnote = riskDotTooltipSubtitle(worstRisk, language),
                                modifier = Modifier.testTag(API_USAGE_CARD_STATUS_HINT_TAG)
                            ) {
                                AppStatusIndicator(
                                    label = statusLabel,
                                    tone = toneFor(worstRisk.level),
                                    modifier = Modifier.semantics {
                                        contentDescription = riskStatusContentDescription(
                                            quotaLabel = worstQuota.label,
                                            risk = worstRisk,
                                            language = language
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.testTag(API_USAGE_CARD_ACTIONS_TAG),
                        horizontalArrangement = Arrangement.spacedBy(density.actionSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardIconActionButton(
                            label = refreshActionLabel(
                                isRefreshing = isRefreshing,
                                language = language
                            ),
                            onClick = onRefresh,
                            buttonSize = density.actionButtonSize,
                            enabled = !isRefreshing
                        ) { tint ->
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(density.actionIconSize),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(density.actionIconSize),
                                    tint = tint
                                )
                            }
                        }

                        CardIconActionButton(
                            label = minimizeActionLabel(
                                isMinimized = isMinimized,
                                language = language
                            ),
                            onClick = onToggleMinimized,
                            buttonSize = density.actionButtonSize
                        ) { tint ->
                            Icon(
                                imageVector = if (isMinimized) {
                                    Icons.Rounded.Add
                                } else {
                                    Icons.Rounded.Remove
                                },
                                modifier = Modifier.size(density.actionIconSize),
                                contentDescription = null,
                                tint = tint
                            )
                        }
                    }
                }

                AppDivider()

                AnimatedContent(
                    targetState = isMinimized,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(durationMillis = AppMotion.normal, delayMillis = 50, easing = AppMotion.enterEasing)) +
                            scaleIn(
                                animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS, easing = AppMotion.enterEasing),
                                initialScale = 0.97f
                            )).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.exitEasing)) +
                                scaleOut(
                                    animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.exitEasing),
                                    targetScale = 0.98f
                                )
                        ).using(SizeTransform(clip = false))
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
                        OpenCodeUsageSummary(
                            source = source,
                            quotas = orderedQuotas,
                            language = language,
                            compact = minimized,
                            modifier = blockPadding
                        )
                    } else if (minimized) {
                        CompactQuotaSummary(
                            source = source,
                            quotas = orderedQuotas,
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
                            quotas = orderedQuotas,
                            showUsageDetails = showUsageDetails,
                            language = language,
                            riskByQuotaKey = riskByQuotaKey,
                            density = density,
                            showTooltip = showQuotaTooltip,
                            now = now
                        )
                    }
                }

            // As quatro ações de navegação desceram do cabeçalho para uma barra
            // própria. No topo elas dividiam espaço com atualizar e minimizar, que
            // agem sobre o card, e num card estreito a fileira de seis botões
            // comia o título. Aqui a divisão é por natureza: em cima o que mexe
            // no card, embaixo o que abre outra janela.
            //
            // Continuam sendo botões de ícone com `contentDescription`, e não
            // botões de texto como no protótipo: a descrição carrega a explicação
            // do pisca ("1 sessão ativa agora pede atenção: …"), que é o motivo de
            // o semáforo existir. Texto no botão não teria onde levá-la.
            AppStatusBar {
                CardIconActionButton(
                    label = historyActionLabel(language = language),
                    onClick = onOpenHistory,
                    buttonSize = density.actionButtonSize
                ) { tint ->
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(density.actionIconSize),
                        tint = tint
                    )
                }

                    if (onOpenCliSessions != null) {
                        CardIconActionButton(
                            label = cliSessionsActionLabel(language = language),
                            onClick = onOpenCliSessions,
                            buttonSize = density.actionButtonSize,
                            pulse = cliSessionPulse,
                            language = language
                        ) { tint ->
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(density.actionIconSize),
                                tint = tint
                            )
                        }
                    }

                    // Só chega não-nulo quando a integração está ligada e esta
                    // conta foi marcada como parte do time nas Configurações.
                    if (onOpenTeamUsage != null) {
                        CardIconActionButton(
                            label = teamUsageActionLabel(language = language),
                            onClick = onOpenTeamUsage,
                            buttonSize = density.actionButtonSize,
                            pulse = teamSessionPulse,
                            language = language
                        ) { tint ->
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(density.actionIconSize),
                                tint = tint
                            )
                        }
                    }

                    // Vizinho do botão de sessões do time de propósito: os dois
                    // abrem janelas do time, e separá-los mandaria o usuário
                    // procurar em dois cantos.
                    //
                    // Sem `pulse`, e o default já é `SessionPulse.EMPTY`. Neste
                    // app o pisca significa uma coisa só — sessão em atenção ou
                    // saturada — e o botão colado a este já a carrega.
                    if (onOpenTeamPresence != null) {
                        CardIconActionButton(
                            label = teamPresenceActionLabel(language = language),
                            onClick = onOpenTeamPresence,
                            buttonSize = density.actionButtonSize
                        ) { tint ->
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(density.actionIconSize),
                                tint = tint
                            )
                        }
                    }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountIdentityLabel(
    account: UsageAccountContext,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = if (language == AppLanguage.PT) {
                        "Conta da última coleta: ${account.displayLabel}"
                    } else {
                        "Account from last snapshot: ${account.displayLabel}"
                    }
                )
            }
        },
        state = tooltipState,
        modifier = modifier
    ) {
        Text(
            text = account.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Alinhada à esquerda, como subtítulo do cabeçalho. Centrada ela
            // flutuava sozinha no meio do card, sem coluna a que pertencer.
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("usageAccountLabel")
                .semantics {
                    contentDescription = if (language == AppLanguage.PT) {
                        "Conta da última coleta: ${account.displayLabel}"
                    } else {
                        "Account from last snapshot: ${account.displayLabel}"
                    }
                }
        )
    }
}

/**
 * Avisos não fatais da fonte, condensados numa exclamação.
 *
 * Eram `AppBanner` empilhados abaixo das cotas, e o texto deles não muda entre
 * coletas: na janela estreita do modo somente cards os dois avisos do Codex
 * ocupavam mais altura que o número que o card existe para mostrar (issue #76).
 *
 * O sinal continua sem hover — é o ícone âmbar, e ele mora no cabeçalho, que é
 * composto também com o card minimizado. O que passou a exigir hover é o texto.
 *
 * Sem piso de largura, ao contrário da tooltip de cota: aquele piso existe
 * porque o popup cobre o número que o ponteiro apontava, e este não aponta
 * número nenhum. Sem a tooltip o aviso ficaria inacessível justamente na janela
 * estreita, que é onde ele mais atrapalhava.
 */
@Composable
private fun CardNoticeHint(
    notices: Set<ApiUsageNotice>,
    language: AppLanguage,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    // Mesma ordem estável dos banners que este hint substituiu.
    val texts = remember(notices, language) {
        notices
            .toList()
            .sortedBy { notice -> notice.ordinal }
            .map { notice -> noticeText(notice = notice, language = language) }
    }
    if (texts.isEmpty()) return

    val title = noticeHintTitle(count = texts.size, language = language)
    // Bullet só com dois ou mais: marcador solto numa frase única é ruído.
    val body = remember(texts) {
        if (texts.size == 1) {
            texts.first()
        } else {
            texts.joinToString(separator = "\n") { text -> "• $text" }
        }
    }
    // A descrição carrega as frases inteiras: sem hover a tooltip não existe na
    // árvore, e é por ela que leitor de tela e testes chegam ao aviso.
    val description = remember(title, texts) {
        "$title: ${texts.joinToString(separator = " ")}"
    }

    HoverTooltipBox(
        title = title,
        metrics = emptyList(),
        footnote = body,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = description,
            modifier = Modifier.size(iconSize),
            // O aviso é do estado da fonte, não da identidade dela: pintá-lo com
            // a cor da API diria "Codex" onde precisa dizer "atenção".
            tint = AppTone.WARNING.color()
        )
    }
}

private fun noticeHintTitle(count: Int, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        if (count == 1) "Aviso" else "Avisos"
    } else {
        if (count == 1) "Notice" else "Notices"
    }
}

private fun noticeText(notice: ApiUsageNotice, language: AppLanguage): String {
    return when (notice) {
        ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE -> {
            if (language == AppLanguage.PT) {
                "Quota 7d indisponível na fonte semanal do Codex"
            } else {
                "7d quota unavailable in Codex weekly source"
            }
        }
        ApiUsageNotice.SOURCE_UNSTABLE -> {
            if (language == AppLanguage.PT) {
                "Fonte de uso do Codex instável: o contrato mudou e os limites podem oscilar até estabilizar."
            } else {
                "Codex usage source is unstable: the contract changed and limits may fluctuate until it stabilizes."
            }
        }
        ApiUsageNotice.EXTRA_CREDITS_UNAVAILABLE -> {
            if (language == AppLanguage.PT) {
                "Créditos de uso não vieram nesta coleta. O saldo no claude.ai continua valendo."
            } else {
                "Usage credits missing from this snapshot. The balance on claude.ai still holds."
            }
        }
    }
}

@Composable
private fun OpenCodeUsageSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val modelSummaries = remember(quotas) { buildOpenCodeModelSummaries(quotas) }

    if (modelSummaries.isEmpty()) {
        // Superfície neutra com borda, como todo bloco de dado do sistema. O
        // fundo pintado com a cor da fonte era o resto do card colorido que a
        // refatoração tirou do resto do dashboard: aqui ele sobreviveu porque
        // OpenCode e Kilo não entram nas capturas.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(AppShapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = if (language == AppLanguage.PT) "Nenhum uso free detectado" else "No free usage detected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (language == AppLanguage.PT) {
                    "Abra o ${source.displayName(language)} e use um modelo free para começar a preencher este card."
                } else {
                    "Use a free ${source.displayName(language)} model to start populating this card."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        modelSummaries.forEach { summary ->
            OpenCodeModelRow(
                source = source,
                summary = summary,
                language = language,
                compact = compact
            )
        }
    }
}

@Composable
private fun OpenCodeModelRow(
    source: ApiSource,
    summary: OpenCodeModelSummary,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // No modo compacto a linha não renderiza as barras internas (que já têm
    // tooltip própria), então é seguro dar tooltip à linha inteira.
    if (compact) {
        HoverTooltipBox(
            title = summary.modelName,
            subtitle = if (language == AppLanguage.PT) "Atividade observada" else "Observed activity",
            metrics = buildOpenCodeTooltipMetrics(summary = summary, language = language),
            modifier = modifier
        ) {
            OpenCodeModelRowContent(
                source = source,
                summary = summary,
                language = language,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        return
    }

    OpenCodeModelRowContent(
        source = source,
        summary = summary,
        language = language,
        compact = false,
        modifier = modifier
    )
}

@Composable
private fun OpenCodeModelRowContent(
    source: ApiSource,
    summary: OpenCodeModelSummary,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // A identidade da fonte fica no marcador de 2dp do cabeçalho do card, como em
    // todos os outros. Aqui ela pintava o bloco inteiro, e num card com três
    // modelos eram três retângulos coloridos dentro de um card já identificado.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .padding(horizontal = AppSpacing.md, vertical = if (compact) AppSpacing.sm else AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else AppSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (language == AppLanguage.PT) "Limite oficial indisponível" else "Official limit unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = localizedRequestCount(summary.requestsFiveHours, language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = openCodePrimaryWindowLabel(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = openCodeSecondaryWindowLabel(summary.requestsSevenDays, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!compact) {
            OpenCodeInlineComparisonChart(
                source = source,
                summary = summary,
                language = language
            )
        }
    }
}


@Composable
private fun OpenCodeInlineComparisonChart(
    source: ApiSource,
    summary: OpenCodeModelSummary,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val maxValue = maxOf(summary.requestsFiveHours, summary.requestsSevenDays, 1L).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (language == AppLanguage.PT) {
                "Atividade observada"
            } else {
                "Observed activity"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OpenCodeInlineBar(
            modelName = summary.modelName,
            label = "5h",
            value = summary.requestsFiveHours,
            fraction = summary.requestsFiveHours / maxValue,
            language = language
        )
        OpenCodeInlineBar(
            modelName = summary.modelName,
            label = "7d",
            value = summary.requestsSevenDays,
            fraction = summary.requestsSevenDays / maxValue,
            language = language
        )
    }
}

@Composable
private fun OpenCodeInlineBar(
    modelName: String,
    label: String,
    value: Long,
    fraction: Float,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HoverTooltipBox(
            title = modelName,
            subtitle = if (language == AppLanguage.PT) {
                "Atividade observada"
            } else {
                "Observed activity"
            },
            metrics = listOf(
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Janela" else "Window",
                    value = label
                ),
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Requisições" else "Requests",
                    value = value.toString()
                )
            ),
            modifier = Modifier.weight(1f)
        ) {
            // A barra do sistema: 4dp, com borda e trilha neutra. Esta era a
            // única do app com 8dp e superfície com alpha própria.
            AppProgressTrack(fraction = fraction, tone = AppTone.INFO)
        }

        Text(
            text = if (language == AppLanguage.PT) "$value req." else "$value req.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun CardIconActionButton(
    label: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    enabled: Boolean = true,
    /** Semáforo das sessões em curso; vazio deixa o botão em repouso. */
    pulse: SessionPulse = SessionPulse.EMPTY,
    language: AppLanguage = AppLanguage.PT,
    /** Recebe a cor do ícone: a do tema em repouso, a da severidade no pisca. */
    content: @Composable (Color) -> Unit
) {
    val frame = rememberSessionPulseFrame(pulse)
    // O motivo entra na descrição, e não só na tooltip: um botão que pisca sem
    // explicação obriga justamente o clique que o semáforo quer poupar.
    //
    // Memorizado porque o pisca recompõe este botão a cada quadro: sem isso o
    // texto seria remontado sessenta vezes por segundo sem nunca mudar.
    val hint = remember(pulse, language) { sessionPulseHint(pulse, language) }
    val description = remember(label, hint) { if (hint == null) label else "$label — $hint" }
    val tint = frame?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Em repouso o botão é a própria superfície do card: o contêiner tonal do
    // Material acrescentava um segundo tom de fundo por botão, e são até seis.
    val containerColor = sessionPulseContainerColor(
        frame = frame,
        resting = Color.Transparent
    )

    HoverTooltipBox(
        title = label,
        subtitle = hint,
        metrics = emptyList()
    ) {
        // Quadrado de raio 6 no lugar do botão circular preenchido: seis
        // círculos no cabeçalho pesavam mais que o número que o card existe
        // para mostrar. O contêiner só ganha cor quando o semáforo está aceso —
        // aí a cor é informação, não decoração.
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(AppShapes.small)
                .background(containerColor)
                .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = description
                },
            contentAlignment = Alignment.Center
        ) {
            content(tint)
        }
    }
}

@Composable
private fun CompactQuotaSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    stacked: Boolean,
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    if (quotas.size == 1) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val badgeWidthFraction = if (maxWidth < 360.dp) 0.76f else 0.5f

            CompactQuotaBadge(
                source = source,
                quota = quotas.first(),
                showUsageDetails = showUsageDetails,
                language = language,
                risk = riskByQuotaKey[quotas.first().seriesKey],
                density = density,
                showTooltip = showTooltip,
                now = now,
                modifier = Modifier.fillMaxWidth(badgeWidthFraction)
            )
        }

        return
    }

    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(density.compactQuotaSpacing)
        ) {
            quotas.forEach { quota ->
                CompactQuotaBadge(
                    source = source,
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    density = density,
                    showTooltip = showTooltip,
                    now = now,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(density.compactQuotaSpacing),
        verticalAlignment = Alignment.Top
    ) {
        quotas.forEach { quota ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                CompactQuotaBadge(
                    source = source,
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    density = density,
                    showTooltip = showTooltip,
                    now = now,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactQuotaBadge(
    source: ApiSource,
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    density: ApiUsageCardDensity,
    /** Ver `shouldShowQuotaTooltip`: em card estreito o popup cobre o card. */
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    // Sem tooltip a `testTag` do bloco desce para o conteúdo: presa ao
    // `HoverTooltipBox`, o nó sumiria da árvore em card estreito.
    if (!showTooltip) {
        CompactQuotaBadgeContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            density = density,
            now = now,
            modifier = modifier.testTag(quotaBlockTag(quota.label))
        )

        return
    }

    HoverTooltipBox(
        title = quota.label,
        subtitle = expandedQuotaTitle(quota = quota, language = language),
        metrics = buildQuotaTooltipMetrics(quota = quota, language = language, now = now, risk = risk),
        footnote = risk?.let { riskDotTooltipSubtitle(risk = it, language = language) },
        modifier = modifier.testTag(quotaBlockTag(quota.label))
    ) {
        CompactQuotaBadgeContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            density = density,
            now = now,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactQuotaBadgeContent(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    density: ApiUsageCardDensity,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val isExpired = quota.isExpiredAt(now)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPACT_QUOTA_BADGE_TAG)
            .clip(AppShapes.small)
            // Fundo neutro e borda: o tom de acento em bloco fazia o card
            // fechado — que existe para ocupar pouco — chamar mais atenção
            // que o aberto.
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .padding(
                horizontal = density.badgeHorizontalPadding,
                vertical = density.badgeVerticalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (risk != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A tooltip do ponto fica desligada: o badge inteiro já tem a
                // própria tooltip e dois TooltipBox aninhados disputam o hover.
                RiskSemaphoreDot(
                    risk = risk,
                    quotaLabel = quota.label,
                    language = language,
                    showTooltip = false
                )
                Text(
                    text = quota.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = quota.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = compactPercentageLabel(quota),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            // Mesmo tratamento do arco: o numero e o da janela anterior.
            modifier = Modifier.alpha(if (isExpired) STALE_QUOTA_ALPHA else 1f)
        )

        val detailText = quotaDetailText(quota = quota, showUsageDetails = showUsageDetails)
        if (detailText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * As cotas do card expandido, uma por linha.
 *
 * Eram colunas com um arco de 92dp cada. O arco ocupava a maior parte da altura
 * do card para dizer um número que a linha diz em 12sp, e três deles lado a lado
 * — a Anthropic tem três cotas desde os créditos de uso — obrigavam uma regra de
 * empilhamento própria, com largura mínima por coluna. Empilhado sempre, essa
 * regra deixa de existir: a linha ocupa a largura que o card tiver.
 */
@Composable
private fun ExpandedQuotaSummary(
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    /** Ver `shouldShowQuotaTooltip`: em card estreito o popup cobre o card. */
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    // Linha de dados, com divisória própria e sem vão entre elas: é a mesma
    // decisão da lista do time. O vão fazia três cotas lerem como três blocos
    // empilhados, e sem a divisória o rótulo de uma encostava no reinício da
    // anterior sem nada dizendo onde uma termina.
    Column(modifier = modifier.fillMaxWidth()) {
        quotas.forEachIndexed { index, quota ->
            AppDataRow(
                showDivider = index != quotas.lastIndex,
                horizontalPadding = density.contentHorizontalPadding,
                verticalPadding = density.contentVerticalPadding
            ) {
                QuotaRow(
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    showTooltip = showTooltip,
                    now = now,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Uma cota: rótulo e valor na mesma linha, barra abaixo, reinício embaixo.
 *
 * A barra é a leitura de relance que o arco fazia, na altura de 4dp em vez de
 * 92. Cota em moeda **não** ganha barra: o saldo da DeepSeek não tem um total
 * contra o qual medir, e uma barra ali desenharia uma fração inventada. Os
 * créditos de uso ganham, porque têm limite mensal declarado.
 */
@Composable
private fun QuotaRow(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    /** Ver `shouldShowQuotaTooltip`: em card estreito o popup cobre o card. */
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    // Sem tooltip a `testTag` do bloco desce para o conteúdo: presa ao
    // `HoverTooltipBox`, o nó sumiria da árvore em card estreito.
    if (!showTooltip) {
        QuotaRowContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            now = now,
            modifier = modifier.testTag(quotaBlockTag(quota.label))
        )

        return
    }

    HoverTooltipBox(
        title = quota.label,
        subtitle = expandedQuotaTitle(quota = quota, language = language),
        metrics = buildQuotaTooltipMetrics(quota = quota, language = language, now = now, risk = risk),
        footnote = risk?.let { riskDotTooltipSubtitle(risk = it, language = language) },
        modifier = modifier.testTag(quotaBlockTag(quota.label))
    ) {
        QuotaRowContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            now = now,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QuotaRowContent(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val isExpired = quota.isExpiredAt(now)
    val staleAlpha = if (isExpired) STALE_QUOTA_ALPHA else 1f
    val hasTrack = quota.unit != UsageUnit.CURRENCY_USD || quota.isExtraCreditsQuota

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (risk != null) {
                RiskSemaphoreDot(
                    risk = risk,
                    quotaLabel = quota.label,
                    language = language,
                    showTooltip = false
                )
            }
            Text(
                text = expandedQuotaTitle(quota = quota, language = language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = compactPercentageLabel(quota),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // Mesmo tratamento de antes: janela vencida mostra o último
                // dado real da fonte, esmaecido para não passar por corrente.
                modifier = Modifier.alpha(staleAlpha)
            )
        }

        if (hasTrack) {
            AppProgressTrack(
                fraction = quota.percentageUsed,
                tone = quotaTone(quota = quota, risk = risk),
                modifier = Modifier
                    .testTag(quotaProgressTrackTag(quota.label))
                    .alpha(staleAlpha)
            )
        }

        val detailText = quotaDetailText(quota = quota, showUsageDetails = showUsageDetails)
        if (detailText != null) {
            Text(
                text = detailText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = resetLabel(quota = quota, language = language, now = now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

/**
 * Severidade da barra.
 *
 * O risco projetado tem prioridade sobre o percentual porque responde à pergunta
 * certa: 40% às onze da manhã pode ser pior que 80% faltando dez minutos para o
 * reinício. Sem projeção conhecida, sobra o percentual, com os mesmos cortes de
 * 75 e 90 que os alertas da bandeja usam.
 */
private fun quotaTone(quota: QuotaInfo, risk: QuotaRiskSummary?): AppTone {
    if (risk != null) {
        return toneFor(risk.level)
    }
    val percent = quota.percentageUsed * 100f
    return when {
        percent >= 90f -> AppTone.CRITICAL
        percent >= 75f -> AppTone.WARNING
        else -> AppTone.OK
    }
}
