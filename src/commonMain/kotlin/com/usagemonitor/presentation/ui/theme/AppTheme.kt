package com.usagemonitor.presentation.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Durações: 120 para hover e foco, 180 para seleção, 240 para expandir e
 * recolher, 90 para saída.
 *
 * São mais curtas que as anteriores (150/250/350) porque a densidade subiu: numa
 * linha de 32dp de altura, 350ms de transição é tempo suficiente para o olho
 * perceber atraso onde antes havia um card de 96dp se movendo.
 *
 * **Tween para cor e opacidade, mola para posição, tamanho e escala.** A tela
 * lia como "sem fluidez" porque tudo era tween de duração fixa: um card que muda
 * de lugar com curva de duração fixa para seco no fim, e interromper a transição
 * no meio recomeçava a curva do zero. A mola herda a velocidade de onde estava —
 * é isso que faz o movimento parecer contínuo quando o alvo muda antes de chegar.
 *
 * **Sem overshoot em dado.** Barra, anel e número usam [Springs.GENTLE], que é
 * criticamente amortecida: uma barra que passa de 88% antes de voltar mostra, por
 * alguns quadros, um valor que não é verdade. A mola com rebote
 * ([Springs.EXPRESSIVE]) fica para superfícies que não carregam número — o
 * desdobrar da HUD e a entrada do menu.
 *
 * **Animação contínua só atrás de [AppMotionPolicy.continuous].** Animação sem
 * fim trava o `waitForIdle` dos testes de componente, e por isso a política
 * nasce desligada em [AppTheme]: só o `Main` a liga, e só para estado vivo.
 */
object AppMotion {
    const val fast:   Int  = 120
    const val normal: Int  = 180
    const val slow:   Int  = 240

    /**
     * Saída. Mais curta que a entrada de propósito: o que sai já não interessa,
     * e uma saída tão longa quanto a entrada deixa dois estados na tela ao mesmo
     * tempo por tempo demais.
     */
    const val exit:   Int  = 90

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

    /**
     * Curva enfática: sai rápido e assenta longo. É a de entrada de superfície —
     * o card, o banner, o conteúdo que troca —, onde o olho precisa ver o
     * movimento começar e o conteúdo parar legível.
     */
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * Três molas, e só três: com uma por tela o sistema volta a ter tantas
     * curvas quantas telas, que é o que a escala de durações existe para evitar.
     */
    enum class Springs(val dampingRatio: Float, val stiffness: Float) {
        /** Dado e superfície: barra, anel, número, card. Sem rebote, assenta em ~450ms. */
        GENTLE(dampingRatio = 1f, stiffness = 400f),

        /** Indicador de seleção e pressão: sublinhado de aba, polegar, botão pressionado. */
        SNAPPY(dampingRatio = 1f, stiffness = 1_500f),

        /** Superfície sem número que se desdobra: HUD e menu. Um rebote curto. */
        EXPRESSIVE(dampingRatio = 0.75f, stiffness = 600f)
    }
}

/**
 * Como a composição trata movimento.
 *
 * [reduced] é a preferência "Reduzir animações": nada se move, tudo troca de uma
 * vez. [continuous] libera animação sem fim — o arco de sessão ativa, o pulso de
 * atenção, o glifo girando na recarga.
 *
 * O default de [AppTheme] é [Static] — transições finitas ligadas, contínuas
 * desligadas — e não o que o app usa. É o que deixa `ScreenshotGenerator`,
 * `HelpMediaGenerator`, `TourGifGenerator` e todo `runDesktopComposeUiTest`
 * seguros sem cada um lembrar de desligar nada: um teste que esquecesse travaria
 * no `waitForIdle` em vez de falhar.
 */
