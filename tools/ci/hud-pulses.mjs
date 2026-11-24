#!/usr/bin/env node
// Detector de pulsos da HUD (A02 do plano #652). Entrada: CSV de quadros da
// captura (tools/real-app/record.ps1), um quadro a cada ~50 ms.
//
// Uso:
//   node tools/ci/hud-pulses.mjs <csv>
//
// Saida: a primeira linha e o veredito (ok, pulsed, not-visible); depois, um
// pulso por linha. Codigos: 0 ok, 1 pulsed, 4 not-visible, 2 uso ou CSV invalido.
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
    return { appeared: true, restForeground, pulses }
}

export function verdictOf(result) {
    if (!result.appeared || result.restForeground === null) return 'not-visible'
    return result.pulses.length > 0 ? 'pulsed' : 'ok'
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

function main(argv) {
    if (argv.length !== 1) throw new Error('uso: node tools/ci/hud-pulses.mjs <csv>')
    const result = detectPulses(parseFrames(readFileSync(argv[0], 'utf8')))
    const verdict = verdictOf(result)
    console.log(verdict)
    result.pulses.forEach((pulse) => console.log(`pulso ${pulse.startMs}-${pulse.endMs} pico ${pulse.peakForeground}`))
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
