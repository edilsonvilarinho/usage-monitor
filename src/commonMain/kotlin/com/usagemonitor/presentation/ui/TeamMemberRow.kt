package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
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
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSourceMarker
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppAccents

/**
 * Ponto colorido + veredito, igual na faixa da conta e na linha do integrante.
 *
 * `internal` porque a tela de presença mostra o mesmo veredito na mesma coluna:
 * duplicar a célula faria as duas divergirem no primeiro ajuste de cor.
 *
 * O desenho é o [AppStatusIndicator], não um ponto próprio. Ele é a única
 * insígnia de estado do sistema, e a regra que ela carrega — cor nunca informa
 * sozinha, todo estado leva ponto **e** palavra — vale igual aqui. O `when` que
 * traduz o veredito em severidade é [healthTone]; [healthColor] continua
 * existindo para quem precisa da cor crua num traço de gráfico.
 *
 * A célula fica em `t10` e não em `t12` como as vizinhas, e é assim no sistema:
 * o kit de presença também desenha a insígnia dentro da linha da tabela. A
 * coluna de estado é a única que responde com palavra fechada, não com número
 * para alinhar.
 */
@Composable
internal fun TeamHealthCell(
    health: CliSessionHealth,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    AppStatusIndicator(
        label = TeamUsageLabels.healthShort(health, language),
        tone = healthTone(health),
        modifier = modifier
    )
}

