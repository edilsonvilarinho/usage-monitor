package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.UsageSnapshot

/**
 * Serialização do [UsageSnapshot] para a página web local (#388).
 *
 * Porta no domain pelo mesmo motivo de [UsageExportEncoder]: o formato é
 * detalhe de `data` (kotlinx.serialization), e quem consome — o serviço web em
 * `desktopMain` — não deve conhecer os DTOs.
 */
fun interface UsageSnapshotEncoder {
    fun encode(snapshot: UsageSnapshot): String
}
