package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppShapes

const val FOOTER_ADMIN_OVERVIEW_TEST_TAG = "footerAdminOverview"

const val FOOTER_TEAM_PRESENCE_TEST_TAG = "footerTeamPresence"

const val FOOTER_HELP_TEST_TAG = "footerHelp"

/**
 * Âncoras do rodapé.
 *
 * A versão e a contagem regressiva são hoje dois emblemas arredondados e viram
 * texto solto de uma barra de estado. O conteúdo é o mesmo — `v1.1.0`, `02:05` —,
 * mas o nó que o carrega muda de forma, e o assert por texto encontraria também
 * qualquer outro lugar da tela onde a versão apareça.
 */
const val FOOTER_VERSION_TEST_TAG = "footerVersion"
const val FOOTER_COUNTDOWN_TEST_TAG = "footerCountdown"

/**
 * Alvo de clique do rodapé: quadrado, dentro da barra de estado de 30dp.
 *
 * Era 26/14 e ficava pequeno demais em tela real — o ícone ocupava pouco mais da
 * metade do alvo. 28 é o máximo que cabe na barra sem encostar na divisória de
 * cima, e 16 é o tamanho de ícone que o resto do app já usa.
 */
private val FOOTER_ACTION_SIZE = 28.dp
private val FOOTER_ICON_SIZE = 16.dp

@Composable
fun FooterBar(
    appVersion: String,
    language: AppLanguage,
    nextRefreshAt: Instant,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    nowProvider: () -> Instant = { Clock.System.now() },
    countdownUpdatesEnabled: Boolean = true,
    waitNextTick: suspend () -> Unit = { delay(1_000L) },
    /**
     * Abre a visão de todas as contas do servidor.
     *
     * `null` esconde o botão — é o estado de quem não administra, que é a
     * maioria. O componente segue sem saber o que é modo admin: quem decide é o
     * `Main`, a partir das configurações de time.
     */
    onOpenAdminOverview: (() -> Unit)? = null,
    /**
     * Abre a presença de todas as contas do servidor.
     *
     * `null` esconde, pela mesma razão de [onOpenAdminOverview]: o integrante
     * comum entra pela porta do card, que já é escopada na conta dele.
     */
    onOpenTeamPresence: (() -> Unit)? = null,
    /**
     * Abre a janela de ajuda.
     *
     * Default vazio pela mesma razão dos demais: os geradores de captura montam
     * o rodapé sem despachar nada. No app ele está sempre presente — a ajuda não
     * depende de configuração nenhuma.
     */
    onOpenHelp: () -> Unit = {}
) {
    val initialRemaining = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
    var secondsUntilRefresh by remember(nextRefreshAt) { mutableStateOf(initialRemaining) }

    LaunchedEffect(nextRefreshAt, countdownUpdatesEnabled) {
        secondsUntilRefresh = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
        if (!countdownUpdatesEnabled) {
            return@LaunchedEffect
        }

        while (true) {
            val remaining = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
            secondsUntilRefresh = remaining
            if (remaining <= 0) {
                break
            }
            waitNextTick()
        }
    }

    // Barra de estado, não faixa elevada: o rodapé é a última linha da janela e
    // o que o separa do conteúdo é a divisória de 1dp, não uma elevação tonal.
    AppStatusBar(modifier = modifier) {
        FooterCompactStatusGroup(
            appVersion = appVersion,
            secondsUntilRefresh = secondsUntilRefresh,
            language = language,
            modifier = Modifier.weight(1f)
        )
        FooterActionGroup(
            language = language,
            onRefresh = onRefresh,
            onOpenSettings = onOpenSettings,
            onOpenAdminOverview = onOpenAdminOverview,
            onOpenTeamPresence = onOpenTeamPresence,
            onOpenHelp = onOpenHelp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FooterCompactStatusGroup(
    appVersion: String,
    secondsUntilRefresh: Int,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val refreshLabel = formatRefreshCountdown(secondsUntilRefresh)
    val versionTooltip = if (language == AppLanguage.PT) "Versão do app" else "App version"
    val refreshTooltip = if (language == AppLanguage.PT) {
        "Próxima atualização automática"
    } else {
        "Next automatic refresh"
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FooterCompactBadge(
            text = "v$appVersion",
            tooltipLabel = versionTooltip,
            testTag = FOOTER_VERSION_TEST_TAG
        )
        FooterCompactBadge(
            text = refreshLabel,
            tooltipLabel = refreshTooltip,
            testTag = FOOTER_COUNTDOWN_TEST_TAG
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FooterCompactBadge(
    text: String,
    tooltipLabel: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    // Texto solto, sem cápsula. Dois emblemas arredondados numa barra de 30dp
    // repetiam a moldura que a barra já é; a tooltip, que é o que explica o que
    // o número significa, continua.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltipLabel)
            }
        },
        state = rememberTooltipState(isPersistent = true)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A tag mora no texto: é o texto que o assert compara.
            modifier = modifier
                .let { base -> if (testTag == null) base else base.testTag(testTag) }
        )
    }
}

@Composable
private fun FooterActionGroup(
    language: AppLanguage,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAdminOverview: (() -> Unit)? = null,
    onOpenTeamPresence: (() -> Unit)? = null,
    onOpenHelp: () -> Unit = {}
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vem antes da visão global e ao lado dela: as duas são do administrador
        // e abrem o servidor inteiro, só que por perguntas diferentes — quem está
        // aí agora, e quanto cada um gastou.
        if (onOpenTeamPresence != null) {
            FooterIconActionButton(
                label = if (language == AppLanguage.PT) {
                    "Quem está conectado agora"
                } else {
                    "Who is connected now"
                },
                onClick = onOpenTeamPresence,
                testTag = FOOTER_TEAM_PRESENCE_TEST_TAG
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(FOOTER_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (onOpenAdminOverview != null) {
            FooterIconActionButton(
                label = if (language == AppLanguage.PT) {
                    "Ver todas as contas do time"
                } else {
                    "View all team accounts"
                },
                onClick = onOpenAdminOverview,
                testTag = FOOTER_ADMIN_OVERVIEW_TEST_TAG
            ) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(FOOTER_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FooterIconActionButton(
            label = if (language == AppLanguage.PT) "Atualizar agora" else "Refresh now",
            onClick = onRefresh
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(FOOTER_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FooterIconActionButton(
            label = if (language == AppLanguage.PT) "Abrir configurações" else "Open settings",
            onClick = onOpenSettings
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(FOOTER_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // A ajuda é a última do grupo: é a ação de que menos se precisa depois
        // que se aprendeu o app, e as duas anteriores são as do uso diário.
        FooterIconActionButton(
            label = if (language == AppLanguage.PT) "Abrir ajuda" else "Open help",
            onClick = onOpenHelp,
            testTag = FOOTER_HELP_TEST_TAG
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = null,
                modifier = Modifier.size(FOOTER_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FooterIconActionButton(
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(label)
            }
        },
        state = rememberTooltipState(isPersistent = true)
    ) {
        Box(
            modifier = Modifier
                .size(FOOTER_ACTION_SIZE)
                .clip(AppShapes.small)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = label
                }
                .let { base -> if (testTag == null) base else base.testTag(testTag) },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

internal fun formatRefreshCountdown(secondsUntilRefresh: Int): String {
    val minutes = secondsUntilRefresh / 60
    val seconds = secondsUntilRefresh % 60
    return String.format("%02d:%02d", minutes, seconds)
}