@Composable
internal fun TeamMemberRow(
    member: TeamMemberUsage,
    share: Double,
    expanded: Boolean,
    language: AppLanguage,
    /**
     * Degrau de aninhamento, aplicado **dentro** da coluna de identidade.
     *
     * Ele desloca só o texto: a coluna cede a mesma largura e as colunas
     * numéricas continuam no x das da faixa da conta, que é a comparação que a
     * faixa existe para permitir.
     */
    indent: Dp,
    removable: Boolean,
    /** A lista tem coluna de status; esta linha reserva a casa mesmo sem veredito. */
    hasStatusColumn: Boolean,
    /** A lista tem coluna de ação; esta linha reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val accents = AppAccents.current

    // Acento da **fonte**, e não o verde da faixa da conta: com os dois no mesmo
    // tom, capa e item traziam o mesmo marcador de 2dp no mesmo x e o marcador
    // deixava de dizer em que nível a linha está (issue #104). É também o que o
    // protótipo já desenhava.
    //
    // Integrante sem uso no período fica neutro: destacá-lo com a mesma cor de
    // quem consumiu daria a impressão de atividade que não houve.
    val accent = if (member.hasActivity) accents.anthropic else MaterialTheme.colorScheme.outline

    // Linha de tabela, não card: eram até vinte cards empilhados numa janela de
    // time grande. O marcador de 2dp mantém a leitura de "esta pessoa produziu"
    // que o acento do card dava, sem pintar a linha inteira.
    //
    // `Row` e não `FlowRow`: quebrar é o que a faixa de legendas não admite. É o
    // orçamento de `TEAM_COLUMN_*` mais o piso da janela que garantem que ela não
    // quebre.
    AppDataRow(
        modifier = Modifier.testTag("$TEAM_MEMBER_ROW_TAG_PREFIX${member.deviceId}"),
        onClick = if (member.hasActivity) onToggle else null,
        horizontalPadding = TEAM_ROW_HORIZONTAL_PADDING,
        verticalPadding = TEAM_MEMBER_VERTICAL_PADDING
    ) {
        AppSourceMarker(color = accent)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TEAM_COLUMN_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.width(TEAM_COLUMN_IDENTITY),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // O degrau vive aqui, dentro da coluna: `TEAM_COLUMN_IDENTITY` é
                // fixa, então o que ele consome sai do texto e nenhuma coluna à
                // direita se move.
                if (indent > 0.dp) {
                    Spacer(modifier = Modifier.width(indent))
                }
                if (member.hasActivity) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) {
                            TeamUsageLabels.collapse(language)
                        } else {
                            TeamUsageLabels.expand(language)
                        },
                        tint = accent
                    )
                } else {
                    // A casa do ícone fica reservada. Sem ela o integrante sem
                    // atividade começava 24dp à esquerda dos outros, e numa lista
                    // com um único integrante parado a coluna inteira lia como
                    // desalinhada — medido na captura do README.
                    Spacer(modifier = Modifier.width(TEAM_EXPAND_ICON_SIZE))
                }
                // Apelido, máquina e último envio: identidade e os dois carimbos
                // que a qualificam. A máquina deixou de ser coluna própria — ela
                // não é uma medida ao lado de custo e tokens, é quem é a pessoa.
                //
                // **Sem a palavra do nível**, e a tentativa está registrada
                // porque ela parecia a resposta óbvia para a issue #104. Ela
                // duplicaria a legenda desta coluna, que já diz "Integrante" uma
                // vez para a lista inteira — o rótulo por célula que a issue #81
                // desfez. E emendada na máquina para não custar uma quarta linha,
                // ela estourava os 148dp úteis da coluna: a captura do README
                // saiu com "Integrante · DESKTOP-…", truncando justamente o dado
                // que identifica a máquina. Quem separa capa de item aqui são o
                // recuo, o marcador, o peso do e-mail da faixa e a palavra que a
                // **faixa** carrega.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.alias,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = member.machineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = TeamUsageLabels.lastSeen(
                            instantLabel = member.lastSeenAt?.let { instant -> formatInstant(instant) },
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
                value = member.sessionCount.toString(),
                modifier = Modifier.width(TEAM_COLUMN_SESSIONS)
            )

            AppCellValue(
                value = if (member.hasActivity) {
                    formatQuantity(member.totalTokens)
                } else {
                    TeamUsageLabels.noActivityInRange(language)
                },
                color = if (member.hasActivity) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(TEAM_COLUMN_TOKENS)
            )

            AppCellValue(
                value = if (member.isCostComplete) {
                    formatMicrosUsd(member.totalCostMicros)
                } else {
                    "${formatMicrosUsd(member.totalCostMicros)}+"
                },
                modifier = Modifier.width(TEAM_COLUMN_COST)
            )

            // Servidor anterior à 0.7.0 não mede tempo e a coluna sai como "—".
            // Zero mediria e diria "não trabalhou", que é outra afirmação.
            AppCellValue(
                value = member.totalActiveMillis
                    ?.takeIf { millis -> millis > 0L }
                    ?.let { millis -> formatActiveTime(millis) }
                    ?: "—",
                modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME)
            )

            Column(modifier = Modifier.width(TEAM_COLUMN_SHARE)) {
                AppCellValue(value = formatPercent(share))
                Spacer(modifier = Modifier.height(4.dp))
                AppProgressTrack(fraction = share.toFloat(), tone = AppTone.INFO)
            }

            // Pior status entre as sessões deste integrante. Sem ele, a única
            // sessão saturada de um time fica escondida atrás de um clique que
            // ninguém dá — nada na linha recolhida indicaria que vale a pena.
            val worstHealth = member.worstHealth
            if (worstHealth != null) {
                TeamHealthCell(
                    health = worstHealth,
                    language = language,
                    modifier = Modifier
                        .width(TEAM_COLUMN_STATUS)
                        .testTag("$TEAM_MEMBER_HEALTH_TAG_PREFIX${member.deviceId}")
                )
            } else if (hasStatusColumn) {
                Spacer(modifier = Modifier.width(TEAM_COLUMN_STATUS))
            }
        }

        // Fora do fluxo de colunas, como na tela de presença: lá dentro a ação é o
        // último item e o primeiro a quebrar numa janela estreita.
        if (removable) {
            AppIconButton(
                contentDescription = TeamUsageLabels.removeMember(language),
                onClick = onRemove,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag("$TEAM_MEMBER_REMOVE_TAG_PREFIX${member.deviceId}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            Spacer(modifier = Modifier.size(TEAM_ACTION_SLOT))
        }
    }
}
