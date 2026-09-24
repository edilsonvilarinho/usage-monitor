package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.WindowMode

/**
 * As ações do app: as do rodapé do modo padrão e as que o card de uma conta abre.
 *
 * Existe porque elas têm **duas portas** — o rodapé e os cards de um lado, o
 * balão da engrenagem e o balão de cada conta da barra HUD do outro — e, escritas
 * duas vezes em `main()`, divergiriam no primeiro breadcrumb ou na primeira
 * condição de admin que mudasse só num lado. `main()` as monta uma vez; o
 * `DashboardScreen` e o `HudWindowHost` só as consomem.
 *
 * As de admin são nulas para quem não administra: `null` esconde o botão, nas
 * duas portas, pela regra do `FooterBar`.
 */
internal class AppShellActions(
    val refreshAll: () -> Unit,
    val openSettings: () -> Unit,
    val openHelp: () -> Unit,
    val changeWindowMode: (WindowMode) -> Unit,
    /** Escreve o retrato das cotas e devolve o caminho; `null` é diálogo cancelado. */
    val exportSnapshot: suspend (List<ApiUsageStats>) -> String?,
    val onExportFailure: (Throwable) -> Unit,
    val openAdminOverview: (() -> Unit)?,
    val openTeamPresenceOverview: (() -> Unit)?,
    // As janelas que o card de uma conta abre — também do balão da conta na HUD.
    val openHistory: (ApiSource, UsageAccountKey?) -> Unit,
    val openCliSessions: (UsageTargetKey) -> Unit,
    val openCodexCliSessions: (UsageTargetKey) -> Unit,
    val openTeamUsage: (UsageTargetKey) -> Unit,
    val openTeamPresence: (UsageTargetKey) -> Unit
)
