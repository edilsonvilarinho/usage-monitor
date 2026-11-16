package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Período do laço da cintilação, em ms: cada período das estrelas divide 240 s, então t e t + período são iguais. */
const val HUD_STARFIELD_PERIOD_MS: Long = 240_000L

/** Semente fixa do campo de estrelas: a mesma semente gera o mesmo céu. */
const val HUD_STARFIELD_SEED: Int = 586

/** Semente dos grãos de poeira, separada da semente das estrelas (A04 #602). */
const val HUD_STARFIELD_DUST_SEED: Int = 77

/**
 * Poço gravitacional: centro e raio do anel, em px do corpo. [activeSinceMs] e [activeUntilMs] são a janela de
 * execução do poço, no mesmo eixo de tempo do `timeMs` (laço de 240 000 ms). Em execução no instante t ⇔
 * since ≤ t e (until nulo ou t < until); since nulo = nunca em execução. Sem janela, o poço fica ocioso (#592 A03).
 */
data class HudStarfieldWell(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val activeSinceMs: Long? = null,
    val activeUntilMs: Long? = null
)

/** Retângulo proibido (caixa de texto ou pílula), em px do corpo. Bordas inclusivas. */
data class HudStarfieldBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Estrela no nascimento: [x] é a posição x0 da deriva e [y] a altura sorteada em [0, altura); [radius] e [alpha] são
 * os valores base (antes do fator de brilho); [gold] marca as douradas (18% do campo); [twinklePeriodMs] e
 * [twinklePhase] definem a cintilação.
 */
data class HudStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val gold: Boolean = false,
    val twinklePeriodMs: Long = 2_000L,
    val twinklePhase: Float = 0f
)

/**
 * Estrela num quadro, já na posição deslocada pela deriva e pelos poços. [alpha] é 0 quando a estrela está apagada
 * (caixa proibida) e já traz brilho, cintilação, sombra do poço e fade de borda. [fade] é a sombra × fade de borda;
 * [twinkle] é o fator da cintilação (1 = sem cintilação).
 */
data class HudStarfieldPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val gold: Boolean = false,
    val fade: Float = 1f,
    val twinkle: Float = 1f
)

/** Deriva horizontal em dp/s (0°): as estrelas andam para a direita e dão a volta pela largura do corpo. */
private const val HUD_STARFIELD_DRIFT_DP_PER_S = 1.0

/** Estrelas: 120 numa camada só; 28% grandes (raio 1,6–2 dp, alfa base 0,9); as demais raio 1–1,5 dp, alfa 0,45–0,8. */
private const val HUD_STARFIELD_STAR_COUNT = 120
private const val HUD_STARFIELD_BIG_CHANCE = 0.28f
private const val HUD_STARFIELD_BIG_ALPHA = 0.9f
private const val HUD_STARFIELD_GOLD_CHANCE = 0.18f

/** Brilho das estrelas: alfa final × 0,20 (prototipo `starBri` = 20). */
private const val HUD_STARFIELD_STAR_BRIGHTNESS = 0.2f

/**
 * Cintilação: 0,8 + 0,2·sen(2π·t/período + fase). Os períodos são (3–6 s)·2/4,5 arredondados a divisores de 240 s:
 * período que não divide o laço quebra o fechamento em t e t + 240 s.
 */
private const val HUD_STARFIELD_TWINKLE_BASE = 0.8f
private const val HUD_STARFIELD_TWINKLE_AMPLITUDE = 0.2f
private val HUD_STARFIELD_TWINKLE_PERIODS_MS = listOf(1_500L, 1_600L, 2_000L, 2_400L)

/** Poeira: 600 grãos parados, raio 0,5 dp, alfa (0,05–0,25) × 2. */
private const val HUD_STARFIELD_GRAIN_COUNT = 600
private const val HUD_STARFIELD_GRAIN_RADIUS_DP = 0.5f
private const val HUD_STARFIELD_GRAIN_ALPHA_MIN = 0.05f
private const val HUD_STARFIELD_GRAIN_ALPHA_SPAN = 0.2f
private const val HUD_STARFIELD_GRAIN_ALPHA_FACTOR = 2f

/** Poeira no claro: alfa × 1,2. */
private const val HUD_STARFIELD_DUST_LIGHT_FACTOR = 1.2f

/** Estrelas no claro: alfa × 0,55 (prototipo `light ? .55 : 1`) e raio × 1,5. */
private const val HUD_STARFIELD_LIGHT_ALPHA = 0.55f
private const val HUD_STARFIELD_LIGHT_RADIUS_SCALE = 1.5f

/** Sombra = 0,62 R; influência = 3 R; massa = (1,1 R)². Razões do protótipo, com o raio do poço. */
private const val HUD_STARFIELD_SHADOW_FACTOR = 0.62f
private const val HUD_STARFIELD_INFLUENCE_FACTOR = 3f
private const val HUD_STARFIELD_MASS_FACTOR = 1.1f