@Immutable
data class AppMotionPolicy(
    val reduced: Boolean,
    val continuous: Boolean
) {
    companion object {
        /** Transições finitas, nada contínuo. Default de testes e geradores. */
        val Static = AppMotionPolicy(reduced = false, continuous = false)

        /** O app em uso: transições e animação contínua de estado vivo. */
        val Live = AppMotionPolicy(reduced = false, continuous = true)

        /** "Reduzir animações": nenhuma transição, nenhuma animação contínua. */
        val Reduced = AppMotionPolicy(reduced = true, continuous = false)

        /** A política do app a partir da preferência do usuário. */
        fun forPreference(reduceMotion: Boolean): AppMotionPolicy {
            return if (reduceMotion) Reduced else Live
        }
    }
}

val LocalAppMotionPolicy = staticCompositionLocalOf { AppMotionPolicy.Static }

/**
 * Mola do sistema sob a política. Com movimento reduzido vira [snap]: o valor
 * chega ao alvo no mesmo quadro, e quem lê o estado final não precisa saber que
 * a preferência existe.
 *
 * Função pura, e não só a versão `@Composable`: é o que deixa a regra testável
 * sem composição.
 */
fun <T> appSpringSpec(
    kind: AppMotion.Springs,
    policy: AppMotionPolicy,
    visibilityThreshold: T? = null
): FiniteAnimationSpec<T> {
    if (policy.reduced) {
        return snap()
    }
    return spring(
        dampingRatio = kind.dampingRatio,
        stiffness = kind.stiffness,
        visibilityThreshold = visibilityThreshold
    )
}

/** Tween do sistema sob a política; mesma regra de [appSpringSpec]. */
fun <T> appTweenSpec(
    durationMillis: Int,
    policy: AppMotionPolicy,
    easing: Easing = AppMotion.enterEasing,
    delayMillis: Int = 0
): FiniteAnimationSpec<T> {
    if (policy.reduced) {
        return snap()
    }
    return tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
}

@Composable
@ReadOnlyComposable
fun <T> appSpring(
    kind: AppMotion.Springs = AppMotion.Springs.GENTLE,
    visibilityThreshold: T? = null
): FiniteAnimationSpec<T> {
    return appSpringSpec(kind, LocalAppMotionPolicy.current, visibilityThreshold)
}

@Composable
@ReadOnlyComposable
fun <T> appTween(
    durationMillis: Int = AppMotion.normal,
    easing: Easing = AppMotion.enterEasing,
    delayMillis: Int = 0
): FiniteAnimationSpec<T> {
    return appTweenSpec(durationMillis, LocalAppMotionPolicy.current, easing, delayMillis)
}

/**
 * Patamares de profundidade.
 *
 * A regra anterior era "card em zero, profundidade só por borda de 1dp", e o
 * resultado foi uma tela chapada: quatro superfícies dentro de ~14% de
 * luminância, separadas por um traço que some no escuro. A sombra que tinha sido
 * tirada era a **de acento** — um brilho colorido em toda superfície, que fazia a
 * tela ler como pilha de blocos de mesmo peso. Esta é outra coisa: sombra neutra,
 * baixa, em duas camadas, que diz o que está **sobre** o quê.
 *
 * Cada patamar são duas sombras empilhadas — [key], curta e densa, que assenta o
 * objeto no plano; [ambient], larga e rala, que dá a distância. Uma sombra só,
 * grande o bastante para ler, borra o contorno; pequena o bastante para não
 * borrar, não se vê no tema escuro.
 *
 * - [FLAT]: linha, célula, bloco dentro de painel. Nunca sombra dentro de superfície.
 * - [CARD]: painel e card em repouso.
 * - [RAISED]: card com o ponteiro em cima e tooltip.
 * - [OVERLAY]: menu.
 * - [DIALOG]: card sendo arrastado e a HUD — o que flutua de fato sobre o resto.
 *
 * O [ambient] de [CARD] fica em 6dp de propósito: o vão da grade do dashboard é
 * 12dp, e sombra maior que metade dele se sobrepõe à do vizinho.
 */
