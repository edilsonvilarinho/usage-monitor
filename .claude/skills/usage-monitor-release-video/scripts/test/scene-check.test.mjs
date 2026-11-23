import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { MAX_ZOOM, MIN_KEY_GAP, zoomLimit, sceneProblems, assertScenes } from '../scene-check.mjs'

const here = dirname(fileURLToPath(import.meta.url))

const screen = (camera) => ({ id: 'demo', kind: 'screen', crop: [0, 0, 2700, 1644], camera })

test('sceneProblems: chaves de câmera a menos de 1,5 s uma da outra são recusadas, com a mensagem dizendo qual', () => {
    assert.equal(MIN_KEY_GAP, 1.5)
    const bad = sceneProblems(screen([
        { at: 0, rect: [0, 0, 2700, 1644] },
        { at: 3, rect: [0, 0, 2700, 1644] },
        { at: 4.4, rect: [0, 0, 2700, 1644] },
    ]))
    assert.equal(bad.length, 1)
    assert.match(bad[0], /demo/)
    assert.match(bad[0], /1,4 s/)
    assert.match(bad[0], /1,5 s/)
})

test('sceneProblems: 1,5 s exatos, ordem embaralhada e cena sem câmera passam', () => {
    assert.deepEqual(sceneProblems(screen([{ at: 3, rect: [0, 0, 2700, 1644] }, { at: 1.5, rect: [0, 0, 2700, 1644] }])), [])
    assert.deepEqual(sceneProblems(screen(undefined)), [])
    assert.deepEqual(sceneProblems({ id: 't', kind: 'title' }), [])
})

test('sceneProblems: focusFor tem de ser positivo; assertScenes junta os problemas de todas as cenas', () => {
    assert.equal(sceneProblems({ ...screen(undefined), focus: [0, 0, 10, 10], focusFor: 0 }).length, 1)
    assert.throws(
        () => assertScenes([screen([{ at: 0, rect: [0, 0, 1, 1] }, { at: 1, rect: [0, 0, 1, 1] }]), { id: 'ok', kind: 'closing' }]),
        /demo/,
    )
    assert.doesNotThrow(() => assertScenes([screen(undefined)]))
})

test('o zoom máximo do validador é o mesmo do video.html', () => {
    const source = readFileSync(join(here, '..', '..', 'assets', 'video.html'), 'utf8')
    const found = source.match(/const MAX_ZOOM = ([\d.]+)/)
    assert.ok(found, 'video.html declara MAX_ZOOM')
    assert.equal(Number(found[1]), MAX_ZOOM)
})

test('zoomLimit: a cena pode declarar maxZoom; sem ele vale o padrão de 1,35x', () => {
    assert.equal(zoomLimit({ id: 'a' }), MAX_ZOOM)
    assert.equal(zoomLimit({ id: 'a', maxZoom: 2.2 }), 2.2)
    assert.equal(zoomLimit({ id: 'a', maxZoom: 1 }), 1)
})

test('sceneProblems: maxZoom precisa ser um número entre 1 e 3, e a mensagem diz a cena', () => {
    assert.deepEqual(sceneProblems({ ...screen(undefined), maxZoom: 2.2 }), [])
    for (const bad of [0.5, 0, -1, 3.5, '2', NaN]) {
        const found = sceneProblems({ ...screen(undefined), maxZoom: bad })
        assert.equal(found.length, 1, `maxZoom ${bad}`)
        assert.match(found[0], /demo/)
        assert.match(found[0], /maxZoom/)
    }
})
