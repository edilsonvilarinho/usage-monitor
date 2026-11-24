import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { detectPulses, dragVerdict, hoverVerdict, parseFrames, verdictOf } from '../hud-pulses.mjs'

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

// Hover: o ponteiro entra em enterMs e sai em leaveMs; o pulso deve abrir ate 1,5 s depois de entrar e fechar ate 2,5 s depois de sair.
const HOVER = { enterMs: 4000, leaveMs: 6000 }
const hoverPulse = (fromMs, toMs) => series(12000, (t) => (t >= fromMs && t < toMs ? PULSE : HUD))

test('hover: abre 0,5 s depois de entrar e fecha 1 s depois de sair: ok', () => {
    assert.equal(hoverVerdict(detectPulses(hoverPulse(4500, 7000)), HOVER), 'ok')
})

test('hover: nenhum pulso: not-opened', () => {
    assert.equal(hoverVerdict(detectPulses(series(12000, () => HUD)), HOVER), 'not-opened')
})

test('hover: abre 2 s depois de entrar: opened-late', () => {
    assert.equal(hoverVerdict(detectPulses(hoverPulse(6000, 7000)), HOVER), 'opened-late')
})

test('hover: ainda aberto 2,95 s depois de sair: not-closed', () => {
    assert.equal(hoverVerdict(detectPulses(hoverPulse(4500, 9000)), HOVER), 'not-closed')
})

test('hover: aberto no ultimo quadro, sem tempo para fechar: not-closed', () => {
    assert.equal(hoverVerdict(detectPulses(hoverPulse(9500, 12001)), { enterMs: 9000, leaveMs: 11000 }), 'not-closed')
})

test('hover: dois pulsos, um deles depois de sair: extra-pulse', () => {
    const twoPulses = series(12000, (t) => ((t >= 4500 && t < 5000) || (t >= 8000 && t < 8500) ? PULSE : HUD))
    assert.equal(hoverVerdict(detectPulses(twoPulses), HOVER), 'extra-pulse')
})

test('hover: pulso antes de entrar no notch: extra-pulse', () => {
    assert.equal(hoverVerdict(detectPulses(hoverPulse(2000, 2500)), HOVER), 'extra-pulse')
})

test('hover: nunca visivel: not-visible', () => {
    assert.equal(hoverVerdict(detectPulses(series(12000, () => 0)), HOVER), 'not-visible')
})

test('CLI hover: 0 ok, 1 falha, 4 nunca visivel, 2 uso', () => {
    const events = JSON.stringify(HOVER)
    const ok = cli(writeCsv('hover-ok.csv', toCsv(hoverPulse(4500, 7000))), '--expect', 'hover', '--events', events)
    assert.equal(ok.code, 0)
    assert.equal(ok.stdout.split('\n')[0], 'ok')

    const late = cli(writeCsv('hover-late.csv', toCsv(hoverPulse(6000, 7000))), '--expect', 'hover', '--events', events)
    assert.equal(late.code, 1)
    assert.equal(late.stdout.split('\n')[0], 'opened-late')

    assert.equal(cli(writeCsv('hover-none.csv', toCsv(series(12000, () => 0))), '--expect', 'hover', '--events', events).code, 4)

    const csv = writeCsv('hover-usage.csv', toCsv(hoverPulse(4500, 7000)))
    assert.equal(cli(csv, '--expect', 'hover').code, 2)
    assert.equal(cli(csv, '--expect', 'hover', '--events', '{"enterMs":1}').code, 2)
    assert.equal(cli(csv, '--expect', 'hover', '--events', '{"enterMs":6000,"leaveMs":4000}').code, 2)
    assert.equal(cli(csv, '--expect', 'hover', '--events', 'nao e json').code, 2)
    assert.equal(cli(csv, '--expect', 'pulse', '--events', events).code, 2)
    assert.equal(cli(csv, '--events', events).code, 2)
})

// Arrasto: a caixa em repouso antes de apertar e 4 s depois de soltar; o centro tem de andar (dx, dy) ate tolerancePx.
const BOX = { x: 100, y: 50, w: 200, h: 80 }
const DRAG = { pressMs: 5000, releaseMs: 5500, dx: -200, dy: 120 }

// Quadros com a caixa dada por at(t); foreground 0 quando nada aparece.
function dragFrames(at, durationMs = 12000) {
    const frames = []
    for (let tMs = 0; tMs <= durationMs; tMs += 50) {
        const { x, y, foreground } = at(tMs)
        frames.push({ tMs, foreground, x, y, w: BOX.w, h: BOX.h })
    }
    return frames
}

