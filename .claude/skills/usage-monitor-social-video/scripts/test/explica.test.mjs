import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validateSpec } from '../spec.mjs'
// Namespace: as funções novas ainda não existem na primeira rodada (teste vermelho na asserção).
import * as timeline from '../timeline.mjs'

const LONG_PT = 'Primeiro, o PR abre. Depois, a CI roda. Por fim, o merge entra.'
const LONG_EN = 'First, the PR opens. Then CI runs. Finally, the merge lands.'
const TEXT_40 = Array.from({ length: 40 }, (_, i) => `linha ${i + 1}`).join('\n')
const FILES = { 'src/x/Thing.kt': TEXT_40 }
const readText = (path) => FILES[path] ?? null
const check = (spec) => validateSpec(spec, { fileExists: () => true, readText })
const has = (errors, message) =>
    assert.ok(errors.includes(message), `esperado: ${message}\nrecebido:\n${errors.join('\n')}`)
const noErrors = (spec) => assert.deepEqual(check(spec), [])

const lbl = (pt, en = pt) => ({ pt, en })
const mk = (n, patch = {}) => ({ n, x: 10, y: 20, label: lbl('PR'), at: { pt: 'o PR abre', en: 'the PR opens' }, ...patch })
const flow = (nodes, extra = {}) => ({ type: 'flow', nodes, ...extra })
const compare = (nodes, extra = {}) => ({ type: 'compare', nodes, ...extra })
const node = (pt, tone) => ({ label: lbl(pt), tone })
const hl = (patch = {}) => ({ lines: [4], tone: 'crit', note: lbl('Falha'), at: { pt: 'a CI roda', en: 'CI runs' }, ...patch })

const beat = (id, patch = {}) => ({
    id,
    kicker: { pt: 'Fluxo', en: 'Workflow' },
    title: { pt: 'Do pedido ao merge', en: 'From request to merge' },
    items: [{ pt: 'PR aberto', en: 'PR opened' }],
    image: 'img/hud.png',
    climax: false,
    short: { pt: 'Texto curto.', en: 'Short text.' },
    long: { pt: LONG_PT, en: LONG_EN },
    ...patch,
})
const closing = (patch = {}) => {
    const { items, image, ...rest } = beat('fechamento')
    return { ...rest, ...patch }
}
const code = (patch = {}) => ({ file: 'src/x/Thing.kt', from: 3, to: 5, ...patch })
const withCode = (patch = {}) => beat('fluxo', { image: undefined, items: undefined, code: code(), ...patch })
const spec = (beats, patch = {}) => ({ slug: 'explica', link: 'https://github.com/x/y', facts: [], beats, ...patch })

// ---------- validação: scene ----------

test('scene: só "explica" ou ausente', () => {
    noErrors(spec([beat('abertura'), beat('fluxo'), closing()], { scene: 'explica' }))
    has(check(spec([beat('abertura'), beat('fluxo'), closing()], { scene: 'outra' })), 'scene: use "explica" ou ausente')
})

// ---------- validação: markers ----------

test('markers: só em beat com image', () => {
    const markers = [mk(1)]
    has(check(spec([beat('abertura', { image: undefined, markers }), beat('fluxo'), closing()])), 'beat "abertura" markers: só com image')
    noErrors(spec([beat('abertura', { markers }), beat('fluxo'), closing()]))
})

test('markers: não vale em beat com code', () => {
    has(check(spec([beat('abertura'), withCode({ markers: [mk(1)] }), closing()])), 'beat "fluxo" markers: só com image')
})

test('markers: no máximo 6', () => {
    const seven = [1, 2, 3, 4, 5, 6, 7].map((n) => mk(n))
    has(check(spec([beat('abertura', { markers: seven }), beat('fluxo'), closing()])), 'beat "abertura" markers: 7 marcadores; o máximo é 6')
    const six = [1, 2, 3, 4, 5, 6].map((n) => mk(n))
    noErrors(spec([beat('abertura', { markers: six }), beat('fluxo'), closing()]))
})

test('markers: n de 1 em diante, sem repetir', () => {
    has(
        check(spec([beat('abertura', { markers: [mk(1), mk(1)] }), beat('fluxo'), closing()])),
        'beat "abertura" markers[1].n: use 2 (vai de 1 sem repetir)',
    )
    has(
        check(spec([beat('abertura', { markers: [mk(2)] }), beat('fluxo'), closing()])),
        'beat "abertura" markers[0].n: use 1 (vai de 1 sem repetir)',
    )
})

