import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildTimeline, RELEASE_PACING } from '../timeline.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, '..', '..', 'assets', 'video.html'), 'utf8')

// As funções de movimento ficam entre "// motion:begin" e "// motion:end" em video.html; o teste carrega esse bloco.
function motion() {
    const block = source.match(/\/\/ motion:begin\r?\n([\s\S]*?)\/\/ motion:end/)
    assert.ok(block, 'video.html tem o bloco motion:begin / motion:end')
    return new Function(`${block[1]}\nreturn { clamp, easeInOut, cameraRectAt, MAX_ZOOM, focusOpacity, TRANSITION, transitionAt, transitionPhases }`)()
}

const keys = [
    { at: 0, rect: [0, 0, 540, 960] },
    { at: 2, rect: [100, 200, 200, 400] },
]

test('cameraRectAt: nas chaves devolve o retângulo da chave; depois da última segura a última', () => {
    const { cameraRectAt } = motion()
    assert.deepEqual(cameraRectAt(keys, 0), [0, 0, 540, 960])
    assert.deepEqual(cameraRectAt(keys, 2), [100, 200, 200, 400])
    assert.deepEqual(cameraRectAt(keys, 5), [100, 200, 200, 400])
    assert.deepEqual(cameraRectAt(keys, -1), [0, 0, 540, 960])
})

test('cameraRectAt: entre chaves interpola com ease-in-out cúbico (no meio, o ponto médio; no quarto, 6,25%)', () => {
    const { cameraRectAt } = motion()
    assert.deepEqual(cameraRectAt(keys, 1), [50, 100, 370, 680])
    const quarter = cameraRectAt(keys, 0.5)
    assert.ok(Math.abs(quarter[0] - 6.25) < 1e-9, `x em 0,5 s = ${quarter[0]}`)
})

test('cameraRectAt: sem overshoot (o retângulo fica entre as chaves) e ordem das chaves não importa', () => {
    const { cameraRectAt } = motion()
    for (let t = 0; t <= 2; t += 0.01) {
        const [x, , width] = cameraRectAt(keys, t)
        assert.ok(x >= 0 && x <= 100, `x=${x} em t=${t}`)
        assert.ok(width >= 200 && width <= 540, `largura=${width} em t=${t}`)
    }
    assert.deepEqual(cameraRectAt([...keys].reverse(), 1), cameraRectAt(keys, 1))
    assert.equal(cameraRectAt([], 1), null)
})

test('cameraRectAt: com a captura, o zoom passa de no máximo 1,35x e o retângulo fica dentro dela', () => {
    const { cameraRectAt, MAX_ZOOM } = motion()
    const capture = [100, 50, 2700, 1644]
    assert.equal(MAX_ZOOM, 1.35)
    const tight = [{ at: 0, rect: [100, 50, 2700, 1644] }, { at: 2, rect: [2400, 1500, 300, 200] }]
    for (let t = 0; t <= 2.5; t += 0.05) {
        const [x, y, width, height] = cameraRectAt(tight, t, capture)
        assert.ok(width >= 2700 / 1.35 - 1e-6, `largura ${width} em t=${t}`)
        assert.ok(height >= 1644 / 1.35 - 1e-6, `altura ${height} em t=${t}`)
        assert.ok(x >= 100 - 1e-6 && x + width <= 2800 + 1e-6, `x=${x} largura=${width} em t=${t}`)
        assert.ok(y >= 50 - 1e-6 && y + height <= 1694 + 1e-6, `y=${y} altura=${height} em t=${t}`)
    }
    const outside = cameraRectAt([{ at: 0, rect: [-500, -500, 4000, 3000] }], 0, capture)
    assert.deepEqual(outside, capture, 'retângulo maior que a captura volta para a captura inteira')
    const shifted = cameraRectAt([{ at: 0, rect: [2000, 1000, 2000, 1500] }], 0, capture)
    assert.ok(shifted[0] + shifted[2] <= 2800 + 1e-6 && shifted[1] + shifted[3] <= 1694 + 1e-6, 'desloca para dentro')
})

