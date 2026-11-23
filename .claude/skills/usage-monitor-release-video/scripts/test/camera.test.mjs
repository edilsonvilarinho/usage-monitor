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
    return new Function(`${block[1]}\nreturn { clamp, easeInOut, cameraRectAt, TRANSITION, transitionAt, transitionCurve, flashCurve, holeRadius }`)()
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

test('transitionCurve: 0 em 0, 1 em 1, monotônica, sem overshoot, ease-in (acelera para dentro)', () => {
    const { transitionCurve } = motion()
    assert.equal(transitionCurve(0), 0)
    assert.equal(transitionCurve(1), 1)
    assert.ok(transitionCurve(0.5) < 0.5, 'ease-in: no meio ainda está atrás do linear')
    let previous = -1
    for (let p = 0; p <= 1; p += 0.01) {
        const value = transitionCurve(p)
        assert.ok(value >= previous && value >= 0 && value <= 1, `p=${p} valor=${value}`)
        previous = value
    }
})

test('flashCurve: clarão só no fim do colapso (zero antes de 0,75 e em 1; pico perto de 0,875)', () => {
    const { flashCurve } = motion()
    assert.equal(flashCurve(0.5), 0)
    assert.equal(flashCurve(1), 0)
    assert.ok(Math.abs(flashCurve(0.875) - 1) < 1e-9, `pico = ${flashCurve(0.875)}`)
})

test('holeRadius: abre o buraco, depois colapsa para um núcleo pequeno no fim', () => {
    const { holeRadius } = motion()
    assert.ok(holeRadius(0.6) > holeRadius(0), 'cresce até o meio')
    assert.ok(holeRadius(1) < holeRadius(0.6), 'colapsa até o fim')
    assert.ok(holeRadius(1) >= 40 && holeRadius(1) <= 60, `raio final = ${holeRadius(1)}`)
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
    assert.ok(from + TRANSITION.duration <= second.voiceAt, 'transição termina antes da próxima fala')
    assert.equal(transitionAt(timeline, from - 0.01), null)
    assert.deepEqual(transitionAt(timeline, from), { from: 0, to: 1, progress: 0 })
    assert.ok(Math.abs(transitionAt(timeline, from + TRANSITION.duration / 2).progress - 0.5) < 1e-9)
    assert.equal(transitionAt(timeline, from + TRANSITION.duration), null)
    assert.equal(transitionAt(timeline, timeline.total), null, 'a última cena não tem transição de saída')
})
