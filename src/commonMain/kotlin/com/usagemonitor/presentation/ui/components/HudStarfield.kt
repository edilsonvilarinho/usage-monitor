package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Período do laço do campo, em ms: cada camada dá uma volta exata em 240 s, então t e t + período são iguais. */
const val HUD_STARFIELD_PERIOD_MS: Long = 240_000L

/** Semente fixa do campo: a mesma semente gera o mesmo céu. */
const val HUD_STARFIELD_SEED: Int = 586

/** Camada de paralaxe: perto é mais rápida, maior e mais clara; longe é lenta e tênue. */
enum class HudStarfieldLayer { NEAR, MID, FAR }

/** Poço gravitacional: centro e raio do anel, em px do corpo. */
data class HudStarfieldWell(val centerX: Float, val centerY: Float, val radius: Float)

/** Retângulo proibido (caixa de texto ou pílula), em px do corpo. Bordas inclusivas. */
data class HudStarfieldBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Estrela no nascimento: [y] é a posição y0 da deriva, sorteada em [0, min(tile, altura)); [radius] e [alpha] são
 * os valores base da camada (sem deflexão); [gold] marca as douradas (18% do campo); [twinklePeriodMs] e
 * [twinklePhase] definem a cintilação; [glint] marca as seis estrelas de perto de maior raio (brilho em cruz).
 */
data class HudStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val layer: HudStarfieldLayer,
    val gold: Boolean = false,
    val twinklePeriodMs: Long = 4_000L,
    val twinklePhase: Float = 0f,
    val glint: Boolean = false
)

/**
 * Estrela num quadro. [alpha] é 0 quando a estrela está apagada (fade de borda, sombra do poço ou
 * caixa proibida); cada cópia do tile que cai no corpo vira uma entrada. [trailX]/[trailY] é o vetor
 * da cauda: a cauda fica em (x - trailX, y - trailY), com 5 px de comprimento. Zero quando não há rastro.
 * [twinkle] é o fator da cintilação (1 = sem cintilação); [glint] vem da estrela.
 */
data class HudStarfieldPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val layer: HudStarfieldLayer,
    val trailX: Float,
    val trailY: Float,
    val gold: Boolean = false,
    val fade: Float = 1f,
    val twinkle: Float = 1f,
    val glint: Boolean = false
)

/**
 * Especificação de uma camada: velocidade de deriva em dp/s, ladrilho em dp, quantidade e faixas.
 * O ladrilho é independente da velocidade: é ele que define a densidade do campo, e a velocidade só
 * decide quão depressa a camada corre por dentro dele.
 */
private class HudStarfieldLayerSpec(
    val layer: HudStarfieldLayer,
    val speed: Float,
    val tile: Float,
    val count: Int,
    val radiusMin: Float,
    val radiusMax: Float,
    val alphaMin: Float,
    val alphaMax: Float,
    val trail: Boolean
)

// Números da AW1 (prototipos/issue-590-r3/aw1-ceu-noturno-claro/index.html, LAYERS): 60/56/40 estrelas.
// #592 A02: velocidade ×4 (12/6/2 dp/s) com os ladrilhos de hoje (720/360/120 dp). Velocidade × 240 s dá
// 4 voltas de ladrilho em cada camada, então o laço de 240 s fecha sem salto.
private val HUD_STARFIELD_LAYERS = listOf(
    HudStarfieldLayerSpec(HudStarfieldLayer.NEAR, speed = 12f, tile = 720f, count = 60, radiusMin = 1.2f, radiusMax = 1.8f, alphaMin = 0.8f, alphaMax = 0.95f, trail = true),
    HudStarfieldLayerSpec(HudStarfieldLayer.MID, speed = 6f, tile = 360f, count = 56, radiusMin = 0.8f, radiusMax = 1.2f, alphaMin = 0.6f, alphaMax = 0.7f, trail = true),
    HudStarfieldLayerSpec(HudStarfieldLayer.FAR, speed = 2f, tile = 120f, count = 40, radiusMin = 0.5f, radiusMax = 0.8f, alphaMin = 0.35f, alphaMax = 0.5f, trail = false)
)

/** Sombra = 0,62 R; influência = 3 R; massa = (1,1 R)². Razões do protótipo, com o raio do poço. */
private const val HUD_STARFIELD_SHADOW_FACTOR = 0.62f
private const val HUD_STARFIELD_INFLUENCE_FACTOR = 3f
private const val HUD_STARFIELD_MASS_FACTOR = 1.1f

