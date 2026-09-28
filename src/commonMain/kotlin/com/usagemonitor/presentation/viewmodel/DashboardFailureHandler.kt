package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.RateLimitedException
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.sanitizeBreadcrumbErrorMessage
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import kotlinx.datetime.Clock

/**
 * O funil de toda falha de coleta do [DashboardViewModel]: classifica, grava na
 * trilha, decide o toast e, no 429, arma o backoff (issue #269). Saiu do view
 * model pelo limite de 800 linhas, sem mudar a ordem das decisões.
 */
internal class DashboardFailureHandler(
    private val breadcrumbs: BreadcrumbRecorder,
    private val scheduler: DashboardRefreshScheduler,
    private val clock: Clock,
    private val profiles: () -> List<AnthropicProfileRef>,
    private val codexProfiles: () -> List<CodexProfileRef> = { emptyList() },
    private val onToast: (DashboardToast) -> Unit
) {
    fun handle(target: UsageTargetKey, error: Throwable): UiApiError {
        val source = target.source
        val uiError = uiApiErrorOf(target, error, profiles(), codexProfiles())
        val message = uiError.message

        // Funil único de toda falha de coleta, e por isso o único ponto de
        // gravação: um passo por fonte que falhou, em qualquer caminho — poll
        // silencioso, atualização pedida ou recarga de um banner.
        //
        // Vai a mensagem **saneada**, a mesma que a tela mostra, e nunca a crua:
        // `sanitizeUiErrorMessage` já é o filtro que decide o que pode aparecer
        // para o usuário, e o relatório é ainda mais público que a tela dele.
        breadcrumbs.record(
            BreadcrumbCategory.API_CALL,
            "${source.name}: falhou — ${error::class.simpleName ?: "falha"}: ${sanitizeBreadcrumbErrorMessage(message)}"
        )

        // Avaliada antes de rate limit/credencial: falha de conectividade nunca
        // teve resposta HTTP nenhuma, então não pode ser confundida com 429/401 —
        // e sem banner próprio (`warningFor`) o toast genérico dispararia uma vez
        // por fonte, virando ruído quando a rede inteira está sem proxy.
        if (uiError.isConnectivityIssue) {
            return uiError
        }

        if (message.contains(HTTP_RATE_LIMIT_MARKER, ignoreCase = true)) {
            val decision = scheduler.recordRateLimit(target, (error as? RateLimitedException)?.retryAfter, clock.now())
            // A medição da #269: o que o servidor pediu e o que o app decidiu.
            breadcrumbs.record(BreadcrumbCategory.API_CALL, rateLimitBreadcrumb(source, decision))
            onToast(DashboardToast.RateLimit(source, retryAt = decision.until))
            return uiError.copy(retryAt = decision.until)
        }

        if (uiError.isServiceUnavailableIssue) {
            return uiError
        }

        if (!uiError.isConfigurationIssue) {
            onToast(DashboardToast.ApiError(source = source, message = message))
        }

        return uiError
    }
}
