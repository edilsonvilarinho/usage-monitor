package com.usagemonitor.presentation.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Captura ativa de uma estrela pelo poço: cabeça em voo com rastro, ou lampejo na borda do núcleo (#590 A03). */
sealed interface HudStarCapture

/** Ponto do rastro: [alpha] vai de .9 perto da cabeça a 0 na cauda; [width] de 1,6 a .4 dp. */
data class HudStarCaptureTrailPoint(val x: Float, val y: Float, val alpha: Float, val width: Float)

/** Cabeça da estrela em voo, com o rastro dos últimos 10 dp do caminho. */
data class HudStarCaptureFlight(
    val headX: Float,
    val headY: Float,
    val headAlpha: Float,
    val trail: List<HudStarCaptureTrailPoint>
) : HudStarCapture

/** Lampejo de 200 ms no ponto em que a estrela some. */
data class HudStarCaptureFlash(val x: Float, val y: Float, val alpha: Float, val radius: Float) : HudStarCapture

/** Agenda: primeira captura aos 4 000 ms, depois uma a cada 30 000 ms, 8 por laço de 240 000 ms. */
private const val HUD_STAR_CAPTURE_START_MS = 4_000L
private const val HUD_STAR_CAPTURE_EVERY_MS = 30_000L
private const val HUD_STAR_CAPTURE_COUNT = 8
private const val HUD_STAR_CAPTURE_FLIGHT_MS = 3_000L
private const val HUD_STAR_CAPTURE_FLASH_MS = 200L
private const val HUD_STAR_CAPTURE_ANGLE_STEP = 2.399963f
private const val HUD_STAR_CAPTURE_TRAIL_DP = 10f
private const val HUD_STAR_CAPTURE_TRAIL_STEP = 0.002f
private const val HUD_STAR_CAPTURE_TRAIL_ALPHA = 0.9f
private const val HUD_STAR_CAPTURE_FLASH_ALPHA = 0.6f
private const val HUD_STAR_CAPTURE_FLASH_RADIUS = 5f

/** Fluxo (A04): nascimento a cada `NUM/DEN` ms = 2000/3 ms (1,5 estrela/s), visível enquanto idade < 3 200 ms. */
private const val HUD_STAR_FLOW_STEP_MS_NUM = 2_000L
private const val HUD_STAR_FLOW_STEP_MS_DEN = 3L
private const val HUD_STAR_FLOW_VISIBLE_MS = 3_200L
private const val HUD_STAR_FLOW_ARM_BASE = 0.6f
private const val HUD_STAR_FLOW_ARM_STEP = 0.9f

/** Soma das duas fontes (A03 ociosas + A04 fluxo), na mesma lista. */
fun hudStarCaptures(
    timeMs: Long,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): List<HudStarCapture> = hudStarIdleCaptures(timeMs, wells, forbidden) + hudStarFlowCaptures(timeMs, wells, forbidden)

/**
 * Capturas de fluxo em [timeMs] (tempo absoluto): 1,5 estrela/s por poço em execução. Nascimentos na grade
 * `k · 2000/3 ms` (absoluta, sem laço); um nascimento só vale se o poço está em execução no próprio instante
 * de nascimento (`since ≤ start` e `start < until`), então desligar não corta voo em curso e ligar não faz
 * estrela surgir no meio. Cada voo fica visível enquanto `idade < 3 200 ms`, com a mesma espiral, rastro e lampejo
 * das ociosas. Braço por índice do poço: `0,6 + i · 0,9` mais jitter determinístico de ±0,125 rad por `(i, k)`.
 */
fun hudStarFlowCaptures(
    timeMs: Long,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): List<HudStarCapture> {
    if (wells.none { it.activeSinceMs != null }) return emptyList()
    val out = mutableListOf<HudStarCapture>()
    val kMin = maxOf(0L, (timeMs - HUD_STAR_FLOW_VISIBLE_MS) * HUD_STAR_FLOW_STEP_MS_DEN / HUD_STAR_FLOW_STEP_MS_NUM - 1)
    val kMax = timeMs * HUD_STAR_FLOW_STEP_MS_DEN / HUD_STAR_FLOW_STEP_MS_NUM + 1
    for (k in kMin..kMax) {
        val start = k * HUD_STAR_FLOW_STEP_MS_NUM / HUD_STAR_FLOW_STEP_MS_DEN
        val age = timeMs - start
        if (age < 0L || age >= HUD_STAR_FLOW_VISIBLE_MS) continue
        for ((i, well) in wells.withIndex()) {
            if (!hudStarfieldWellRunning(well, start)) continue
            val theta0 = HUD_STAR_FLOW_ARM_BASE + i * HUD_STAR_FLOW_ARM_STEP + hudStarFlowJitter(i, k)
            out.add(hudStarCaptureAt(age, well, theta0, forbidden))
        }
    }
    return out
}

/** Jitter determinístico de ±0,125 rad por `(índice do poço, k)`: mistura tipo fmix64 em Long, sem `Random`. */
private fun hudStarFlowJitter(well: Int, k: Long): Float {
    var h = well.toLong() * -7046029254386353131L + k
    h = (h xor (h ushr 33)) * -49064778989728563L
    h = (h xor (h ushr 33)) * -4265267296055464877L
    h = h xor (h ushr 33)
    val unit = (h ushr 11).toDouble() / (1L shl 53).toDouble()
    return ((unit * 2.0 - 1.0) * 0.125).toFloat()
}

/**
 * Capturas ociosas em [timeMs] (tempo absoluto do quadro; o laço de 240 000 ms só posiciona a agenda). Cada captura
 * escolhe, no instante absoluto do próprio início (`timeMs − idade`), um poço sem execução (`idle[n mod idle.size]`),
 * para o voo não trocar de poço no meio; sem poço ocioso, não há captura.
 * Ângulo inicial `n · 2,399963`. Ponto dentro de caixa proibida sai com alfa zero, como as estrelas. Sem poços, nada.
 */
fun hudStarIdleCaptures(
    timeMs: Long,
    wells: List<HudStarfieldWell>,
    forbidden: List<HudStarfieldBox>
): List<HudStarCapture> {
    if (wells.isEmpty()) return emptyList()
    val loop = hudStarCaptureWrap(timeMs, HUD_STARFIELD_PERIOD_MS)
    val out = mutableListOf<HudStarCapture>()
    for (n in 0 until HUD_STAR_CAPTURE_COUNT) {
        val start = HUD_STAR_CAPTURE_START_MS + n * HUD_STAR_CAPTURE_EVERY_MS
        val age = hudStarCaptureWrap(loop - start, HUD_STARFIELD_PERIOD_MS)
        if (age >= HUD_STAR_CAPTURE_FLIGHT_MS + HUD_STAR_CAPTURE_FLASH_MS) continue
        val absoluteStart = timeMs - age
        val idle = wells.filter { !hudStarfieldWellRunning(it, absoluteStart) }
        if (idle.isEmpty()) continue
        val well = idle[n % idle.size]
        out.add(hudStarCaptureAt(age, well, n * HUD_STAR_CAPTURE_ANGLE_STEP, forbidden))
    }
    return out
}

/** Poço em execução no instante [t]: `since ≤ t` e (`until` nulo ou `t < until`), semiaberto no fim; `since` nulo é ocioso. */
private fun hudStarfieldWellRunning(well: HudStarfieldWell, t: Long): Boolean {
    val since = well.activeSinceMs ?: return false
    val until = well.activeUntilMs
    return t >= since && (until == null || t < until)
}

private fun hudStarCaptureAt(
    age: Long,
    well: HudStarfieldWell,
    theta0: Float,
    forbidden: List<HudStarfieldBox>
): HudStarCapture =
    if (age < HUD_STAR_CAPTURE_FLIGHT_MS) {
        hudStarCaptureFlight(age, well, theta0, forbidden)
    } else {
        hudStarCaptureFlash(age, well, theta0, forbidden)
    }

/** Cabeça em voo: `s = idade / 2600 ms`, fade `ss(0,.12,s)·(1−ss(.9,1,s))`, rastro atrás dela. */
private fun hudStarCaptureFlight(
    age: Long,
    well: HudStarfieldWell,
    theta0: Float,
    forbidden: List<HudStarfieldBox>
): HudStarCaptureFlight {
    val s = age.toFloat() / HUD_STAR_CAPTURE_FLIGHT_MS
    val head = hudStarCapturePosition(s, well, theta0)
    val headAlpha = hudStarCaptureFade(s) * hudStarCaptureVisibility(head.first, head.second, forbidden)
    return HudStarCaptureFlight(head.first, head.second, headAlpha, hudStarCaptureTrail(s, well, theta0, forbidden))
}

/** Rastro: caminha para trás pelo caminho até 10 dp de arco; alfa .9→0 e largura 1,6→.4 da cabeça à cauda. */
private fun hudStarCaptureTrail(
    s: Float,
    well: HudStarfieldWell,
    theta0: Float,
    forbidden: List<HudStarfieldBox>
): List<HudStarCaptureTrailPoint> {
    val out = mutableListOf<HudStarCaptureTrailPoint>()
    val head = hudStarCapturePosition(s, well, theta0)
    var prevX = head.first
    var prevY = head.second
    var arc = 0f
    var back = s
    while (back > 0f) {
        back = maxOf(0f, back - HUD_STAR_CAPTURE_TRAIL_STEP)
        val point = hudStarCapturePosition(back, well, theta0)
        val dx = point.first - prevX
        val dy = point.second - prevY
        arc += sqrt(dx * dx + dy * dy)
        prevX = point.first
        prevY = point.second
        if (arc > HUD_STAR_CAPTURE_TRAIL_DP) break
        val f = arc / HUD_STAR_CAPTURE_TRAIL_DP
        val alpha = HUD_STAR_CAPTURE_TRAIL_ALPHA * (1f - f) * hudStarCaptureVisibility(point.first, point.second, forbidden)
        out.add(HudStarCaptureTrailPoint(point.first, point.second, alpha, width = 1.6f - 1.2f * f))
    }
    return out
}

/** Lampejo: começa em alfa .6 e some em 200 ms, no ponto final da espiral (s = 1, raio 0,55R). */
private fun hudStarCaptureFlash(
    age: Long,
    well: HudStarfieldWell,
    theta0: Float,
    forbidden: List<HudStarfieldBox>
): HudStarCaptureFlash {
    val end = hudStarCapturePosition(1f, well, theta0)
    val t = (age - HUD_STAR_CAPTURE_FLIGHT_MS).toFloat() / HUD_STAR_CAPTURE_FLASH_MS
    val alpha = HUD_STAR_CAPTURE_FLASH_ALPHA * (1f - t) * hudStarCaptureVisibility(end.first, end.second, forbidden)
    return HudStarCaptureFlash(end.first, end.second, alpha, HUD_STAR_CAPTURE_FLASH_RADIUS)
}

/** Caminho: `r(s) = 2,4R(1−s)^1,6 + 0,55R·s`, `θ = θ0 + 3π·s`, centro do poço como origem. */
private fun hudStarCapturePosition(s: Float, well: HudStarfieldWell, theta0: Float): Pair<Float, Float> {
    val r = well.radius
    val radius = 2.4f * r * (1f - s).pow(1.6f) + 0.55f * r * s
    val theta = theta0 + 3f * PI.toFloat() * s
    return Pair(well.centerX + radius * cos(theta), well.centerY + radius * sin(theta))
}

/** Alfa de visibilidade: zero dentro de qualquer caixa proibida, um fora. */
private fun hudStarCaptureVisibility(x: Float, y: Float, forbidden: List<HudStarfieldBox>): Float =
    if (forbidden.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) 0f else 1f

private fun hudStarCaptureFade(s: Float): Float =
    hudStarfieldSmoothstep(0f, 0.12f, s) * (1f - hudStarfieldSmoothstep(0.9f, 1f, s))

private fun hudStarCaptureWrap(value: Long, period: Long): Long = ((value % period) + period) % period