/** Margem de 2 px em volta das caixas proibidas ao sortear, como no protótipo. */
private const val HUD_STARFIELD_BOX_MARGIN = 2f

/** Folga de 3 px em volta dos discos ao sortear, como no protótipo. */
private const val HUD_STARFIELD_WELL_CLEARANCE = 3f

/** Limite de tentativas por estrela; esgotado, a estrela não entra e a lista sai menor. */
private const val HUD_STARFIELD_SPOT_TRIES = 20_000

/** Fade de 22 px no topo e na base do corpo: o campo nunca aparece como corte. */
private const val HUD_STARFIELD_FADE_PX = 22f

/** Claro: estrela a até 8 dp de uma caixa proibida fica com alfa × 0,4. */
private const val HUD_STARFIELD_LIGHT_REACH_DP = 8f
private const val HUD_STARFIELD_LIGHT_ATTENUATION = 0.4f

/** Gerador mulberry32 (variante do protótipo AT1): mesma sequência em qualquer execução. */
private class HudStarfieldRandom(seed: Int) {
    private var state: Int = seed

    /** Próximo valor em [0, 1). */
    fun next(): Float {
        state += 0x6D2B79F5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        val unsigned = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return (unsigned / 4294967296.0).toFloat()
    }
}

private class HudStarfieldSpot(val x: Float, val y: Float)

/** Resultado da deflexão: posição desviada e fator de sombra (0 some, 1 inteiro). */
private class HudStarfieldDeflection(val x: Float, val y: Float, val cap: Float)

/**
 * Gera as 120 estrelas de nascimento por semente. Cada estrela cai só em área livre: y em [0, altura), fora das caixas
 * proibidas (com margem de 2 px) e fora dos discos dos poços (raio + 3 px). Corpo de até 4 px em algum lado não recebe
 * estrelas.
 */
fun hudStarfieldStars(
    width: Float,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>,
    seed: Int = HUD_STARFIELD_SEED
): List<HudStar> {
    if (width <= 4f || height <= 4f) return emptyList()
    val random = HudStarfieldRandom(seed)
    val stars = mutableListOf<HudStar>()
    while (stars.size < HUD_STARFIELD_STAR_COUNT) {
        val spot = hudStarfieldFreeSpot(random, width, height, wells, forbidden) ?: break
        val big = random.next() < HUD_STARFIELD_BIG_CHANCE
        val radius = if (big) 1.6f + 0.4f * random.next() else 1f + 0.5f * random.next()
        val alpha = if (big) HUD_STARFIELD_BIG_ALPHA else 0.45f + 0.35f * random.next()
        val gold = random.next() < HUD_STARFIELD_GOLD_CHANCE
        val period = HUD_STARFIELD_TWINKLE_PERIODS_MS[(random.next() * HUD_STARFIELD_TWINKLE_PERIODS_MS.size).toInt()]
        val phase = random.next() * 2f * PI.toFloat()
        stars += HudStar(spot.x, spot.y, radius, alpha, gold, period, phase)
    }
    return stars
}

/**
 * Quadro do campo no instante [timeMs]: uma entrada por estrela, deslocada na horizontal pela deriva e desviada
 * pelos poços. A deriva usa o tempo inteiro; a cintilação usa o tempo reduzido ao laço de 240 000 ms.
 */
fun hudStarfieldFrame(
    timeMs: Long,
    width: Float,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>,
    seed: Int = HUD_STARFIELD_SEED
): List<HudStarfieldPoint> {
    val loopMs = hudStarfieldLoopMs(timeMs)
    return hudStarfieldStars(width, height, wells, forbidden, seed).map { star ->
        hudStarfieldPoint(star, hudStarfieldDriftX(star.x, timeMs, width), height, loopMs, wells, forbidden)
    }
}

/** Tempo reduzido ao laço: a mesma conta para t e t + período. */
private fun hudStarfieldLoopMs(timeMs: Long): Long =
    ((timeMs % HUD_STARFIELD_PERIOD_MS) + HUD_STARFIELD_PERIOD_MS) % HUD_STARFIELD_PERIOD_MS

/** Deriva horizontal: x0 + 1 dp/s × t, módulo a largura, em Double. Sem laço: a volta da largura não fecha em 240 s. */
private fun hudStarfieldDriftX(startX: Float, timeMs: Long, width: Float): Float {
    val span = width.toDouble()
    val moved = startX.toDouble() + HUD_STARFIELD_DRIFT_DP_PER_S * (timeMs / 1000.0)
    return (((moved % span) + span) % span).toFloat()
}

