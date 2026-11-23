import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { sceneProblems } from '../scene-check.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(here, '..', '..', 'assets', 'video.html'), 'utf8')

// O HTML da captura de uma cena screen fica entre "// surface:begin" e "// surface:end" em video.html.
function surface() {
    const block = source.match(/\/\/ surface:begin\r?\n([\s\S]*?)\/\/ surface:end/)
    assert.ok(block, 'video.html tem o bloco surface:begin / surface:end')
    return new Function(`${block[1]}\nreturn { screenVisual }`)()
}

const shots = '<img src="a.png">'
const focus = '<div class="focus"></div>'

function rule(selector) {
    const start = source.split('\n').findIndex((line) => line.startsWith(`${selector} {`))
    assert.ok(start >= 0, `CSS de ${selector}`)
    const rest = source.split('\n').slice(start).join('\n')
    return rest.slice(rest.indexOf('{') + 1, rest.indexOf('}'))
}

test('cena screen não desenha barra de janela: nem os três pontos, nem o título', () => {
    const { screenVisual } = surface()
    for (const scene of [{ window: 'usage-monitor' }, { surface: 'hud', window: 'usage-monitor' }, {}]) {
        const html = screenVisual(scene, shots, focus)
        assert.ok(!/class="bar"/.test(html), `sem .bar: ${html}`)
        assert.ok(!/<i><\/i>/.test(html), 'sem os três pontos')
        assert.ok(!html.includes('usage-monitor'), 'sem o título da janela')
    }
})

test('janela (sem surface): cartão com raio 10, borda sutil e sombra, recortando a captura', () => {
    const { screenVisual } = surface()
    const html = screenVisual({}, shots, focus)
    assert.match(html, /class="panel cine"/)
    assert.match(rule('.panel'), /border-radius: 10px/)
    assert.match(rule('.panel'), /border: 1px solid var\(--border\)/)
    assert.match(rule('.panel.cine'), /box-shadow/)
    assert.match(rule('.shot'), /overflow: hidden/)
})

test('surface hud: captura direto sobre as estrelas, sem caixa, sem fundo, sem clip, com drop-shadow', () => {
    const { screenVisual } = surface()
    const html = screenVisual({ surface: 'hud' }, shots, focus)
    assert.ok(!/class="[^"]*\bpanel\b/.test(html), 'sem .panel (caixa)')
    assert.match(html, /class="hudshot"/)
    const css = rule('.hudshot')
    assert.ok(!/overflow:\s*hidden|background:|border:|border-radius/.test(css), `.hudshot sem caixa/clip/fundo: ${css}`)
    assert.match(css, /overflow: visible/)
    assert.match(rule('.hudshot img'), /filter: drop-shadow\(/)
    assert.ok(!/box-shadow/.test(rule('.hudshot .focus')), 'foco do HUD sem véu gigante')
})

test('layout: sem barra, o cromo da captura é só a borda do cartão', () => {
    const block = source.match(/const CAPTURE = \{([^}]*)\}/)
    assert.match(block[1], /chrome: 2\b/)
})

test('validador: surface hud recusa camera, com a cena na mensagem; hud sem camera e janela com camera passam', () => {
    const base = { id: 'minimize', kind: 'screen', crop: [0, 0, 100, 100] }
    const bad = sceneProblems({ ...base, surface: 'hud', camera: [{ at: 0, rect: [0, 0, 100, 100] }] })
    assert.equal(bad.length, 1)
    assert.match(bad[0], /minimize/)
    assert.match(bad[0], /camera/)
    assert.match(bad[0], /hud/)
    assert.deepEqual(sceneProblems({ ...base, surface: 'hud' }), [])
    assert.deepEqual(sceneProblems({ ...base, camera: [{ at: 0, rect: [0, 0, 100, 100] }] }), [])
})

test('validador: surface só aceita "hud"', () => {
    const found = sceneProblems({ id: 'x', kind: 'screen', crop: [0, 0, 1, 1], surface: 'card' })
    assert.equal(found.length, 1)
    assert.match(found[0], /surface/)
})
