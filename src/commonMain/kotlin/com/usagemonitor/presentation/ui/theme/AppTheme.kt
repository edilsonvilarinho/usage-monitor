package com.usagemonitor.presentation.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Durações: 120 para hover e foco, 180 para seleção, 240 para expandir e
 * recolher.
 *
 * São mais curtas que as anteriores (150/250/350) porque a densidade subiu: numa
 * linha de 32dp de altura, 350ms de transição é tempo suficiente para o olho
 * perceber atraso onde antes havia um card de 96dp se movendo.
 *
 * **Nenhuma animação infinita.** A regra não é estética: animação sem fim trava
 * o `waitForIdle` dos testes de componente, e o `ShimmerBox` — a única que
 * existe — fica onde está sem ser replicada.
 */
object AppMotion {
    const val fast:   Int  = 120
    const val normal: Int  = 180
    const val slow:   Int  = 240

    /**
     * Atraso entre itens vizinhos na entrada de uma grade ou lista.
     *
     * O `ScreenshotGenerator` é calibrado contra ele: o laço de aquecimento
     * dorme tempo real suficiente para cobrir o maior atraso da grade. Encurtar
     * é seguro; **alongar exige revalidar o gerador**, ou a captura sai
     * meio-desenhada.
     */
    const val stagger: Long = 60L

    val enterEasing: Easing = FastOutSlowInEasing
    val exitEasing:  Easing = FastOutLinearInEasing
}

/**
 * Três patamares: 0, 2 e 8.
 *
 * A superfície de dados fica em **zero** — quem a separa do fundo é a borda de
 * 1dp e o espaçamento, não a sombra. Sombra em card de conteúdo foi justamente
 * o que empilhou containers de mesmo peso na tela anterior. [dialog] é o único
 * patamar alto, e existe porque diálogo e menu flutuam de fato sobre a janela.
 */
object AppElevation {
    /** Superfície de dados e banner: separação por borda, não por sombra. */
    val card:   Dp = 0.dp
    val banner: Dp = 0.dp

    /** Overlay curto: tooltip e menu suspenso. */
    val raised: Dp = 2.dp

    /** Diálogo e qualquer coisa que cubra a janela. */
    val dialog: Dp = 8.dp
}

/**
 * Grade de espaçamento: 4 · 8 · 12 · 16 · 24 · 32.
 *
 * Existe para o padding parar de ser um literal diferente em cada arquivo. Os
 * nomes são posicionais de propósito — `sm`/`lg` obrigariam a decidir o que é
 * "grande" antes de saber onde o valor cai.
 */
object AppSpacing {
    val xs:  Dp = 4.dp
    val sm:  Dp = 8.dp
    val md:  Dp = 12.dp
    val lg:  Dp = 16.dp
    val xl:  Dp = 24.dp
    val xxl: Dp = 32.dp
}

/**
 * Raios: 4 · 6 · 8 · 10, e o teto é 10.
 *
 * A escala anterior ia de 10 a 28dp e transformava quase toda superfície num
 * card grande; com tudo arredondado do mesmo jeito, a hierarquia deixava de ser
 * lida. [extraLarge] repete 10 de propósito — o nome sobrevive nas chamadas
 * existentes sem reabrir a porta para um raio maior.
 */
object AppShapes {
    /** Marcador, célula, amostra de cor. */
    val extraSmall = RoundedCornerShape(4.dp)

    /** Botão, campo, banner, bloco de métrica. */
    val small      = RoundedCornerShape(6.dp)

    /** Superfície de dados e painel de gráfico. */
    val medium     = RoundedCornerShape(8.dp)

    /** Janela e diálogo — o teto da escala. */
    val large      = RoundedCornerShape(10.dp)
    val extraLarge = RoundedCornerShape(10.dp)
}

private val appShapes = Shapes(
    extraSmall = AppShapes.extraSmall,
    small      = AppShapes.small,
    medium     = AppShapes.medium,
    large      = AppShapes.large,
    extraLarge = AppShapes.extraLarge
)

/**
 * Escala tipográfica: 10 · 12 · 14 · 16 · 20 · 28.
 *
 * A divisão entre as duas famílias segue o papel do texto, não o tamanho.
 * `label*`, `title*`, `headline*` e `display*` são [AppFontFamilies.mono] —
 * rótulo, número, cabeçalho de coluna e título de painel, onde a largura fixa do
 * dígito é o que alinha a coluna. `body*` é [AppFontFamilies.sans], porque é
 * onde mora o texto corrido: descrição de opção, glossário, mensagem de estado.
 *
 * Sem itálico e sem peso 700: a família carrega 400, 500 e 600, e pedir um peso
 * ausente faz o Skia sintetizar o traço, que numa monoespaçada borra o alinhamento
 * vertical das colunas.
 */
