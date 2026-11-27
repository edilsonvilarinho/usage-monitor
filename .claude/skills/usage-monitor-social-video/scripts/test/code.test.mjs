import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validateSpec } from '../spec.mjs'
import { buildScript } from '../timeline.mjs'
import { CODE_CARD, codeCardData, codeCardHtml, codeColumns, codeExcerpt, dedentLines } from '../code-card.mjs'

const IMAGE = 'img/hud.png'
const TEXT_40 = Array.from({ length: 40 }, (_, i) => `linha ${i + 1}`).join('\n')
const FILES = {
    'src/x/Thing.kt': TEXT_40,
    'src/x/Big.kt': `${'a'.repeat(107)}\t\nb`,
    'src/x/Ok.kt': `${'a'.repeat(106)}\t`,
}
const readText = (path) => FILES[path] ?? null
const check = (spec) => validateSpec(spec, { fileExists: () => true, readText })
const has = (errors, message) =>
    assert.ok(errors.includes(message), `esperado: ${message}\nrecebido:\n${errors.join('\n')}`)

const beat = (id, patch = {}) => ({
    id,
    kicker: { pt: 'Fluxo', en: 'Workflow' },
    title: { pt: 'Do pedido ao merge', en: 'From request to merge' },
    items: [{ pt: 'PR aberto', en: 'PR opened' }],
    climax: false,
    short: { pt: 'Texto curto.', en: 'Short text.' },
    long: { pt: 'Texto longo do beat.', en: 'Long text of the beat.' },
    ...patch,
})
const closing = (patch = {}) => {
    const { items, image, ...rest } = beat('fechamento')
    return { ...rest, ...patch }
}
const CODE = { file: 'src/x/Thing.kt', from: 3, to: 5 }
const withCode = (patch = {}) => beat('fluxo', { image: undefined, code: CODE, ...patch })
const spec = (beats) => ({ slug: 'codigo', link: 'https://github.com/x/y', facts: [], beats })

test('code: trecho válido, sem legenda, passa (o video.json atual usa assim)', () => {
    assert.deepEqual(check(spec([beat('abertura'), withCode(), closing()])), [])
})

test('code: não pode vir junto com image', () => {
    has(check(spec([beat('abertura'), withCode({ image: IMAGE }), closing()])), 'beat "fluxo" code: não pode vir junto com image')
})

test('code: o fechamento não tem código', () => {
    has(check(spec([beat('abertura'), closing({ code: CODE })])), 'beat "fechamento" code: o fechamento não tem código')
})

test('code: arquivo precisa existir na raiz do repo', () => {
    const errors = check(spec([beat('abertura'), withCode({ code: { ...CODE, file: 'src/x/Nope.kt' } }), closing()]))
    has(errors, 'beat "fluxo" code.file: arquivo não existe (src/x/Nope.kt)')
})

test('code: from e to são inteiros com 1 ≤ from ≤ to ≤ total de linhas', () => {
    const message = 'beat "fluxo" code: from e to precisam ser inteiros, com 1 ≤ from ≤ to ≤ 40'
    for (const range of [{ from: 0, to: 3 }, { from: 3, to: 41 }, { from: 9, to: 4 }, { from: 1.5, to: 3 }]) {
        has(check(spec([beat('abertura'), withCode({ code: { ...CODE, ...range } }), closing()])), message)
    }
})

test('code: no máximo 16 linhas', () => {
    const errors = check(spec([beat('abertura'), withCode({ code: { ...CODE, from: 1, to: 17 } }), closing()]))
    has(errors, 'beat "fluxo" code: 17 linhas; o máximo é 16')
    assert.deepEqual(check(spec([beat('abertura'), withCode({ code: { ...CODE, from: 1, to: 16 } }), closing()])), [])
})

