package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.usagemonitor.presentation.ui.theme.AppChrome
import com.usagemonitor.presentation.ui.theme.AppShapes

/*
 * Os botões da barra de título e as decisões de maximizar, fora de
 * `DesktopWindowFrame.kt`. Aquele arquivo é cola de janela (moldura, `window.shape`,
 * arrasto) e sai do Kover por nome (onda 6.5 do plano `cobertura-95-execucao.md`); o
 * que decide alguma coisa ou tem teste de componente mora aqui e segue contado.
 */

/**
 * Altura da barra de título das seis janelas.
 *
 * Sai de [AppChrome], onde os cinco patamares do cromo vivem: era um literal
 * aqui e um `private val` em `AppStructure`, os dois com o mesmo 34 e nenhum
 * sabendo do outro.
 */
internal val TITLE_BAR_HEIGHT = AppChrome.titleBar

/** O que o botão de maximizar faz: restaura se a janela já está maximizada, senão maximiza. */
internal fun maximizeToggled(current: WindowPlacement): WindowPlacement =
    if (current == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized

/** O glifo do botão de maximizar: `❐` quando a janela já está maximizada (restaura), `□` quando não. */
internal fun maximizeButtonLabel(placement: WindowPlacement): String =
    if (placement == WindowPlacement.Maximized) "❐" else "□"

@Composable
internal fun TitleBarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Descrição semântica de ações cujo glifo não se explica.
     *
     * Minimizar, maximizar e fechar são o vocabulário de janela que todo sistema
     * desenha igual e dispensam descrição; um glifo fora dele, não.
     */
    description: String? = null,
    hoverColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
) {
    val defaultColor = Color.Transparent
    val textColor = MaterialTheme.colorScheme.onSurface
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(
        modifier = modifier
            // Retângulo que preenche a altura da barra, como numa janela de
            // sistema: o botão arredondado flutuando dentro dela era o único
            // lugar do app onde um controle não encostava na própria moldura.
            .width(40.dp)
            .height(TITLE_BAR_HEIGHT - 1.dp)
            .clip(AppShapes.extraSmall)
            .background(if (isHovered) hoverColor else defaultColor)
            .hoverable(hoverInteraction)
            .clickable(onClick = onClick)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}