private fun appTypography(fonts: AppFontFamilies) = Typography(
    displayLarge = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 28.sp,
        lineHeight    = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 28.sp,
        lineHeight    = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 28.sp,
        lineHeight    = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleLarge = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily    = fonts.sans,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily    = fonts.sans,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily    = fonts.sans,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 17.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily    = fonts.mono,
        fontWeight    = FontWeight.Medium,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.7.sp
    )
)

/**
 * Superfícies neutras da paleta.
 *
 * São quatro degraus dentro de ~14% de luminância, e é deliberado: a
 * profundidade passa a vir da **borda de 1dp e do espaçamento**, não de sombra
 * nem de gradiente de acento. Quatro superfícies muito distintas entre si
 * reconstruiriam por volume a hierarquia que este sistema quer ler por camada.
 *
 * [AppSurfaces] existe como objeto nomeado — e não só como argumento do
 * `darkColorScheme` — porque os degraus não têm papel Material equivalente um a
 * um: `raised` cobre `surfaceVariant` e a família `surfaceContainer*` ao mesmo
 * tempo, e sem um nome próprio cada chamada repetiria o literal.
 */
object AppSurfaces {
    // ── Escuro (a referência aprovada) ────────────────────────────────────────
    val darkBackground = Color(0xFF131010)
    val darkSurface    = Color(0xFF1B1818)
    val darkRaised     = Color(0xFF211E1E)
    val darkBorder     = Color(0xFF3D3838)
    val darkForeground = Color(0xFFF2EDED)
    val darkMuted      = Color(0xFFB8B2B2)

    // ── Claro ─────────────────────────────────────────────────────────────────
    val lightBackground = Color(0xFFF6F3F3)
    val lightSurface    = Color(0xFFFFFCFC)
    val lightRaised     = Color(0xFFEFEAEA)
    val lightBorder     = Color(0xFFD7D0D0)
    val lightForeground = Color(0xFF171414)
    val lightMuted      = Color(0xFF686060)
}

// O azul de informação é o mesmo `input` de `AppAccents`, e o vermelho é o mesmo
// `saturated`: um segundo vermelho "de erro" ao lado do vermelho de contexto
// saturado faria a tela ter duas cores para a mesma ideia.
private val DarkInfo     = Color(0xFF4C8DFF)
private val DarkCritical = Color(0xFFE86A6A)
private val LightInfo     = Color(0xFF1565C0)
private val LightCritical = Color(0xFFB3261E)

// ── Paleta Dark ───────────────────────────────────────────────────────────────
private val usageMonitorDarkColorScheme = darkColorScheme(
    background              = AppSurfaces.darkBackground,
    onBackground            = AppSurfaces.darkForeground,
    surface                 = AppSurfaces.darkSurface,
    onSurface               = AppSurfaces.darkForeground,
    surfaceVariant          = AppSurfaces.darkRaised,
    onSurfaceVariant        = AppSurfaces.darkMuted,
    surfaceContainerLowest  = AppSurfaces.darkBackground,
    surfaceContainerLow     = AppSurfaces.darkSurface,
    surfaceContainer        = AppSurfaces.darkRaised,
    surfaceContainerHigh    = AppSurfaces.darkRaised,
    surfaceContainerHighest = AppSurfaces.darkRaised,
    primary                 = DarkInfo,
    onPrimary               = AppSurfaces.darkBackground,
    primaryContainer        = AppSurfaces.darkRaised,
    onPrimaryContainer      = AppSurfaces.darkForeground,
    secondary               = AppSurfaces.darkMuted,
    onSecondary             = AppSurfaces.darkBackground,
    secondaryContainer      = AppSurfaces.darkRaised,
    onSecondaryContainer    = AppSurfaces.darkMuted,
    tertiary                = AppSurfaces.darkMuted,
    onTertiary              = AppSurfaces.darkBackground,
    tertiaryContainer       = AppSurfaces.darkRaised,
    onTertiaryContainer     = AppSurfaces.darkForeground,
    error                   = DarkCritical,
    onError                 = AppSurfaces.darkBackground,
    errorContainer          = AppSurfaces.darkRaised,
    onErrorContainer        = DarkCritical,
    outline                 = AppSurfaces.darkBorder,
    outlineVariant          = AppSurfaces.darkBorder,
    surfaceTint             = DarkInfo,
    inverseSurface          = AppSurfaces.darkForeground,
    inverseOnSurface        = AppSurfaces.darkBackground
)

