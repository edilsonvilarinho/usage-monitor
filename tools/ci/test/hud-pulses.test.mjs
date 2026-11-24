import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { detectPulses, parseFrames, verdictOf } from '../hud-pulses.mjs'

const SCRIPT = fileURLToPath(new URL('../hud-pulses.mjs', import.meta.url))
const HEADER = 't_ms,foreground,x,y,w,h'
const HUD = 1000
const PULSE = 3000
const dir = mkdtempSync(join(tmpdir(), 'hud-pulses-'))
after(() => rmSync(dir, { recursive: true, force: true }))

// Quadros a cada 50 ms de 0 ate durationMs; valueAt(t) e o foreground do quadro.
function series(durationMs, valueAt) {
    const frames = []
    for (let tMs = 0; tMs <= durationMs; tMs += 50) {
        frames.push({ tMs, foreground: valueAt(tMs), x: 10, y: 10, w: 200, h: 100 })
    }
    return frames
}

function toCsv(frames) {
    const rows = frames.map((frame) => `${frame.tMs},${frame.foreground},${frame.x},${frame.y},${frame.w},${frame.h}`)
    return [HEADER, ...rows].join('\n') + '\n'
}

function writeCsv(name, text) {
    const path = join(dir, name)
    writeFileSync(path, text)
    return path
}

function cli(...args) {
    try {
        const stdout = execFileSync(process.execPath, [SCRIPT, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
        return { code: 0, stdout }
    } catch (error) {
        return { code: error.status, stdout: error.stdout }
    }
}

const threePulses = series(12000, (t) => ([3000, 4000, 5000].some((start) => t >= start && t < start + 600) ? PULSE : HUD))

test('tres pulsos de 600 ms, 400 ms de intervalo, a partir de 3 s: 3 pulsos', () => {
    const result = detectPulses(threePulses)
    assert.deepEqual(result.pulses.map((pulse) => pulse.startMs), [3000, 4000, 5000])
    assert.equal(result.pulses[0].endMs, 3550)
    assert.equal(result.pulses[0].peakForeground, PULSE)
    assert.equal(verdictOf(result), 'pulsed')
})

test('ruido de +-10% em torno do repouso: ok', () => {
    const result = detectPulses(series(12000, (t) => HUD * (1 + 0.1 * Math.sin(t / 37))))
    assert.equal(result.appeared, true)
    assert.deepEqual(result.pulses, [])
    assert.equal(verdictOf(result), 'ok')
})

test('pico de um quadro nao e pulso', () => {
    assert.equal(verdictOf(detectPulses(series(12000, (t) => (t === 6000 ? 5000 : HUD)))), 'ok')
})

test('HUD constante e grande: ok', () => {
    assert.equal(verdictOf(detectPulses(series(12000, () => 50000))), 'ok')
})

test('primeira aparicao nao e pulso', () => {
    const result = detectPulses(series(12000, (t) => (t < 1000 ? 0 : HUD)))
    assert.equal(result.appeared, true)
    assert.equal(verdictOf(result), 'ok')
})

test('pulso aberto no ultimo quadro conta, com endMs no ultimo t', () => {
    const result = detectPulses(series(12000, (t) => (t >= 11000 ? PULSE : HUD)))
    assert.deepEqual(result.pulses, [{ startMs: 11000, endMs: 12000, peakForeground: PULSE }])
})

test('pulsos de ate 30% dos quadros nao movem o repouso', () => {
    const result = detectPulses(series(12000, (t) => (t >= 4000 && t < 7000 ? PULSE : HUD)))
    assert.equal(result.restForeground, HUD)
    assert.deepEqual(result.pulses.map((pulse) => pulse.startMs), [4000])
})

test('nunca visivel: not-visible', () => {
    assert.equal(verdictOf(detectPulses(series(12000, () => 0))), 'not-visible')
})

test('menos quadros depois do settle que minFrames: not-visible', () => {
    assert.equal(verdictOf(detectPulses(series(2000, () => HUD))), 'not-visible')
})

test('ruido de 100 px e some, ou fica: not-visible', () => {
    assert.equal(verdictOf(detectPulses(series(12000, (t) => (t < 3000 ? 100 : 0)))), 'not-visible')
    assert.equal(verdictOf(detectPulses(series(12000, () => 100))), 'not-visible')
    assert.equal(verdictOf(detectPulses(series(12000, (t) => (t === 0 ? 100 : 0)))), 'not-visible')
})

test('app cai no meio da gravacao e nao volta: not-visible', () => {
    assert.equal(verdictOf(detectPulses(series(12000, (t) => (t < 6000 ? HUD : 0)))), 'not-visible')
})

test('app cai no meio, depois de pulsos: not-visible', () => {
    const fallAfterPulse = (t) => {
        if (t >= 3000 && t < 3600) return PULSE
        return t < 6000 ? HUD : 0
    }
    assert.equal(verdictOf(detectPulses(series(12000, fallAfterPulse))), 'not-visible')
})

test('pixel solto antes do app nao adianta o settle', () => {
    const stray = (t) => {
        if (t === 0) return 100
        if (t < 5000) return 0
        if (t < 6500) return PULSE
        return HUD
    }
    const result = detectPulses(series(12000, stray))
    assert.equal(result.restForeground, HUD)
    assert.equal(verdictOf(result), 'ok')
})

test('parseFrames le o CSV e aceita so o cabecalho', () => {
    const frames = parseFrames(`${HEADER}\n0,1000,10,10,200,100\n50,1000,10,10,200,100\n`)
    assert.equal(frames.length, 2)
    assert.deepEqual(frames[1], { tMs: 50, foreground: 1000, x: 10, y: 10, w: 200, h: 100 })
    assert.deepEqual(parseFrames(`${HEADER}\n`), [])
})

test('CSV truncado ou malformado: erro', () => {
    assert.throws(() => parseFrames(`${HEADER}\n0,1000,10,10,200`), /linha 2/)
    assert.throws(() => parseFrames(`${HEADER}\n0,abc,10,10,200,100\n`), /linha 2/)
    assert.throws(() => parseFrames(`${HEADER}\n50,1,1,1,1,1\n0,1,1,1,1,1\n`), /linha 3/)
    assert.throws(() => parseFrames('t_ms,foreground\n0,1\n'), /cabecalho/)
    assert.throws(() => parseFrames(''), /cabecalho/)
})

test('CLI: codigos de saida 0, 1, 4 e 2 e veredito na primeira linha', () => {
    const ok = cli(writeCsv('ok.csv', toCsv(series(12000, () => HUD))))
    assert.equal(ok.code, 0)
    assert.equal(ok.stdout.split('\n')[0], 'ok')

    const pulsed = cli(writeCsv('pulsed.csv', toCsv(threePulses)))
    assert.equal(pulsed.code, 1)
    assert.equal(pulsed.stdout.split('\n')[0], 'pulsed')

    assert.equal(cli(writeCsv('none.csv', toCsv(series(12000, () => 0)))).code, 4)
    assert.equal(cli(writeCsv('bad.csv', `${HEADER}\n0,1000,10\n`)).code, 2)
    assert.equal(cli().code, 2)
    assert.equal(cli(join(dir, 'missing.csv')).code, 2)
})
