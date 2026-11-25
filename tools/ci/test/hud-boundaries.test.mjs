import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { detectPulses, hoverVerdict, parseFrames, sceneProblem, verdictOf } from '../hud-pulses.mjs'

// Limites exatos do detector de pulsos e dos vereditos de cena (sceneProblem e CLI).
const SCRIPT = fileURLToPath(new URL('../hud-pulses.mjs', import.meta.url))
const HEADER = 't_ms,foreground,x,y,w,h'
const SCENE = 'floating-idle'
// Medidas de floating-idle em tools/real-app/scenes.json.
const REF = { x: 481, y: 139, w: 62, h: 42, foreground: 1418 }
const HOVER = { enterMs: 3000, leaveMs: 6000 }
const DRAG = { pressMs: 10000, releaseMs: 11000, dx: -200, dy: 120 }
const DRAG_LANDED = { x: REF.x + DRAG.dx, y: REF.y + DRAG.dy }
const dir = mkdtempSync(join(tmpdir(), 'hud-boundaries-'))
after(() => rmSync(dir, { recursive: true, force: true }))
let counter = 0

const frame = (tMs, patch = {}) => ({ tMs, ...REF, ...patch })

// Grade de from a to em passos de step (inclusive).
function grid(from, to, step = 50) {
    const times = []
    for (let t = from; t <= to; t += step) times.push(t)
    return times
}

function framesAt(times, patch = () => ({})) {
    return times.map((tMs) => frame(tMs, patch(tMs)))
}

// Gravacao de 0 a 9000 ms a cada 50 ms, t0 = 0, 181 quadros.
const BASE = grid(0, 9000)
// Quadros extras (fora da grade) em ordem crescente.
const withExtra = (extra) => [...new Set([...BASE, ...extra])].sort((a, b) => a - b)

// Janela de repouso: 6 quadros em 2000..7000, 7001 bom, lacunas de 1000 ms (low-rate passa).
const WINDOW_TIMES = [0, ...grid(1, 150, 1), 1000, 2000, 3000, 4000, 5000, 6000, 7000, 7001, 8000, 9000]
const windowPatch = (bad, field, value) => (tMs) => (bad.includes(tMs) ? { [field]: value } : {})

// Grade ate 16000 ms: o arrasto precisa de quadros depois de release + 4000 (15000).
const DRAG_BASE = grid(0, 16000)
const withDragExtra = (extra) => [...new Set([...DRAG_BASE, ...extra])].sort((a, b) => a - b)

// Arrasto: caixa de repouso ate release + 4000 e deslocada depois.
function dragFrames(times, patch = () => ({})) {
    return framesAt(times, (tMs) => ({ ...(tMs >= DRAG.releaseMs + 4000 ? DRAG_LANDED : {}), ...patch(tMs) }))
}

function scene(frames, expect = 'default', events = undefined) {
    return sceneProblem(frames, { scene: SCENE, expect, events })
}

function csvOf(frames) {
    const rows = frames.map((f) => `${f.tMs},${f.foreground},${f.x},${f.y},${f.w},${f.h}`)
    return [HEADER, ...rows].join('\n') + '\n'
}

// Roda a CLI com o CSV dos quadros; devolve o codigo de saida e as linhas da saida.
function cli(frames, args = []) {
    counter += 1
    const file = join(dir, `case-${counter}.csv`)
    writeFileSync(file, csvOf(frames))
    const result = spawnSync(process.execPath, [SCRIPT, file, ...args], { encoding: 'utf8' })
    return { status: result.status, lines: result.stdout.split(/\r?\n/) }
}

const SCENE_ARGS = ['--scene', SCENE]
const pulse = (startMs, endMs) => ({ startMs, endMs, peakForeground: 0 })
const result = (overrides) => ({ appeared: true, restForeground: 1000, pulses: [], lastMs: 9000, ...overrides })

// ---------- parseFrames ----------

test('parseFrames aceita 6 campos e recusa 5 ou 7', () => {
    assert.equal(parseFrames(`${HEADER}\n0,1418,481,139,62,42\n50,1418,481,139,62,42\n`).length, 2)
    assert.throws(() => parseFrames(`${HEADER}\n0,1418,481,139,62\n`), { message: 'linha 2: esperados 6 campos, achei 5' })
    assert.throws(() => parseFrames(`${HEADER}\n0,1418,481,139,62,42,9\n`), { message: 'linha 2: esperados 6 campos, achei 7' })
})

