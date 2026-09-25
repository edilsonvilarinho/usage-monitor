package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.ApiSource
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.test.Test
import kotlin.test.assertTrue

class AppProviderMarkTest {

    /**
     * As formas vêm de SVG copiado de fora; um caractere perdido na cópia faz o
     * parser devolver caminho vazio e o card sai sem marca, sem erro nenhum.
     */
    @Test
    fun `toda marca vira um caminho desenhavel`() {
        for (mark in ProviderMark.entries) {
            val nodes = PathParser().parsePathString(mark.pathData).toNodes()
            assertTrue(nodes.size > 3, "A marca $mark gerou só ${nodes.size} comandos.")
            mark.toImageVector()
        }
    }

    @Test
    fun `toda fonte tem marca`() {
        for (source in ApiSource.entries) {
            // O `when` exaustivo já garante na compilação; o teste documenta.
            source.providerMark()
        }
    }
}
