package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.theme.AppSpacing

// Larguras das colunas da lista de sessões, num lugar só: a faixa de legendas e
// as linhas têm de cair no mesmo x.
//
// O somatório não é livre e é ele que sustenta a faixa de cabeçalho. Com a janela
// em 960dp, as seis colunas mais o vão de 12dp entre elas dão 766; somados os
// 24dp de padding da linha, os 12 da barra de rolagem, os 32 do corpo da janela e
// os 26 do botão de remover do modo administrativo, sobram 874 — abaixo do piso
// da janela. Passar disso faria a linha quebrar, e uma faixa de legendas sobre
// linha quebrada promete um alinhamento que o conteúdo não cumpre.
//
// O veredito de saturação **não** é coluna: ele desceu para uma segunda linha da
// própria linha, com a razão que o gerou ao lado. Como coluna ele media 210dp e
// era o que estourava o orçamento.
private val SESSION_COLUMN_ID = 170.dp

private val SESSION_COLUMN_PROJECT = 130.dp

private val SESSION_COLUMN_TOKENS = 136.dp

private val SESSION_COLUMN_CACHE = 90.dp

private val SESSION_COLUMN_COST = 96.dp

private val SESSION_COLUMN_ACTIVE_TIME = 84.dp

/** Mesma pegada do `AppIconButton`, para o cabeçalho reservar a casa certa. */
private val SESSION_ACTION_SLOT = 26.dp

/**
 * Sinal de que a tela se atualiza sozinha — o botão de atualizar não existe mais.
 *
 * É [AppStatusIndicator], a única insígnia de estado do sistema, e não um ponto
 * próprio: ponto **e** palavra, e o tom saindo de [AppTone]. Ele desenhava o
 * ponto com `CACHE_READ_COLOR`, que é `darkAppAccents.cacheRead` congelado num
 * `val` de topo de arquivo — resolvido uma vez por processo, sem ler o tema em
 * vigor. No tema claro aquele verde dá 2,64:1 contra a `surface`, e a primitiva
 * o troca por `AppAccents.current.cacheRead`, que passa nos dois.
 */
@Composable
internal fun LiveBadge(language: AppLanguage) {
    AppStatusIndicator(
        label = CliSessionsLabels.live(language),
        tone = AppTone.OK
    )
}

/**
 * Linha de sessão da lista.
 *
 * `internal` porque o modal de time a reaproveita ao expandir um integrante: a
 * sessão de um colega tem de ser lida exatamente como a sessão da própria
 * máquina, com as mesmas colunas e o mesmo veredito de saturação.
 */
/**
 * Faixa de legendas da lista de sessões, uma vez para a lista inteira.
 *
 * `internal` porque o bloco de sessões do modal do time reaproveita a mesma
 * lista: duas faixas com as mesmas colunas divergiriam no primeiro ajuste.
 *
 * [hasActionColumn] reserva a casa do botão de remover do modo administrativo,
 * que fica fora do fluxo de colunas.
 */
@Composable
internal fun CliSessionColumnHeader(
    language: AppLanguage,
    modifier: Modifier = Modifier,
    hasActionColumn: Boolean = false
) {
    AppColumnHeaderRow(
        // Sem marcador na linha de sessão: a faixa começa onde a primeira célula
        // começa.
        startGutter = 0.dp,
        modifier = modifier.testTag(CLI_SESSION_COLUMN_HEADER_TAG)
    ) {
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnSession(language),
            modifier = Modifier.width(SESSION_COLUMN_ID)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnProject(language),
            modifier = Modifier.width(SESSION_COLUMN_PROJECT)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnTokens(language),
            modifier = Modifier.width(SESSION_COLUMN_TOKENS)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnCache(language),
            modifier = Modifier.width(SESSION_COLUMN_CACHE)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnCost(language),
            modifier = Modifier.width(SESSION_COLUMN_COST)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.activeTime(language),
            modifier = Modifier.width(SESSION_COLUMN_ACTIVE_TIME)
        )
        if (hasActionColumn) {
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(SESSION_ACTION_SLOT))
        }
    }
}

/**
 * Uma sessão como linha de tabela.
 *
 * Era um card por sessão, com brilho de acento e 14dp de padding: numa janela de
 * cinco sessões a lista já pedia rolagem. Depois virou linha, mas com o rótulo
 * repetido dentro de cada célula — a concessão que a passada de agosto registrou,
 * porque as células somavam quase 1.000dp e a janela abre em 960.
 *
 * O que desfaz a concessão é o veredito sair do fluxo de colunas: ele media 210dp
 * e desceu para uma **segunda linha** da própria linha, junto da razão que o
 * gerou, que é como o protótipo desenha. Com ele fora, as seis colunas cabem, a
 * linha não quebra e a legenda pode viver uma vez só na faixa de cabeçalho.
 *
 * `Row` e não `FlowRow` justamente por isso: quebrar é o que a faixa de legendas
 * não admite.
 *
 * O status continua sendo **ponto e palavra** — cor sozinha não informa — e
 * continua vindo com o número que o gerou.
 */
