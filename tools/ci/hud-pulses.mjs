#!/usr/bin/env node
// Detector de pulsos da HUD (A02 do plano #652). Entrada: CSV de quadros da
// captura (tools/real-app/record.ps1), um quadro a cada ~50 ms.
//
// Uso: node tools/ci/hud-pulses.mjs <csv> [--expect hover --events '{"enterMs":n,"leaveMs":n}' | --expect drag --events '{"pressMs":n,"releaseMs":n,"dx":n,"dy":n}'] [--rise-ratio <n>] [--min-rest-width <n>] [--max-rest-foreground <n>] [--max-foreground <n>] [--scene <nome>]
// Saida: veredito na primeira linha, depois um pulso por linha. Padrao: ok, pulsed, not-visible (0, 1, 4).
// --scene <nome> (cenas em tools/real-app/scenes.json): so roda quando o veredito atual e ok; low-rate (6), demais de cena (1), motivo na 2a linha. Nome desconhecido: uso (2).
// Limiar: hover mede o repouso antes de entrar (mediana); os outros modos usam o p5 dos visiveis.
// Hover: ok (0), not-opened, opened-late, not-closed, extra-pulse (1), not-visible (4). Drag: ok (0), not-moved, wrong-place, not-closed (1), not-visible (4). Open-at-rest (1) com --max-rest-foreground (inteiro > 0): repouso acima do maximo, antes do veredito de pulso. Flash (1) com --max-foreground (inteiro > 0): um quadro acima do maximo, em qualquer ponto, antes de open-at-rest; uma linha `quadro <tMs> <foreground>` por quadro. Uso ou CSV invalido: 2.
// --rise-ratio (> 1, padrao 1.5) troca o limiar de subida. --min-rest-width (inteiro > 0) confere a escala da cena antes de qualquer
// outro veredito: mediana de w no repouso abaixo do minimo e wrong-scene (5); sem quadro nessa janela, not-visible.
import { readFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HEADER = 't_ms,foreground,x,y,w,h'
const DEFAULTS = { settleMs: 2000, riseRatio: 1.5, minFrames: 2, minForeground: 400, tailMs: 1000 }
const EXIT = { ok: 0, pulsed: 1, 'open-at-rest': 1, flash: 1, usage: 2, 'not-visible': 4, 'wrong-scene': 5, 'low-rate': 6, 'late-start': 1, vanished: 1, 'startup-pulse': 1, 'wrong-box': 1, 'wrong-size': 1, unstable: 1 }

export function parseFrames(csv) {
    const frames = []
    let headerSeen = false
    let previous = -Infinity
    csv.split(/\r?\n/).forEach((raw, index) => {
        const line = raw.trim()
        if (line === '') return
        if (!headerSeen) {
            if (line !== HEADER) throw new Error(`cabecalho invalido; esperado "${HEADER}"`)
            headerSeen = true
            return
        }
        const where = `linha ${index + 1}`
        const cells = line.split(',').map((cell) => cell.trim())
        if (cells.length !== 6) throw new Error(`${where}: esperados 6 campos, achei ${cells.length}`)
        if (cells.some((cell) => cell === '' || !Number.isFinite(Number(cell)))) throw new Error(`${where}: valor nao numerico`)
        const [tMs, foreground, x, y, w, h] = cells.map(Number)
        if (foreground < 0) throw new Error(`${where}: foreground negativo`)
        if (tMs <= previous) throw new Error(`${where}: t_ms nao cresce`)
        previous = tMs
        frames.push({ tMs, foreground, x, y, w, h })
    })
    if (!headerSeen) throw new Error(`cabecalho invalido; esperado "${HEADER}"`)
    return frames
}

// Visivel = foreground >= minForeground. Pulsos so depois do settle, contado da
// primeira aparicao visivel (pixel solto antes nao adianta). Repouso abaixo de
// minForeground, ou nada visivel nos ultimos tailMs, e not-visible. O limiar usa o
// repouso de restLevel (nunca a mediana de tudo: painel aberto por muito tempo puxaria o limiar).
export function detectPulses(frames, options = {}) {
    const { settleMs, riseRatio, minFrames, minForeground, tailMs, restBeforeMs } = { ...DEFAULTS, ...options }
    const notVisible = (appeared) => ({ appeared, restForeground: null, pulses: [] })
    const first = frames.find((frame) => frame.foreground >= minForeground)
    if (!first) return notVisible(false)
    const settled = frames.filter((frame) => frame.tMs >= first.tMs + settleMs)
    if (settled.length < minFrames) return notVisible(true)
    const lastMs = frames[frames.length - 1].tMs
    if (frames.filter((frame) => frame.tMs >= lastMs - tailMs).every((frame) => frame.foreground < minForeground)) {
        return notVisible(true)
    }
    if (median(settled.map((frame) => frame.foreground)) < minForeground) return notVisible(true)

    const restForeground = restLevel(settled, { minForeground, restBeforeMs })
    if (restForeground === null) return notVisible(true)
    const threshold = restForeground * riseRatio
    const pulses = []
    let run = []
    for (const frame of settled) {
        if (frame.foreground > threshold) {
            run.push(frame)
            continue
        }
        closeRun(pulses, run, minFrames)
        run = []
    }
    closeRun(pulses, run, minFrames)
    return { appeared: true, restForeground, pulses, lastMs }
}

export function verdictOf(result) {
    if (!result.appeared || result.restForeground === null) return 'not-visible'
    return result.pulses.length > 0 ? 'pulsed' : 'ok'
}

// Flash (--max-foreground): quadro acima do maximo, em qualquer ponto da gravacao (inclusive antes do settle).
// Igual ao maximo nao conta; sem maximo, nenhum quadro.
export function flashFrames(frames, maxForeground) {
    if (maxForeground === undefined) return []
    return frames.filter((frame) => frame.foreground > maxForeground)
}

// Um pulso so: abre ate openWithinMs depois de entrar e fecha (ultimo quadro acima do limiar
// antes do fim da gravacao) ate closeWithinMs depois de sair. Pulso antes de entrar conta como extra.
export function hoverVerdict(result, { enterMs, leaveMs, openWithinMs = 1500, closeWithinMs = 2500 }) {
    if (!result.appeared || result.restForeground === null) return 'not-visible'
    if (result.pulses.length === 0) return 'not-opened'
    if (result.pulses.length > 1) return 'extra-pulse'
    const [pulse] = result.pulses
    if (pulse.startMs < enterMs) return 'extra-pulse'
    if (pulse.startMs - enterMs > openWithinMs) return 'opened-late'
    const closed = pulse.endMs < result.lastMs && pulse.endMs <= leaveMs + closeWithinMs
    return closed ? 'ok' : 'not-closed'
}

// Arrasto (A04): repouso antes = quadros visiveis >= 3 s antes de apertar; depois = >= 4 s depois de soltar.
// Tem de fechar (foreground dentro de restTolerance do inicial) e o centro andar (dx, dy) ate tolerancePx.
export function dragVerdict(frames, { pressMs, releaseMs, dx, dy, tolerancePx = 8, restTolerance = 0.1 }) {
    const visible = (frame) => frame.foreground >= DEFAULTS.minForeground
    const before = frames.filter((frame) => frame.tMs <= pressMs - 3000 && visible(frame))
    const after = frames.filter((frame) => frame.tMs >= releaseMs + 4000 && visible(frame))
    if (before.length === 0 || after.length === 0) return 'not-visible'
    const rest = restOf(before)
    const landed = restOf(after)
    if (Math.abs(landed.foreground - rest.foreground) > restTolerance * rest.foreground) return 'not-closed'
    const moveX = landed.cx - rest.cx
    const moveY = landed.cy - rest.cy
    if (Math.hypot(moveX - dx, moveY - dy) <= tolerancePx) return 'ok'
    if (Math.hypot(moveX, moveY) <= tolerancePx) return 'not-moved'
    return 'wrong-place'
}

// Caixa de repouso: medianas do foreground e do centro da caixa de pixels alterados.
function restOf(frames) {
    return {
        foreground: median(frames.map((frame) => frame.foreground)),
        cx: median(frames.map((frame) => frame.x + frame.w / 2)),
        cy: median(frames.map((frame) => frame.y + frame.h / 2)),
    }
}

// Cena (--scene): medidas em tools/real-app/scenes.json, ao lado de tools/ci (caminho por import.meta.url).
const SCENES_PATH = fileURLToPath(new URL('../real-app/scenes.json', import.meta.url))
const LOW_RATE_FRAMES = 150
const MAX_GAP_MS = 1000
const STARTUP_WINDOW_MS = 2000
const REST_WINDOW_END_MS = 7000
const STARTUP_TOLERANCE = 1.05
const BOX_TOLERANCE_PX = 2
const SIZE_TOLERANCE = 0.02
const LANDING_TOLERANCE_PX = 8
const PARKED_AFTER_LEAVE_MS = 2500
const PARKED_BEFORE_PRESS_MS = 600
const SETTLE_AFTER_RELEASE_MS = 4000

// Problema de cena ({ verdict, motivo }) ou null. Ordem: low-rate, vanished, startup-pulse, wrong-box, wrong-size, unstable.
// t0 = primeiro quadro com foreground >= minForeground. Chamada so quando o veredito atual e ok.
export function sceneProblem(frames, { scene, expect, events }) {
    const { rest: reference, startupMax } = sceneNamed(scene)
    const first = frames.find((frame) => frame.foreground >= DEFAULTS.minForeground)
    if (!first) return null
    const t0 = first.tMs
    const after = frames.filter((frame) => frame.tMs > t0)
    return lowRateProblem(after, t0)
        ?? lateStartProblem(t0)
        ?? vanishedProblem(after)
        ?? startupProblem(frames, startupMax, t0)
        ?? boxProblem(frames, reference, t0)
        ?? sizeProblem(frames, reference, t0)
        ?? unstableProblem(frames, reference, t0, { expect, events })
}

function verdictWith(verdict, motivo) {
    return { verdict, motivo }
}

function sceneNamed(name) {
    const scenes = JSON.parse(readFileSync(SCENES_PATH, 'utf8'))
    if (!Object.hasOwn(scenes, name)) throw new Error(`uso: --scene desconhecido "${name}"; cenas: ${Object.keys(scenes).join(', ')}`)
    return scenes[name]
}

function lowRateProblem(after, t0) {
    if (after.length < LOW_RATE_FRAMES) return verdictWith('low-rate', `${after.length} quadros depois de t0, esperado >= ${LOW_RATE_FRAMES}`)
    let previous = t0
    for (const frame of after) {
        const gap = frame.tMs - previous
        if (gap > MAX_GAP_MS) return verdictWith('low-rate', `intervalo de ${gap} ms ate t=${frame.tMs} ms, esperado <= ${MAX_GAP_MS} ms`)
        previous = frame.tMs
    }
    return null
}

// Primeira aparicao depois de LATE_START_MS da gravacao: real tem no maximo 20,7 s (566 gravacoes).
const LATE_START_MS = 30000

function lateStartProblem(t0) {
    if (t0 <= LATE_START_MS) return null
    return verdictWith('late-start', `primeiro quadro visivel em t=${t0} ms (esperado <= ${LATE_START_MS} ms)`)
}

function vanishedProblem(after) {
    const frame = after.find((item) => item.foreground < DEFAULTS.minForeground)
    if (!frame) return null
    return verdictWith('vanished', `t=${frame.tMs} ms com foreground ${frame.foreground} (esperado >= ${DEFAULTS.minForeground})`)
}

function startupProblem(frames, startupMax, t0) {
    const limit = startupMax * STARTUP_TOLERANCE
    const frame = frames.find((item) => item.tMs >= t0 && item.tMs <= t0 + STARTUP_WINDOW_MS && item.foreground > limit)
    if (!frame) return null
    return verdictWith('startup-pulse', `t=${frame.tMs} ms com foreground ${frame.foreground} (esperado <= ${limit.toFixed(0)})`)
}

// Janela de repouso: t0 + 2 s ate t0 + 7 s.
function restWindow(frames, t0) {
    return frames.filter((frame) => frame.tMs >= t0 + STARTUP_WINDOW_MS && frame.tMs <= t0 + REST_WINDOW_END_MS)
}

function boxProblem(frames, reference, t0) {
    const rest = restWindow(frames, t0)
    if (rest.length === 0) return null
    const field = ['x', 'y', 'w', 'h'].find((key) => Math.abs(median(rest.map((item) => item[key])) - reference[key]) > BOX_TOLERANCE_PX)
    if (field === undefined) return null
    const measured = median(rest.map((item) => item[field]))
    return verdictWith('wrong-box', `mediana de ${field} ${measured} (esperado ${reference[field]} ± ${BOX_TOLERANCE_PX} px)`)
}

function sizeProblem(frames, reference, t0) {
    const rest = restWindow(frames, t0)
    if (rest.length === 0) return null
    const measured = median(rest.map((item) => item.foreground))
    if (Math.abs(measured - reference.foreground) <= reference.foreground * SIZE_TOLERANCE) return null
    return verdictWith('wrong-size', `mediana de foreground ${measured} (esperado ${reference.foreground} ± 2%)`)
}

// Quadro parado: idle = tudo a partir de t0 + 2 s; hover = antes de entrar ou depois de sair + 2,5 s; drag = antes do aperto - 600 ms ou apos soltar + 4 s.
function isParked(tMs, t0, expect, events) {
    if (tMs < t0 + STARTUP_WINDOW_MS) return false
    if (expect === 'hover') return tMs < events.enterMs || tMs > events.leaveMs + PARKED_AFTER_LEAVE_MS
    if (expect === 'drag') return tMs < events.pressMs - PARKED_BEFORE_PRESS_MS || tMs >= events.releaseMs + SETTLE_AFTER_RELEASE_MS
    return true
}

function unstableProblem(frames, reference, t0, { expect, events }) {
    for (const frame of frames) {
        if (!isParked(frame.tMs, t0, expect, events)) continue
        const settled = expect === 'drag' && frame.tMs >= events.releaseMs + SETTLE_AFTER_RELEASE_MS
        const landing = settled ? { x: reference.x + events.dx, y: reference.y + events.dy } : { x: reference.x, y: reference.y }
        const positionTolerance = settled ? LANDING_TOLERANCE_PX : BOX_TOLERANCE_PX
        const expected = { x: landing.x, y: landing.y, w: reference.w, h: reference.h, foreground: reference.foreground }
        const limits = { x: positionTolerance, y: positionTolerance, w: BOX_TOLERANCE_PX, h: BOX_TOLERANCE_PX, foreground: reference.foreground * SIZE_TOLERANCE }
        const field = Object.keys(expected).find((key) => Math.abs(frame[key] - expected[key]) > limits[key])
        if (field !== undefined) return verdictWith('unstable', `t=${frame.tMs} ms com ${field} ${frame[field]} (esperado ${expected[field]} ± ${limits[field]})`)
    }
    return null
}

function closeRun(pulses, run, minFrames) {
    if (run.length < minFrames) return
    pulses.push({
        startMs: run[0].tMs,
        endMs: run[run.length - 1].tMs,
        peakForeground: Math.max(...run.map((frame) => frame.foreground)),
    })
}

// Repouso do limiar: hover mede a mediana dos visiveis antes de entrar (sem eles, null). Sem restBeforeMs,
// p5 (posto mais proximo) dos visiveis: um quadro isolado ou 3% de quadros baixos nao viram repouso.
function restLevel(settled, { minForeground, restBeforeMs }) {
    const visible = settled.filter((frame) => frame.foreground >= minForeground)
    if (restBeforeMs !== undefined) {
        const before = visible.filter((frame) => frame.tMs < restBeforeMs)
        return before.length === 0 ? null : median(before.map((frame) => frame.foreground))
    }
    if (visible.length === 0) return null
    return nearestRank(visible.map((frame) => frame.foreground), 5)
}

function nearestRank(values, percent) {
    const sorted = [...values].sort((left, right) => left - right)
    const rank = Math.ceil((percent / 100) * sorted.length)
    return sorted[Math.max(rank, 1) - 1]
}

function median(values) {
    const sorted = [...values].sort((left, right) => left - right)
    const middle = Math.floor(sorted.length / 2)
    return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

const USAGE = 'uso: node tools/ci/hud-pulses.mjs <csv> [--expect hover --events \'{"enterMs":n,"leaveMs":n}\']'
const VALUE_FLAGS = ['--expect', '--events', '--rise-ratio', '--min-rest-width', '--max-rest-foreground', '--max-foreground', '--scene']
const REST_FROM_MS = 2000
const REST_UNTIL_MS = 7000

function parseArgs(argv) {
    const positional = []
    const flags = {}
    for (let index = 0; index < argv.length; index += 1) {
        if (!VALUE_FLAGS.includes(argv[index])) {
            positional.push(argv[index])
            continue
        }
        if (index + 1 >= argv.length) throw new Error(USAGE)
        flags[argv[index]] = argv[index + 1]
        index += 1
    }
    if (positional.length !== 1) throw new Error(USAGE)
    const [csv] = positional
    const tuning = {
        riseRatio: parseRiseRatio(flags['--rise-ratio']),
        minRestWidth: parsePositiveInteger('--min-rest-width', flags['--min-rest-width']),
        maxRestForeground: parsePositiveInteger('--max-rest-foreground', flags['--max-rest-foreground']),
        maxForeground: parsePositiveInteger('--max-foreground', flags['--max-foreground']),
        scene: flags['--scene'],
    }
    if (flags['--expect'] === undefined) {
        if (flags['--events'] !== undefined) throw new Error(USAGE)
        return { csv, expect: 'default', ...tuning }
    }
    if (flags['--expect'] === 'drag') return { csv, expect: 'drag', events: parseDragEvents(flags['--events']), ...tuning }
    if (flags['--expect'] !== 'hover') throw new Error(USAGE)
    return { csv, expect: 'hover', events: parseEvents(flags['--events']), ...tuning }
}

function parseRiseRatio(text) {
    if (text === undefined) return undefined
    const message = 'uso: --rise-ratio exige numero decimal maior que 1'
    if (!/^\d+(\.\d+)?$/.test(text)) throw new Error(message)
    const value = Number(text)
    if (!Number.isFinite(value) || value <= 1) throw new Error(message)
    return value
}

function parsePositiveInteger(flag, text) {
    if (text === undefined) return undefined
    if (!/^[1-9]\d*$/.test(text)) throw new Error(`uso: ${flag} exige inteiro positivo`)
    return Number(text)
}

function parseJsonEvents(text, message) {
    if (text === undefined) throw new Error(message)
    try {
        return JSON.parse(text) ?? {}
    } catch {
        throw new Error(message)
    }
}

function parseEvents(text) {
    const message = 'uso: --expect hover exige --events {"enterMs":n,"leaveMs":n}, em ms, com leaveMs >= enterMs'
    const { enterMs, leaveMs } = parseJsonEvents(text, message)
    if (!Number.isFinite(enterMs) || !Number.isFinite(leaveMs) || leaveMs < enterMs) throw new Error(message)
    return { enterMs, leaveMs }
}

function parseDragEvents(text) {
    const message = 'uso: --expect drag exige --events {"pressMs":n,"releaseMs":n,"dx":n,"dy":n}, em ms e px, com releaseMs >= pressMs'
    const { pressMs, releaseMs, dx, dy } = parseJsonEvents(text, message)
    if (![pressMs, releaseMs, dx, dy].every(Number.isFinite) || releaseMs < pressMs) throw new Error(message)
    return { pressMs, releaseMs, dx, dy }
}

// Largura do repouso: mediana de w dos quadros visiveis entre +2 s e +7 s da primeira aparicao (relativo, nao ao processo). Sem quadro nessa janela: null.
function restWidth(frames) {
    const first = frames.find((frame) => frame.foreground >= DEFAULTS.minForeground)
    if (!first) return null
    const inRest = frames.filter((frame) => frame.foreground >= DEFAULTS.minForeground
        && frame.tMs >= first.tMs + REST_FROM_MS && frame.tMs <= first.tMs + REST_UNTIL_MS)
    return inRest.length === 0 ? null : median(inRest.map((frame) => frame.w))
}

// Escala da cena (--min-rest-width): veredito proprio, antes de qualquer outro; null quando a cena confere.
function sceneVerdict(frames, minRestWidth) {
    if (minRestWidth === undefined) return null
    const width = restWidth(frames)
    if (width === null) return 'not-visible'
    return width < minRestWidth ? 'wrong-scene' : null
}

// Repouso aberto (--max-rest-foreground): repouso acima do maximo e open-at-rest (1), antes de qualquer veredito de pulso.
function isOpenAtRest(rest, maxRestForeground) {
    return maxRestForeground !== undefined && rest !== null && rest > maxRestForeground
}

// Arrasto: mediana dos visiveis antes do aperto (mesma janela de dragVerdict: 3 s antes de apertar).
function dragRestForeground(frames, pressMs) {
    const before = frames.filter((frame) => frame.tMs <= pressMs - 3000 && frame.foreground >= DEFAULTS.minForeground)
    return before.length === 0 ? null : median(before.map((frame) => frame.foreground))
}

function hoverExit(verdict) {
    return verdict === 'ok' ? EXIT.ok : verdict === 'not-visible' ? EXIT['not-visible'] : EXIT.pulsed
}

// Veredito e linhas de saida, sem imprimir: a cena (--scene) so roda quando este veredito e ok.
function verdictRun(frames, { expect, events, riseRatio, minRestWidth, maxRestForeground, maxForeground }) {
    const scene = sceneVerdict(frames, minRestWidth)
    if (scene !== null) return { code: EXIT[scene], lines: [scene] }
    const flashes = flashFrames(frames, maxForeground)
    if (flashes.length > 0) return { code: EXIT.flash, lines: ['flash', ...flashes.map((frame) => `quadro ${frame.tMs} ${frame.foreground}`)] }
    if (expect === 'drag') {
        const verdict = dragVerdict(frames, events)
        if (verdict !== 'not-visible' && isOpenAtRest(dragRestForeground(frames, events.pressMs), maxRestForeground)) {
            return { code: EXIT['open-at-rest'], lines: ['open-at-rest'] }
        }
        return { code: hoverExit(verdict), lines: [verdict] }
    }
    const restBeforeMs = expect === 'hover' ? events.enterMs : undefined
    const options = { ...(riseRatio === undefined ? {} : { riseRatio }), restBeforeMs }
    const result = detectPulses(frames, options)
    if (isOpenAtRest(result.restForeground, maxRestForeground)) return { code: EXIT['open-at-rest'], lines: ['open-at-rest'] }
    const pulseLines = result.pulses.map((pulse) => `pulso ${pulse.startMs}-${pulse.endMs} pico ${pulse.peakForeground}`)
    if (expect === 'hover') {
        const verdict = hoverVerdict(result, events)
        return { code: hoverExit(verdict), lines: [verdict, ...pulseLines] }
    }
    const verdict = verdictOf(result)
    return { code: EXIT[verdict], lines: [verdict, ...pulseLines] }
}

function main(argv) {
    const args = parseArgs(argv)
    if (args.scene !== undefined) sceneNamed(args.scene)
    const frames = parseFrames(readFileSync(args.csv, 'utf8'))
    const run = verdictRun(frames, args)
    const problem = run.code === EXIT.ok && args.scene !== undefined
        ? sceneProblem(frames, { scene: args.scene, expect: args.expect, events: args.events })
        : null
    if (problem === null) {
        run.lines.forEach((line) => console.log(line))
        return run.code
    }
    console.log(problem.verdict)
    console.log(`motivo: ${problem.motivo}`)
    return EXIT[problem.verdict]
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    try {
        process.exitCode = main(process.argv.slice(2))
    } catch (error) {
        console.error(error.message)
        process.exitCode = EXIT.usage
    }
}
