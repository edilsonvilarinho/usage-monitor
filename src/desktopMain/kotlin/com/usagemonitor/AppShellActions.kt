package com.usagemonitor

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.presentation.ui.components.WindowMode

/**
 * As ações do app que não pertencem a uma conta: as do rodapé do modo padrão.
 *
 * Existe porque elas têm **duas portas** — o rodapé e o balão da engrenagem da
 * barra HUD — e escritas duas vezes em `main()` divergiriam no primeiro
 * breadcrumb ou na primeira condição de admin que mudasse só num lado. `main()`
 * as monta uma vez; o `DashboardScreen` e o `HudWindowHost` só as consomem.
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
    val openTeamPresenceOverview: (() -> Unit)?
)