test('markers: x e y são % da imagem, de 0 a 100', () => {
    has(
        check(spec([beat('abertura', { markers: [mk(1, { x: 101 })] }), beat('fluxo'), closing()])),
        'beat "abertura" markers[0].x: use número de 0 a 100',
    )
    has(
        check(spec([beat('abertura', { markers: [mk(1, { y: -1 })] }), beat('fluxo'), closing()])),
        'beat "abertura" markers[0].y: use número de 0 a 100',
    )
})

test('markers: at é trecho literal do long do mesmo beat, em cada idioma', () => {
    has(
        check(spec([beat('abertura', { markers: [mk(1, { at: { pt: 'o PR voa', en: 'the PR opens' } })] }), beat('fluxo'), closing()])),
        'beat "abertura" markers[0].at.pt: trecho não aparece no texto longo',
    )
    has(
        check(spec([beat('abertura', { markers: [mk(1, { at: { pt: 'o PR abre', en: 'the PR flies' } })] }), beat('fluxo'), closing()])),
        'beat "abertura" markers[0].at.en: trecho não aparece no texto longo',
    )
})

// ---------- validação: diagram ----------

test('diagram: flow tem de 2 a 4 nós; compare tem 2', () => {
    const one = [node('A')]
    const five = ['A', 'B', 'C', 'D', 'E'].map((x) => node(x))
    has(check(spec([beat('abertura', { diagram: flow(one) }), beat('fluxo'), closing()])), 'beat "abertura" diagram.nodes: flow tem de 2 a 4 nós')
    has(check(spec([beat('abertura', { diagram: flow(five) }), beat('fluxo'), closing()])), 'beat "abertura" diagram.nodes: flow tem de 2 a 4 nós')
    has(
        check(spec([beat('abertura', { diagram: compare([node('A'), node('B'), node('C')]) }), beat('fluxo'), closing()])),
        'beat "abertura" diagram.nodes: compare tem 2 nós',
    )
    noErrors(spec([beat('abertura', { diagram: flow([node('A'), node('B')]) }), beat('fluxo'), closing()]))
    noErrors(spec([beat('abertura', { diagram: compare([node('A'), node('B', 'ok')]) }), beat('fluxo'), closing()]))
})

test('diagram: back só em flow, com rótulo de até 28 caracteres', () => {
    has(
        check(spec([beat('abertura', { diagram: compare([node('A'), node('B')], { back: { label: lbl('Volta') } }) }), beat('fluxo'), closing()])),
        'beat "abertura" diagram.back: só em flow',
    )
    has(
        check(spec([beat('abertura', { diagram: flow([node('A'), node('B')], { back: { label: lbl('x'.repeat(29)) } }) }), beat('fluxo'), closing()])),
        'beat "abertura" diagram.back.label.pt: 29 caracteres; o máximo é 28',
    )
})

test('diagram: rótulo de nó até 28 caracteres; tone crit, ok ou null; type flow ou compare', () => {
    has(
        check(spec([beat('abertura', { diagram: flow([node('x'.repeat(29)), node('B')]) }), beat('fluxo'), closing()])),
        'beat "abertura" diagram.nodes[0].label.pt: 29 caracteres; o máximo é 28',
    )
    has(
        check(spec([beat('abertura', { diagram: flow([node('A', 'warn'), node('B')]) }), beat('fluxo'), closing()])),
        'beat "abertura" diagram.nodes[0].tone: use crit, ok ou null',
    )
    has(
        check(spec([beat('abertura', { diagram: { type: 'bar', nodes: [node('A'), node('B')] } }), beat('fluxo'), closing()])),
        'beat "abertura" diagram.type: use flow ou compare',
    )
    noErrors(spec([beat('abertura', { diagram: flow([node('A', null), node('B', 'crit')]) }), beat('fluxo'), closing()]))
})

// ---------- validação: code.highlight ----------

test('code.highlight: precisa ser uma lista', () => {
    has(
        check(spec([beat('abertura'), withCode({ code: code({ highlight: 'x' }) }), closing()])),
        'beat "fluxo" code.highlight: precisa ser uma lista',
    )
})

test('code.highlight: linhas dentro de from..to, tom, nota e at', () => {
    has(
        check(spec([beat('abertura'), withCode({ code: code({ highlight: [hl({ lines: [2] })] }) }), closing()])),
        'beat "fluxo" code.highlight[0].lines: use linhas dentro de 3..5',
    )
    has(
        check(spec([beat('abertura'), withCode({ code: code({ highlight: [hl({ tone: 'warn' })] }) }), closing()])),
        'beat "fluxo" code.highlight[0].tone: use info, crit ou ok',
    )
    has(
        check(spec([beat('abertura'), withCode({ code: code({ highlight: [hl({ note: lbl('x'.repeat(41)) })] }) }), closing()])),
        'beat "fluxo" code.highlight[0].note.pt: 41 caracteres; o máximo é 40',
    )
    has(
        check(spec([beat('abertura'), withCode({ code: code({ highlight: [hl({ at: { pt: 'a CI voa', en: 'CI runs' } })] }) }), closing()])),
        'beat "fluxo" code.highlight[0].at.pt: trecho não aparece no texto longo',
    )
    noErrors(spec([beat('abertura'), withCode({ code: code({ highlight: [hl({ lines: [3, 5], note: lbl('x'.repeat(40)) })] }) }), closing()]))
})

