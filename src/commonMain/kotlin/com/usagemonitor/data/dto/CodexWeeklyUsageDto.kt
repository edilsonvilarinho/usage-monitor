package com.usagemonitor.data.dto

/**
 * Contrato interno mínimo para a quota semanal do Codex.
 *
 * Este contrato existe somente para uma fonte semanal de fallback. A coleta
 * normal usa `secondary_window` da resposta oficial `/backend-api/wham/usage`;
 * se ela não vier, o snapshot é rejeitado até que uma fonte semanal completa
 * esteja disponível.
 */
data class CodexWeeklyUsageResponse(
    val usedPercent: Long,
    val limitWindowSeconds: Long,
    val resetAfterSeconds: Long,
    val resetAt: Long
)
