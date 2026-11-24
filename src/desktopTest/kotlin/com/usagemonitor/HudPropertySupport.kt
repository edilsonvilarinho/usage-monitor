package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.random.Random

/**
 * Executor de propriedades da HUD: a mesma semente produz a mesma sequência de entradas.
 * Uma falha é um resultado: a mensagem traz semente, iteração e entrada para reproduzir.
 */
internal fun <T> forAll(seed: Long, iterations: Int, generator: (Random) -> T, assertion: (T) -> Unit) {
    val random = Random(seed)
    for (iteration in 0 until iterations) {
        val input = generator(random)
        try {
            assertion(input)
        } catch (failure: Throwable) {
            throw AssertionError("propriedade falhou: semente=$seed, iteração=$iteration, entrada=$input: ${failure.message}", failure)
        }
    }
}

/** Uma entrada de geometria da HUD. A área fica em pixels físicos; a geometria recebe [areaDp]. */
internal data class HudScenario(
    val accounts: List<HudAccount>,
    val edge: HudEdge,
    val offsetFraction: Float,
    val area: ScreenWorkArea,
    val scale: Float,
    val hasUpdateIndicator: Boolean,
    val hasUpdateAction: Boolean
) {
    /** Área útil na densidade de composição, como [hudHostWindowBounds] a recebe. */
    val areaDp: ScreenWorkArea
        get() = ScreenWorkArea(
            x = area.x / scale,
            y = area.y / scale,
            size = DpSize(area.size.width / scale, area.size.height / scale)
        )
}

/** Entrada do buraco flutuante: centros e janela em pixels. */
internal data class HudFloatingScenario(
    val center: HudFloatCenter?,
    val notchCenter: HudFloatCenter,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val workArea: HudPxRect,
    val anchorRadiusPx: Int
)

internal fun generateHudScenario(random: Random): HudScenario {
    val scale = 0.75f + random.nextFloat() * 1.5f
    val width = random.nextInt(640, 3841)
    val height = random.nextInt(480, 2161)
    val originX = random.nextInt(-1920, 3841)
    val originY = random.nextInt(-1080, 2161)
    val accountCount = random.nextInt(0, 13)
    return HudScenario(
        accounts = List(accountCount) { index -> generateHudAccount(random, index) },
        edge = HudEdge.entries[random.nextInt(HudEdge.entries.size)],
        offsetFraction = generateOffset(random),
        area = ScreenWorkArea(originX.dp, originY.dp, DpSize(width.dp, height.dp)),
        scale = scale,
        hasUpdateIndicator = random.nextBoolean(),
        hasUpdateAction = random.nextBoolean()
    )
}

internal fun generateFloatingScenario(random: Random): HudFloatingScenario {
    val workWidth = random.nextInt(640, 3841)
    val workHeight = random.nextInt(480, 2161)
    val workX = random.nextInt(-1920, 3841)
    val workY = random.nextInt(-1080, 2161)
    val workArea = HudPxRect(workX, workY, workWidth, workHeight)
    val withCenter = random.nextBoolean()
    return HudFloatingScenario(
        center = if (withCenter) randomPoint(random, workArea) else null,
        notchCenter = randomPoint(random, workArea),
        windowWidthPx = random.nextInt(120, workWidth + 1),
        windowHeightPx = random.nextInt(120, workHeight + 1),
        workArea = workArea,
        anchorRadiusPx = random.nextInt(0, 61)
    )
}

private fun randomPoint(random: Random, area: HudPxRect): HudFloatCenter =
    HudFloatCenter(
        xPx = random.nextInt(area.xPx, area.xPx + area.widthPx + 1),
        yPx = random.nextInt(area.yPx, area.yPx + area.heightPx + 1)
    )

/** Offset de encaixe em 0..1, com as bordas 0 e 1 sorteadas com peso próprio. */
private fun generateOffset(random: Random): Float =
    when (random.nextInt(5)) {
        0 -> 0f
        1 -> 1f
        else -> random.nextFloat()
    }