/** Rastro: comprimento fixo e limiar de "perto" (dentro de 2 R). */
private const val HUD_STARFIELD_TRAIL_PX = 5f
private const val HUD_STARFIELD_NEAR_FACTOR = 2f

/** Margem de 2 px em volta das caixas proibidas ao sortear, como no protótipo. */
private const val HUD_STARFIELD_BOX_MARGIN = 2f

/** Folga de 3 px em volta dos discos ao sortear, como no protótipo. */
private const val HUD_STARFIELD_WELL_CLEARANCE = 3f

/** Limite de tentativas por estrela; esgotado, a estrela não entra e a lista sai menor. */
private const val HUD_STARFIELD_SPOT_TRIES = 20_000

/** Fade de 22 px no topo e na base do corpo: o laço nunca aparece como corte. */
private const val HUD_STARFIELD_FADE_PX = 22f

/** Fração de estrelas douradas (limiar do sorteio). */
private const val HUD_STARFIELD_GOLD_CHANCE = 0.18f

/** Cintilação: amplitude de 15% e períodos sorteados que dividem 240 s. */
private const val HUD_STARFIELD_TWINKLE_AMPLITUDE = 0.15f
private val HUD_STARFIELD_TWINKLE_PERIODS_MS = listOf(3_000L, 4_000L, 5_000L, 6_000L)

/** Brilho em cruz: seis estrelas de perto, com comprimento de 2,5 × raio e largura de 0,6 dp. */
private const val HUD_STARFIELD_GLINT_COUNT = 6
private const val HUD_STARFIELD_GLINT_LENGTH_FACTOR = 2.5f
private const val HUD_STARFIELD_GLINT_WIDTH_DP = 0.6f

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

/** Resultado da deflexão: posição desviada, fator de sombra (0 some, 1 inteiro), perto de algum poço. */
private class HudStarfieldDeflection(val x: Float, val y: Float, val cap: Float, val near: Boolean)

/**
 * Gera as 156 estrelas de nascimento por semente (60 perto, 56 no meio, 40 longe). Cada estrela cai só em
 * área livre: y0 em [0, min(tile, altura)), fora das caixas proibidas (com margem de 2 px) e fora dos discos
 * dos poços (raio + 3 px). Corpo menor que 4 px em qualquer lado não recebe estrelas.
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
    for (spec in HUD_STARFIELD_LAYERS) {
        val yLimit = min(spec.tile, height)
        var placed = 0
        while (placed < spec.count) {
            val spot = hudStarfieldFreeSpot(random, width, yLimit, wells, forbidden) ?: break
            val radius = spec.radiusMin + (spec.radiusMax - spec.radiusMin) * random.next()
            val alpha = spec.alphaMin + (spec.alphaMax - spec.alphaMin) * random.next()
            val gold = random.next() < HUD_STARFIELD_GOLD_CHANCE
            val period = HUD_STARFIELD_TWINKLE_PERIODS_MS[(random.next() * HUD_STARFIELD_TWINKLE_PERIODS_MS.size).toInt()]
            val phase = random.next() * 2f * PI.toFloat()
            stars += HudStar(spot.x, spot.y, radius, alpha, spec.layer, gold, period, phase)
            placed++
        }
    }
    return hudStarfieldWithGlints(stars)
}

/** Marca as seis estrelas de perto de maior raio; empate mantém a ordem de sorteio (determinística). */
private fun hudStarfieldWithGlints(stars: List<HudStar>): List<HudStar> {
    val glintIndices = stars.indices
        .filter { stars[it].layer == HudStarfieldLayer.NEAR }
        .sortedByDescending { stars[it].radius }
        .take(HUD_STARFIELD_GLINT_COUNT)
        .toSet()
    return stars.mapIndexed { index, star -> if (index in glintIndices) star.copy(glint = true) else star }
}

