package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.BannerTone
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.UiApiError

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
        ApiSource.KILO -> null
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

    if (error.isMiniMaxEnvVarIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "MiniMax precisa de MINIMAX_API_KEY",
                description = "Defina `MINIMAX_API_KEY` antes de abrir o app e reinicie o monitor. Windows: `set MINIMAX_API_KEY=sua_chave`. No macOS, o app aberto pelo Finder não herda o `export` do shell: use `launchctl setenv MINIMAX_API_KEY sua_chave`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "MiniMax needs MINIMAX_API_KEY",
                description = "Set `MINIMAX_API_KEY` before opening the app and restart the monitor. Windows: `set MINIMAX_API_KEY=your_key`. On macOS the app launched from Finder does not inherit the shell `export`: use `launchctl setenv MINIMAX_API_KEY your_key`.",
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

@Composable
internal fun AppUpdateBanner(
    state: AppUpdateUiState,
    language: AppLanguage,
    onOpenRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = updateBannerContent(state = state, language = language)

    PersistentApiWarningBanner(
        title = content.title,
        description = content.description,
        actionLabel = content.actionLabel,
        onAction = if (content.showAction) onOpenRelease else null,
        tone = BannerTone.INFO,
        modifier = modifier
    )
}

internal fun updateBannerContent(
    state: AppUpdateUiState,
    language: AppLanguage
): UpdateBannerContent {
    return when (state) {
        is AppUpdateUiState.Available -> {
            val title = if (language == AppLanguage.PT) {
                "Nova versão ${state.update.version} disponível"
            } else {
                "Version ${state.update.version} is available"
            }
            val description = if (language == AppLanguage.PT) {
                "A atualização está disponível na release publicada. Abra a página da versão para baixar e instalar."
            } else {
                "The update is available on the published release page. Open the version page to download and install it."
            }

            UpdateBannerContent(
                title = title,
                description = description,
                actionLabel = if (language == AppLanguage.PT) {
                    "Baixar atualização"
                } else {
                    "Download update"
                },
                showAction = true
            )
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
    val description: String,
    val actionLabel: String? = null,
    val showAction: Boolean = false
)