@Composable
internal fun CliSessionRow(
    session: CliSessionSummary,
    language: AppLanguage,
    onOpen: () -> Unit,
    /**
     * `false` quando o transcript não está nesta máquina — a sessão de um colega
     * na lista do time. Ali a linha **não** oferece o botão de copiar: o
     * `--resume` cairia num seletor vazio, e a issue #102 pede que a sessão de
     * outro integrante não seja copiável.
     */
    isLocalSession: Boolean = true,
    /** Ação destrutiva opcional; ausente nas listas locais e para não administradores. */
    onRemove: (() -> Unit)? = null,
    removeButtonTag: String? = null,
    /** A lista tem coluna de ação; esta linha reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean = onRemove != null,
    /**
     * Há quanto tempo o último pedido desta sessão está sem resposta; `null` é o
     * caso normal — respondeu, ou não foi possível avaliar.
     *
     * Sempre `null` na lista do time: a marca sai da cauda do transcript, que só
     * existe na máquina onde a sessão rodou.
     */
    stalledForMillis: Long? = null
) {
    val status = session.contextStatus
    val statusTone = healthTone(status.health)

    AppDataRow(
        modifier = Modifier.testTag(cliSessionRowTag(session.sessionId)),
        onClick = onOpen
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.width(SESSION_COLUMN_ID),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Com peso, o botão de copiar cabe sempre: a coluna cede
                    // espaço em vez de empurrá-lo para fora da largura fixa.
                    Column(modifier = Modifier.weight(1f)) {
                        // Identidade e o carimbo que a qualifica, não duas
                        // medidas: a legenda "Sessão" nomeia as duas linhas.
                        AppCellValue(value = shortSessionId(session.sessionId))
                        Text(
                            text = formatInstant(session.lastTs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    // O clique do botão é consumido por ele: copiar não abre o detalhe.
                    if (isLocalSession) {
                        CopySessionCommandButton(
                            sessionId = session.sessionId,
                            language = language
                        )
                    }
                }

                Column(modifier = Modifier.width(SESSION_COLUMN_PROJECT)) {
                    AppCellValue(value = session.projectName ?: "—")
                    Text(
                        text = CliSessionsLabels.turnsLabel(session.turnCount, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                AppCellValue(
                    value = formatQuantity(session.totalTokens),
                    modifier = Modifier.width(SESSION_COLUMN_TOKENS)
                )

                Column(modifier = Modifier.width(SESSION_COLUMN_CACHE)) {
                    AppCellValue(value = formatPercent(session.cacheHitRate))
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    AppProgressTrack(fraction = session.cacheHitRate.toFloat(), tone = AppTone.OK)
                }

                AppCellValue(
                    value = if (session.isCostComplete) {
                        formatMicrosUsd(session.costMicros)
                    } else {
                        "${formatMicrosUsd(session.costMicros)}+"
                    },
                    modifier = Modifier.width(SESSION_COLUMN_COST)
                )

                // Tempo de trabalho, não duração: as pausas acima de cinco
                // minutos ficam de fora. Sem medida e sem intervalo sai o
                // travessão — "0min" seria lido como sessão instantânea.
                AppCellValue(
                    value = session.activeMillis
                        ?.takeIf { millis -> millis > 0L }
                        ?.let { millis -> formatActiveTime(millis) }
                        ?: "—",
                    modifier = Modifier.width(SESSION_COLUMN_ACTIVE_TIME)
                )
            }

            // Segunda linha, e não sétima coluna: como coluna o veredito media
            // 210dp e era ele que fazia a linha quebrar. Aqui ele atravessa a
            // largura inteira, que é o que a frase precisa.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppStatusIndicator(
                    label = CliSessionsLabels.healthShort(status.health, language),
                    tone = statusTone
                )
                // Ponto e palavra, como o veredito ao lado: a marca não pode ser
                // só cor. Fica na segunda linha, e não numa sétima coluna — o
                // orçamento de largura das seis colunas não comporta mais uma, e
                // a faixa de legendas não admite linha quebrada.
                if (stalledForMillis != null) {
                    AppStatusIndicator(
                        label = CliSessionsLabels.stalledLabel(stalledForMillis, language),
                        tone = AppTone.WARNING,
                        modifier = Modifier.testTag(cliSessionStalledTag(session.sessionId))
                    )
                }
                Text(
                    text = CliSessionsLabels.healthReason(
                        saturationLabel = status.contextSaturation?.let { value -> formatPercent(value) },
                        nextCostLabel = formatMicrosUsd(status.nextInteractionCostMicros),
                        language = language
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Fora do fluxo de colunas, como nas listas de time e de presença: lá
        // dentro a ação é o último item e o primeiro a sair numa janela estreita.
        if (onRemove != null) {
            AppIconButton(
                contentDescription = TeamUsageLabels.removeSession(language),
                onClick = onRemove,
                tone = AppButtonTone.DANGER,
                modifier = if (removeButtonTag != null) {
                    Modifier.testTag(removeButtonTag)
                } else {
                    Modifier
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            // Quadrado, não só largura: sem reservar a altura a linha sem botão
            // sairia mais baixa que as vizinhas.
            Spacer(modifier = Modifier.size(SESSION_ACTION_SLOT))
        }
    }
}
