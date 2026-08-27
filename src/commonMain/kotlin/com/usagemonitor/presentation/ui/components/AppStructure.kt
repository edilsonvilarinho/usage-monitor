package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Primitivas estruturais do sistema visual.
 *
 * Todas stateless: dados por parâmetro, eventos por lambda. É o que impede a
 * próxima tela de reinventar um retângulo — a razão de o app ter chegado a ter
 * vinte arquivos com forma e raio próprios.
 *
 * O vocabulário é fechado: **superfície de dados** ([AppDataSurface]) com
 * **cabeçalho** ([AppSectionHeader]) e **linhas** ([AppDataRow]), organizadas
 * por uma **barra de controles** ([AppToolbar]) e por **abas** ([AppTabs]),
 * dentro do **corpo da janela** ([AppWindowScaffold]).
 */

/** Espessura da borda de toda superfície de dados. Um valor, um dono. */
val AppBorderWidth: Dp = 1.dp

/** Largura do marcador semântico que identifica fonte ou severidade. */
private val MARKER_WIDTH = 2.dp

/**
 * Do começo da linha até a primeira célula: o marcador mais o vão que o segue.
 *
 * [AppDataRow] separa os filhos por [AppSpacing.md], e o primeiro deles é o
 * [AppSourceMarker] de 2dp. Uma faixa de legendas não é linha de dados e não tem
 * marcador; sem repetir esta soma ela prometeria um alinhamento deslocado do que
 * as linhas entregam. Existe aqui, e não como literal em cada tela, porque são
 * duas listas que precisam concordar com a mesma linha de dados.
 */
val AppMarkerGutter: Dp = MARKER_WIDTH + AppSpacing.md

/** Altura da barra de controles e da barra de estado. */
private val TOOLBAR_HEIGHT = 34.dp
private val STATUS_BAR_HEIGHT = 30.dp

/** Altura mínima de uma linha de dados: alvo de clique sem inchar a lista. */
private val ROW_MIN_HEIGHT = 32.dp

/**
 * Corpo de uma janela: fundo, padding e o espaçamento entre blocos.
 *
 * Não desenha barra de título — no desktop ela é `DesktopWindowFrame`, que vive
 * em `desktopMain` porque mexe com a janela AWT. Aqui fica só o que é comum às
 * seis janelas: a cor de fundo, a margem e a [statusBar] opcional, que é sempre
 * a última linha e nunca rola junto com o conteúdo.
 */
@Composable
fun AppWindowScaffold(
    modifier: Modifier = Modifier,
    contentPadding: Dp = AppSpacing.lg,
    spacing: Dp = AppSpacing.md,
    statusBar: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
        if (statusBar != null) {
            AppStatusBar(content = statusBar)
        }
    }
}

/**
 * Barra de estado do rodapé: 30dp, texto de 10, divisória em cima.
 *
 * Separada de [AppWindowScaffold] porque também aparece dentro de painel — o
 * card do dashboard tem a sua, com as ações da fonte.
 */
@Composable
fun AppStatusBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .height(STATUS_BAR_HEIGHT)
                .padding(horizontal = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            content = content
        )
    }
}

/**
 * Barra de controles: seletores, filtros e ações numa faixa só de 34dp.
 *
 * O `heightIn` é mínimo e não fixo de propósito: numa janela estreita os
 * controles quebram para uma segunda linha, e altura travada os cortaria.
 */
@Composable
fun AppToolbar(
    modifier: Modifier = Modifier,
    spacing: Dp = AppSpacing.md,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = TOOLBAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Superfície de dados: fundo de painel, borda de 1dp, raio 8, **sem sombra**.
 *
 * É a substituta do card com brilho de acento. A cor da fonte, quando existe,
 * entra pelo marcador de [AppSectionHeader] — 2dp na vertical —, não como
 * gradiente sobre a superfície inteira.
 */
@Composable
fun AppDataSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.medium,
    contentPadding: Dp = AppSpacing.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content
    )
}

/**
 * Superfície de dados cujo conteúdo encosta na borda.
 *
 * Existe separada porque uma lista de [AppDataRow] não pode ter padding em
 * volta: a divisória de cada linha precisa atravessar o painel de ponta a ponta,
 * ou a lista deixa de ler como tabela.
 */
@Composable
fun AppDataSurfaceFlush(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.medium,
    header: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        if (header != null) {
            header()
            AppDivider()
        }
        content()
    }
}

/**
 * Cabeçalho de painel: marcador opcional, título, subtítulo e ações à direita.
 *
 * O [markerColor] é a identidade da fonte ou a severidade. Ele é um traço de
 * 2dp e não o fundo do painel — a mesma cor que antes lavava o card inteiro
 * agora só orienta a varredura.
 */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    markerColor: Color? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOOLBAR_HEIGHT)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (markerColor != null) {
            AppSourceMarker(color = markerColor)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                content = trailing
            )
        }
    }
}