// HUD em repouso, anda linearmente no arrasto e pousa em (landX, landY); durante o arrasto aparece com duringForeground.
function floatingDrag({ landX = BOX.x + DRAG.dx, landY = BOX.y + DRAG.dy, restForeground = HUD, duringForeground = HUD, openForMs = 0 } = {}) {
    return dragFrames((tMs) => {
        if (tMs < DRAG.pressMs) return { x: BOX.x, y: BOX.y, foreground: HUD }
        if (tMs < DRAG.releaseMs) {
            const progress = (tMs - DRAG.pressMs) / (DRAG.releaseMs - DRAG.pressMs)
            return { x: BOX.x + (landX - BOX.x) * progress, y: BOX.y + (landY - BOX.y) * progress, foreground: duringForeground }
        }
        const open = tMs < DRAG.releaseMs + openForMs
        return { x: landX, y: landY, foreground: open ? PULSE : restForeground }
    })
}

test('arrasto: a HUD anda (dx, dy) e termina recolhida: ok', () => {
    assert.equal(dragVerdict(floatingDrag(), DRAG), 'ok')
})

test('arrasto: 5 px em cada eixo (7 px na diagonal) ainda e ok', () => {
    assert.equal(dragVerdict(floatingDrag({ landX: BOX.x + DRAG.dx + 5, landY: BOX.y + DRAG.dy - 5 }), DRAG), 'ok')
})

test('arrasto: a caixa nao andou: not-moved', () => {
    assert.equal(dragVerdict(floatingDrag({ landX: BOX.x, landY: BOX.y }), DRAG), 'not-moved')
})

test('arrasto: andou so 5 px: not-moved', () => {
    assert.equal(dragVerdict(floatingDrag({ landX: BOX.x + 5, landY: BOX.y }), DRAG), 'not-moved')
})

test('arrasto: andou para o lado oposto: wrong-place', () => {
    assert.equal(dragVerdict(floatingDrag({ landX: BOX.x - DRAG.dx, landY: BOX.y + DRAG.dy }), DRAG), 'wrong-place')
})

test('arrasto: andou 9 px fora do destino: wrong-place', () => {
    assert.equal(dragVerdict(floatingDrag({ landX: BOX.x + DRAG.dx + 9, landY: BOX.y + DRAG.dy }), DRAG), 'wrong-place')
})

test('arrasto: HUD ainda aberta 4 s depois de soltar: not-closed', () => {
    assert.equal(dragVerdict(floatingDrag({ restForeground: PULSE }), DRAG), 'not-closed')
})

test('arrasto: repouso 11% acima do inicial: not-closed; 10%: ok', () => {
    assert.equal(dragVerdict(floatingDrag({ restForeground: 1110 }), DRAG), 'not-closed')
    assert.equal(dragVerdict(floatingDrag({ restForeground: 1100 }), DRAG), 'ok')
})

test('arrasto: aberta durante o arrasto e fechada 4 s depois de soltar: ok', () => {
    assert.equal(dragVerdict(floatingDrag({ duringForeground: PULSE, openForMs: 2000 }), DRAG), 'ok')
})

test('arrasto: nunca visivel depois de soltar: not-visible', () => {
    assert.equal(dragVerdict(floatingDrag({ restForeground: 0 }), DRAG), 'not-visible')
})

test('arrasto: nunca visivel: not-visible', () => {
    assert.equal(dragVerdict(dragFrames(() => ({ x: BOX.x, y: BOX.y, foreground: 0 })), DRAG), 'not-visible')
})

test('arrasto: tolerancePx por opcao: 9 px com tolerancePx 10 e ok', () => {
    assert.equal(dragVerdict(floatingDrag({ landX: BOX.x + DRAG.dx + 9 }), { ...DRAG, tolerancePx: 10 }), 'ok')
})

test('CLI drag: 0 ok, 1 falha, 4 nunca visivel, 2 uso', () => {
    const events = JSON.stringify(DRAG)
    const ok = cli(writeCsv('drag-ok.csv', toCsv(floatingDrag())), '--expect', 'drag', '--events', events)
    assert.equal(ok.code, 0)
    assert.equal(ok.stdout.split('\n')[0], 'ok')

    const still = cli(writeCsv('drag-still.csv', toCsv(floatingDrag({ landX: BOX.x, landY: BOX.y }))), '--expect', 'drag', '--events', events)
    assert.equal(still.code, 1)
    assert.equal(still.stdout.split('\n')[0], 'not-moved')

    assert.equal(cli(writeCsv('drag-none.csv', toCsv(floatingDrag({ restForeground: 0 }))), '--expect', 'drag', '--events', events).code, 4)

    const csv = writeCsv('drag-usage.csv', toCsv(floatingDrag()))
    assert.equal(cli(csv, '--expect', 'drag').code, 2)
    assert.equal(cli(csv, '--expect', 'drag', '--events', '{"pressMs":5000,"releaseMs":4000,"dx":-200,"dy":120}').code, 2)
    assert.equal(cli(csv, '--expect', 'drag', '--events', '{"pressMs":5000,"releaseMs":5500,"dx":-200}').code, 2)
    assert.equal(cli(csv, '--expect', 'drag', '--events', 'nao e json').code, 2)
})
