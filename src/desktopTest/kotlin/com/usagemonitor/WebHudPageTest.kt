package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.ui.components.providerMark
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A HUD no navegador (#388) desenha o anel em Canvas 2D, sem o Compose. Desde
 * a #517 ela usa as **mesmas** formas do `AppProviderMark`, para toda fonte: a
 * cópia dos paths fica no `hud.html`, e este teste é o que impede as duas de
 * divergirem quando uma marca mudar no app.
 */
class WebHudPageTest {

    private val page = checkNotNull(javaClass.getResourceAsStream("/web/hud.html")).use { it.readBytes().decodeToString() }

    @Test
    fun `a HUD web desenha a marca de toda fonte com os paths do app`() {
        val missing = ApiSource.entries.mapNotNull { source ->
            val mark = source.providerMark()
            when {
                "${source.name}:'${mark.name}'" !in page -> "${source.name} sem marca"
                "${mark.name}:{d:'${mark.pathData}',eo:${mark.evenOdd}}" !in page -> "${mark.name} com path diferente do app"
                else -> null
            }
        }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }
}