test('parseFrames recusa t_ms que nao cresce, igual incluido', () => {
    const csv = `${HEADER}\n0,1418,481,139,62,42\n50,1418,481,139,62,42\n50,1418,481,139,62,42\n`
    assert.throws(() => parseFrames(csv), { message: 'linha 4: t_ms nao cresce' })
})

// ---------- detectPulses: presenca, settle, cauda, pulso ----------

test('detectPulses sem quadro visivel: appeared false', () => {
    const frames = framesAt(BASE, () => ({ foreground: 0 }))
    assert.deepStrictEqual(detectPulses(frames), { appeared: false, restForeground: null, pulses: [] })
})

test('detectPulses com menos de 2 quadros depois do settle: appeared true', () => {
    const frames = [frame(0), frame(1000, { foreground: 0 })]
    assert.deepStrictEqual(detectPulses(frames), { appeared: true, restForeground: null, pulses: [] })
})

test('detectPulses devolve lastMs do ultimo quadro', () => {
    assert.equal(detectPulses(framesAt(BASE)).lastMs, 9000)
})

test('cauda inclui o quadro em lastMs - 1000 (visivel) e ok', () => {
    const times = [...grid(0, 8000), ...grid(8050, 9000)]
    const frames = framesAt(times, (tMs) => ({ foreground: tMs <= 8000 ? REF.foreground : 0 }))
    assert.equal(verdictOf(detectPulses(frames)), 'ok')
})

test('cauda de 1000 ms toda invisivel e not-visible; quadro em 7999 fica fora', () => {
    const times = [...grid(0, 7950), 7999, ...grid(8000, 9000)]
    const frames = framesAt(times, (tMs) => ({ foreground: tMs === 7999 || tMs <= 7950 ? REF.foreground : 0 }))
    assert.equal(verdictOf(detectPulses(frames)), 'not-visible')
})

test('settle de 2000 ms: pulso que comeca em 2000 conta, em 1950 nao', () => {
    const frames = framesAt(BASE, (tMs) => ({ foreground: tMs === 2000 || tMs === 2050 ? 3000 : 1000 }))
    const found = detectPulses(frames).pulses
    assert.equal(found.length, 1)
    assert.equal(found[0].startMs, 2000)
    assert.equal(found[0].endMs, 2050)
})

test('pulso exige 2 quadros: um quadro nao vira pulso, dois viram', () => {
    assert.equal(verdictOf(detectPulses(framesAt(BASE, (t) => ({ foreground: t === 3000 ? 3000 : 1000 })))), 'ok')
    assert.equal(verdictOf(detectPulses(framesAt(BASE, (t) => ({ foreground: t === 3000 || t === 3050 ? 3000 : 1000 })))), 'pulsed')
})

test('limiar de subida 1.5 e estrito: 1500 nao pulsa, 1510 pulsa', () => {
    const at = (fg) => framesAt(BASE, (t) => ({ foreground: t === 3000 || t === 3050 ? fg : 1000 }))
    assert.equal(verdictOf(detectPulses(at(1500))), 'ok')
    assert.equal(verdictOf(detectPulses(at(1490))), 'ok')
    assert.equal(verdictOf(detectPulses(at(1510))), 'pulsed')
})

// ---------- detectPulses: repouso (p5 e mediana) ----------

test('p5 com 121 valores fora de ordem: posto 7 (nao 6 nem 8)', () => {
    const values = Array.from({ length: 121 }, (_, index) => 1001 + ((index * 37) % 121))
    const settled = grid(2000, 8000).map((tMs, index) => frame(tMs, { foreground: values[index] }))
    assert.equal(detectPulses([frame(0, { foreground: 1000 }), ...settled]).restForeground, 1007)
})

test('p5 com 20 valores: o menor (posto 1), nao o segundo', () => {
    const settled = grid(2000, 2950).map((tMs, index) => frame(tMs, { foreground: index === 0 ? 500 : 900 }))
    assert.equal(detectPulses([frame(0, { foreground: 900 }), ...settled]).restForeground, 500)
})

test('p5 com 1 valor visivel: o proprio valor', () => {
    const frames = [frame(0, { foreground: 1000 }), frame(2000, { foreground: 1000 }), frame(2050, { foreground: 0 })]
    assert.equal(detectPulses(frames).restForeground, 1000)
})

