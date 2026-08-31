package com.usagemonitor.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.viewmodel.AppUpdateFailureReason
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.UiApiError

const val APP_UPDATE_BANNER_TAG = "appUpdateBanner"

/**
 * A ação recarrega só o alvo que falhou. Recarregar toda a fonte também refazia a
 * coleta dos perfis saudáveis, que é justamente o custo que a tela evita ao ter um
 * botão por banner.
 */
internal fun warningActionFor(
    warning: DashboardWarning,
    onRetryTarget: (UsageTargetKey) -> Unit
): (() -> Unit)? {
    return when (warning.source) {
        ApiSource.ANTHROPIC -> {
            { onRetryTarget(warning.target) }
        }
        ApiSource.MINIMAX -> null
        ApiSource.CODEX -> null
        ApiSource.DEEPSEEK -> null
        ApiSource.OPENCODE -> null
        ApiSource.OPENCODE_GO -> null
        ApiSource.KILO -> null
        ApiSource.OPENROUTER -> null
    }
}

/**
 * Com várias contas Anthropic o rótulo da fonte é o mesmo para todas, e dois
 * banners idênticos não dizem qual conta precisa de atenção. `targetLabel` já
 * chega do view model como "Anthropic — <perfil>".
 */
internal fun warningTargetLabel(error: UiApiError): String {
    val label = error.targetLabel
    if (label != null && label.isNotBlank()) {
        return label
    }
    return sourceLabelFromKey(error.source)
}

