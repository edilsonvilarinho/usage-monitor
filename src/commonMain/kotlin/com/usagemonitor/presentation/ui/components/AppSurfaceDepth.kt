package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders

/**
 * Bloco de superfície: recorte, fundo e a borda de 1dp, sem layout nenhum.
 *
 * É o mesmo trio que [AppDataSurface] aplica — `clip`, `background`, `border` —
 * separado do contêiner para os casos em que o layout já existe e só a
 * superfície falta. O card do dashboard e os quatro blocos internos dele
 * repetiam as três linhas por extenso, cada um com o próprio `padding`, o
 * próprio alinhamento e, no card, uma cor de fundo animada no hover.
 *
 * O [color] é parâmetro porque o hover **é** uma troca de superfície neutra
 * (`surface` → `surfaceVariant`), que é a regra de hover deste sistema, e porque
 * o bloco interno mora em `surfaceVariant` enquanto o painel mora em `surface`.
 * Não é porta para cor de acento: acento vive no marcador de 2dp e na linha do
 * gráfico.
 *
 * [depth] é [AppDepth.FLAT] por default: o bloco dentro de um painel não tem
 * sombra — sombra dentro de superfície é o empilhamento de blocos de mesmo peso
 * que a refatoração de agosto tirou. Painel e card pedem [AppDepth.CARD]. A
 * sombra vem **antes** do recorte, senão o `clip` a cortaria junto.
 *
 * [sheen] liga o brilho de topo ([appSheen]); só as superfícies de primeiro
 * nível o pedem.
 */
@Composable
fun Modifier.appSurfaceBlock(
    shape: Shape = AppShapes.small,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    depth: AppDepth = AppDepth.FLAT,
    sheen: Boolean = false
): Modifier {
    val base = this.appDepth(depth, shape).clip(shape).background(color)
    if (!sheen) {
        return base.border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    // Só a superfície de primeiro nível ganha a borda em gradiente: no bloco
    // interno ela desenharia uma segunda luz dentro da primeira.
    val ladder = AppSurfaceLadders.current
    return base
        .appSheen()
        .border(
            width = AppBorderWidth,
            brush = Brush.verticalGradient(listOf(ladder.borderTop, ladder.borderBottom)),
            shape = shape
        )
}

/**
 * Sombra em duas camadas de um patamar de [AppDepth].
 *
 * Duas `shadow` empilhadas porque o Compose 1.7 só tem sombra por elevação: a
 * curta ([AppDepth.key]) assenta o objeto no plano e a larga
 * ([AppDepth.ambient]) dá a distância. `clip = false` nas duas, para o conteúdo
 * não ser recortado pela forma da sombra — quem recorta é o chamador, depois.
 */
@Composable
fun Modifier.appDepth(depth: AppDepth, shape: Shape): Modifier {
    return appDepth(key = depth.key, ambient = depth.ambient, shape = shape)
}

/**
 * Versão por valor, para quem anima a profundidade — o card sobe no hover e
 * flutua no arrasto, e um enum não interpola.
 */
@Composable
fun Modifier.appDepth(key: Dp, ambient: Dp, shape: Shape): Modifier {
    if (key <= 0.dp && ambient <= 0.dp) {
        return this
    }
    val ladder = AppSurfaceLadders.current
    val ambientColor = ladder.shadow.copy(alpha = ladder.ambientShadowAlpha)
    val keyColor = ladder.shadow.copy(alpha = ladder.keyShadowAlpha)
    return this
        .shadow(elevation = ambient, shape = shape, clip = false, ambientColor = ambientColor, spotColor = ambientColor)
        .shadow(elevation = key, shape = shape, clip = false, ambientColor = keyColor, spotColor = keyColor)
}

/**
 * Brilho de topo: um gradiente vertical que some no primeiro terço e uma linha
 * de 1dp logo abaixo da borda. É a luz batendo de cima, e é ela que faz a
 * superfície ler como objeto no tema escuro, onde sombra preta sobre fundo
 * quase preto mal aparece.
 *
 * Desenhado **por cima** do conteúdo, dentro do recorte do chamador — a linha
 * não passa dos cantos arredondados. Por cima porque o card pinta o cabeçalho com
 * fundo próprio, e por baixo dele o brilho não chegava à tela; em 4,5% de alfa
 * ele não mexe no contraste do texto. A altura tem teto de [SHEEN_MAX_HEIGHT]:
 * um terço de um card de 600dp seria um véu no meio das cotas. Neutro sempre:
 * gradiente de acento é o que a refatoração de agosto tirou, e não volta.
 */
@Composable
fun Modifier.appSheen(): Modifier {
    val ladder = AppSurfaceLadders.current
    return this.drawWithContent {
        drawContent()
        val sheenHeight = minOf(size.height / 3f, SHEEN_MAX_HEIGHT.toPx())
        if (sheenHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(ladder.sheen, Color.Transparent),
                    startY = 0f,
                    endY = sheenHeight
                ),
                size = Size(size.width, sheenHeight)
            )
        }
        val line = AppBorderWidth.toPx()
        drawRect(
            color = ladder.highlight,
            topLeft = Offset(0f, line),
            size = Size(size.width, line)
        )
    }
}

/** Até onde o brilho de topo desce. */
private val SHEEN_MAX_HEIGHT = 56.dp
