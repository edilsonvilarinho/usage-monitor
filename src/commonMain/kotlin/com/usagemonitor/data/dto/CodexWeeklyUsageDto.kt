package com.usagemonitor.data.dto

/**
 * Contrato interno mínimo para a quota semanal do Codex.
 *
 * A API HTTP real ainda precisa ser descoberta na UI autenticada do ChatGPT.
 * Quando ela existir de forma reutilizável, a implementação remota deve mapear
 * o payload bruto para este formato estável antes de chegar ao mapper.
 */
data class CodexWeeklyUsageResponse(
    val usedPercent: Long,
    val limitWindowSeconds: Long,
    val resetAfterSeconds: Long,
    val resetAt: Long
)
