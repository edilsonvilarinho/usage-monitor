package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.AppGroupBand
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSourceMarker
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.TeamAccountGroup
import com.usagemonitor.presentation.viewmodel.TeamEmailGroup

/**
 * Faixa que separa uma conta da seguinte na visão global.
 *
 * Mostra o rótulo **e** o `accountUuid`: o rótulo é texto que o administrador
 * digitou ao emitir a chave e o servidor não o verifica, então ele orienta mas
 * não prova. Conta sem chave emitida aparece só pelo uuid.
 *
 * Os totais repetem as colunas da linha de integrante, nas mesmas larguras: sem
 * eles, comparar duas contas exige somar as linhas de cada uma na mão — o único
 * total da tela é o do cabeçalho, que já mistura todas as contas.
 *
 * É por ela que a conta abre e fecha. A visão global nasce recolhida, então esta
 * faixa é a lista inteira até alguém pedir o detalhe de uma conta.
 */
@Composable
internal fun TeamAccountGroupHeader(
    group: TeamEmailGroup,
    share: Double,
    expanded: Boolean,
    language: AppLanguage,
    hasStatusColumn: Boolean,
    hasActionColumn: Boolean,
    onToggle: () -> Unit
) {
    val accents = AppAccents.current

    // Escada de três superfícies neutras, todas já na paleta: a faixa da conta em
    // `surfaceVariant`, a linha do integrante transparente sobre o fundo da janela
    // e o bloco de sessões em `surface`. É ela que responde ao "não está claro
    // identificar os times": até aqui os três níveis eram retângulos de mesmo peso
    // empilhados, separados só por um vão de 8dp.
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
                    horizontal = TEAM_ROW_HORIZONTAL_PADDING,
                    vertical = TEAM_ACCOUNT_VERTICAL_PADDING
                )
                .testTag(
                    "$TEAM_ACCOUNT_GROUP_TAG_PREFIX${group.accounts.singleOrNull()?.accountKey ?: group.groupKey}"
                ),
            // Marcador e vão iguais aos do `AppDataRow` da linha do integrante: é o que
            // mantém os totais da conta no mesmo x das colunas dela. Sem ele a faixa
            // começava 14dp à esquerda da linha e as duas colunas de custo não
            // alinhavam, que é justamente a comparação que a faixa existe para permitir.
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSourceMarker(color = accents.cacheRead)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(TEAM_COLUMN_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone dentro da coluna de identidade, como na linha do integrante: é o
                // que mantém as colunas seguintes no mesmo x nas duas.
                Row(
                    modifier = Modifier.width(TEAM_COLUMN_IDENTITY),
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
                        //
                        // A contagem de integrantes vem emendada nela: a coluna
                        // que ela ocupava virou "Sessões", e um número sob a
                        // legenda "Tempo ativo" diria uma coisa e valeria outra.
                        Text(
                            text = TeamUsageLabels.accountBandWithMembers(
                                memberCount = group.activeMemberCount,
                                language = language
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // O e-mail é o dado, e dado fica na cor do texto. Quem
                        // identifica a faixa como conta é o marcador de 2dp à
                        // esquerda e a palavra logo acima.
                        // Continua em `titleSmall`, e o degrau tipográfico foi
                        // medido e recusado: com `titleMedium` (16sp) o e-mail
                        // não cabe nos 148dp úteis da coluna e a captura saiu com
                        // "ana@example…" — truncar a identidade da conta é pior
                        // que a capa ser um degrau menos pesada. A altura da
                        // faixa vem do padding vertical, que não custa largura.
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
                                    count = group.accountCount,
                                    language = language
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AppCellValue(
                    value = group.sessionCount.toString(),
                    modifier = Modifier.width(TEAM_COLUMN_SESSIONS)
                )

                AppCellValue(
                    value = formatQuantity(group.totalTokens),
                    modifier = Modifier.width(TEAM_COLUMN_TOKENS)
                )

                AppCellValue(
                    // Custo na cor do texto, como na lista da máquina: azul só no custo
                    // sugeria uma categoria que as outras colunas não têm.
                    value = if (group.isCostComplete) {
                        formatMicrosUsd(group.totalCostMicros)
                    } else {
                        "${formatMicrosUsd(group.totalCostMicros)}+"
                    },
                    modifier = Modifier.width(TEAM_COLUMN_COST)
                )

                // A conta não agrega tempo de trabalho: somar o tempo ativo de
                // duas máquinas que trabalharam ao mesmo tempo daria uma hora que
                // ninguém passou. A coluna fica vazia — e continua reservada, ou
                // as colunas seguintes sairiam de x.
                Spacer(modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME))

                Column(modifier = Modifier.width(TEAM_COLUMN_SHARE)) {
                    AppCellValue(value = formatPercent(share))
                    Spacer(modifier = Modifier.height(4.dp))
                    AppProgressTrack(fraction = share.toFloat(), tone = AppTone.INFO)
                }

                val worstHealth = group.worstHealth
                if (worstHealth != null) {
                    TeamHealthCell(
                        health = worstHealth,
                        language = language,
                        modifier = Modifier.width(TEAM_COLUMN_STATUS)
                    )
                } else if (hasStatusColumn) {
                    Spacer(modifier = Modifier.width(TEAM_COLUMN_STATUS))
                }
            }

            if (hasActionColumn) {
                Spacer(modifier = Modifier.size(TEAM_ACTION_SLOT))
            }
        }
        AppDivider()
    }
}

@Composable
internal fun TeamAccountUuidHeader(
    account: TeamAccountGroup,
    language: AppLanguage,
    /** Um degrau abaixo da faixa do e-mail e um acima do integrante que ela cobre. */
    indent: Dp
) {
    AppGroupBand(
        label = "${TeamUsageLabels.accountBand(language)} · ${account.accountKey.orEmpty()}",
        indent = indent,
        horizontalPadding = TEAM_ROW_HORIZONTAL_PADDING
    )
}