enum class AppDepth(val key: Dp, val ambient: Dp) {
    FLAT(key = 0.dp, ambient = 0.dp),
    CARD(key = 1.dp, ambient = 6.dp),
    RAISED(key = 2.dp, ambient = 10.dp),
    OVERLAY(key = 3.dp, ambient = 14.dp),
    DIALOG(key = 4.dp, ambient = 20.dp)
}

/**
 * Camadas de estado e de luz derivadas do preset.
 *
 * **Derivadas, não retocadas.** Os 26 presets têm contraste medido contra a
 * `surface` (`AppThemePresetTest`, `AppAccentsContrastTest`), e mexer nos hex de
 * cada um para abrir espaço entre os degraus reabriria as 26 medições. Uma
 * camada translúcida do `foreground` somada ao que está embaixo dá o mesmo
 * degrau em qualquer superfície — e é por isso que a linha com hover dentro de
 * um card com hover volta a reagir: a camada soma, não troca.
 *
 * - [hoverLayer]/[pressedLayer]: pintados **por cima** da superfície, nunca no lugar dela.
 * - [highlight]: a linha de 1dp no topo interno de painel, card e HUD — a luz
 *   que bate de cima. No claro ela quase some, e é certo: ali a sombra já separa.
 * - [sheen]: o topo do gradiente vertical sutil das mesmas superfícies.
 * - [shadow]: preto no escuro; o `foreground` morno no claro, para a sombra não
 *   ficar cinza-azulada sobre um fundo quente.
 * - [borderTop]/[borderBottom]: a borda de 1dp vira gradiente vertical. **No
 *   escuro a sombra quase não existe** — medido: 10dp de sombra preta sobre
 *   `#131010` escurecem o fundo em três níveis de 255 —, e quem dá volume ali é a
 *   luz: a borda mais clara em cima que embaixo. No claro é o inverso, e a borda
 *   de baixo escurece um pouco para assentar o objeto.
 */
@Immutable
data class AppSurfaceLadder(
    val hoverLayer: Color,
    val pressedLayer: Color,
    val highlight: Color,
    val sheen: Color,
    val shadow: Color,
    val keyShadowAlpha: Float,
    val ambientShadowAlpha: Float,
    val borderTop: Color,
    val borderBottom: Color
) {
    companion object {
        fun of(preset: AppThemePreset): AppSurfaceLadder {
            return if (preset.isDark) {
                AppSurfaceLadder(
                    hoverLayer = preset.foreground.copy(alpha = 0.06f),
                    pressedLayer = preset.foreground.copy(alpha = 0.10f),
                    highlight = Color.White.copy(alpha = 0.09f),
                    sheen = preset.foreground.copy(alpha = 0.045f),
                    shadow = Color.Black,
                    keyShadowAlpha = 1f,
                    ambientShadowAlpha = 1f,
                    borderTop = lerp(preset.border, preset.foreground, 0.22f),
                    borderBottom = preset.border
                )
            } else {
                AppSurfaceLadder(
                    hoverLayer = preset.foreground.copy(alpha = 0.045f),
                    pressedLayer = preset.foreground.copy(alpha = 0.08f),
                    highlight = Color.White.copy(alpha = 0.8f),
                    sheen = Color.White.copy(alpha = 0.6f),
                    shadow = preset.foreground,
                    keyShadowAlpha = 0.5f,
                    ambientShadowAlpha = 0.4f,
                    borderTop = preset.border,
                    borderBottom = lerp(preset.border, preset.foreground, 0.12f)
                )
            }
        }
    }
}

val LocalAppSurfaceLadder = staticCompositionLocalOf { AppSurfaceLadder.of(AppThemePreset.OBSIDIANA_DARK) }

/** Mesmo desenho de `AppAccents.current`: lê o tema em vigor, nunca um `val` de topo. */
object AppSurfaceLadders {
    val current: AppSurfaceLadder
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSurfaceLadder.current
}

