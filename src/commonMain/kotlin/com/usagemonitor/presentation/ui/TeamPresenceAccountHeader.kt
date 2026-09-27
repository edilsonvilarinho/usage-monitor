package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.AppGroupBand
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppSourceMarker
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.TeamPresenceAccountGroup
import com.usagemonitor.presentation.viewmodel.TeamPresenceEmailGroup

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamPresenceEmailHeader(
    group: TeamPresenceEmailGroup,
    expanded: Boolean,
    language: AppLanguage,
    /** A lista tem coluna de ação; a faixa reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean,
    onToggle: () -> Unit
) {
    val accents = AppAccents.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(
                    horizontal = PRESENCE_ROW_CONTENT_PADDING,
                    vertical = PRESENCE_ACCOUNT_VERTICAL_PADDING
                )
                .testTag(
                    "$PRESENCE_ACCOUNT_GROUP_TAG_PREFIX${group.accounts.singleOrNull()?.accountKey ?: group.groupKey}"
                ),
            // Marcador e vão iguais aos do `AppDataRow` da linha do integrante: é o
            // que mantém os agregados da conta no mesmo x das colunas dela.
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSourceMarker(color = accents.cacheRead)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PRESENCE_COLUMN_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A primeira coluna da linha é o estado, e a faixa não tem estado
                // agregado: o vão mantém a identidade da conta no mesmo x do
                // apelido do integrante.
                Spacer(modifier = Modifier.width(PRESENCE_COLUMN_STATE))

                // Ícone dentro da coluna de identidade, como na linha do integrante: é o
                // que mantém as colunas seguintes no mesmo x nas duas.
                Row(
                    modifier = Modifier.width(PRESENCE_COLUMN_IDENTITY),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) {
                            TeamUsageLabels.collapseAccount(language)
                        } else {
                            TeamUsageLabels.expandAccount(language)
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        // A palavra vem antes do e-mail: sem ela a faixa entregava um
                        // endereço e um uuid sem dizer que aquilo é a conta, e ao lado de
                        // uma linha de integrante — que também tem nome e identificador —
                        // as duas liam igual.
                        Text(
                            text = TeamUsageLabels.accountBand(language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Continua em `titleSmall`, como na lista de consumo:
                        // com 16sp o e-mail não cabe nos 118dp úteis da coluna e
                        // a captura saiu com "ana@example…". A altura da faixa
                        // vem do padding vertical, que não custa largura.
                        Text(
                            text = group.accountEmail ?: TeamUsageLabels.unlabeledAccount(language),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = group.accounts.singleOrNull()?.accountKey.orEmpty().takeIf { it.isNotEmpty() }
                                ?.let { accountKey -> accountKey }
                                ?: TeamUsageLabels.technicalAccounts(
                                    count = group.accounts.size,
                                    language = language
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // A faixa da conta agrega, e a coluna Máquina não tem agregado: o vão
                // mantém as colunas seguintes no mesmo x da linha do integrante.
                Spacer(modifier = Modifier.width(PRESENCE_COLUMN_MACHINE))

                // Uma célula só, atravessando as três colunas que a conta não
                // tem — sessões ativas, último sinal e último turno. O texto se
                // descreve ("2 de 2 conectados · 1 trabalhando"), então ele não
                // depende da legenda de coluna nenhuma; o que não pode acontecer
                // é um número cru sob a legenda errada.
                AppCellValue(
                    value = TeamPresenceLabels.accountBandSummary(
                        online = group.onlineCount,
                        total = group.totalCount,
                        working = group.workingCount,
                        language = language
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(
                        PRESENCE_COLUMN_ACTIVE_SESSIONS +
                            PRESENCE_COLUMN_SPACING + PRESENCE_COLUMN_LAST_SEEN +
                            PRESENCE_COLUMN_SPACING + PRESENCE_COLUMN_LAST_TURN
                    )
                )
            }

            // Fora do `Row` de propósito: dentro dele a ação é o último item e
            // portanto o primeiro a quebrar, e numa janela estreita o botão de
            // apagar aparecia sozinho numa linha abaixo do e-mail.
            if (hasActionColumn) {
                Spacer(modifier = Modifier.size(PRESENCE_ACTION_SLOT))
            }
        }
        AppDivider()
    }
}

@Composable
internal fun TeamPresenceAccountSubgroupHeader(
    group: TeamPresenceAccountGroup,
    language: AppLanguage,
    deletable: Boolean,
    hasActionColumn: Boolean,
    onDelete: () -> Unit
) {
    AppGroupBand(
        label = "${TeamUsageLabels.accountBand(language)} · ${group.accountKey.orEmpty()}",
        detail = TeamPresenceLabels.accountBandSummary(
            online = group.onlineCount,
            total = group.totalCount,
            working = group.workingCount,
            language = language
        ),
        indent = AppSpacing.xl,
        horizontalPadding = PRESENCE_ROW_CONTENT_PADDING
    ) {
        if (deletable) {
            AppIconButton(
                contentDescription = TeamPresenceLabels.deleteAccount(language),
                onClick = onDelete,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag("$PRESENCE_ACCOUNT_DELETE_TAG_PREFIX${group.groupKey}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            Spacer(modifier = Modifier.size(PRESENCE_ACTION_SLOT))
        }
    }
}