/**
 * Marcador vertical de 2dp que identifica fonte ou severidade.
 *
 * `fillMaxHeight` não serve aqui: dentro de uma `Row` ele mediria a altura da
 * linha antes de o conteúdo existir. O `IntrinsicSize` que resolveria isso custa
 * uma medição a mais por linha, e a lista de sessões tem centenas — daí a altura
 * chegar por parâmetro.
 */
@Composable
fun AppSourceMarker(
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp
) {
    Box(
        modifier = modifier
            .width(MARKER_WIDTH)
            .height(height)
            .clip(AppShapes.extraSmall)
            .background(color)
    )
}

/**
 * Guia vertical que liga um bloco aninhado à linha que o abriu.
 *
 * Uma lista que abre sub-itens numa `LazyColumn` não pode aninhar uma lista
 * dentro da outra — aninhar quebra a rolagem e desliga o reaproveitamento —,
 * então os filhos entram como itens **irmãos** do pai. O preço é que nada na
 * árvore diz que aqueles itens pertencem à linha de cima: eles são retângulos
 * soltos com um recuo, e recuo sozinho lê como alinhamento diferente, não como
 * nível abaixo (issue #104).
 *
 * O traço é desenhado por item e fica contínuo porque a lista não tem vão entre
 * eles. É `drawBehind` e não `Modifier.border`: aquele arredonda a espessura
 * para cima e pinta **depois** do conteúdo, que foi o defeito da issue #83.
 * Aqui o traço não ocupa layout nenhum — quem reserva o espaço é o `padding` do
 * próprio item, que é onde [indent] também é aplicado.
 *
 * @param indent distância da borda esquerda do item até o traço. É o mesmo
 *   recuo que o conteúdo recebe, e o traço mora na metade dele.
 */
fun Modifier.appNestedGroupGuide(color: Color, indent: Dp): Modifier {
    return drawBehind {
        val strokeWidth = MARKER_WIDTH.toPx()
        // Na metade do recuo: encostado no conteúdo ele viraria borda da célula,
        // e encostado na borda do item ele viraria borda da lista.
        val left = indent.toPx() / 2f - strokeWidth / 2f
        drawRect(
            color = color,
            topLeft = Offset(left, 0f),
            size = Size(strokeWidth, size.height)
        )
    }
}

/**
 * Item de um bloco aninhado: o terceiro degrau da escada de superfícies.
 *
 * A escada tem três degraus neutros — a faixa do grupo em `surfaceVariant`, a
 * linha do pai transparente sobre o fundo da janela e o bloco dos filhos em
 * `surface`. O bloco é `surface` e **nunca** `surfaceVariant`: aquele é o realce
 * de hover do [AppDataRow], e com ele aqui passar o mouse num filho deixaria de
 * dar retorno nenhum.
 *
 * Não é [AppDataSurfaceFlush]. Aquele recorta, arredonda e desenha borda em
 * volta do que envolve; aplicado por item de `LazyColumn` — que é como o bloco
 * aninhado tem de ser montado, porque aninhar lista em lista quebra a rolagem —,
 * ele poria uma caixa arredondada em volta de **cada** filho e cortaria a guia
 * de 2dp, que só fica contínua porque a lista não tem vão entre itens.
 *
 * @param indent recuo do conteúdo, e o dobro da distância do traço à borda.
 */
@Composable
fun Modifier.appNestedGroupItem(indent: Dp): Modifier {
    return this
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .appNestedGroupGuide(
            color = MaterialTheme.colorScheme.outlineVariant,
            indent = indent
        )
        .padding(start = indent)
}

/**
 * Linha de dados: altura mínima de 32dp, divisória embaixo e realce no hover.
 *
 * A divisória é do próprio item e não um separador entre itens porque a lista é
 * frequentemente `LazyColumn`: um separador intercalado seria mais um item para
 * o `LazyColumn` compor e deslocaria todo índice.
 *
 * [showDivider] existe para a última linha do painel, onde a divisória
 * duplicaria a borda de baixo.
 */
@Composable
fun AppDataRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    highlighted: Boolean = false,
    horizontalPadding: Dp = AppSpacing.md,
    verticalPadding: Dp = AppSpacing.sm,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        highlighted || hovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .then(clickable)
                .background(background)
                .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            content = content
        )
        if (showDivider) {
            AppDivider()
        }
    }
}

