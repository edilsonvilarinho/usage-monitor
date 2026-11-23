import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { copyFrameSequences, frameIndexAt, framesPathFor, withSequences } from '../frames.mjs'

const here = dirname(fileURLToPath(import.meta.url))

// Sequência de 3 quadros: 0–1 s o primeiro, 1–1,5 s o segundo, 1,5–3,5 s o terceiro.
const frames = [
    { file: '001.png', delayMillis: 1000 },
    { file: '002.png', delayMillis: 500 },
    { file: '003.png', delayMillis: 2000 },
]

test('frameIndexAt: cada quadro dura o seu delayMillis, a partir do instante 0 da cena', () => {
    assert.equal(frameIndexAt(frames, 0), 0)
    assert.equal(frameIndexAt(frames, 0.999), 0)
    assert.equal(frameIndexAt(frames, 1), 1)
    assert.equal(frameIndexAt(frames, 1.4), 1)
    assert.equal(frameIndexAt(frames, 1.5), 2)
    assert.equal(frameIndexAt(frames, 3.4), 2)
})

test('frameIndexAt: depois do último segura o último; com loop recomeça do primeiro', () => {
    assert.equal(frameIndexAt(frames, 10), 2)
    assert.equal(frameIndexAt(frames, 3.5, true), 0)
    assert.equal(frameIndexAt(frames, 4.6, true), 1)
    assert.equal(frameIndexAt(frames, 10, false), 2)
})

test('frameIndexAt: quadro de duração zero é pulado; antes do início mostra o primeiro', () => {
    const withZero = [{ file: 'a', delayMillis: 100 }, { file: 'b', delayMillis: 0 }, { file: 'c', delayMillis: 100 }]
    assert.equal(frameIndexAt(withZero, 0.1), 2)
    assert.equal(frameIndexAt(frames, -1), 0)
})

test('framesPathFor: {lang} vira o idioma; caminho sem {lang} fica igual', () => {
    assert.equal(framesPathFor('build/video-media/{lang}/demo-42', 'pt'), 'build/video-media/pt/demo-42')
    assert.equal(framesPathFor('build/video-media/demo', 'en'), 'build/video-media/demo')
})

test('copyFrameSequences: copia NNN.png e frames.json de cada idioma para stage/<chave>/<lang>/ e devolve a sequência', () => {
    const root = mkdtempSync(join(tmpdir(), 'frames-root-'))
    const stage = mkdtempSync(join(tmpdir(), 'frames-stage-'))
    for (const language of ['en', 'pt']) {
        const dir = join(root, 'build', 'video-media', language, 'demo-42')
        mkdirSync(dir, { recursive: true })
        writeFileSync(join(dir, '001.png'), `${language}-1`)
        writeFileSync(join(dir, '002.png'), `${language}-2`)
        writeFileSync(join(dir, 'frames.json'), JSON.stringify([{ file: '001.png', delayMillis: 800 }, { file: '002.png', delayMillis: 1200 }]))
    }
    const sequences = copyFrameSequences({
        root,
        stage,
        sources: { term: { frames: 'build/video-media/{lang}/demo-42' } },
    })
    assert.ok(existsSync(join(stage, 'term', 'pt', '002.png')), 'quadro pt copiado')
    assert.equal(readFileSync(join(stage, 'term', 'en', '001.png'), 'utf8'), 'en-1')
    assert.deepEqual(sequences.term.en, [{ file: '001.png', delayMillis: 800 }, { file: '002.png', delayMillis: 1200 }])
    assert.deepEqual(sequences.term.pt.map((frame) => frame.delayMillis), [800, 1200])
})

test('withSequences: cena screen com "frames" ganha sequence com caminhos do palco; as demais cenas ficam como estão', () => {
    const sequences = { term: { en: [{ file: '001.png', delayMillis: 800 }, { file: '002.png', delayMillis: 1200 }] } }
    const screen = { id: 'term', kind: 'screen', frames: 'term', crop: [0, 0, 400, 300], loop: true }
    const plain = { id: 'hook', kind: 'title' }
    const [scene, other] = withSequences([screen, plain], sequences, 'en')
    assert.deepEqual(scene.sequence, [
        { src: 'term/en/001.png', delayMillis: 800 },
        { src: 'term/en/002.png', delayMillis: 1200 },
    ])
    assert.equal(scene.loop, true)
    assert.equal(other, plain)
    assert.equal(screen.sequence, undefined, 'não altera o roteiro original')
})

test('video.html: frameIndexAt da página é a mesma função do módulo', () => {
    const source = readFileSync(join(here, '..', '..', 'assets', 'video.html'), 'utf8')
    const match = source.match(/function frameIndexAt\([\s\S]*?\r?\n\}\r?\n/)
    assert.ok(match, 'video.html define frameIndexAt')
    const pageFrameIndexAt = new Function(`${match[0]}\nreturn frameIndexAt`)()
    for (const [seconds, loop] of [[0, false], [0.999, false], [1.2, false], [2, false], [10, false], [4.6, true], [-1, false]]) {
        assert.equal(pageFrameIndexAt(frames, seconds, loop), frameIndexAt(frames, seconds, loop), `t=${seconds} loop=${loop}`)
    }
})
