package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ReleaseNotes
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

const val RELEASE_NOTES_CONTENT_TAG = "releaseNotesContent"

/**
 * Novidades da versão que acabou de ser instalada.
 *
 * Stateless como todo componente deste projeto: recebe o que mostrar e emite os
 * dois eventos. Quem decide se ela aparece é o controlador em `desktopMain`.
 */
@Composable
fun ReleaseNotesContent(
    notes: ReleaseNotes,
    language: AppLanguage,
    onOpenReleasePage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(RELEASE_NOTES_CONTENT_TAG)
    ) {
        // O cabeçalho não repete a versão: a barra de título da moldura já diz
        // "Novidades da versão X", e o mesmo texto duas vezes a 40dp de
        // distância gasta a altura que a lista precisa.
        AppSectionHeader(
            title = if (isPt) "Novidades" else "What's new",
            subtitle = releaseNotesSubtitle(notes, isPt),
            markerColor = MaterialTheme.colorScheme.primary
        )

        // O Box é que estica; a superfície dentro dele cresce só até onde a
        // lista vai. Sem essa separação, ou a caixa fica com um vão vazio sob
        // três itens, ou os botões sobem para o meio da janela — os dois
        // estados foram vistos em execução antes de chegar aqui. O teto de
        // altura continua sendo o do Box, que é o que faz a lista longa rolar.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AppSpacing.lg)
        ) {
            AppDataSurfaceFlush(modifier = Modifier.align(Alignment.TopStart)) {
                // Column + verticalScroll e não LazyColumn: a lista é curta e a
                // regra do projeto é explícita sobre não converter uma na outra.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    notes.items.forEachIndexed { index, item ->
                        AppDataRow(showDivider = index != notes.items.lastIndex) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(BULLET_COLUMN_WIDTH)
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppButton(
                label = if (isPt) "Ver no GitHub" else "View on GitHub",
                onClick = onOpenReleasePage,
                tone = AppButtonTone.GHOST
            )
            // Uma primária por tela, e aqui ela é fechar: a tela informa, não
            // propõe trabalho. "Ver no GitHub" leva para fora do app.
            AppButton(
                label = if (isPt) "Fechar" else "Close",
                onClick = onClose,
                tone = AppButtonTone.PRIMARY
            )
        }
    }
}

/** Largura do marcador de item. Fixa para os textos alinharem entre si. */
private val BULLET_COLUMN_WIDTH = 16.dp

fun releaseNotesTitle(version: String, isPt: Boolean): String {
    return if (isPt) "Novidades da versão $version" else "What's new in $version"
}

/**
 * De onde se veio e quando a versão saiu.
 *
 * As duas informações são independentes e cada uma pode faltar: o instalador nem
 * sempre consegue ler a versão anterior do registro, e `published_at` é
 * anulável. Sem nenhuma das duas não há subtítulo — meia frase pendurada é pior
 * que frase nenhuma.
 */
fun releaseNotesSubtitle(notes: ReleaseNotes, isPt: Boolean): String? {
    val parts = mutableListOf<String>()

    val previous = notes.previousVersion
    if (previous != null) {
        parts += if (isPt) "Atualizado de $previous" else "Updated from $previous"
    }

    val publishedAt = notes.publishedAt
    if (publishedAt != null) {
        // Mesmo fuso de apresentação do resto do app.
        val date = publishedAt.toLocalDateTime(TimeZone.of("America/Sao_Paulo")).date
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthNumber.toString().padStart(2, '0')
        parts += if (isPt) "$day/$month/${date.year}" else "${date.year}-$month-$day"
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
