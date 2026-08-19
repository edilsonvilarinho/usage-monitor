package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Superfície de conteúdo: fundo de painel, borda de 1dp e nada mais.
 *
 * O nome ficou, o brilho saiu. Até a refatoração visual esta função pintava um
 * gradiente de acento descendo do topo de **toda** superfície, e era o principal
 * motivo de a tela ler como uma pilha de cards de mesmo peso: a mesma cor que
 * identificava a fonte lavava o painel inteiro, e blocos aninhados repetiam o
 * efeito uns sobre os outros.
 *
 * Com o gradiente fora, os parâmetros `accent` e `glowAlpha` deixaram de existir
 * — e com eles o objeto `AppGlow`, que nomeava os três patamares de brilho. A
 * cor da fonte volta como marcador de 2dp em [AppSectionHeader], onde orienta a
 * varredura sem competir com o dado.
 *
 * [elevation] fica só onde a superfície **de fato** flutua: diálogo e overlay.
 * O default é [AppElevation.card], que agora é zero.
 */
@Composable
fun DepthSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.medium,
    elevation: Dp = AppElevation.card,
    contentPadding: Dp = AppSpacing.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Sombra só quando há elevação; com zero o `Surface` não desenha nada e a
        // borda abaixo é a única separação, que é justamente a intenção.
        shadowElevation = elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(contentPadding),
            content = content
        )
    }
}