test('code: linha mais longa com tab contando 4 colunas, no máximo 110', () => {
    const big = { file: 'src/x/Big.kt', from: 1, to: 1 }
    has(check(spec([beat('abertura'), withCode({ code: big }), closing()])), 'beat "fluxo" code: linha mais longa com 111 colunas; o máximo é 110')
    const ok = { file: 'src/x/Ok.kt', from: 1, to: 1 }
    assert.deepEqual(check(spec([beat('abertura'), withCode({ code: ok }), closing()])), [])
})

test('code: legenda opcional, mas pt e en validados como item (44 caracteres)', () => {
    const long = 'x'.repeat(45)
    has(check(spec([beat('abertura'), withCode({ code: { ...CODE, caption: { pt: long, en: 'ok' } } }), closing()])), 'beat "fluxo" code.caption.pt: 45 caracteres; o máximo é 44')
    has(check(spec([beat('abertura'), withCode({ code: { ...CODE, caption: { pt: 'Trecho' } } }), closing()])), 'beat "fluxo" code.caption.en: ausente')
})

test('code: a regra 7 (até 3 itens) vale para beat com código', () => {
    const items = [1, 2, 3, 4].map((n) => ({ pt: `item ${n}`, en: `item ${n}` }))
    has(check(spec([beat('abertura'), withCode({ items }), closing()])), 'beat "fluxo" items: 4 itens com código; o máximo é 3')
})

test('codeColumns conta tab como 4 colunas', () => {
    assert.equal(codeColumns('\tab'), 6)
})

test('codeExcerpt devolve de from a to, inclusive', () => {
    assert.deepEqual(codeExcerpt('a\nb\nc\nd', 2, 3), ['b', 'c'])
})

test('dedentLines tira a indentação comum ao trecho, linhas em branco ficam vazias', () => {
    assert.deepEqual(dedentLines(['    a', '      b', '']), ['a', '  b', ''])
})

test('codeCardData: cabeçalho file:from-to, números de linha e linhas sem indentação comum', () => {
    const data = codeCardData({ file: 'src/x/Thing.kt', from: 2, to: 3, lines: ['    b', '      c'] })
    assert.deepEqual(data, { head: 'src/x/Thing.kt:2-3', numbers: ['2', '3'], lines: ['b', '  c'] })
})

test('CODE_CARD: visual do design system', () => {
    assert.deepEqual({ ...CODE_CARD }, {
        width: 1500,
        font: 26,
        fontFloor: 18,
        maxLines: 16,
        maxColumns: 110,
        bg: '#141414',
        border: '#3D3838',
        radius: 8,
        padding: 28,
        text: '#E6E1E1',
        number: '#7A7373',
        head: '#FAB283',
        headFont: 20,
    })
})

test('codeCardHtml: fontes por @font-face com as URLs dadas, largura 1500 px', () => {
    const html = codeCardHtml({ regular: 'file:///fonts/Regular.ttf', medium: 'file:///fonts/Medium.ttf' })
    assert.ok(html.includes('url("file:///fonts/Regular.ttf")'), 'fonte 400 ausente')
    assert.ok(html.includes('url("file:///fonts/Medium.ttf")'), 'fonte 500 ausente')
    assert.ok(html.includes('width: 1500px'), 'largura 1500 px ausente')
})

test('caption vira o primeiro item do beat quando items não vem preenchido', () => {
    const caption = { pt: 'Trecho do teste', en: 'Test excerpt' }
    const durations = { abertura: 3, fluxo: 3, fechamento: 3 }
    const script = (first) => ({ slug: 'codigo', link: 'https://github.com/x/y', beats: [first, beat('fluxo'), closing()] })
    const empty = buildScript(script(beat('abertura', { items: undefined, image: undefined, code: { ...CODE, caption } })), 'long', 'pt', durations)
    assert.deepEqual(empty.beats[0].items.map((item) => item.text), ['Trecho do teste'])
    const filled = buildScript(script(beat('abertura', { image: undefined, code: { ...CODE, caption } })), 'long', 'pt', durations)
    assert.deepEqual(filled.beats[0].items.map((item) => item.text), ['PR aberto'])
})
