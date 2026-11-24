#!/usr/bin/env node
// Detector de pulsos da HUD (A02 do plano #652). Entrada: CSV de quadros da
// captura (tools/real-app/record.ps1), um quadro a cada ~50 ms.
//
// Uso: node tools/ci/hud-pulses.mjs <csv> [--expect hover --events '{"enterMs":n,"leaveMs":n}' | --expect drag --events '{"pressMs":n,"releaseMs":n,"dx":n,"dy":n}']
// Saida: veredito na primeira linha, depois um pulso por linha. Padrao: ok, pulsed, not-visible (0, 1, 4).
// Hover: ok (0), not-opened, opened-late, not-closed, extra-pulse (1), not-visible (4). Drag: ok (0), not-moved, wrong-place, not-closed (1), not-visible (4). Uso ou CSV invalido: 2.
import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const HEADER = 't_ms,foreground,x,y,w,h'
const DEFAULTS = { settleMs: 2000, riseRatio: 1.5, minFrames: 2, minForeground: 400, tailMs: 1000 }
const EXIT = { ok: 0, pulsed: 1, usage: 2, 'not-visible': 4 }

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
// minForeground, ou nada visivel nos ultimos tailMs, e not-visible.
export function detectPulses(frames, options = {}) {
    const { settleMs, riseRatio, minFrames, minForeground, tailMs } = { ...DEFAULTS, ...options }
    const notVisible = (appeared) => ({ appeared, restForeground: null, pulses: [] })
    const first = frames.find((frame) => frame.foreground >= minForeground)
    if (!first) return notVisible(false)
    const settled = frames.filter((frame) => frame.tMs >= first.tMs + settleMs)
    if (settled.length < minFrames) return notVisible(true)
    const lastMs = frames[frames.length - 1].tMs
    if (frames.filter((frame) => frame.tMs >= lastMs - tailMs).every((frame) => frame.foreground < minForeground)) {
        return notVisible(true)
    }

    const restForeground = median(settled.map((frame) => frame.foreground))
    if (restForeground < minForeground) return notVisible(true)
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

function closeRun(pulses, run, minFrames) {
    if (run.length < minFrames) return
    pulses.push({
        startMs: run[0].tMs,
        endMs: run[run.length - 1].tMs,
        peakForeground: Math.max(...run.map((frame) => frame.foreground)),
    })
}

function median(values) {
    const sorted = [...values].sort((left, right) => left - right)
    const middle = Math.floor(sorted.length / 2)
    return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

const USAGE = 'uso: node tools/ci/hud-pulses.mjs <csv> [--expect hover --events \'{"enterMs":n,"leaveMs":n}\']'

function parseArgs(argv) {
    const positional = []
    const flags = {}
    for (let index = 0; index < argv.length; index += 1) {
        if (argv[index] !== '--expect' && argv[index] !== '--events') {
            positional.push(argv[index])
            continue
        }
        if (index + 1 >= argv.length) throw new Error(USAGE)
        flags[argv[index]] = argv[index + 1]
        index += 1
    }
    if (positional.length !== 1) throw new Error(USAGE)
    const [csv] = positional
    if (flags['--expect'] === undefined) {
        if (flags['--events'] !== undefined) throw new Error(USAGE)
        return { csv, expect: 'default' }
    }
    if (flags['--expect'] === 'drag') return { csv, expect: 'drag', events: parseDragEvents(flags['--events']) }
    if (flags['--expect'] !== 'hover') throw new Error(USAGE)
    return { csv, expect: 'hover', events: parseEvents(flags['--events']) }
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

function printVerdict(verdict, result) {
    console.log(verdict)
    result.pulses.forEach((pulse) => console.log(`pulso ${pulse.startMs}-${pulse.endMs} pico ${pulse.peakForeground}`))
}

function main(argv) {
    const { csv, expect, events } = parseArgs(argv)
    const frames = parseFrames(readFileSync(csv, 'utf8'))
    if (expect === 'drag') {
        const verdict = dragVerdict(frames, events)
        console.log(verdict)
        return verdict === 'ok' ? EXIT.ok : verdict === 'not-visible' ? EXIT['not-visible'] : EXIT.pulsed
    }
    const result = detectPulses(frames)
    if (expect === 'hover') {
        const verdict = hoverVerdict(result, events)
        printVerdict(verdict, result)
        return verdict === 'ok' ? EXIT.ok : verdict === 'not-visible' ? EXIT['not-visible'] : EXIT.pulsed
    }
    const verdict = verdictOf(result)
    printVerdict(verdict, result)
    return EXIT[verdict]
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
    try {
        process.exitCode = main(process.argv.slice(2))
    } catch (error) {
        console.error(error.message)
        process.exitCode = EXIT.usage
    }
}
