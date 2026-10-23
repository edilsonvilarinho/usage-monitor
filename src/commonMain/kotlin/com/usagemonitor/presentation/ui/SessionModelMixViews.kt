package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ModelShare
import com.usagemonitor.domain.entity.ModelShareBasis
import com.usagemonitor.domain.entity.SessionModelMix
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val SESSION_MODEL_TABLE_TAG = "sessionModelTable"

const val SESSION_MODEL_ROW_TAG_PREFIX = "sessionModelRow:"

/** Tag da linha de modelos dentro da linha de uma sessão. */
fun sessionModelMixLineTag(sessionId: String): String = "sessionModelMix:$sessionId"

// Larguras da tabela de modelos da sessão. Somam 598dp mais o vão, abaixo do
// que o detalhe deixa numa janela de 960dp: a linha não quebra.
private val MODEL_COLUMN_MIN = 150.dp

private val TURNS_COLUMN = 64.dp

private val VALUE_COLUMN = 96.dp

/** "Tokens (com cache)" corta abaixo de 136dp com a Plex Mono; é a largura da coluna da lista. */
private val TOKENS_COLUMN = 136.dp

private val SHARE_COLUMN = 110.dp

/** Os textos do corte por modelo da sessão (#445), PT/EN por [localized]. */
internal object SessionModelMixLabels {

    fun title(language: AppLanguage): String = localized(language, "Modelos da sessão", "Session models")

    /** O que a fatia mede; a base muda quando nada na sessão tem tarifa. */
    fun basis(basis: ModelShareBasis, language: AppLanguage): String =
        when (basis) {
            ModelShareBasis.COST -> localized(language, "% do custo", "% of cost")
            ModelShareBasis.TOKENS -> localized(language, "% dos tokens", "% of tokens")
        }

    fun subtitle(mix: SessionModelMix, language: AppLanguage): String {
        val count = mix.shares.size
        val models = localized(language, if (count == 1) "1 modelo" else "$count modelos", if (count == 1) "1 model" else "$count models")
        val basisNote = when (mix.basis) {
            ModelShareBasis.COST -> localized(language, "participação no custo estimado", "share of the estimated cost")
            ModelShareBasis.TOKENS -> localized(language, "participação nos tokens, sem tarifa", "share of the tokens, no price")
        }
        return "$models · $basisNote"
    }

    fun unknownModel(language: AppLanguage): String = localized(language, "modelo desconhecido", "unknown model")

    fun unpriced(language: AppLanguage): String = localized(language, "sem tarifa", "no price")

    /** `Modelos (% do custo): claude-opus-5 82% · claude-haiku-4-5 18%`. */
    fun inline(mix: SessionModelMix, language: AppLanguage): String {
        val parts = mix.shares.joinToString(" · ") { share -> "${modelName(share, language)} ${shareText(share, language)}" }
        return localized(language, "Modelos (${basis(mix.basis, language)}): $parts", "Models (${basis(mix.basis, language)}): $parts")
    }

    fun modelName(share: ModelShare, language: AppLanguage): String = share.bucket.label ?: unknownModel(language)

    /** Fatia nula é modelo sem tarifa numa base de custo: palavra, nunca 0%. */
    fun shareText(share: ModelShare, language: AppLanguage): String = share.share?.let(::formatPercent) ?: unpriced(language)
}

/**
 * A mistura de modelos numa linha só, para a linha da sessão. Mistura vazia
 * (servidor de time anterior ao campo) não desenha nada.
 */