// ── Paleta Light ──────────────────────────────────────────────────────────────
private val usageMonitorLightColorScheme = lightColorScheme(
    background              = AppSurfaces.lightBackground,
    onBackground            = AppSurfaces.lightForeground,
    surface                 = AppSurfaces.lightSurface,
    onSurface               = AppSurfaces.lightForeground,
    surfaceVariant          = AppSurfaces.lightRaised,
    onSurfaceVariant        = AppSurfaces.lightMuted,
    surfaceContainerLowest  = AppSurfaces.lightSurface,
    surfaceContainerLow     = AppSurfaces.lightBackground,
    surfaceContainer        = AppSurfaces.lightRaised,
    surfaceContainerHigh    = AppSurfaces.lightRaised,
    surfaceContainerHighest = AppSurfaces.lightRaised,
    primary                 = LightInfo,
    onPrimary               = AppSurfaces.lightSurface,
    primaryContainer        = AppSurfaces.lightRaised,
    onPrimaryContainer      = AppSurfaces.lightForeground,
    secondary               = AppSurfaces.lightMuted,
    onSecondary             = AppSurfaces.lightSurface,
    secondaryContainer      = AppSurfaces.lightRaised,
    onSecondaryContainer    = AppSurfaces.lightMuted,
    tertiary                = AppSurfaces.lightMuted,
    onTertiary              = AppSurfaces.lightSurface,
    tertiaryContainer       = AppSurfaces.lightRaised,
    onTertiaryContainer     = AppSurfaces.lightForeground,
    error                   = LightCritical,
    onError                 = AppSurfaces.lightSurface,
    errorContainer          = AppSurfaces.lightRaised,
    onErrorContainer        = LightCritical,
    outline                 = AppSurfaces.lightBorder,
    outlineVariant          = AppSurfaces.lightBorder,
    surfaceTint             = LightInfo,
    inverseSurface          = AppSurfaces.lightForeground,
    inverseOnSurface        = AppSurfaces.lightSurface
)

private val rememberedTypography by lazy { appTypography(appFontFamilies) }

/**
 * Tema principal da aplicação.
 *
 * `@Composable` indica uma função que descreve parte da UI.
 * É como um componente Vue.js: recebe props e renderiza filhos.
 *
 * `content: @Composable () -> Unit` é o equivalente ao `<slot>` do Vue.
 *
 * [uiScalePercent] é a escala global da interface. Ela troca a **densidade**, não
 * a escala tipográfica: subir só a tipografia deixaria ícone, padding, altura de
 * linha e alvo de clique — todos `Dp` fixos — do tamanho anterior, e o rodapé de
 * 26dp continuaria pequeno ao lado de um texto maior. Densidade é o único ponto
 * em que dp e sp crescem juntos e as proporções do protótipo permanecem.
 *
 * O default é **100 (neutro)**, e não [DEFAULT_UI_SCALE_PERCENT]: é o que mantém
 * a geometria de referência do `ScreenshotGenerator`, do `TourGifGenerator` e dos
 * testes de componente. Quem escolhe a escala do app é o `Main`, e **cada janela
 * precisa receber o valor**: `Window`/`DialogWindow` do Compose Desktop têm
 * composição própria e a plataforma reprovisiona `LocalDensity` na raiz de cada
 * uma — provisionar na janela pai não atravessa para a filha.
 */
@Composable
fun AppTheme(
    isDark: Boolean = true,
    uiScalePercent: Int = 100,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) {
        usageMonitorDarkColorScheme
    } else {
        usageMonitorLightColorScheme
    }
    val accents = if (isDark) darkAppAccents else lightAppAccents
    // A escala é montada uma vez por processo: as famílias não mudam com o tema,
    // e reconstruir catorze `TextStyle` a cada troca seria trabalho sem efeito.
    val typography = rememberedTypography

    // Só `density` é multiplicado: `sp` já deriva dela, e multiplicar `fontScale`
    // junto aplicaria a escala duas vezes ao texto e uma só ao resto.
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiScalePercent) {
        if (uiScalePercent == 100) {
            baseDensity
        } else {
            Density(
                density = baseDensity.density * uiScalePercent / 100f,
                fontScale = baseDensity.fontScale
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes      = appShapes,
        typography  = typography
    ) {
        val scrollbarStyle = LocalScrollbarStyle.current.copy(
            unhoverColor = colorScheme.onSurface.copy(alpha = 0.24f),
            hoverColor   = colorScheme.onSurface.copy(alpha = 0.5f)
        )
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalScrollbarStyle provides scrollbarStyle,
            LocalAppAccents provides accents
        ) {
            content()
        }
    }
}
