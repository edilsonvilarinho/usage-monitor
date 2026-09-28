package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.viewmodel.HudQuotaEntry
import kotlinx.datetime.Instant

/**
 * O valor do anel da bandeja (issue #328): o **maior** percentual entre as cotas
 * vigentes de todas as contas. É a pergunta que o ícone responde sem abrir nada —
 * "alguma coisa está perto de acabar?" —, e o tooltip continua dizendo qual.
 *
 * Saldo em dinheiro (`CURRENCY_USD`) fica de fora: o percentual dele é saldo
 * sobre saldo, não consumo de uma janela. Cota vencida também: o número dela
 * descreve uma janela que não existe mais. Sem nenhuma cota elegível, `null`, e
 * o ícone sai sem anel — anel vazio pareceria "0% usado", que ninguém mediu.
 */
internal fun trayUsageRingFraction(entries: List<HudQuotaEntry>, now: Instant): Float? {
    return entries
        .map { entry -> entry.quota }
        .filter { quota -> quota.unit != UsageUnit.CURRENCY_USD && quota.total > 0L && !quota.isExpiredAt(now) }
        .maxOfOrNull { quota -> quota.percentageUsed }
}
