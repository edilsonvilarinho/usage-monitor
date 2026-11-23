import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, '..', '..', 'assets', 'video.html'), 'utf8')

// O dimensionamento da captura fica entre "// layout:begin" e "// layout:end" em video.html; o teste carrega esse bloco.
function layout() {
    const block = source.match(/\/\/ layout:begin\r?\n([\s\S]*?)\/\/ layout:end/)
    assert.ok(block, 'video.html tem o bloco layout:begin / layout:end')
    return new Function(`${block[1]}\nreturn { fitCapture, CAPTURE }`)()
}

const LANDSCAPE = [0, 0, 2700, 1644]
const PORTRAIT = [0, 0, 816, 1872]

test('CAPTURE: painel de 952 px (950 de captura + borda de 1 px de cada lado)', () => {
    const { CAPTURE } = layout()
    assert.equal(CAPTURE.maxWidth + 2, 952)
    assert.equal(CAPTURE.chrome, 2, 'sem barra: só a borda do cartão')
})

test('fitCapture: retrato cabe na altura máxima e passa dos 236 px antigos de largura', () => {
    const { fitCapture } = layout()
    const box = fitCapture(PORTRAIT, { maxWidth: 950, maxHeight: 638 })
    assert.equal(box.height, 638, 'a altura é o limite que aperta')
    assert.ok(box.width > 236, `largura ${box.width} deve passar de 236`)
    assert.ok(Math.abs(box.width / box.height - 816 / 1872) < 0.01, 'mantém a proporção do retrato')
})

test('fitCapture: paisagem usa os 950 px de largura e mantém a proporção', () => {
    const { fitCapture } = layout()
    const box = fitCapture(LANDSCAPE, { maxWidth: 950, maxHeight: 638 })
    assert.equal(box.width, 950)
    assert.ok(Math.abs(box.height - 950 * 1644 / 2700) <= 1, `altura ${box.height}`)
})

test('fitCapture: nunca passa dos limites e encosta em um deles, também em capturas pequenas (amplia)', () => {
    const { fitCapture } = layout()
    const crops = [PORTRAIT, LANDSCAPE, [0, 0, 400, 300], [0, 0, 1000, 1000], [0, 0, 300, 1200]]
    for (const crop of crops) {
        const box = fitCapture(crop, { maxWidth: 950, maxHeight: 638 })
        assert.ok(box.width <= 950 && box.height <= 638, `${crop} -> ${box.width}x${box.height}`)
        assert.ok(Math.max(box.width / 950, box.height / 638) > 0.99, `${crop} encosta num limite`)
        assert.ok(Math.abs(box.width / box.height - crop[2] / crop[3]) < 0.01, `${crop} mantém a proporção`)
    }
})

test('buildScene e setup: nenhuma medida fixa de 876×544 no dimensionamento de captura', () => {
    assert.ok(!/Math\.min\(876\s*\//.test(source), 'o 876/544 antigo saiu')
    assert.ok(/fitCapture\(/.test(source), 'a captura é dimensionada por fitCapture')
})

test('zona do painel: legenda na faixa 1500–1680 px e título em até 2 linhas (A15)', () => {
    const { CAPTURE } = layout()
    assert.equal(1920 - CAPTURE.captionEdge, 1680, 'a legenda acaba em 1680 px')
    assert.equal(CAPTURE.headlineLines, 2)
    assert.match(source, /#caption \{[^}]*bottom: 240px/)
})
