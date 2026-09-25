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
import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import com.usagemonitor.domain.repository.CursorUsageFailureKind
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
    // Falha de conectividade não é problema de uma fonte específica — é a rede
    // inteira sem proxy —, então o retry vale para qualquer fonte, não só a
    // Anthropic. Checado antes do `when` para não precisar repetir o mesmo
    // lambda nos 8 ramos.
    if (warning.forcesUniversalRetry) {
        return { onRetryTarget(warning.target) }
    }
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
        ApiSource.GEMINI -> null
        ApiSource.CURSOR -> null
        ApiSource.ANTIGRAVITY -> null
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

    // Avaliada primeiro: falha de conectividade (proxy corporativo ausente ou
    // incorreto, DNS, timeout de conexão) nunca teve resposta HTTP nenhuma, e
    // não pode ser confundida com rate limit (429) ou credencial (401/403).
    if (error.isConnectivityIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "$label sem conexão",
                description = "Não foi possível conectar ao servidor. Se a rede exige proxy corporativo, configure-o em Configurações > Rede e reinicie o Usage Monitor para aplicar (não é preciso reiniciar o computador).",
                actionLabel = "Tentar novamente",
                forcesUniversalRetry = true
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "$label could not connect",
                description = "Could not connect to the server. If the network requires a corporate proxy, configure it under Settings > Network and restart Usage Monitor to apply it (no need to restart your computer).",
                actionLabel = "Retry",
                forcesUniversalRetry = true
            )
        }
    }

    if (error.isProxyAuthIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "Proxy exige autenticação",
                description = "O proxy configurado recusou a credencial enviada (HTTP 407). Revise usuário e senha em Configurações > Rede e reinicie o Usage Monitor.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "Proxy requires authentication",
                description = "The configured proxy rejected the sent credential (HTTP 407). Review the username and password under Settings > Network and restart Usage Monitor.",
                actionLabel = null
            )
        }
    }

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

    if (error.isGeminiSessionHistoryUnreadable) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                target = error.target,
                title = "Histórico local do Gemini CLI indisponível",
                description = "Os arquivos de sessão locais não contêm registros de uso reconhecidos. Verifique se o histórico foi gerado pelo Gemini CLI e atualize novamente.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                target = error.target,
                title = "Gemini CLI local history is unavailable",
                description = "The local session files contain no recognized usage records. Check that Gemini CLI generated the history, then refresh.",
                actionLabel = null
            )
        }
    }

    error.antigravityFailureKind?.let { kind ->
        return antigravityWarning(error, kind, language)
    }

    error.cursorFailureKind?.let { kind ->
        return cursorWarning(error, kind, language)
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
    val action = updateBannerAction(
        state = state,
        onOpenRelease = onOpenRelease,
        onRestartAndUpdate = onRestartAndUpdate
    )

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

/**
 * O que a ação da atualização faz em cada estado. O despacho mora junto dos
 * rótulos de [updateBannerContent] e tem uma dona só: a faixa do modo padrão e o
 * balão da engrenagem da HUD oferecem a mesma ação, e dois `when` divergiriam.
 */
internal fun updateBannerAction(
    state: AppUpdateUiState,
    onOpenRelease: () -> Unit,
    onRestartAndUpdate: () -> Unit
): (() -> Unit)? {
    return when (state) {
        is AppUpdateUiState.Available -> onOpenRelease
        is AppUpdateUiState.Ready -> onRestartAndUpdate
        is AppUpdateUiState.Failed -> onOpenRelease
        // Baixando não tem ação: um alvo clicável sem rótulo de ação seria um
        // alvo de clique invisível.
        is AppUpdateUiState.Downloading -> null
    }
}

/**
 * A ação de aplicar a atualização pronta (issue #274). Diz **o que** reinicia:
 * "Reiniciar e atualizar agora" era lido como reiniciar o computador. "o app" e
 * não "o Usage Monitor": a faixa é de uma linha e quem cede espaço é o título, e
 * com o nome inteiro o rótulo passava de ~209dp para ~281dp e o título sumia numa
 * janela de 400dp. O nome do app vai no título. Constantes porque o passo da ajuda
 * cita o rótulo, e uma cópia literal lá divergiria na primeira troca de texto.
 */
internal const val UPDATE_RESTART_ACTION_PT = "Reiniciar o app e atualizar"
internal const val UPDATE_RESTART_ACTION_EN = "Restart app and update"

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
                "Versão $version pronta — será aplicada ao fechar o Usage Monitor"
            } else {
                "Version $version is ready — applies when Usage Monitor closes"
            },
            actionLabel = if (isPt) UPDATE_RESTART_ACTION_PT else UPDATE_RESTART_ACTION_EN,
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
    val actionLabel: String?,
    /**
     * Falha de conectividade (issue #174) não é problema de uma fonte
     * específica — sem essa marca, ela virava banner sem ação (só a Anthropic
     * tem retry em [warningActionFor]) e o usuário atrás de proxy corporativo
     * perdia o botão "Tentar novamente" que o erro genérico já oferecia antes.
     */
    val forcesUniversalRetry: Boolean = false
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

