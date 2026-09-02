package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.presentation.ui.components.formatBrtDateTime

/** Texto pronto para a bandeja: título curto e corpo com o detalhe. */
data class UsageAlertMessage(
    val title: String,
    val body: String
)

/**
 * Traduz um [UsageAlert] para a língua da interface.
 *
 * Mesma anatomia de [decodeToastMessage]: o domain carrega só o fato e a frase
 * nasce aqui, na borda da UI.
 */
fun usageAlertMessage(alert: UsageAlert, language: AppLanguage): UsageAlertMessage {
    return when (alert) {
        is UsageAlert.QuotaThreshold -> quotaThresholdMessage(alert, language)
        is UsageAlert.SessionSaturated -> sessionSaturatedMessage(alert, language)
        is UsageAlert.SessionStalled -> sessionStalledMessage(alert, language)
        is UsageAlert.SpendSpike -> spendSpikeMessage(alert, language)
    }
}

/**
 * O consumo de hoje contra o hábito, nunca contra o teto.
 *
 * Título fixo e alvo no corpo, como em [sessionSaturatedMessage]: o título de
 * [quotaThresholdMessage] já é `alvo · cota`, e repeti-lo aqui faria os dois
 * avisos chegarem à bandeja com a mesma primeira linha, dizendo coisas
 * diferentes.
 *
 * A contagem de dias sai por extenso porque ela **é** a régua: "4,0× acima" sem
 * dizer acima de quê não permite julgar se o número merece atenção. Sempre
 * plural — [MIN_BASELINE_DAYS] são três.
 */
private fun spendSpikeMessage(
    alert: UsageAlert.SpendSpike,
    language: AppLanguage
): UsageAlertMessage {
    val isPt = language == AppLanguage.PT
    val factor = formatSpikeFactor(alert.factor, language)
    val subject = "${alert.targetLabel} · ${alert.quotaLabel}"

    if (isPt) {
        return UsageAlertMessage(
            title = "Consumo acima do habitual",
            body = "$subject: hoje está ${factor}× acima da mediana dos últimos " +
                "${alert.baselineDays} dias no mesmo horário. Verifique se há automação ou " +
                "sessão rodando sem supervisão."
        )
    }

    return UsageAlertMessage(
        title = "Usage above the usual",
        body = "$subject: today is ${factor}× above the median of the last " +
            "${alert.baselineDays} days at the same time of day. Check whether some automation " +
            "or session is running unattended."
    )
}

private fun quotaThresholdMessage(
    alert: UsageAlert.QuotaThreshold,
    language: AppLanguage
): UsageAlertMessage {
    val title = "${alert.targetLabel} · ${alert.quotaLabel}"

    // Sem `resets_at` conhecido a frase termina no consumo: inventar um horário
    // de reinício seria pior que omiti-lo.
    val resetSuffix = if (alert.hasKnownResetAt) {
        val formatted = formatBrtDateTime(alert.periodEndAt, language)
        if (language == AppLanguage.PT) " Reinício: $formatted BRT." else " Reset: $formatted BRT."
    } else {
        ""
    }

    val head = if (language == AppLanguage.PT) {
        if (alert.thresholdPercent >= FULL_QUOTA_PERCENT) {
            "Cota esgotada (${alert.actualPercent}%)."
        } else {
            "Uso em ${alert.actualPercent}%, acima do limiar de ${alert.thresholdPercent}%."
        }
    } else {
        if (alert.thresholdPercent >= FULL_QUOTA_PERCENT) {
            "Quota exhausted (${alert.actualPercent}%)."
        } else {
            "Usage at ${alert.actualPercent}%, above the ${alert.thresholdPercent}% threshold."
        }
    }

    return UsageAlertMessage(title = title, body = head + resetSuffix)
}

private fun sessionSaturatedMessage(
    alert: UsageAlert.SessionSaturated,
    language: AppLanguage
): UsageAlertMessage {
    val title = if (language == AppLanguage.PT) "Sessão CLI saturada" else "CLI session saturated"

    val body = if (language == AppLanguage.PT) {
        val subject = alert.projectName?.let { name -> "A sessão em $name" } ?: "Uma sessão"
        "$subject encheu a janela de contexto. Rode /compact ou comece uma sessão nova."
    } else {
        val subject = alert.projectName?.let { name -> "The session in $name" } ?: "A session"
        "$subject filled its context window. Run /compact or start a new session."
    }

    return UsageAlertMessage(title = title, body = body)
}

/**
 * Ausência de resposta, nunca "seu processo travou".
 *
 * A evidência é o transcript: houve pedido e não houve resposta. O app não olha o
 * sistema operacional e não sabe se o processo existe — prometer isso na
 * notificação seria afirmar o que não se mediu, e a própria issue #177 pede a
 * reserva.
 */
private fun sessionStalledMessage(
    alert: UsageAlert.SessionStalled,
    language: AppLanguage
): UsageAlertMessage {
    val isPt = language == AppLanguage.PT
    val title = if (isPt) "Sessão CLI sem resposta" else "CLI session with no reply"
    // Mesmo formatador da coluna de tempo ativo das Sessões CLI: um segundo dono
    // do formato daria duas grafias para a mesma grandeza na mesma tela.
    val elapsed = formatActiveTime(alert.pendingMillis)

    val body = if (isPt) {
        val subject = alert.projectName?.let { name -> "A sessão em $name" } ?: "Uma sessão"
        "$subject está há $elapsed sem resposta desde o último pedido. " +
            "Verifique se o processo do Claude Code ainda está em execução."
    } else {
        val subject = alert.projectName?.let { name -> "The session in $name" } ?: "A session"
        "$subject has had no reply for $elapsed since the last request. " +
            "Check whether the Claude Code process is still running."
    }

    return UsageAlertMessage(title = title, body = body)
}

private const val FULL_QUOTA_PERCENT = 100