/**
 * Alturas fixas do cromo, em dp.
 *
 * São **contrato com o design system**, não escolha de tela: `tokens/spacing.css`
 * publica os cinco valores como `--h-titlebar`, `--h-toolbar`, `--h-statusbar`,
 * `--h-control` e `--h-updatestrip`, e é por eles que a barra de título de uma
 * janela tem a mesma altura da barra de controles da janela ao lado.
 *
 * Existiam como três `private val` espalhados por dois arquivos de componente e
 * um literal `34.dp` na moldura do desktop — quatro donos para um valor que o
 * sistema define uma vez.
 */
object AppChrome {
    /** Barra de título das janelas, e a faixa de hover do modo somente cards. */
    val titleBar: Dp = 34.dp

    /** Barra de controles fixada no topo de uma janela que fatia por tempo. */
    val toolbar: Dp = 34.dp

    /** Barra de estado do rodapé. */
    val statusBar: Dp = 30.dp

    /** Altura de um controle: botão, campo, segmento, chip. */
    val control: Dp = 28.dp

    /** Faixa de atualização, a linha mais baixa do sistema. */
    val updateStrip: Dp = 28.dp

    /**
     * Faixa HUD (issue #164): terceiro chrome da janela principal, ancorado no
     * topo da tela. Fura de propósito o piso de 28dp acima — ali mora um
     * controle interativo, aqui só o ponto+palavra de `AppStatusIndicator` e a
     * fonte/tempo até reset, sem alvo de clique próprio além da faixa inteira.
     */
    val hud: Dp = 24.dp
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
 * São quatro degraus dentro de ~14% de luminância, e continua deliberado:
 * quatro superfícies muito distintas entre si reconstruiriam por volume a
 * hierarquia que este sistema quer ler por camada. O que faltava — a tela lia
 * chapada — não se resolve afastando os degraus, e sim com o que fica **por
 * cima** deles: [AppDepth] para o que está sobre o quê, e [AppSurfaceLadder] para
 * hover, pressão, brilho e sombra.
 *
 * [AppSurfaces] existe como objeto nomeado — e não só como argumento do
 * `darkColorScheme` — porque os degraus não têm papel Material equivalente um a
 * um: `raised` cobre `surfaceVariant` e a família `surfaceContainer*` ao mesmo
 * tempo, e sem um nome próprio cada chamada repetiria o literal.
 */
object AppSurfaces {
    // ── Escuro (a referência aprovada) ────────────────────────────────────────
    val darkBackground = AppThemePreset.OBSIDIANA_DARK.background
    val darkSurface    = AppThemePreset.OBSIDIANA_DARK.surface
    val darkRaised     = AppThemePreset.OBSIDIANA_DARK.raised
    val darkBorder     = AppThemePreset.OBSIDIANA_DARK.border
    val darkForeground = AppThemePreset.OBSIDIANA_DARK.foreground
    val darkMuted      = AppThemePreset.OBSIDIANA_DARK.muted