/**
 * Faixa de legendas de coluna, uma vez para a lista inteira.
 *
 * É o `thead` do protótipo. A alternativa — cada célula reimprimindo o próprio
 * rótulo — dobra o texto da tela e o ruído cresce com o número de linhas: a
 * legenda pertence à coluna, não à célula.
 *
 * Ela só cumpre a promessa se a linha **não quebrar**: as células precisam ser
 * `Row` de largura fixa, e a soma das larguras tem de caber na largura mínima da
 * janela. Onde isso não for verdade, a faixa mente sobre o alinhamento.
 *
 * Fora da `LazyColumn`, nunca `stickyHeader`: na visão global de time já há
 * faixas de conta rolando dentro da lista, e dois níveis de cabeçalho grudado
 * empilhariam.
 */
@Composable
fun AppColumnHeaderRow(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AppSpacing.md,
    /** Vão até a primeira coluna; o default acompanha o marcador da linha de dados. */
    startGutter: Dp = AppMarkerGutter,
    /** O default é o vão entre células do [AppDataRow]; passar outro obriga a linha a passar o mesmo. */
    spacing: Dp = AppSpacing.md,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(start = startGutter),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom,
        content = content
    )
}

/**
 * Uma legenda de coluna.
 *
 * Morava em `CliSessionsScreen.kt` — arquivo de tela — e já era consumida por
 * três telas, o mesmo defeito que fez `MetricCard` virar [AppMetricBlock].
 */
@Composable
fun AppColumnHeaderLabel(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Valor de célula, sem rótulo: quem nomeia a coluna é [AppColumnHeaderLabel].
 *
 * `label*` — mono — e não `body*`: a escala divide as duas famílias por papel, e
 * número em fonte proporcional não alinha coluna, que é a razão de a mono estar
 * aqui.
 */
@Composable
fun AppCellValue(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1
) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelMedium,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Bloco de métrica: rótulo em cima, valor abaixo, dentro de uma borda.
 *
 * É o `.metric` do protótipo, e a ordem importa: o rótulo diz o que o número
 * significa e vem primeiro, porque numa fileira de quatro blocos o olho varre os
 * rótulos para achar o que procura, não os valores.
 *
 * O valor fica **na cor do texto**. O acento é identidade de fonte e vive no
 * marcador de seção e na linha do gráfico; repetido em cada número, a fileira
 * inteira vira paleta e nada se destaca. [footerColor] é a exceção — ali a cor
 * qualifica o rodapé, que é comentário sobre o valor, não o valor.
 *
 * [labelTrailing] existe para o `?` do glossário: ele pertence ao rótulo, e um
 * slot é o que evita a primitiva importar o vocabulário da tela de sessões.
 */
@Composable
fun AppMetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    footerColor: Color? = null,
    labelTrailing: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // O rótulo cede a largura ao que vier depois: truncado ele ainda
                // se lê, mas um ícone empurrado para fora do bloco some.
                modifier = Modifier.weight(1f, fill = false)
            )
            if (labelTrailing != null) {
                labelTrailing()
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = footerColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A mesma divisória na vertical, para colunas lado a lado. */
@Composable
fun AppVerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(AppBorderWidth)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Divisória de 1dp na cor da borda. O único traço horizontal do sistema. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppBorderWidth)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Abas com sublinhado, não pílula.
 *
 * Pílula é o que o app usa hoje para aba, para chip de filtro e para janela de
 * tempo ao mesmo tempo — três funções com o mesmo desenho. Aqui a aba troca o
 * conteúdo da tela, e o sublinhado a distingue do controle segmentado, que
 * escolhe um parâmetro do mesmo conteúdo.
 *
 * Recebe rótulos e índice: quem guarda a escolha é a tela, como em todo o resto
 * deste arquivo.
 */
@Composable
fun AppTabs(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            tabs.forEachIndexed { index, tab ->
                AppTabItem(
                    tab = tab,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
        AppDivider()
    }
}

/** Uma aba: rótulo e a `testTag` que a tela usa para encontrá-la. */
data class AppTab(
    val label: String,
    val testTag: String? = null
)

/**
 * O sublinhado é desenhado sob o rótulo, **não** é um `Box` abaixo dele.
 *
 * Um `Box(Modifier.fillMaxWidth())` dentro de uma `Column` filha de `Row` faz a
 * coluna inteira esticar até a largura disponível: a primeira aba cobria as
 * outras duas e todo clique caía nela. `drawBehind` mede o que o texto mede.
 */
@Composable
private fun AppTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val underline = if (selected) contentColor else Color.Transparent
    val tagged = if (tab.testTag != null) Modifier.testTag(tab.testTag) else Modifier

    Text(
        text = tab.label,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        maxLines = 1,
        modifier = tagged
            .selectable(selected = selected, onClick = onClick)
            .drawBehind {
                val thickness = TAB_UNDERLINE_THICKNESS.toPx()
                drawRect(
                    color = underline,
                    topLeft = Offset(0f, size.height - thickness),
                    size = Size(size.width, thickness)
                )
            }
            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm)
    )
}

private val TAB_UNDERLINE_THICKNESS = 2.dp