test('mediana de restBeforeMs: impar, par, um valor, nenhum valor', () => {
    const before = (values) => values.map((fg, index) => frame(2000 + index * 50, { foreground: fg }))
    const odd = [frame(0, { foreground: 1000 }), ...before([1300, 1000, 1400, 1100, 1200]), frame(3500, { foreground: 1000 })]
    assert.equal(detectPulses(odd, { restBeforeMs: 3000 }).restForeground, 1200)
    const even = [frame(0, { foreground: 1000 }), ...before([1400, 1000, 1200, 1100]), frame(3500, { foreground: 1000 })]
    assert.equal(detectPulses(even, { restBeforeMs: 3000 }).restForeground, 1150)
    const single = [frame(0, { foreground: 1000 }), frame(2000, { foreground: 1234 }), frame(2050, { foreground: 1234 }), frame(3500, { foreground: 1000 })]
    assert.equal(detectPulses(single, { restBeforeMs: 2010 }).restForeground, 1234)
    const none = [frame(0, { foreground: 1000 }), frame(2000, { foreground: 1000 }), frame(2050, { foreground: 1000 })]
    assert.deepStrictEqual(detectPulses(none, { restBeforeMs: 1500 }), { appeared: true, restForeground: null, pulses: [] })
})

test('restBeforeMs exclui o quadro exatamente no limite', () => {
    const frames = [frame(0, { foreground: 1000 }), frame(2000, { foreground: 1000 }), frame(2050, { foreground: 3000 }), frame(2100, { foreground: 1000 })]
    assert.equal(detectPulses(frames, { restBeforeMs: 2050 }).restForeground, 1000)
})

// ---------- hoverVerdict ----------

test('hoverVerdict: sem repouso ou sem aparicao e not-visible', () => {
    assert.equal(hoverVerdict(result({ restForeground: null }), HOVER), 'not-visible')
    assert.equal(hoverVerdict(result({ appeared: false }), HOVER), 'not-visible')
})

test('hoverVerdict: abertura em 1500 ms ok, em 1501 opened-late', () => {
    const at = (startMs) => result({ pulses: [pulse(startMs, 6100)] })
    assert.equal(hoverVerdict(at(4500), HOVER), 'ok')
    assert.equal(hoverVerdict(at(4501), HOVER), 'opened-late')
})

test('hoverVerdict: fechamento em leave + 2500 ok, em +2501 not-closed; fim no ultimo quadro nao conta', () => {
    assert.equal(hoverVerdict(result({ pulses: [pulse(4000, 8500)] }), HOVER), 'ok')
    assert.equal(hoverVerdict(result({ pulses: [pulse(4000, 8501)] }), HOVER), 'not-closed')
    assert.equal(hoverVerdict(result({ pulses: [pulse(4000, 8500)], lastMs: 8500 }), HOVER), 'not-closed')
})

test('hoverVerdict: pulso antes de entrar e extra-pulse; zero pulso e not-opened', () => {
    assert.equal(hoverVerdict(result({ pulses: [pulse(2999, 4000)] }), HOVER), 'extra-pulse')
    assert.equal(hoverVerdict(result({ pulses: [pulse(3000, 4000)] }), HOVER), 'ok')
    assert.equal(hoverVerdict(result({ pulses: [] }), HOVER), 'not-opened')
})

// ---------- sceneProblem: limites de tempo ----------

test('cena boa nao devolve problema; sem quadro visivel devolve null exato', () => {
    assert.equal(scene(framesAt(BASE)), null)
    assert.strictEqual(scene(framesAt(BASE, () => ({ foreground: 0 }))), null)
})

test('low-rate: 150 quadros depois de t0 passam, 149 reprovam', () => {
    const dense = (count) => framesAt([0, ...grid(1, count, 1)])
    assert.equal(scene(dense(150)), null)
    assert.deepStrictEqual(scene(dense(149)), { verdict: 'low-rate', motivo: '149 quadros depois de t0, esperado >= 150' })
})

test('low-rate: intervalo de 1000 ms passa, 1001 reprova', () => {
    const gapped = (gap) => framesAt([0, gap, ...grid(gap + 50, 9000)])
    assert.equal(scene(gapped(1000)), null)
    assert.deepStrictEqual(scene(gapped(1001)), { verdict: 'low-rate', motivo: 'intervalo de 1001 ms ate t=1001 ms, esperado <= 1000 ms' })
})

