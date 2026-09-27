package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.statusBadgeLabel
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appTween
import kotlinx.datetime.Instant

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

@Composable
internal fun CardIconActionButton(
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
    val accents = AppAccents.current
    val tint = frame?.color(accents) ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Em repouso o botão é a própria superfície do card: o contêiner tonal do
    // Material acrescentava um segundo tom de fundo por botão, e são até seis.
    val containerColor = sessionPulseContainerColor(
        frame = frame,
        resting = Color.Transparent,
        accents = accents
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
        // Sem hover nem pressão, a ação do card era o único botão da tela que não
        // respondia ao ponteiro. Ganha as duas camadas e a escala de pressão --
        // é superfície sem texto, então encolher não borra nada.
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val pressed by interaction.collectIsPressedAsState()
        val ladder = AppSurfaceLadders.current
        val layer by animateColorAsState(
            targetValue = when {
                !enabled -> Color.Transparent
                pressed -> ladder.pressedLayer
                hovered -> ladder.hoverLayer
                else -> Color.Transparent
            },
            animationSpec = appTween(AppMotion.fast),
            label = "cardActionLayer"
        )
        Box(
            modifier = Modifier
                .size(buttonSize)
                .appPressScale(interaction, enabled)
                .appSurfaceBlock(color = containerColor)
                .background(layer)
                .hoverable(interaction, enabled = enabled)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = description
                },
            contentAlignment = Alignment.Center
        ) {
            content(tint)
        }
    }
}

/**
 * O glifo de recarga do card.
 *
 * Coletando, ele **gira** — só com [com.usagemonitor.presentation.ui.theme.AppMotionPolicy.continuous]
 * ligada, que é o app em uso; nos testes e nos geradores de captura ele fica
 * parado no tom de informação, e a semântica ("Atualizando…") continua dizendo o
 * estado. Era um `CircularProgressIndicator` do Material: outra espessura, outro
 * raio e animação infinita incondicional, a mesma classe de coisa que trava o
 * `waitForIdle`.
 */
@Composable
internal fun RefreshGlyph(refreshing: Boolean, tint: Color, size: Dp) {
    val policy = LocalAppMotionPolicy.current
    val rotation = if (refreshing && policy.continuous) {
        val transition = rememberInfiniteTransition(label = "refreshGlyph")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(REFRESH_TURN_MILLIS, easing = LinearEasing)),
            label = "refreshGlyphAngle"
        )
        angle
    } else {
        0f
    }
    Icon(
        imageVector = Icons.Rounded.Refresh,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
        tint = if (refreshing) AppTone.INFO.color() else tint
    )
}

/** Uma volta por segundo: rápido o bastante para ler "trabalhando", lento para não agitar. */
private const val REFRESH_TURN_MILLIS = 1_000

/** A marca do cabeçalho: do tamanho do glifo de ação, para não disputar com o título. */
private val PROVIDER_MARK_SIZE = 16.dp