internal fun warningFor(
    error: UiApiError,
    language: AppLanguage
): DashboardWarning? {
    val label = warningTargetLabel(error)

    if (error.isRateLimitIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "$label temporariamente limitado",
                description = "A API respondeu HTTP 429. Isso normalmente é limite de requisições ou cota temporária do próprio serviço; estar logado no Claude Code não evita esse bloqueio. Aguarde a janela de limite liberar e tente novamente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "$label is temporarily limited",
                description = "The API returned HTTP 429. This usually means a request limit or temporary quota window on the service side; being signed in to Claude Code does not bypass it. Wait for the limit window to clear, then retry.",
                actionLabel = "Retry"
            )
        }
    }

    if (error.isServiceUnavailableIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "$label temporariamente indisponível",
                description = "O serviço remoto respondeu com indisponibilidade temporária ou falha de upstream. Aguarde alguns instantes e tente novamente. As outras integrações podem continuar funcionando normalmente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "$label is temporarily unavailable",
                description = "The remote service returned a temporary unavailability or upstream failure. Wait a few moments and retry. Other integrations can continue working normally.",
                actionLabel = "Retry"
            )
        }
    }

    if (error.isAnthropicCredentialIssue) {
        val hasScopeGuidance = error.message.contains(
            "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada",
            ignoreCase = true
        ) || error.message.contains(
            "Claude Code session is missing the expected permission or is outdated",
            ignoreCase = true
        )

        if (hasScopeGuidance) {
            return if (language == AppLanguage.PT) {
                DashboardWarning(
                    target = error.target,
                    title = "$label precisa revalidar a sessão",
                    description = "1. Feche o Usage Monitor.\n2. Abra o Claude Code e confirme que a sessão está ativa; se preciso, faça login novamente.\n3. Abra o Usage Monitor outra vez.\n4. Se ainda falhar, desative temporariamente Anthropic nas configurações para continuar vendo as outras APIs.",
                    actionLabel = "Tentar novamente"
                )
            } else {
                DashboardWarning(
                    target = error.target,
                    title = "$label needs the session refreshed",
                    description = "1. Close Usage Monitor.\n2. Open Claude Code and confirm the session is active; sign in again if needed.\n3. Open Usage Monitor again.\n4. If it still fails, temporarily disable Anthropic in settings so the other APIs keep working.",
                    actionLabel = "Retry"
                )
            }
        }

        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "$label precisa de autenticação",
                description = "Faça login no Claude Code para recriar ou renovar a credencial (`~/.claude/.credentials.json`; no macOS, a entrada `Claude Code-credentials` do Keychain) e depois tente novamente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "$label needs authentication",
                description = "Sign in with Claude Code to recreate or renew the credential (`~/.claude/.credentials.json`; on macOS the `Claude Code-credentials` Keychain entry), then try again.",
                actionLabel = "Retry"
            )
        }
    }

    if (error.isMiniMaxApiKeyIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "MiniMax precisa de uma API key",
                description = "Abra Configurações > APIs, informe a chave da MiniMax e tente atualizar novamente.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "MiniMax needs an API key",
                description = "Open Settings > APIs, enter the MiniMax key, and try refreshing again.",
                actionLabel = null
            )
        }
    }

    if (error.isMiniMaxInactivePlanIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "MiniMax sem plano/token ativo",
                description = "A conta MiniMax respondeu que não há assinatura ativa para consultar as cotas. Ative um plano ou gere um token vinculado a uma assinatura válida e depois atualize o monitor.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "MiniMax has no active plan/token",
                description = "The MiniMax account reported that there is no active subscription available for quota checks. Activate a plan or generate a token tied to a valid subscription, then refresh the monitor.",
                actionLabel = null
            )
        }
    }

    if (error.isOpenCodeLocalIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "OpenCode Zen Free indisponível",
                description = "O banco local do OpenCode não foi encontrado. Abra o OpenCode pelo menos uma vez nesta máquina para gerar `~/.local/share/opencode/opencode.db`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "OpenCode Zen Free is unavailable",
                description = "The local OpenCode database was not found. Open OpenCode at least once on this machine to create `~/.local/share/opencode/opencode.db`.",
                actionLabel = null
            )
        }
    }

    if (error.isOpenCodeGoApiKeyIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "OpenCode Go precisa de uma API key",
                description = "Abra Configurações > APIs, informe a chave da API do OpenCode (a mesma usada pelo Zen) e tente atualizar novamente.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "OpenCode Go needs an API key",
                description = "Open Settings > APIs, enter the OpenCode API key (the same one Zen uses), and try refreshing again.",
                actionLabel = null
            )
        }
    }

    // Chave válida numa conta sem o plano Go: nada há para corrigir na credencial,
    // então o banner não oferece "Tentar novamente" — repetir a chamada devolveria
    // o mesmo 403.
    if (error.isOpenCodeGoSubscriptionIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "OpenCode Go sem assinatura ativa",
                description = "A chave informada é válida, mas a conta não tem o plano Go. Assine o plano Go em opencode.ai ou desative esta integração em Configurações > APIs. O saldo pago do Zen não é exposto por API e não é lido aqui.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "OpenCode Go has no active subscription",
                description = "The key is valid, but the account has no Go plan. Subscribe to Go at opencode.ai or disable this integration under Settings > APIs. The paid Zen balance is not exposed by any API and is not read here.",
                actionLabel = null
            )
        }
    }

    if (error.isKiloLocalIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "Kilo Free indisponível",
                description = "O banco local do Kilo não foi encontrado. Abra o Kilo pelo menos uma vez nesta máquina para gerar `~/.local/share/kilo/kilo.db`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "Kilo Free is unavailable",
                description = "The local Kilo database was not found. Open Kilo at least once on this machine to create `~/.local/share/kilo/kilo.db`.",
                actionLabel = null
            )
        }
    }

    return null
}

/**
 * Faixa de uma linha só. O banner antigo era o `PersistentApiWarningBanner`
 * genérico — título, parágrafo de descrição e `Button` empilhados —, e ficava fixo
 * no topo do dashboard empurrando os cards enquanto a atualização não fosse
 * instalada (issue #67).
 *
 * A faixa inteira é clicável e não há botão: a descrição só repetia em prosa o que
 * o rótulo da ação já diz, e uma linha clicável entrega a mesma ação com um terço
 * da altura.
 *
 * Com a atualização automática ligada a faixa ganhou mais três estados. O
 * progresso é **texto**, nunca indicador animado: animação sem fim trava o
 * `waitForIdle` dos testes de componente, e é a mesma regra que já vale para o
 * resto do app.
 */