/**
 * Quadro do campo no instante [timeMs]. Cada estrela aparece em cada cópia do seu tile (y0 + v·t, módulo tile)
 * que caia dentro do corpo; cada cópia é desviada pelos poços e apagada se cair numa caixa proibida.
 * O tempo é reduzido ao laço de 240 000 ms, então t e t + 240 000 dão o mesmo quadro.
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
    return hudStarfieldStars(width, height, wells, forbidden, seed).flatMap { star ->
        hudStarfieldCopies(star, loopMs, height, wells, forbidden)
    }
}

/** Tempo reduzido ao laço: a mesma conta para t e t + período. */
private fun hudStarfieldLoopMs(timeMs: Long): Long =
    ((timeMs % HUD_STARFIELD_PERIOD_MS) + HUD_STARFIELD_PERIOD_MS) % HUD_STARFIELD_PERIOD_MS

/** Deriva em velocidade absoluta: y0 + v·t, módulo o tile da camada. Sem desvio de poço. */
fun hudStarfieldDriftY(star: HudStar, timeMs: Long): Float {
    val spec = hudStarfieldSpec(star.layer)
    val seconds = hudStarfieldLoopMs(timeMs) / 1000f
    return hudStarfieldWrap(star.y + spec.speed * seconds, spec.tile)
}

private fun hudStarfieldSpec(layer: HudStarfieldLayer): HudStarfieldLayerSpec =
    HUD_STARFIELD_LAYERS.first { it.layer == layer }

private fun hudStarfieldTile(layer: HudStarfieldLayer): Float = hudStarfieldSpec(layer).tile

/** Cópias da estrela no corpo: a deriva e depois +1 tile por vez, enquanto cair abaixo da altura. */
private fun hudStarfieldCopies(
    star: HudStar,
    loopMs: Long,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): List<HudStarfieldPoint> {
    val tile = hudStarfieldTile(star.layer)
    val drift = hudStarfieldDriftY(star, loopMs)
    val points = mutableListOf<HudStarfieldPoint>()
    var copy = 0
    var y = drift
    while (y < height) {
        points += hudStarfieldPoint(star, y, loopMs, height, wells, forbidden)
        copy++
        y = drift + copy * tile
    }
    return points
}

private fun hudStarfieldPoint(
    star: HudStar,
    by: Float,
    loopMs: Long,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): HudStarfieldPoint {
    val spec = hudStarfieldSpec(star.layer)
    val deflected = hudStarfieldDeflect(star.x, by, wells)
    val edge = hudStarfieldSmoothstep(0f, HUD_STARFIELD_FADE_PX, deflected.y) *
        (1f - hudStarfieldSmoothstep(height - HUD_STARFIELD_FADE_PX, height, deflected.y))
    val hidden = forbidden.any { hudStarfieldInside(it, deflected.x, deflected.y, 0f) }
    val twinkle = hudStarfieldTwinkle(star, loopMs)
    val alpha = if (hidden) 0f else min(1f, star.alpha * twinkle * deflected.cap * edge)
    var trailX = 0f
    var trailY = 0f
    if (spec.trail && deflected.near && by >= 5f) {
        val tail = hudStarfieldDeflect(star.x, by - 5f, wells)
        val dx = deflected.x - tail.x
        val dy = deflected.y - tail.y
        val length = sqrt(dx * dx + dy * dy)
        if (length > 1e-3f) {
            trailX = dx / length * HUD_STARFIELD_TRAIL_PX
            trailY = dy / length * HUD_STARFIELD_TRAIL_PX
        }
    }
    return HudStarfieldPoint(
        x = deflected.x,
        y = deflected.y,
        radius = star.radius,
        alpha = alpha,
        layer = star.layer,
        trailX = trailX,
        trailY = trailY,
        gold = star.gold,
        fade = if (hidden) 0f else deflected.cap * edge,
        twinkle = twinkle,
        glint = star.glint
    )
}

/** Cintilação: 1 + 0,15·sen(2π·t/período + fase). O período divide 240 s, então o laço fecha. */
private fun hudStarfieldTwinkle(star: HudStar, loopMs: Long): Float {
    val angle = 2.0 * PI * loopMs / star.twinklePeriodMs + star.twinklePhase
    return 1f + HUD_STARFIELD_TWINKLE_AMPLITUDE * sin(angle).toFloat()
}