private fun generateHudAccount(random: Random, index: Int): HudAccount {
    val source = ApiSource.entries[random.nextInt(ApiSource.entries.size)]
    val profileId = if (source == ApiSource.ANTHROPIC) "perfil-$index" else null
    val label = when (random.nextInt(4)) {
        0 -> ""
        1 -> "Conta muito longa com nome de perfil extenso $index ".repeat(6)
        else -> "Conta $index"
    }
    val quotaCount = random.nextInt(0, 5)
    return HudAccount(
        targetKey = UsageTargetKey(source, profileId),
        label = label,
        statusLabel = if (random.nextBoolean()) "OK" else "Limite",
        tone = AppTone.entries[random.nextInt(AppTone.entries.size)],
        quotas = List(quotaCount) { quotaIndex -> generateHudQuota(random, quotaIndex) },
        focusIndex = 0
    )
}

private fun generateHudQuota(random: Random, quotaIndex: Int): HudQuota {
    val shortLabels = listOf("5h", "7d", "Saldo", "Mês")
    val percent = random.nextInt(0, 101)
    return HudQuota(
        shortLabel = shortLabels[quotaIndex % shortLabels.size],
        percentText = "$percent%",
        fraction = percent / 100f,
        tone = AppTone.entries[random.nextInt(AppTone.entries.size)],
        resetText = if (random.nextBoolean()) "14:00" else null,
        hasForecast = random.nextBoolean()
    )
}

/**
 * Roda cenários da HUD como [forAll]; na primeira reprovação encolhe a entrada enquanto ela ainda reprova
 * e lança o menor caso com o motivo do produto.
 */
internal fun forAllHudShrinking(seed: Long, iterations: Int, assertion: (HudScenario) -> Unit) {
    val random = Random(seed)
    for (iteration in 0 until iterations) {
        val input = generateHudScenario(random)
        val failure = failureOf(input, assertion) ?: continue
        val smallest = shrinkHudScenario(input) { candidate -> failureOf(candidate, assertion) != null }
        val reason = failureOf(smallest, assertion)?.message
        throw AssertionError(
            "propriedade falhou: semente=$seed, iteração=$iteration, menor caso=${summarizeHudScenario(smallest)}: $reason",
            failure
        )
    }
}

private fun failureOf(input: HudScenario, assertion: (HudScenario) -> Unit): Throwable? =
    try {
        assertion(input)
        null
    } catch (failure: Throwable) {
        failure
    }

/** Encolhe enquanto [fails] aceita; cada passo tira uma conta, uma cota, um rótulo ou a escala, então termina. */
internal fun shrinkHudScenario(start: HudScenario, fails: (HudScenario) -> Boolean): HudScenario {
    var current = start
    while (true) {
        val next = shrinkCandidates(current).firstOrNull(fails) ?: return current
        current = next
    }
}

private fun shrinkCandidates(s: HudScenario): List<HudScenario> {
    val candidates = mutableListOf<HudScenario>()
    s.accounts.forEachIndexed { index, account ->
        candidates += s.copy(accounts = s.accounts.filterIndexed { other, _ -> other != index })
        account.quotas.forEachIndexed { quotaIndex, _ ->
            val quotas = account.quotas.filterIndexed { other, _ -> other != quotaIndex }
            candidates += s.copy(accounts = s.accounts.replaced(index, account.copy(quotas = quotas)))
        }
        if (account.label.isNotEmpty()) {
            candidates += s.copy(accounts = s.accounts.replaced(index, account.copy(label = "")))
        }
    }
    if (s.scale != 1f) candidates += s.copy(scale = 1f)
    if (s.hasUpdateIndicator) candidates += s.copy(hasUpdateIndicator = false)
    if (s.hasUpdateAction) candidates += s.copy(hasUpdateAction = false)
    return candidates
}

private fun List<HudAccount>.replaced(index: Int, account: HudAccount): List<HudAccount> =
    mapIndexed { other, current -> if (other == index) account else current }

/** Uma linha por caso: borda, área útil em dp, número de contas e cotas, escala. */
internal fun summarizeHudScenario(s: HudScenario): String =
    "borda=${s.edge}, área=${s.areaDp.size.width.value} x ${s.areaDp.size.height.value} dp, " +
        "contas=${s.accounts.size}, cotas=${s.accounts.sumOf { account -> account.quotas.size }}, escala=${s.scale}"