/** O selo do cabeçalho do card: plano da conta, fonte local. */
@Composable
private fun CardHeaderBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .appSurfaceBlock(color = Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** O selo do plano; os testes o acham por aqui. */
const val API_USAGE_CARD_PLAN_TAG = "apiUsageCardPlan"

/** O cabeçalho do card: identidade da fonte e da conta, avisos, estado e as ações sobre o card. */
@Composable
internal fun ApiUsageCardHeader(
    source: ApiSource,
    apiName: String,
    accountContext: UsageAccountContext?,
    notices: Set<ApiUsageNotice>,
    lastReadingAt: Instant?,
    planLabel: String?,
    accent: Color?,
    emoji: AccountEmoji?,
    quotas: List<QuotaInfo>,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    now: Instant,
    isRefreshing: Boolean,
    isMinimized: Boolean,
    language: AppLanguage,
    density: ApiUsageCardDensity,
    onRefresh: () -> Unit,
    onToggleMinimized: () -> Unit
) {
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
                color = accent ?: accentColorFor(source = source, accents = AppAccents.current),
                height = if (accountContext == null) 18.dp else 28.dp
            )
            // A marca do fornecedor, no acento da fonte: reconhecer o
            // card antes de ler o título, como no Codenotch e no
            // ai-usagebar. O traço continua — ele é a identidade no
            // alinhamento vertical da grade; a marca é a do olho.
            AppProviderMark(
                source = source,
                tint = accent ?: accentColorFor(source = source, accents = AppAccents.current),
                size = PROVIDER_MARK_SIZE
            )
            // O emoji da conta (issue #287): o mesmo selo do anel da
            // HUD. A identidade não pode existir num lugar e sumir no
            // outro.
            if (emoji != null) {
                AccountEmojiGlyph(
                    emoji = emoji,
                    size = PROVIDER_MARK_SIZE,
                    modifier = Modifier.testTag(API_USAGE_CARD_EMOJI_TAG)
                )
            }
            // Título e conta na mesma coluna, como o `.ptitle`/`.psub`
            // do protótipo. A conta era uma linha de largura cheia
            // abaixo do cabeçalho inteiro, alinhada à borda do card e
            // não ao título de que ela é o subtítulo.
            Column(modifier = Modifier.weight(1f, fill = false)) {
                // O plano da conta colado no nome — o "Claude Max 20x"
                // do ai-usagebar. Na linha do título e não depois da
                // coluna: a coluna mede o e-mail, que é mais largo, e o
                // selo ia parar longe do nome de que ele é atributo.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HoverTooltipBox(
                        title = apiName,
                        metrics = emptyList(),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = apiName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    planLabel?.let { plan -> CardHeaderBadge(plan, Modifier.testTag(API_USAGE_CARD_PLAN_TAG)) }
                }
                if (accountContext != null) {
                    AccountIdentityLabel(
                        account = accountContext,
                        language = language
                    )
                }
            }
            source.statusBadgeLabel(language)?.let { badgeLabel -> CardHeaderBadge(badgeLabel) }

            // O aviso da fonte sai aqui, no cabeçalho, e não como
            // banner abaixo das cotas: o cabeçalho é composto tanto
            // com o card aberto quanto fechado — a garantia que o
            // aviso de créditos exige — e ocupa a altura que já
            // existe.
            if (notices.isNotEmpty()) {
                CardNoticeHint(
                    notices = notices,
                    source = source,
                    language = language,
                    lastReadingAgeMinutes = lastReadingAt?.let { at -> (now - at).inWholeMinutes.coerceAtLeast(0) },
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
            quotas = quotas,
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
                RefreshGlyph(
                    refreshing = isRefreshing,
                    tint = tint,
                    size = density.actionIconSize
                )
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
}

/** As ações que abrem outra janela, abaixo das cotas. */
@Composable
internal fun ApiUsageCardNavigationBar(
    language: AppLanguage,
    density: ApiUsageCardDensity,
    cliSessionPulse: SessionPulse,
    teamSessionPulse: SessionPulse,
    onOpenHistory: () -> Unit,
    onOpenCliSessions: (() -> Unit)?,
    onOpenCodexCliSessions: (() -> Unit)?,
    onOpenTeamUsage: (() -> Unit)?,
    onOpenTeamPresence: (() -> Unit)?
) {
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
        // A ordem e as condições são de `cardActionsFor`, dono único da
        // regra; aqui chegam como lambdas nulas ou não.
        val actions = buildList {
            add(CardAction.HISTORY)
            if (onOpenCodexCliSessions != null) add(CardAction.CODEX_CLI_SESSIONS)
            if (onOpenCliSessions != null) add(CardAction.CLI_SESSIONS)
            if (onOpenTeamUsage != null) add(CardAction.TEAM_USAGE)
            if (onOpenTeamPresence != null) add(CardAction.TEAM_PRESENCE)
        }
        actions.forEach { action ->
            CardActionButton(
                action = action,
                language = language,
                buttonSize = density.actionButtonSize,
                iconSize = density.actionIconSize,
                cliSessionPulse = cliSessionPulse,
                teamSessionPulse = teamSessionPulse,
                onClick = when (action) {
                    CardAction.HISTORY -> onOpenHistory
                    CardAction.CODEX_CLI_SESSIONS -> onOpenCodexCliSessions ?: {}
                    CardAction.CLI_SESSIONS -> onOpenCliSessions ?: {}
                    CardAction.TEAM_USAGE -> onOpenTeamUsage ?: {}
                    CardAction.TEAM_PRESENCE -> onOpenTeamPresence ?: {}
                }
            )
        }
    }
}
