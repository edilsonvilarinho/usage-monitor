package com.usagemonitor.presentation.ui.help

import com.usagemonitor.presentation.ui.CommonLabels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppModalRevealScope
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.StartupTipsToggle
import com.usagemonitor.presentation.ui.components.appModalRevealRow
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val STARTUP_TIP_TAG = "startupTip"
const val STARTUP_TIP_NEXT_TAG = "startupTipNext"
const val STARTUP_TIP_HELP_TAG = "startupTipHelp"

/**
 * A dica do dia (#411, direção AC4): o mesmo cartão do guia em tamanho menor.
 *
 * O título é o do tópico da Ajuda e a demo é a dele — a dica diz uma coisa, e
 * "Ver na Ajuda" abre o tópico inteiro. O interruptor é o mesmo das
 * Configurações › Sistema: desligar aqui desliga lá, sem segunda verdade.
 *
 * Uma primária por tela, e aqui ela é fechar: a dica informa, não propõe
 * trabalho — o mesmo critério das Novidades.
 */
@Composable
fun StartupTipContent(
    tipIndex: Int,
    language: AppLanguage,
    tipsEnabled: Boolean,
    onTipsEnabledChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onOpenHelp: (HelpTopic) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    media: HelpMediaState = HelpMediaState.Unavailable,
    tips: List<StartupTip> = StartupTipCatalog.tips
) {
    val isPt = language == AppLanguage.PT
    val index = tipIndex.coerceIn(0, tips.lastIndex)
    val tip = tips[index]
    val topicTitle = HelpCatalog.entry(tip.topic, language).title

    Surface(
        modifier = modifier.fillMaxSize().testTag(STARTUP_TIP_TAG),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val mediaHeight = minOf(TIP_MEDIA_HEIGHT, maxHeight * TIP_MEDIA_HEIGHT_SHARE)
                AppModalRevealScope(replayKey = index) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        AppDataSurfaceFlush(
                            header = {
                                AppSectionHeader(
                                    title = topicTitle,
                                    subtitle = startupTipCounterLabel(index, tips.size, language),
                                    markerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            HelpMediaFrame(
                                media = media,
                                title = topicTitle,
                                isPt = isPt,
                                height = mediaHeight,
                                modifier = Modifier.padding(AppSpacing.md)
                            )
                            Text(
                                text = tip.text(language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.appModalRevealRow().padding(
                                    start = AppSpacing.md,
                                    end = AppSpacing.md,
                                    bottom = AppSpacing.md
                                )
                            )
                        }
                        AppDataSurfaceFlush {
                            StartupTipsToggle(
                                enabled = tipsEnabled,
                                language = language,
                                onToggle = onTipsEnabledChange,
                                showDivider = false
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppButton(
                    label = if (isPt) "Ver na Ajuda" else "Open in Help",
                    onClick = { onOpenHelp(tip.topic) },
                    tone = AppButtonTone.GHOST,
                    modifier = Modifier.testTag(STARTUP_TIP_HELP_TAG)
                )
                Spacer(modifier = Modifier.weight(1f))
                AppButton(
                    label = if (isPt) "Próxima dica" else "Next tip",
                    onClick = onNext,
                    modifier = Modifier.testTag(STARTUP_TIP_NEXT_TAG)
                )
                AppButton(
                    label = CommonLabels.close(language),
                    onClick = onClose,
                    tone = AppButtonTone.PRIMARY
                )
            }
        }
    }
}

/**
 * Teto da demo na dica. Menor que no guia: a janela é estreita, e a demo aqui
 * ilustra — a leitura de perto é em "Ver na Ajuda", onde ela tem a largura toda.
 */
private val TIP_MEDIA_HEIGHT = 260.dp

private const val TIP_MEDIA_HEIGHT_SHARE = 0.5f