private fun hudStarfieldPoint(
    star: HudStar,
    x: Float,
    height: Float,
    loopMs: Long,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): HudStarfieldPoint {
    val deflected = hudStarfieldDeflect(x, star.y, wells)
    val edge = hudStarfieldSmoothstep(0f, HUD_STARFIELD_FADE_PX, deflected.y) *
        (1f - hudStarfieldSmoothstep(height - HUD_STARFIELD_FADE_PX, height, deflected.y))
    val hidden = forbidden.any { hudStarfieldInside(it, deflected.x, deflected.y, 0f) }
    val twinkle = hudStarfieldTwinkle(star, loopMs)
    val shade = if (hidden) 0f else deflected.cap * edge
    val alpha = if (hidden) 0f else min(1f, star.alpha * HUD_STARFIELD_STAR_BRIGHTNESS * twinkle * shade)
    return HudStarfieldPoint(
        x = deflected.x,
        y = deflected.y,
        radius = star.radius,
        alpha = alpha,
        gold = star.gold,
        fade = shade,
        twinkle = twinkle
    )
}

/** Cintilação: 0,8 + 0,2·sen(2π·t/período + fase). O período divide 240 s, então o laço fecha. */
private fun hudStarfieldTwinkle(star: HudStar, loopMs: Long): Float {
    val angle = 2.0 * PI * loopMs / star.twinklePeriodMs + star.twinklePhase
    return HUD_STARFIELD_TWINKLE_BASE + HUD_STARFIELD_TWINKLE_AMPLITUDE * sin(angle).toFloat()
}

private fun hudStarfieldFreeSpot(
    random: HudStarfieldRandom,
    width: Float,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): HudStarfieldSpot? {
    for (attempt in 0 until HUD_STARFIELD_SPOT_TRIES) {
        val x = 2f + random.next() * (width - 4f)
        val y = random.next() * height
        val inBox = forbidden.any { hudStarfieldInside(it, x, y, HUD_STARFIELD_BOX_MARGIN) }
        val onWell = wells.any { hudStarfieldDistance(x, y, it) < it.radius + HUD_STARFIELD_WELL_CLEARANCE }
        if (!inBox && !onWell) return HudStarfieldSpot(x, y)
    }
    return null
}

/** Deflexão de lente aproximada: empurra o ponto para fora de cada poço e apaga o que cai na sombra. */
private fun hudStarfieldDeflect(bx: Float, by: Float, wells: List<HudStarfieldWell>): HudStarfieldDeflection {
    var dx = 0f
    var dy = 0f
    var cap = 1f
    for (well in wells) {
        if (well.radius <= 0f) continue
        val ox = bx - well.centerX
        val oy = by - well.centerY
        val d = max(sqrt(ox * ox + oy * oy), 1e-3f)
        val shadow = well.radius * HUD_STARFIELD_SHADOW_FACTOR
        val influence = well.radius * HUD_STARFIELD_INFLUENCE_FACTOR
        if (d >= influence) continue
        val ein = well.radius * HUD_STARFIELD_MASS_FACTOR
        val mass = ein * ein / (2f * max(d, shadow)) * (1f - hudStarfieldSmoothstep(influence * 0.5f, influence, d))
        dx += ox / d * mass
        dy += oy / d * mass
        cap *= hudStarfieldSmoothstep(shadow, shadow * 1.8f, d)
    }
    return HudStarfieldDeflection(bx + dx, by + dy, cap)
}

private fun hudStarfieldDistance(x: Float, y: Float, well: HudStarfieldWell): Float {
    val dx = x - well.centerX
    val dy = y - well.centerY
    return sqrt(dx * dx + dy * dy)
}

private fun hudStarfieldInside(box: HudStarfieldBox, x: Float, y: Float, margin: Float): Boolean =
    x >= box.left - margin && x <= box.right + margin && y >= box.top - margin && y <= box.bottom + margin

/** Smoothstep sem overshoot, igual ao `ss` do protótipo. */
internal fun hudStarfieldSmoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Tinta do desenho: cor, alfa que multiplica o da estrela ou do grão e escala do raio. */
data class HudStarfieldInk(val color: Color, val alpha: Float, val radiusScale: Float)

/** Tinta clara: `--fg` de `docs/design-system/tokens/colors.css:77/116` (preset `PORCELANA_LIGHT`, `AppThemePresets.kt:93`). */
private val HUD_STARFIELD_INK_LIGHT = Color(0xFF171414)

/**
 * Escolhe a tinta das estrelas. Escuro: quente, ou ouro nas douradas, com alfa e raio cheios. Claro: `--fg` com alfa
 * ×0,55 e raio ×1,5; a dourada usa a mesma tinta.
 */