test('late-start: t0 de 30000 ms passa, 30001 reprova', () => {
    const late = (t0) => framesAt([...grid(0, t0 - 50), ...grid(t0, t0 + 9000)], (tMs) => (tMs < t0 ? { foreground: 0 } : {}))
    assert.equal(scene(late(30000)), null)
    assert.deepStrictEqual(scene(late(30001)), { verdict: 'late-start', motivo: 'primeiro quadro visivel em t=30001 ms (esperado <= 30000 ms)' })
})

test('precedencia: low-rate antes de late-start, late-start antes de vanished', () => {
    const shortLate = framesAt([...grid(0, 30000), ...grid(30001, 31000)], (tMs) => (tMs <= 30000 ? { foreground: 0 } : {}))
    assert.equal(scene(shortLate).verdict, 'low-rate')
    const lateVanished = framesAt(grid(0, 39000), (tMs) => (tMs < 30001 ? { foreground: 0 } : tMs === 31000 ? { foreground: 399 } : {}))
    assert.equal(scene(lateVanished).verdict, 'late-start')
})

// ---------- sceneProblem: vanished, startup ----------

test('vanished: 400 passa, 399 reprova com o motivo exato', () => {
    assert.equal(scene(framesAt(BASE, (tMs) => (tMs === 1000 ? { foreground: 400 } : {}))), null)
    assert.deepStrictEqual(scene(framesAt(BASE, (tMs) => (tMs === 1000 ? { foreground: 399 } : {}))),
        { verdict: 'vanished', motivo: 't=1000 ms com foreground 399 (esperado >= 400)' })
})

test('startup: quadro em t0 + 2000 reprova, em t0 + 2001 nao e startup (vira unstable)', () => {
    const at = (tMs) => framesAt(BASE, (t) => (t === tMs ? { foreground: 1500 } : {}))
    assert.deepStrictEqual(scene(at(2000)), { verdict: 'startup-pulse', motivo: 't=2000 ms com foreground 1500 (esperado <= 1498)' })
    assert.equal(scene(framesAt(withExtra([2001]), (t) => (t === 2001 ? { foreground: 1500 } : {}))).verdict, 'unstable')
    assert.equal(scene(at(0)).verdict, 'startup-pulse')
})

test('startup: limite 1427 x 1.05 e estrito (igual passa, mais de 0,5 reprova)', () => {
    const limit = 1427 * 1.05
    assert.equal(scene(framesAt(BASE, (t) => (t === 1000 ? { foreground: limit } : {}))), null)
    assert.equal(scene(framesAt(BASE, (t) => (t === 1000 ? { foreground: limit + 0.5 } : {}))).verdict, 'startup-pulse')
})

// ---------- sceneProblem: wrong-box, janela de repouso ----------

test('wrong-box: deslocamento de 2 px passa, 3 px reprova', () => {
    assert.equal(scene(framesAt(BASE, (t) => (t >= 2000 ? { x: REF.x + 2 } : {}))), null)
    assert.deepStrictEqual(scene(framesAt(BASE, (t) => (t >= 2000 ? { x: REF.x + 3 } : {}))),
        { verdict: 'wrong-box', motivo: 'mediana de x 484 (esperado 481 ± 2 px)' })
})

test('janela de repouso: 2000 e 7000 entram, 7001 nao (mediana de 3 ruins e 3 bons)', () => {
    const bad = [2000, 4000, 6000]
    assert.deepStrictEqual(scene(framesAt(WINDOW_TIMES, windowPatch(bad, 'x', REF.x + 10))),
        { verdict: 'wrong-box', motivo: 'mediana de x 486 (esperado 481 ± 2 px)' })
    assert.equal(scene(framesAt(WINDOW_TIMES, windowPatch([4000, 6000], 'x', REF.x + 10))).verdict, 'unstable')
})

test('janela de repouso: 7001 ruim com 7000 bom nao entra na mediana', () => {
    assert.equal(scene(framesAt(WINDOW_TIMES, windowPatch([7001], 'x', REF.x + 10))).verdict, 'unstable')
})

// ---------- sceneProblem: wrong-size ----------

test('wrong-size: 2% exatos passam, 2% + 0,5 reprova', () => {
    const limit = REF.foreground * 0.02
    assert.equal(scene(framesAt(BASE, (t) => (t >= 2000 ? { foreground: REF.foreground + limit } : {}))), null)
    assert.deepStrictEqual(scene(framesAt(BASE, (t) => (t >= 2000 ? { foreground: REF.foreground + limit + 0.5 } : {}))).verdict, 'wrong-size')
})

