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
const OPEN = 13000
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

// Escala da cena (--min-rest-width): mediana de w entre +2 s e +7 s da primeira aparicao.
const SCENE = ['--min-rest-width', '300']
const withWidth = (frames, widthAt) => frames.map((frame) => ({ ...frame, w: widthAt(frame.tMs) }))
const hoverAt = (fromMs, toMs, value) => series(12000, (t) => (t >= fromMs && t < toMs ? value : HUD))

test('min-rest-width: repouso estreito e wrong-scene no padrao, no hover e no arrasto, com codigo 5', () => {
    const narrowHud = withWidth(series(12000, () => HUD), () => 100)
    const byDefault = cli(writeCsv('scene-default.csv', toCsv(narrowHud)), ...SCENE)
    assert.equal(byDefault.code, 5)
    assert.equal(byDefault.stdout.split('\n')[0], 'wrong-scene')

    const narrowHover = withWidth(hoverPulse(4500, 7000), () => 100)
    const hover = cli(writeCsv('scene-hover.csv', toCsv(narrowHover)), '--expect', 'hover', '--events', JSON.stringify(HOVER), ...SCENE)
    assert.equal(hover.code, 5)
    assert.equal(hover.stdout.split('\n')[0], 'wrong-scene')

    const narrowDrag = withWidth(floatingDrag(), () => 100)
    const drag = cli(writeCsv('scene-drag.csv', toCsv(narrowDrag)), '--expect', 'drag', '--events', JSON.stringify(DRAG), ...SCENE)
    assert.equal(drag.code, 5)
    assert.equal(drag.stdout.split('\n')[0], 'wrong-scene')
})

test('min-rest-width: largura igual ao minimo passa', () => {
    const exact = cli(writeCsv('scene-exact.csv', toCsv(withWidth(series(12000, () => HUD), () => 300))), ...SCENE)
    assert.equal(exact.code, 0)
    assert.equal(exact.stdout.split('\n')[0], 'ok')
})

test('min-rest-width: quadros depois de +7 s da primeira aparicao nao entram na mediana', () => {
    // 30 s, primeira aparicao em 0: 101 quadros na janela (100 px) e 460 depois de 7 s (300 px). Se o corte falhasse, a mediana seria 300.
    const wideAfter = withWidth(series(30000, () => HUD), (tMs) => (tMs > 7000 ? 300 : 100))
    const run = cli(writeCsv('scene-after.csv', toCsv(wideAfter)), ...SCENE)
    assert.equal(run.code, 5)
    assert.equal(run.stdout.split('\n')[0], 'wrong-scene')
})

test('min-rest-width: sem quadro visivel na janela de +2 s a +7 s da primeira aparicao: not-visible, codigo 4', () => {
    // Primeira aparicao em 10 s; a serie termina em 11 s, antes de 12 s (+2 s). Janela sem quadro.
    const lateStart = withWidth(series(11000, (t) => (t >= 10000 ? HUD : 0)), () => 300)
    const run = cli(writeCsv('scene-late-start.csv', toCsv(lateStart)), ...SCENE)
    assert.equal(run.code, 4)
    assert.equal(run.stdout.split('\n')[0], 'not-visible')
})

// Janela do repouso relativa a primeira aparicao: [+2 s, +7 s]. Quadro de 50 ms mais proximo de 13,236 s.
const FIRST_VISIBLE_MS = 13250
const appearsLate = (widthAt) => withWidth(series(30000, (t) => (t >= FIRST_VISIBLE_MS ? HUD : 0)), widthAt)

test('min-rest-width: HUD aparece em 13,25 s com 357 px: ok (janela relativa, nao fixa)', () => {
    const run = cli(writeCsv('scene-late-first.csv', toCsv(appearsLate(() => 357))), ...SCENE)
    assert.equal(run.code, 0)
    assert.equal(run.stdout.split('\n')[0], 'ok')
})

test('min-rest-width: HUD aparece em 13,25 s com 100 px: wrong-scene dentro de +2 s a +7 s', () => {
    const run = cli(writeCsv('scene-late-narrow.csv', toCsv(appearsLate(() => 100))), ...SCENE)
    assert.equal(run.code, 5)
    assert.equal(run.stdout.split('\n')[0], 'wrong-scene')
})

test('min-rest-width: quadros depois de +7 s da primeira aparicao nao entram na mediana', () => {
    // Janela 15,25 s a 20,25 s: 300 px. Depois de 20,25 s: 100 px (195 quadros). Corte fixo ou sem teto daria wrong-scene.
    const wideWindow = appearsLate((tMs) => (tMs >= FIRST_VISIBLE_MS + 2000 && tMs <= FIRST_VISIBLE_MS + 7000 ? 300 : 100))
    const run = cli(writeCsv('scene-relative-window.csv', toCsv(wideWindow)), ...SCENE)
    assert.equal(run.code, 0)
    assert.equal(run.stdout.split('\n')[0], 'ok')
})

test('rise-ratio: pico de 1,4x do repouso e not-opened no padrao e ok com 1,25', () => {
    const csv = writeCsv('rise-14.csv', toCsv(hoverAt(4500, 7000, 1400)))
    const events = JSON.stringify(HOVER)
    const byDefault = cli(csv, '--expect', 'hover', '--events', events)
    assert.equal(byDefault.code, 1)
    assert.equal(byDefault.stdout.split('\n')[0], 'not-opened')

    const tuned = cli(csv, '--expect', 'hover', '--events', events, '--rise-ratio', '1.25')
    assert.equal(tuned.code, 0)
    assert.equal(tuned.stdout.split('\n')[0], 'ok')
})