fun hudStarfieldInk(dark: Boolean, gold: Boolean): HudStarfieldInk {
    if (!dark) return HudStarfieldInk(HUD_STARFIELD_INK_LIGHT, HUD_STARFIELD_LIGHT_ALPHA, HUD_STARFIELD_LIGHT_RADIUS_SCALE)
    val color = if (gold) AppGargantuaTokens.gold else AppGargantuaTokens.hot
    return HudStarfieldInk(color, 1f, 1f)
}

/**
 * Atenuação do claro: 0,4 quando o centro da estrela está a até 8 dp de uma caixa proibida; no escuro, 1.
 * Distância ao retângulo (zero dentro dele), não ao centro da caixa.
 */
fun hudStarfieldTextAttenuation(dark: Boolean, x: Float, y: Float, forbidden: List<HudStarfieldBox>): Float {
    if (dark) return 1f
    val near = forbidden.any { hudStarfieldBoxDistance(it, x, y) <= HUD_STARFIELD_LIGHT_REACH_DP }
    return if (near) HUD_STARFIELD_LIGHT_ATTENUATION else 1f
}

private fun hudStarfieldBoxDistance(box: HudStarfieldBox, x: Float, y: Float): Float {
    val dx = max(max(box.left - x, 0f), x - box.right)
    val dy = max(max(box.top - y, 0f), y - box.bottom)
    return sqrt(dx * dx + dy * dy)
}

/** Alfa do desenho de uma estrela: alfa da estrela × tinta × atenuação, até 1. */
fun hudStarfieldDrawAlpha(dark: Boolean, point: HudStarfieldPoint, forbidden: List<HudStarfieldBox>): Float {
    val ink = hudStarfieldInk(dark, point.gold)
    val attenuation = hudStarfieldTextAttenuation(dark, point.x, point.y, forbidden)
    return min(1f, point.alpha * ink.alpha * attenuation)
}

/** Tinta da poeira: `AppGargantuaTokens.dust` nos dois temas; no claro, alfa × 1,2. */
fun hudStarfieldDustInk(dark: Boolean): HudStarfieldInk =
    HudStarfieldInk(AppGargantuaTokens.dust, if (dark) 1f else HUD_STARFIELD_DUST_LIGHT_FACTOR, 1f)

/** Grão de poeira num quadro: posição parada, raio e alfa já com a sombra dos poços. */
data class HudStarfieldGrainPoint(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/**
 * Campo de poeira: [HUD_STARFIELD_GRAIN_COUNT] grãos parados, sorteados por semente no corpo. Grão dentro de caixa
 * proibida ou na sombra de um poço sai com alfa 0; a lista mantém o tamanho.
 */
fun hudStarfieldGrainFrame(
    width: Float,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>,
    seed: Int = HUD_STARFIELD_DUST_SEED
): List<HudStarfieldGrainPoint> {
    val random = HudStarfieldRandom(seed)
    return List(HUD_STARFIELD_GRAIN_COUNT) {
        val x = random.next() * width
        val y = random.next() * height
        val base = (HUD_STARFIELD_GRAIN_ALPHA_MIN + HUD_STARFIELD_GRAIN_ALPHA_SPAN * random.next()) * HUD_STARFIELD_GRAIN_ALPHA_FACTOR
        hudStarfieldGrainPoint(x, y, base, wells, forbidden)
    }
}

private fun hudStarfieldGrainPoint(
    x: Float,
    y: Float,
    base: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): HudStarfieldGrainPoint {
    val deflected = hudStarfieldDeflect(x, y, wells)
    val hidden = forbidden.any { hudStarfieldInside(it, deflected.x, deflected.y, 0f) }
    val alpha = if (hidden) 0f else base * deflected.cap
    return HudStarfieldGrainPoint(deflected.x, deflected.y, HUD_STARFIELD_GRAIN_RADIUS_DP, alpha)
}

/** Caixa do item menos o recorte do anel: pedaços da [box] fora de [hole] (#586 A03). */
fun hudStarfieldRemainder(box: HudStarfieldBox, hole: HudStarfieldBox): List<HudStarfieldBox> {
    if (hole.right <= box.left || hole.left >= box.right || hole.bottom <= box.top || hole.top >= box.bottom) {
        return listOf(box)
    }
    val left = maxOf(hole.left, box.left)
    val top = maxOf(hole.top, box.top)
    val right = minOf(hole.right, box.right)
    val bottom = minOf(hole.bottom, box.bottom)
    val pieces = mutableListOf<HudStarfieldBox>()
    if (top > box.top) pieces += HudStarfieldBox(box.left, box.top, box.right, top)
    if (bottom < box.bottom) pieces += HudStarfieldBox(box.left, bottom, box.right, box.bottom)
    if (left > box.left) pieces += HudStarfieldBox(box.left, top, left, bottom)
    if (right < box.right) pieces += HudStarfieldBox(right, top, box.right, bottom)
    return pieces
}