private fun hudStarfieldFreeSpot(
    random: HudStarfieldRandom,
    width: Float,
    yLimit: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): HudStarfieldSpot? {
    for (attempt in 0 until HUD_STARFIELD_SPOT_TRIES) {
        val x = 2f + random.next() * (width - 4f)
        val y = random.next() * yLimit
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
    var near = false
    for (well in wells) {
        if (well.radius <= 0f) continue
        val ox = bx - well.centerX
        val oy = by - well.centerY
        val d = max(sqrt(ox * ox + oy * oy), 1e-3f)
        if (d < HUD_STARFIELD_NEAR_FACTOR * well.radius) near = true
        val shadow = well.radius * HUD_STARFIELD_SHADOW_FACTOR
        val influence = well.radius * HUD_STARFIELD_INFLUENCE_FACTOR
        if (d >= influence) continue
        val ein = well.radius * HUD_STARFIELD_MASS_FACTOR
        val mass = ein * ein / (2f * max(d, shadow)) * (1f - hudStarfieldSmoothstep(influence * 0.5f, influence, d))
        dx += ox / d * mass
        dy += oy / d * mass
        cap *= hudStarfieldSmoothstep(shadow, shadow * 1.8f, d)
    }
    return HudStarfieldDeflection(bx + dx, by + dy, cap, near)
}

private fun hudStarfieldDistance(x: Float, y: Float, well: HudStarfieldWell): Float {
    val dx = x - well.centerX
    val dy = y - well.centerY
    return sqrt(dx * dx + dy * dy)
}

private fun hudStarfieldInside(box: HudStarfieldBox, x: Float, y: Float, margin: Float): Boolean =
    x >= box.left - margin && x <= box.right + margin && y >= box.top - margin && y <= box.bottom + margin

private fun hudStarfieldWrap(value: Float, period: Float): Float = ((value % period) + period) % period

/** Smoothstep sem overshoot, igual ao `ss` do protótipo. */
internal fun hudStarfieldSmoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Tinta do desenho: cor, alfa que multiplica o da estrela e escala do raio. */
data class HudStarfieldInk(val color: Color, val alpha: Float, val radiusScale: Float)

/** Tinta clara: `--fg` de `docs/design-system/tokens/colors.css:77/116` (preset `PORCELANA_LIGHT`, `AppThemePresets.kt:93`). */
private val HUD_STARFIELD_INK_LIGHT = Color(0xFF171414)

/**
 * Escolhe a tinta do campo. Escuro: quente, ou ouro nas douradas, com alfa e raio cheios (o alfa de camada já
 * vem do quadro). Claro: `--fg` com alfa da L1 por camada e raio ×1,5; a dourada usa a mesma tinta.
 */
fun hudStarfieldInk(dark: Boolean, layer: HudStarfieldLayer, gold: Boolean): HudStarfieldInk {
    if (!dark) return HudStarfieldInk(HUD_STARFIELD_INK_LIGHT, lightLayerAlpha(layer), 1.5f)
    val color = if (gold) AppGargantuaTokens.gold else AppGargantuaTokens.hot
    return HudStarfieldInk(color, 1f, 1f)
}

private fun lightLayerAlpha(layer: HudStarfieldLayer): Float = when (layer) {
    HudStarfieldLayer.NEAR -> 0.95f
    HudStarfieldLayer.MID -> 0.7f
    HudStarfieldLayer.FAR -> 0.45f
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

/**
 * Alfa do desenho de uma estrela. Escuro: alfa da estrela × tinta. Claro: alfa da L1 × fade × cintilação ×
 * atenuação, até 1 (a L1 substitui o alfa da estrela no claro).
 */
fun hudStarfieldDrawAlpha(dark: Boolean, point: HudStarfieldPoint, forbidden: List<HudStarfieldBox>): Float {
    val ink = hudStarfieldInk(dark, point.layer, point.gold)
    val attenuation = hudStarfieldTextAttenuation(dark, point.x, point.y, forbidden)
    if (dark) return min(1f, point.alpha * ink.alpha * attenuation)
    return min(1f, ink.alpha * point.fade * point.twinkle * attenuation)
}

/** Meia-extensão do brilho em cruz: 1,25 × raio, para uma linha de 2,5 × raio. */
fun hudStarfieldGlintHalfLength(radius: Float): Float = radius * HUD_STARFIELD_GLINT_LENGTH_FACTOR / 2f

/** Largura da linha do brilho em cruz, em dp. */
const val HUD_STARFIELD_GLINT_WIDTH: Float = HUD_STARFIELD_GLINT_WIDTH_DP

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