/**
 * Nenhum dos quatro oferece "Tentar novamente": todos dependem de uma ação fora do
 * app (instalar, atualizar, autenticar) ou de reiniciá-lo, e repetir a coleta
 * devolveria a mesma falha.
 */
private fun antigravityWarning(
    error: UiApiError,
    kind: AntigravityUsageFailureKind,
    language: AppLanguage
): DashboardWarning {
    val pt = language == AppLanguage.PT
    val (title, description) = when (kind) {
        AntigravityUsageFailureKind.CLI_NOT_INSTALLED,
        AntigravityUsageFailureKind.UNSUPPORTED_LAUNCHER -> if (pt) {
            "Antigravity CLI não encontrado" to
                "Instale o Antigravity CLI (agy) ou desative esta integração em Configurações > APIs. " +
                "O monitor procura o executável agy em %LOCALAPPDATA%\\agy\\bin e no PATH, nunca um atalho .cmd ou .bat."
        } else {
            "Antigravity CLI not found" to
                "Install the Antigravity CLI (agy) or disable this integration under Settings > APIs. " +
                "The monitor looks for the agy executable in %LOCALAPPDATA%\\agy\\bin and on PATH, never a .cmd or .bat shim."
        }
        AntigravityUsageFailureKind.UNVERIFIED_VERSION -> if (pt) {
            "Atualize o Antigravity CLI" to
                "Esta integração foi verificada com o agy 1.2.9 ou mais novo. Rode `agy update` e o monitor volta a ler as cotas na próxima coleta."
        } else {
            "Update the Antigravity CLI" to
                "This integration was verified against agy 1.2.9 or newer. Run `agy update` and the monitor reads the quotas again on the next refresh."
        }
        AntigravityUsageFailureKind.AUTHENTICATION_UNAVAILABLE -> if (pt) {
            "Antigravity CLI sem login" to
                "Abra o agy num terminal e faça login. O monitor usa a sessão que já existe e não inicia login por conta própria."
        } else {
            "Antigravity CLI is signed out" to
                "Open agy in a terminal and sign in. The monitor uses the existing session and never starts a sign-in itself."
        }
        AntigravityUsageFailureKind.COLLECTION_PAUSED -> if (pt) {
            "Coleta do Antigravity pausada" to
                "O CLI não confirmou ter respondido /usage sozinho, e a próxima chamada poderia gastar cota de modelo. A coleta fica parada até o Usage Monitor ser reiniciado."
        } else {
            "Antigravity collection paused" to
                "The CLI did not confirm that it answered /usage by itself, and another call could spend model quota. Collection stays paused until Usage Monitor is restarted."
        }
    }
    return DashboardWarning(
        target = error.target,
        title = title,
        description = description,
        actionLabel = null
    )
}

/**
 * Nenhum oferece "Tentar novamente": instalar ou entrar no Cursor acontece fora do
 * app, e o plano sem nada a medir continua igual na próxima coleta.
 */
private fun cursorWarning(
    error: UiApiError,
    kind: CursorUsageFailureKind,
    language: AppLanguage
): DashboardWarning {
    val pt = language == AppLanguage.PT
    val (title, description) = when (kind) {
        CursorUsageFailureKind.NOT_INSTALLED -> if (pt) {
            "Cursor não encontrado" to
                "O banco local do editor Cursor não existe nesta máquina. Instale e abra o Cursor, ou desative esta integração em Configurações > APIs."
        } else {
            "Cursor not found" to
                "The Cursor editor's local database does not exist on this machine. Install and open Cursor, or disable this integration under Settings > APIs."
        }
        CursorUsageFailureKind.SIGNED_OUT, CursorUsageFailureKind.SESSION_REJECTED -> if (pt) {
            "Cursor sem sessão" to
                "Entre na sua conta no editor Cursor. O monitor usa a sessão que o editor já mantém e não inicia login por conta própria."
        } else {
            "Cursor is signed out" to
                "Sign in to your account in the Cursor editor. The monitor uses the session the editor already keeps and never starts a sign-in itself."
        }
        CursorUsageFailureKind.NOTHING_METERED -> if (pt) {
            "Cursor sem franquia medida" to
                "O plano desta conta não informa nenhuma franquia com percentual ou teto. Isso não é consumo zero: não há o que medir."
        } else {
            "Cursor has nothing metered" to
                "This account's plan reports no allowance with a percentage or a ceiling. That is not zero usage: there is nothing to measure."
        }
    }
    return DashboardWarning(
        target = error.target,
        title = title,
        description = description,
        actionLabel = null
    )
}
