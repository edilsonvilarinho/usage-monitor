package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.BannerTone
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.UiApiError

internal fun warningActionFor(
    source: ApiSource,
    onRetryAnthropic: () -> Unit
): (() -> Unit)? {
    return when (source) {
        ApiSource.ANTHROPIC -> onRetryAnthropic
        ApiSource.MINIMAX -> null
        ApiSource.CODEX -> null
        ApiSource.DEEPSEEK -> null
        ApiSource.OPENCODE -> null
        ApiSource.KILO -> null
    }
}

internal fun warningFor(
    error: UiApiError,
    language: AppLanguage
): DashboardWarning? {
    if (error.isRateLimitIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "${sourceLabelFromKey(error.source)} temporariamente limitado",
                description = "A API respondeu HTTP 429. Isso normalmente é limite de requisições ou cota temporária do próprio serviço; estar logado no Claude Code não evita esse bloqueio. Aguarde a janela de limite liberar e tente novamente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "${sourceLabelFromKey(error.source)} is temporarily limited",
                description = "The API returned HTTP 429. This usually means a request limit or temporary quota window on the service side; being signed in to Claude Code does not bypass it. Wait for the limit window to clear, then retry.",
                actionLabel = "Retry"
            )
        }
    }

    if (error.isServiceUnavailableIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "${sourceLabelFromKey(error.source)} temporariamente indisponível",
                description = "O serviço remoto respondeu com indisponibilidade temporária ou falha de upstream. Aguarde alguns instantes e tente novamente. As outras integrações podem continuar funcionando normalmente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "${sourceLabelFromKey(error.source)} is temporarily unavailable",
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
                    source = error.source,
                    title = "Anthropic precisa revalidar a sessão",
                    description = "1. Feche o Usage Monitor.\n2. Abra o Claude Code e confirme que a sessão está ativa; se preciso, faça login novamente.\n3. Abra o Usage Monitor outra vez.\n4. Se ainda falhar, desative temporariamente Anthropic nas configurações para continuar vendo as outras APIs.",
                    actionLabel = "Tentar novamente"
                )
            } else {
                DashboardWarning(
                    source = error.source,
                    title = "Anthropic needs the session refreshed",
                    description = "1. Close Usage Monitor.\n2. Open Claude Code and confirm the session is active; sign in again if needed.\n3. Open Usage Monitor again.\n4. If it still fails, temporarily disable Anthropic in settings so the other APIs keep working.",
                    actionLabel = "Retry"
                )
            }
        }

        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "Anthropic precisa de autenticação",
                description = "Faça login no Claude Code para recriar ou renovar `~/.claude/.credentials.json` e depois tente novamente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "Anthropic needs authentication",
                description = "Sign in with Claude Code to recreate or renew `~/.claude/.credentials.json`, then try again.",
                actionLabel = "Retry"
            )
        }
    }

    if (error.isMiniMaxEnvVarIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "MiniMax precisa de MINIMAX_API_KEY",
                description = "Defina `MINIMAX_API_KEY` antes de abrir o app e reinicie o monitor. Exemplo no Windows: `set MINIMAX_API_KEY=sua_chave`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "MiniMax needs MINIMAX_API_KEY",
                description = "Set `MINIMAX_API_KEY` before opening the app and restart the monitor. Example on Windows: `set MINIMAX_API_KEY=your_key`.",
                actionLabel = null
            )
        }
    }

    if (error.isMiniMaxInactivePlanIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "MiniMax sem plano/token ativo",
                description = "A conta MiniMax respondeu que não há assinatura ativa para consultar as cotas. Ative um plano ou gere um token vinculado a uma assinatura válida e depois atualize o monitor.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "MiniMax has no active plan/token",
                description = "The MiniMax account reported that there is no active subscription available for quota checks. Activate a plan or generate a token tied to a valid subscription, then refresh the monitor.",
                actionLabel = null
            )
        }
    }

    if (error.isOpenCodeLocalIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "OpenCode Zen Free indisponível",
                description = "O banco local do OpenCode não foi encontrado. Abra o OpenCode pelo menos uma vez nesta máquina para gerar `~/.local/share/opencode/opencode.db`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "OpenCode Zen Free is unavailable",
                description = "The local OpenCode database was not found. Open OpenCode at least once on this machine to create `~/.local/share/opencode/opencode.db`.",
                actionLabel = null
            )
        }
    }

    if (error.isKiloLocalIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "Kilo Free indisponível",
                description = "O banco local do Kilo não foi encontrado. Abra o Kilo pelo menos uma vez nesta máquina para gerar `~/.local/share/kilo/kilo.db`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                source = error.source,
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
    val source: ApiSource,
    val title: String,
    val description: String,
    val actionLabel: String?
)

internal data class UpdateBannerContent(
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val showAction: Boolean = false
)