test('contrato novo completo passa sem erros', () => {
    noErrors(
        spec(
            [
                beat('abertura', {
                    markers: [mk(1), mk(2, { label: lbl('Merge'), at: { pt: 'Por fim', en: 'Finally' } })],
                    diagram: flow([node('Abre PR'), node('CI roda', 'ok')], { back: { label: lbl('Corrige') } }),
                }),
                withCode({ diagram: compare([node('Antes'), node('Depois', 'ok')]), code: code({ highlight: [hl()] }) }),
                closing(),
            ],
            { scene: 'explica' },
        ),
    )
})

// ---------- phraseTime ----------

test('phraseTime: começo do trecho pelo mesmo critério das legendas', () => {
    const text = 'Primeiro, o PR abre. Depois, a CI roda.'
    assert.equal(timeline.phraseTime(text, 'Depois', 10, 20), timeline.timeCaptions(text, 10, 20)[1].start)
    assert.equal(timeline.phraseTime(text, 'Depois', 10, 20), 15.263)
    assert.equal(timeline.phraseTime(text, 'Primeiro', 10, 20), 10)
})

test('phraseTime: trecho no meio de uma legenda soma os caracteres até ele', () => {
    const text = 'Primeiro, o PR abre. Depois, a CI roda.'
    assert.equal(timeline.phraseTime(text, 'roda', 10, 20), 18.684)
})

test('phraseTime: null se o trecho não existe', () => {
    assert.equal(timeline.phraseTime(LONG_PT, 'merge saiu', 0, 5), null)
})

// ---------- buildScript ----------

const DURATIONS = { abertura: 5, fluxo: 5, fechamento: 4 }
const script3 = () =>
    spec(
        [
            beat('abertura', {
                markers: [mk(1, { x: 12.5, y: 30, label: lbl('PR aberto', 'PR opened'), at: { pt: 'Depois', en: 'Then' } })],
                diagram: flow([node('Abre PR'), node('CI roda', 'ok')], { back: { label: lbl('Corrige') } }),
            }),
            withCode({
                diagram: compare([node('Antes'), node('Depois', 'ok')]),
                code: code({ highlight: [hl({ at: { pt: 'a CI roda', en: 'CI runs' } })] }),
            }),
            closing(),
        ],
        { scene: 'explica' },
    )
const CODE_LINES = { fluxo: ['  if (x) {', '    run()', '  }'] }

test('buildScript: markers, diagram e code com textos do idioma e tempos em segundos', () => {
    const out = timeline.buildScript(script3(), 'long', 'pt', DURATIONS, { codeLines: CODE_LINES })
    assert.equal(out.scene, 'explica')
    const [a, f] = out.beats
    assert.deepEqual(a.markers, [{ n: 1, x: 12.5, y: 30, label: 'PR aberto', at: 2.639 }])
    assert.deepEqual(a.diagram, {
        type: 'flow',
        nodes: [
            { label: 'Abre PR', tone: null },
            { label: 'CI roda', tone: 'ok' },
        ],
        back: { label: 'Corrige' },
    })
    assert.equal(f.diagram.back, null)
    assert.deepEqual(f.code, {
        file: 'src/x/Thing.kt',
        from: 3,
        to: 5,
        lines: ['if (x) {', '  run()', '}'],
        highlight: [{ lines: [4], tone: 'crit', note: 'Falha', at: 11.295 }],
    })
    assert.equal(out.beats[2].markers, undefined)
})

test('buildScript: em inglês, o texto e o tempo vêm do long en', () => {
    const out = timeline.buildScript(script3(), 'long', 'en', DURATIONS, { codeLines: CODE_LINES })
    assert.deepEqual(out.beats[0].markers, [{ n: 1, x: 12.5, y: 30, label: 'PR opened', at: 2.724 }])
})

test('buildScript: scene só no longo; no curto, scene null e at null', () => {
    const short = timeline.buildScript(script3(), 'short', 'pt', DURATIONS, { codeLines: CODE_LINES })
    assert.equal(short.scene, null)
    assert.equal(short.beats[0].markers[0].at, null)
    assert.equal(short.beats[1].code.highlight[0].at, null)
    const none = timeline.buildScript(spec(script3().beats), 'long', 'pt', DURATIONS, { codeLines: CODE_LINES })
    assert.equal(none.scene, null)
})
