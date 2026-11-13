package com.usagemonitor.presentation.ui.components

import kotlin.math.max
import kotlin.math.sqrt

/** Período do laço do campo, em ms: o quadro em t e em t + período é igual (paralaxe fecha). */
const val HUD_STARFIELD_PERIOD_MS: Long = 82_500L

/** Semente fixa do campo: a mesma semente gera o mesmo céu. */
const val HUD_STARFIELD_SEED: Int = 586

/** Camada de paralaxe: perto é mais rápida, maior e mais clara; longe é lenta e tênue. */
enum class HudStarfieldLayer { NEAR, MID, FAR }

/** Poço gravitacional: centro e raio do anel, em px do corpo. */
data class HudStarfieldWell(val centerX: Float, val centerY: Float, val radius: Float)

/** Retângulo proibido (caixa de texto ou pílula), em px do corpo. Bordas inclusivas. */
data class HudStarfieldBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Estrela no nascimento: [y] é a posição y0 da deriva, [radius] e [alpha] são os valores base da
 * camada (sem deflexão), [gold] marca as douradas (18% do campo) para a cor do desenho.
 */
data class HudStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val layer: HudStarfieldLayer,
    val gold: Boolean = false
)

/**
 * Estrela num quadro. [alpha] é 0 quando a estrela está apagada (fade de borda, sombra do poço ou
 * caixa proibida); a lista mantém todas as estrelas para o tamanho do quadro ser fixo.
 * [trailX]/[trailY] é o vetor da cauda: a cauda fica em (x - trailX, y - trailY), com 5 px de
 * comprimento na direção do movimento curvo. Zero quando não há rastro.
 */
data class HudStarfieldPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val layer: HudStarfieldLayer,
    val trailX: Float,
    val trailY: Float,
    val gold: Boolean = false
)

/** Especificação de uma camada: velocidade de deriva (px por período de altura), quantidade e faixas. */
private class HudStarfieldLayerSpec(
    val layer: HudStarfieldLayer,
    val speed: Int,
    val count: Int,
    val radiusMin: Float,
    val radiusMax: Float,
    val alphaMin: Float,
    val alphaMax: Float,
    val trail: Boolean
)

// Números do protótipo AT1 (at1-tres-camadas/index.html): 30/45/65 estrelas, velocidades 4/2/1.
private val HUD_STARFIELD_LAYERS = listOf(
    HudStarfieldLayerSpec(HudStarfieldLayer.NEAR, speed = 4, count = 30, radiusMin = 1.3f, radiusMax = 1.7f, alphaMin = 0.8f, alphaMax = 0.95f, trail = true),
    HudStarfieldLayerSpec(HudStarfieldLayer.MID, speed = 2, count = 45, radiusMin = 0.9f, radiusMax = 0.9f, alphaMin = 0.45f, alphaMax = 0.45f, trail = true),
    HudStarfieldLayerSpec(HudStarfieldLayer.FAR, speed = 1, count = 65, radiusMin = 0.6f, radiusMax = 0.6f, alphaMin = 0.25f, alphaMax = 0.25f, trail = false)
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
 * Gera as 140 estrelas de nascimento por semente. Cada estrela cai só em área livre: fora das caixas
 * proibidas (com margem de 2 px) e fora dos discos dos poços (raio + 3 px). Corpo menor que 4 px
 * em qualquer lado não recebe estrelas.
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
        var placed = 0
        while (placed < spec.count) {
            val spot = hudStarfieldFreeSpot(random, width, height, wells, forbidden) ?: break
            val radius = spec.radiusMin + (spec.radiusMax - spec.radiusMin) * random.next()
            val alpha = spec.alphaMin + (spec.alphaMax - spec.alphaMin) * random.next()
            val gold = random.next() < HUD_STARFIELD_GOLD_CHANCE
            stars += HudStar(spot.x, spot.y, radius, alpha, spec.layer, gold)
            placed++
        }
    }
    return stars
}

/**
 * Quadro do campo no instante [timeMs]. A posição de cada estrela é a deriva da camada
 * (y0 + v × altura × fase, com fase = tempo módulo período), desviada pelos poços e apagada se cair
 * numa caixa proibida. Sempre devolve uma entrada por estrela (140 com a configuração padrão).
 */
fun hudStarfieldFrame(
    timeMs: Long,
    width: Float,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>,
    seed: Int = HUD_STARFIELD_SEED
): List<HudStarfieldPoint> {
    val stars = hudStarfieldStars(width, height, wells, forbidden, seed)
    val period = HUD_STARFIELD_PERIOD_MS
    val phase = (((timeMs % period) + period) % period).toFloat() / period.toFloat()
    return stars.map { star -> hudStarfieldPoint(star, phase, height, wells, forbidden) }
}

private fun hudStarfieldPoint(
    star: HudStar,
    phase: Float,
    height: Float,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): HudStarfieldPoint {
    val spec = HUD_STARFIELD_LAYERS.first { it.layer == star.layer }
    val by = hudStarfieldWrap(star.y + spec.speed * height * phase, height)
    val deflected = hudStarfieldDeflect(star.x, by, wells)
    val edge = hudStarfieldSmoothstep(0f, HUD_STARFIELD_FADE_PX, deflected.y) *
        (1f - hudStarfieldSmoothstep(height - HUD_STARFIELD_FADE_PX, height, deflected.y))
    val hidden = forbidden.any { hudStarfieldInside(it, deflected.x, deflected.y, 0f) }
    val alpha = if (hidden) 0f else star.alpha * deflected.cap * edge
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
        gold = star.gold
    )
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
        val y = 2f + random.next() * (height - 4f)
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
private fun hudStarfieldSmoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