test('rise-ratio: subida de 1,13x e not-opened mesmo com 1,25', () => {
    const csv = writeCsv('rise-113.csv', toCsv(hoverAt(4500, 7000, 1130)))
    const run = cli(csv, '--expect', 'hover', '--events', JSON.stringify(HOVER), '--rise-ratio', '1.25')
    assert.equal(run.code, 1)
    assert.equal(run.stdout.split('\n')[0], 'not-opened')
})

test('rise-ratio e min-rest-width validos: aceitos com qualquer --expect', () => {
    const csv = writeCsv('flags-valid.csv', toCsv(withWidth(series(12000, () => HUD), () => 300)))
    const byDefault = cli(csv, '--rise-ratio', '1.25', '--min-rest-width', '300')
    assert.equal(byDefault.code, 0)
    assert.equal(byDefault.stdout.split('\n')[0], 'ok')
    const hover = cli(csv, '--expect', 'hover', '--events', JSON.stringify(HOVER), '--rise-ratio', '1.25', '--min-rest-width', '300')
    assert.equal(hover.code, 1)
    assert.equal(hover.stdout.split('\n')[0], 'not-opened')
})

test('rise-ratio e min-rest-width invalidos: uso, codigo 2', () => {
    const csv = writeCsv('flags-invalid.csv', toCsv(series(12000, () => HUD)))
    for (const value of ['1', '0.5', 'abc', '', 'Infinity']) {
        assert.equal(cli(csv, '--rise-ratio', value).code, 2, `--rise-ratio "${value}"`)
    }
    for (const value of ['0', '-5', '1.5', '1e3', 'abc', '']) {
        assert.equal(cli(csv, '--min-rest-width', value).code, 2, `--min-rest-width "${value}"`)
    }
    assert.equal(cli(csv, '--rise-ratio').code, 2)
    assert.equal(cli(csv, '--min-rest-width').code, 2)
})

test('repouso do hover vem de antes de entrar: painel que nunca fecha e not-closed, nao not-opened', () => {
    const csv = writeCsv('hover-never-closes.csv', toCsv(series(12000, (tMs) => (tMs < 3500 ? HUD : OPEN))))
    const run = cli(csv, '--expect', 'hover', '--events', '{"enterMs":3000,"leaveMs":6000}')
    assert.equal(run.code, 1)
    assert.equal(run.stdout.split('\n')[0], 'not-closed')
})

test('repouso antes da entrada, unidade: detectPulses usa a mediana dos visiveis antes de restBeforeMs', () => {
    const frames = series(12000, (tMs) => (tMs < 3500 ? HUD : OPEN))
    assert.equal(detectPulses(frames, { restBeforeMs: 3000 }).restForeground, HUD)
})

test('idle que abre em 9 s e fica aberto ate o fim: pulsed', () => {
    const csv = writeCsv('idle-opens-late.csv', toCsv(series(12000, (tMs) => (tMs < 9000 ? HUD : OPEN))))
    const run = cli(csv)
    assert.equal(run.code, 1)
    assert.equal(run.stdout.split('\n')[0], 'pulsed')
})

test('idle aberto desde o primeiro quadro: ok sem flag, open-at-rest com --max-rest-foreground', () => {
    const csv = writeCsv('idle-open-all.csv', toCsv(series(12000, () => OPEN)))
    const plain = cli(csv)
    assert.equal(plain.code, 0)
    assert.equal(plain.stdout.split('\n')[0], 'ok')
    const capped = cli(csv, '--max-rest-foreground', '2100')
    assert.equal(capped.code, 1)
    assert.equal(capped.stdout.split('\n')[0], 'open-at-rest')
})

test('--max-rest-foreground: repouso igual ao maximo passa', () => {
    const csv = writeCsv('rest-at-max.csv', toCsv(series(12000, () => 2100)))
    const run = cli(csv, '--max-rest-foreground', '2100')
    assert.equal(run.code, 0)
    assert.equal(run.stdout.split('\n')[0], 'ok')
})

test('--max-rest-foreground: hover e drag com repouso aberto dao open-at-rest', () => {
    const csv = writeCsv('open-everywhere.csv', toCsv(series(12000, () => OPEN)))
    const hover = cli(csv, '--expect', 'hover', '--events', '{"enterMs":4000,"leaveMs":7000}', '--max-rest-foreground', '2100')
    assert.equal(hover.code, 1)
    assert.equal(hover.stdout.split('\n')[0], 'open-at-rest')
    const drag = cli(csv, '--expect', 'drag', '--events', '{"pressMs":6000,"releaseMs":8000,"dx":0,"dy":0}', '--max-rest-foreground', '2100')
    assert.equal(drag.code, 1)
    assert.equal(drag.stdout.split('\n')[0], 'open-at-rest')
})

test('limiar de repouso e p5: 3% de quadros baixos nao viram repouso', () => {
    const csv = writeCsv('low-3pct.csv', toCsv(series(12000, (tMs) => ((tMs / 50) % 33 === 0 ? 400 : HUD))))
    const run = cli(csv)
    assert.equal(run.code, 0)
    assert.equal(run.stdout.split('\n')[0], 'ok')
})

test('--max-rest-foreground invalido: uso, codigo 2', () => {
    const csv = writeCsv('max-rest-invalid.csv', toCsv(series(12000, () => HUD)))
    for (const value of ['0', '-5', '1.5', 'abc', '']) {
        assert.equal(cli(csv, '--max-rest-foreground', value).code, 2, `--max-rest-foreground "${value}"`)
    }
    assert.equal(cli(csv, '--max-rest-foreground').code, 2)
})
