package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage

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
internal fun CardNoticeHint(
    notices: Set<ApiUsageNotice>,
    source: ApiSource,
    language: AppLanguage,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    // Mesma ordem estável dos banners que este hint substituiu.
    val texts = remember(notices, source, language) {
        notices
            .toList()
            .sortedBy { notice -> notice.ordinal }
            .map { notice -> noticeText(notice = notice, source = source, language = language) }
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

private fun noticeText(notice: ApiUsageNotice, source: ApiSource, language: AppLanguage): String {
    return when (notice) {
        ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE -> {
            if (language == AppLanguage.PT) {
                "Quota 7d indisponível na fonte semanal do Codex"
            } else {
                "7d quota unavailable in Codex weekly source"
            }
        }
        // Fora do Codex a marca é posta pelo painel quando guarda a última leitura
        // depois de uma falha (issue #267): a frase do contrato do Codex não se aplica.
        ApiUsageNotice.SOURCE_UNSTABLE -> if (source != ApiSource.CODEX) {
            if (language == AppLanguage.PT) {
                "A coleta mais recente falhou. Os números são da última leitura válida e podem estar desatualizados."
            } else {
                "The latest refresh failed. These numbers are from the last valid reading and may be out of date."
            }
        } else {
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