@Composable
internal fun AppUpdateBanner(
    state: AppUpdateUiState,
    language: AppLanguage,
    onOpenRelease: () -> Unit,
    onRestartAndUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val content = updateBannerContent(state = state, language = language)

    // O despacho da ação mora junto dos rótulos que a descrevem: separá-los
    // deixaria a tela decidindo o que "a ação da faixa" significa em cada estado.
    val action: (() -> Unit)? = when (state) {
        is AppUpdateUiState.Available -> onOpenRelease
        is AppUpdateUiState.Ready -> onRestartAndUpdate
        is AppUpdateUiState.Failed -> onOpenRelease
        // Baixando não tem ação: uma faixa clicável sem rótulo de ação seria um
        // alvo de clique invisível.
        is AppUpdateUiState.Downloading -> null
    }

    // É o [AppBanner] do sistema, não um `Surface` próprio: mesma superfície,
    // mesma borda de 1dp, mesmo marcador de 2dp à esquerda — o marcador que ele
    // desenhava à mão era uma cópia do que a primitiva já traz. A faixa continua
    // sem descrição, porque ela repetiria em prosa o que o rótulo da ação diz, e
    // é isso que a mantém com um terço da altura de um aviso de duas linhas.
    AppBanner(
        title = content.title,
        tone = content.tone,
        modifier = modifier
            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
            .testTag(APP_UPDATE_BANNER_TAG),
        action = if (content.actionLabel == null) {
            null
        } else {
            {
                Text(
                    text = "${content.actionLabel} →",
                    style = MaterialTheme.typography.labelLarge,
                    color = content.tone.color(),
                    maxLines = 1
                )
            }
        }
    )
}

internal fun updateBannerContent(
    state: AppUpdateUiState,
    language: AppLanguage
): UpdateBannerContent {
    val isPt = language == AppLanguage.PT
    val version = state.update.version

    return when (state) {
        is AppUpdateUiState.Available -> UpdateBannerContent(
            title = if (isPt) "Nova versão $version disponível" else "Version $version is available",
            actionLabel = if (isPt) "Baixar atualização" else "Download update",
            tone = AppTone.INFO
        )

        is AppUpdateUiState.Downloading -> UpdateBannerContent(
            title = when {
                // Sem tamanho declarado não há porcentagem, e inventar uma seria
                // pior que dizer só "baixando".
                state.percent == null && isPt -> "Baixando a versão $version…"
                state.percent == null -> "Downloading version $version…"
                isPt -> "Baixando a versão $version — ${state.percent}%"
                else -> "Downloading version $version — ${state.percent}%"
            },
            actionLabel = null,
            tone = AppTone.INFO
        )

        is AppUpdateUiState.Ready -> UpdateBannerContent(
            title = if (isPt) {
                "Versão $version pronta — será aplicada ao fechar"
            } else {
                "Version $version is ready — it will be applied on exit"
            },
            actionLabel = if (isPt) "Reiniciar e atualizar agora" else "Restart and update now",
            tone = AppTone.OK
        )

        is AppUpdateUiState.Failed -> UpdateBannerContent(
            title = updateFailureTitle(version = version, reason = state.reason, isPt = isPt),
            // O caminho manual é o comportamento que o app sempre teve; a falha
            // do automático devolve o usuário a ele em vez de deixá-lo sem saída.
            actionLabel = if (isPt) "Baixar manualmente" else "Download manually",
            tone = AppTone.WARNING
        )
    }
}

private fun updateFailureTitle(
    version: String,
    reason: AppUpdateFailureReason,
    isPt: Boolean
): String {
    return when (reason) {
        AppUpdateFailureReason.DOWNLOAD -> if (isPt) {
            "Falha ao baixar a versão $version"
        } else {
            "Could not download version $version"
        }

        AppUpdateFailureReason.SCHEDULE -> if (isPt) {
            "Falha ao iniciar a instalação da versão $version"
        } else {
            "Could not start the version $version install"
        }
    }
}

internal data class DashboardWarning(
    val target: UsageTargetKey,
    val title: String,
    val description: String,
    val actionLabel: String?
) {
    val source: ApiSource
        get() = target.source
}

internal data class UpdateBannerContent(
    val title: String,
    /** Nulo quando o estado não oferece ação — a faixa deixa de ser clicável. */
    val actionLabel: String?,
    val tone: AppTone
)