test('wrong-size: 28 passa, 29 reprova (tolerancia 28,36)', () => {
    assert.equal(scene(framesAt(BASE, (t) => (t >= 2000 ? { foreground: REF.foreground + 28 } : {}))), null)
    assert.deepStrictEqual(scene(framesAt(BASE, (t) => (t >= 2000 ? { foreground: REF.foreground + 29 } : {}))),
        { verdict: 'wrong-size', motivo: 'mediana de foreground 1447 (esperado 1418 ± 2%)' })
})

// ---------- sceneProblem: unstable, quadro parado ----------

test('unstable idle: 1999 nao e parado, 2000 e parado', () => {
    assert.equal(scene(framesAt(BASE, (t) => (t === 1999 ? { x: REF.x + 3 } : {}))), null)
    const bad = framesAt(withExtra([1999]), (t) => (t === 2000 ? { x: REF.x + 3 } : {}))
    assert.deepStrictEqual(scene(bad), { verdict: 'unstable', motivo: 't=2000 ms com x 484 (esperado 481 ± 2)' })
})

test('unstable idle: 2 px passa, 3 px reprova no quadro parado', () => {
    assert.equal(scene(framesAt(BASE, (t) => (t === 2000 ? { x: REF.x + 2 } : {}))), null)
    assert.equal(scene(framesAt(BASE, (t) => (t === 2000 ? { x: REF.x + 3 } : {}))).verdict, 'unstable')
})

test('unstable hover: 2999 parado, 3000 em diante nao; 8500 nao, 8501 parado', () => {
    const hover = (extra) => framesAt(withExtra([2999, 8501]), (t) => (t === extra ? { x: REF.x + 3 } : {}))
    assert.equal(scene(hover(3000), 'hover', HOVER), null)
    assert.equal(scene(hover(2999), 'hover', HOVER).verdict, 'unstable')
    assert.equal(scene(framesAt(BASE, (t) => (t === 8500 ? { x: REF.x + 3 } : {})), 'hover', HOVER), null)
    assert.equal(scene(hover(8501), 'hover', HOVER).verdict, 'unstable')
})

test('unstable drag: 9399 parado, 9400 nao; 14999 nao; 15000 em diante deslocado', () => {
    const drag = (extra, patch) => dragFrames(withDragExtra([9399, 9400, 14999, 15000]), (t) => (t === extra ? patch : {}))
    assert.equal(scene(drag(9400, { x: REF.x + 3 }), 'drag', DRAG), null)
    assert.equal(scene(drag(9399, { x: REF.x + 3 }), 'drag', DRAG).verdict, 'unstable')
    assert.equal(scene(drag(14999, { x: REF.x + 3 }), 'drag', DRAG), null)
    assert.deepStrictEqual(scene(drag(15000, { x: REF.x, y: REF.y }), 'drag', DRAG),
        { verdict: 'unstable', motivo: 't=15000 ms com x 481 (esperado 281 ± 8)' })
})

test('unstable drag: deslocamento de pouso de 8 px passa, 9 px reprova', () => {
    const landed = (dx) => dragFrames(withDragExtra([15000]), (t) => (t === 15000 ? { x: DRAG_LANDED.x + dx } : {}))
    assert.equal(scene(landed(8), 'drag', DRAG), null)
    assert.equal(scene(landed(9), 'drag', DRAG).verdict, 'unstable')
})

// ---------- CLI: codigo de saida de cada veredito ----------

test('CLI vanished, startup-pulse, wrong-box, wrong-size, unstable saem com 1 e motivo', () => {
    const cases = [
        ['vanished', framesAt(BASE, (t) => (t === 1000 ? { foreground: 399 } : {}))],
        ['startup-pulse', framesAt(BASE, (t) => (t === 1000 ? { foreground: 1500 } : {}))],
        ['wrong-box', framesAt(BASE, () => ({ x: REF.x + 10 }))],
        ['wrong-size', framesAt(BASE, () => ({ foreground: 1447 }))],
        ['unstable', framesAt(BASE, (t) => (t === 2000 ? { x: REF.x + 3 } : {}))],
    ]
    for (const [verdict, frames] of cases) {
        const run = cli(frames, SCENE_ARGS)
        assert.equal(run.status, 1, verdict)
        assert.equal(run.lines[0], verdict)
        assert.match(run.lines[1], /^motivo: /, verdict)
    }
})