test('focusOpacity: acende em 0,4 s, fica até focusFor (padrão 2,5 s) e apaga em 0,4 s', () => {
    const { focusOpacity } = motion()
    assert.equal(focusOpacity(1, 2), 0)
    assert.equal(focusOpacity(2, 2), 0)
    assert.equal(focusOpacity(2.4, 2), 1)
    assert.equal(focusOpacity(2 + 2.5, 2), 1, 'ainda aceso no limite')
    const half = focusOpacity(2 + 2.5 + 0.2, 2)
    assert.ok(half > 0 && half < 1, `meio do fade = ${half}`)
    assert.equal(focusOpacity(2 + 2.5 + 0.4, 2), 0)
    assert.equal(focusOpacity(30, 2), 0, 'depois some de vez')
    assert.equal(focusOpacity(2 + 1.0 + 0.4, 2, 1.0), 0, 'focusFor customizado')
    assert.equal(focusOpacity(2 + 0.9, 2, 1.0), 1)
    let previous = 1
    for (let t = 4.5; t <= 4.9; t += 0.01) {
        const value = focusOpacity(t, 2)
        assert.ok(value <= previous + 1e-12 && value >= 0, `monotônico em t=${t}`)
        previous = value
    }
})

test('transitionPhases: a cena que sai some antes de a próxima nascer, e o buraco aparece entre as duas', () => {
    const { transitionPhases } = motion()
    const start = transitionPhases(0)
    assert.deepEqual([start.pull, start.hole, start.flash, start.arrive], [0, 0, 0, 0])
    const end = transitionPhases(1)
    assert.equal(end.pull, 1)
    assert.equal(end.arrive, 1)
    assert.equal(end.hole, 0)
    assert.equal(end.flash, 0)
    let previousPull = -1
    let previousArrive = -1
    let peakHole = 0
    let peakFlash = { value: 0, at: 0 }
    for (let p = 0; p <= 1.0001; p += 0.005) {
        const phase = transitionPhases(Math.min(p, 1))
        for (const [name, value] of Object.entries(phase)) {
            assert.ok(value >= 0 && value <= 1 + 1e-9, `${name}=${value} em p=${p} (sem overshoot)`)
        }
        assert.ok(phase.pull >= previousPull - 1e-12, `pull monotônico em p=${p}`)
        assert.ok(phase.arrive >= previousArrive - 1e-12, `arrive monotônico em p=${p}`)
        if (phase.arrive > 0) assert.ok(phase.pull > 0.999, `a próxima só nasce depois da que sai sumir (p=${p})`)
        previousPull = phase.pull
        previousArrive = phase.arrive
        peakHole = Math.max(peakHole, phase.hole)
        if (phase.flash > peakFlash.value) peakFlash = { value: phase.flash, at: p }
    }
    assert.ok(Math.abs(peakHole - 1) < 1e-6, `o Gargantua chega ao máximo = ${peakHole}`)
    assert.ok(peakFlash.value > 0.99, `clarão chega ao pico = ${peakFlash.value}`)
    assert.ok(peakFlash.at > 0.45 && peakFlash.at < 0.7, `clarão entre as duas cenas, em p=${peakFlash.at}`)
    assert.ok(transitionPhases(0.5).hole > 0.9, 'no meio o buraco está pleno')
    assert.ok(transitionPhases(0.2).pull < 0.5 && transitionPhases(0.2).pull > 0, 'puxada em curva suave (ease-in-out)')
})

test('transitionAt: a janela da transição fica dentro do respiro entre a fala que acaba e a próxima', () => {
    const { transitionAt, TRANSITION } = motion()
    const scenes = [
        { id: 'a', lines: { en: { text: 'x' }, pt: { text: 'x' } } },
        { id: 'b', lines: { en: { text: 'y' }, pt: { text: 'y' } } },
    ]
    const timeline = buildTimeline(scenes, { en: { a: 2, b: 2 }, pt: { a: 2.5, b: 2 } }, RELEASE_PACING)
    const [first, second] = timeline.scenes
    const voiceEnd = first.voiceAt + 2.5
    const from = first.end - TRANSITION.lead
    assert.ok(from >= voiceEnd, `transição começa em ${from}, fala acaba em ${voiceEnd}`)
    assert.ok(from + TRANSITION.duration <= second.voiceAt + 1e-9, 'transição termina antes da próxima fala')
    assert.ok(Math.abs(TRANSITION.duration - 1.8) < 1e-9, 'a transição dura 1,8 s')
    assert.equal(transitionAt(timeline, from - 0.01), null)
    assert.deepEqual(transitionAt(timeline, from), { from: 0, to: 1, progress: 0 })
    assert.ok(Math.abs(transitionAt(timeline, from + TRANSITION.duration / 2).progress - 0.5) < 1e-9)
    assert.equal(transitionAt(timeline, from + TRANSITION.duration), null)
    assert.equal(transitionAt(timeline, timeline.total), null, 'a última cena não tem transição de saída')
})