    // ── Claro ─────────────────────────────────────────────────────────────────
    val lightBackground = AppThemePreset.PORCELANA_LIGHT.background
    val lightSurface    = AppThemePreset.PORCELANA_LIGHT.surface
    val lightRaised     = AppThemePreset.PORCELANA_LIGHT.raised
    val lightBorder     = AppThemePreset.PORCELANA_LIGHT.border
    val lightForeground = AppThemePreset.PORCELANA_LIGHT.foreground
    val lightMuted      = AppThemePreset.PORCELANA_LIGHT.muted
}

// O azul de informação é o mesmo `input` de `AppAccents`, e o vermelho é o mesmo
// `saturated`: um segundo vermelho "de erro" ao lado do vermelho de contexto
// saturado faria a tela ter duas cores para a mesma ideia.
private val DarkCritical = Color(0xFFE86A6A)
private val LightCritical = Color(0xFFB3261E)

private fun appColorScheme(preset: AppThemePreset): ColorScheme {
    return if (preset.isDark) {
        darkColorScheme(
            background = preset.background,
            onBackground = preset.foreground,
            surface = preset.surface,
            onSurface = preset.foreground,
            surfaceVariant = preset.raised,
            onSurfaceVariant = preset.muted,
            surfaceContainerLowest = preset.background,
            surfaceContainerLow = preset.surface,
            surfaceContainer = preset.raised,
            surfaceContainerHigh = preset.raised,
            surfaceContainerHighest = preset.raised,
            primary = preset.primary,
            onPrimary = preset.background,
            primaryContainer = preset.raised,
            onPrimaryContainer = preset.foreground,
            secondary = preset.muted,
            onSecondary = preset.background,
            secondaryContainer = preset.raised,
            onSecondaryContainer = preset.muted,
            tertiary = preset.muted,
            onTertiary = preset.background,
            tertiaryContainer = preset.raised,
            onTertiaryContainer = preset.foreground,
            error = DarkCritical,
            onError = preset.background,
            errorContainer = preset.raised,
            onErrorContainer = DarkCritical,
            outline = preset.border,
            outlineVariant = preset.border,
            surfaceTint = preset.primary,
            inverseSurface = preset.foreground,
            inverseOnSurface = preset.background
        )
    } else {
        lightColorScheme(
            background = preset.background,
            onBackground = preset.foreground,
            surface = preset.surface,
            onSurface = preset.foreground,
            surfaceVariant = preset.raised,
            onSurfaceVariant = preset.muted,
            surfaceContainerLowest = preset.surface,
            surfaceContainerLow = preset.background,
            surfaceContainer = preset.raised,
            surfaceContainerHigh = preset.raised,
            surfaceContainerHighest = preset.raised,
            primary = preset.primary,
            onPrimary = preset.surface,
            primaryContainer = preset.raised,
            onPrimaryContainer = preset.foreground,
            secondary = preset.muted,
            onSecondary = preset.surface,
            secondaryContainer = preset.raised,
            onSecondaryContainer = preset.muted,
            tertiary = preset.muted,
            onTertiary = preset.surface,
            tertiaryContainer = preset.raised,
            onTertiaryContainer = preset.foreground,
            error = LightCritical,
            onError = preset.surface,
            errorContainer = preset.raised,
            onErrorContainer = LightCritical,
            outline = preset.border,
            outlineVariant = preset.border,
            surfaceTint = preset.primary,
            inverseSurface = preset.foreground,
            inverseOnSurface = preset.surface
        )
    }
}

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
 *
 * [motion] segue a mesma regra, e pelo mesmo motivo: janela que não recebe a
 * política fica em [AppMotionPolicy.Static] sem erro nenhum — anima, mas ignora
 * "Reduzir animações".
 */
@Composable
fun AppTheme(
    preset: AppThemePreset = AppThemePreset.OBSIDIANA_DARK,
    uiScalePercent: Int = 100,
    motion: AppMotionPolicy = AppMotionPolicy.Static,
    content: @Composable () -> Unit
) {
    val colorScheme = appColorScheme(preset)
    val accents = if (preset.isDark) darkAppAccents else lightAppAccents
    val ladder = remember(preset) { AppSurfaceLadder.of(preset) }
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
            LocalAppAccents provides accents,
            LocalAppMotionPolicy provides motion,
            LocalAppSurfaceLadder provides ladder
        ) {
            content()
        }
    }
}

/** Compatibilidade para fixtures e consumidores que ainda escolhem só o modo. */
@Composable
fun AppTheme(
    isDark: Boolean,
    uiScalePercent: Int = 100,
    motion: AppMotionPolicy = AppMotionPolicy.Static,
    content: @Composable () -> Unit
) {
    AppTheme(
        preset = AppThemePreset.fromLegacyMode(isDark),
        uiScalePercent = uiScalePercent,
        motion = motion,
        content = content
    )
}