@Composable
internal fun SessionModelMixLine(
    mix: SessionModelMix,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    if (mix.isEmpty) {
        return
    }
    Text(
        text = SessionModelMixLabels.inline(mix, language),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * "Modelos da sessão": um modelo por linha, com turnos, tokens, cache lido e
 * gravado, custo e a participação (#445). É o recorte que explica por que duas
 * sessões de volume parecido pesam diferente na cota.
 *
 * [showCost] é falso no Codex, que não tem tarifa no app: a coluna some em vez
 * de mostrar um custo que ninguém calculou.
 */
@Composable
internal fun SessionModelTable(
    mix: SessionModelMix,
    language: AppLanguage,
    markerColor: Color,
    showCost: Boolean = true
) {
    if (mix.isEmpty) {
        return
    }
    AppDataSurfaceFlush(
        modifier = Modifier.fillMaxWidth().testTag(SESSION_MODEL_TABLE_TAG),
        header = {
            Column {
                AppSectionHeader(
                    title = SessionModelMixLabels.title(language),
                    subtitle = SessionModelMixLabels.subtitle(mix, language),
                    markerColor = markerColor
                )
                AppColumnHeaderRow(startGutter = 0.dp, modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                    AppColumnHeaderLabel(
                        label = CliSessionsLabels.columnModel(language),
                        modifier = Modifier.weight(1f).widthIn(min = MODEL_COLUMN_MIN)
                    )
                    AppColumnHeaderLabel(label = BreakdownLabels.columnTurns(language), modifier = Modifier.width(TURNS_COLUMN))
                    AppColumnHeaderLabel(label = CliSessionsLabels.columnTokens(language), modifier = Modifier.width(TOKENS_COLUMN))
                    AppColumnHeaderLabel(label = CliSessionsLabels.cacheRead(language), modifier = Modifier.width(VALUE_COLUMN))
                    AppColumnHeaderLabel(label = CliSessionsLabels.cacheWrite(language), modifier = Modifier.width(VALUE_COLUMN))
                    if (showCost) {
                        AppColumnHeaderLabel(label = CliSessionsLabels.columnCost(language), modifier = Modifier.width(VALUE_COLUMN))
                    }
                    AppColumnHeaderLabel(label = SessionModelMixLabels.basis(mix.basis, language), modifier = Modifier.width(SHARE_COLUMN))
                }
            }
        }
    ) {
        mix.shares.forEachIndexed { index, share ->
            SessionModelRow(
                share = share,
                language = language,
                showCost = showCost,
                showDivider = index < mix.shares.lastIndex
            )
        }
    }
}

@Composable
private fun SessionModelRow(
    share: ModelShare,
    language: AppLanguage,
    showCost: Boolean,
    showDivider: Boolean
) {
    val bucket = share.bucket
    AppDataRow(
        modifier = Modifier.testTag("$SESSION_MODEL_ROW_TAG_PREFIX${bucket.label.orEmpty()}"),
        showDivider = showDivider
    ) {
        AppCellValue(
            value = SessionModelMixLabels.modelName(share, language),
            modifier = Modifier.weight(1f).widthIn(min = MODEL_COLUMN_MIN)
        )
        AppCellValue(value = bucket.turnCount.toString(), modifier = Modifier.width(TURNS_COLUMN))
        AppCellValue(value = formatQuantity(bucket.totalTokens), modifier = Modifier.width(TOKENS_COLUMN))
        AppCellValue(value = formatQuantity(bucket.cacheReadTokens), modifier = Modifier.width(VALUE_COLUMN))
        AppCellValue(value = formatQuantity(bucket.cacheWriteTokens), modifier = Modifier.width(VALUE_COLUMN))
        if (showCost) {
            // Sem tarifa não há custo: travessão, nunca US$ 0.
            AppCellValue(
                value = if (bucket.isCostComplete) formatMicrosUsd(bucket.costMicros) else "—",
                modifier = Modifier.width(VALUE_COLUMN)
            )
        }
        Column(modifier = Modifier.width(SHARE_COLUMN)) {
            AppCellValue(value = SessionModelMixLabels.shareText(share, language))
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            AppProgressTrack(fraction = (share.share ?: 0.0).toFloat(), tone = AppTone.INFO)
        }
    }
}