test('CLI late-start sai com 1, low-rate com 6', () => {
    const late = framesAt([...grid(0, 30000), ...grid(30001, 39001)], (t) => (t <= 30000 ? { foreground: 0 } : {}))
    assert.equal(cli(late, SCENE_ARGS).status, 1)
    assert.equal(cli(late, SCENE_ARGS).lines[0], 'late-start')
    const short = framesAt(grid(0, 2500))
    const run = cli(short, SCENE_ARGS)
    assert.equal(run.status, 6)
    assert.equal(run.lines[0], 'low-rate')
})

test('CLI sem cena: pulsed e open-at-rest saem com 1', () => {
    const pulsed = cli(framesAt(BASE, (t) => (t === 5000 || t === 5050 ? { foreground: 3000 } : {})))
    assert.equal(pulsed.status, 1)
    assert.equal(pulsed.lines[0], 'pulsed')
    assert.equal(pulsed.lines[1], 'pulso 5000-5050 pico 3000')
    const rest = cli(framesAt(BASE), ['--max-rest-foreground', '1000'])
    assert.equal(rest.status, 1)
    assert.equal(rest.lines[0], 'open-at-rest')
})

test('CLI flash: 1500 passa, 1600 sai com 1 e lista o quadro', () => {
    assert.equal(cli(framesAt(BASE, (t) => (t === 3000 ? { foreground: 1500 } : {})), ['--max-foreground', '1500']).status, 0)
    const flash = cli(framesAt(BASE, (t) => (t === 3000 ? { foreground: 1600 } : {})), ['--max-foreground', '1500'])
    assert.equal(flash.status, 1)
    assert.deepStrictEqual(flash.lines.slice(0, 2), ['flash', 'quadro 3000 1600'])
})

test('CLI foreground 400 e visivel (ok), 399 nao (not-visible, 4)', () => {
    assert.equal(cli(framesAt(BASE, () => ({ foreground: 400 }))).status, 0)
    const hidden = cli(framesAt(BASE, () => ({ foreground: 399 })))
    assert.equal(hidden.status, 4)
    assert.equal(hidden.lines[0], 'not-visible')
})

test('CLI --min-rest-width: 62 reprova (mediana 61), 61 passa; 400 bom na janela', () => {
    const narrow = framesAt(WINDOW_TIMES, windowPatch([2000, 4000, 6000], 'w', 60))
    const at62 = cli(narrow, ['--min-rest-width', '62'])
    assert.equal(at62.status, 5)
    assert.equal(at62.lines[0], 'wrong-scene')
    assert.equal(cli(narrow, ['--min-rest-width', '61']).status, 0)
    assert.equal(cli(framesAt(BASE, () => ({ foreground: 400 })), ['--min-rest-width', '1']).status, 0)
})

// ---------- CLI: arrasto, janela antes do aperto (linha 377) ----------

const DRAG_ARGS = ['--expect', 'drag', '--events', JSON.stringify(DRAG)]

// Antes de 7000 invisivel; 7000 com restAt7000; 7050..14950 visivel 1000; depois de 15000 em afterFg, deslocado.
function dragCli(restAt7000, afterFg) {
    const times = grid(0, 20000)
    return dragFrames(times, (t) => {
        if (t < 7000) return { foreground: 0 }
        if (t === 7000) return { foreground: restAt7000 }
        if (t >= 15000) return { foreground: afterFg }
        return { foreground: 1000 }
    })
}

test('drag: repouso antes de 7000 (incluido) acima do maximo sai com open-at-rest', () => {
    const run = cli(dragCli(3000, 3000), [...DRAG_ARGS, '--max-rest-foreground', '1000'])
    assert.equal(run.status, 1)
    assert.equal(run.lines[0], 'open-at-rest')
    assert.equal(cli(dragCli(3000, 3000), DRAG_ARGS).status, 0)
})

test('drag: quadro em 7000 com foreground 400 entra (>=), 400 e visivel', () => {
    const run = cli(dragCli(400, 400), [...DRAG_ARGS, '--max-rest-foreground', '300'])
    assert.equal(run.status, 1)
    assert.equal(run.lines[0], 'open-at-rest')
})

// ---------- estado do quadro em cena com t0 tardio ----------

test('t0 tardio: escala e caixa ficam nulas sem quebrar o veredito', () => {
    const late = framesAt([0, ...grid(1, 150, 1)])
    assert.equal(scene(late), null)
    const run = cli(late, SCENE_ARGS)
    assert.equal(run.status, 4)
    assert.equal(run.lines[0], 'not-visible')
})
