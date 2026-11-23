import { test } from 'node:test'
import assert from 'node:assert/strict'
import { sequenceHolds } from '../frames.mjs'
import { PACING, RELEASE_PACING, buildTimeline } from '../timeline.mjs'

const scene = (id, en, pt, extra = {}) => ({ id, lines: { en: { text: en }, pt: { text: pt } }, ...extra })

test('RELEASE_PACING: 12 caracteres/s, respiro de 1,8 s, teto de 150 s e 1,5 s depois de cada sequência de quadros', () => {
    assert.equal(RELEASE_PACING.maxCharsPerSecond, 12)
    assert.equal(RELEASE_PACING.breath, 1.8)
    assert.equal(RELEASE_PACING.maxTotal, 150)
    assert.equal(RELEASE_PACING.sequenceTail, 1.5)
    assert.equal(RELEASE_PACING.captionChars, PACING.captionChars, 'legenda não muda')
})

test('RELEASE_PACING: fala a 13 caracteres/s falha (o limite agora é 12)', () => {
    const scenes = [scene('a', 'x'.repeat(13), 'x'.repeat(13))]
    assert.throws(() => buildTimeline(scenes, { en: { a: 1 }, pt: { a: 1 } }, RELEASE_PACING), /a \(en\): 13 caracteres/)
})

test('cena com sequência de quadros dura no mínimo a sequência + 1,5 s, mesmo com fala curta', () => {
    const scenes = [scene('term', 'Short.', 'Curto.', { kind: 'screen', frames: 'term' })]
    const durations = { en: { term: 0.5 }, pt: { term: 0.5 } }
    const timeline = buildTimeline(scenes, durations, RELEASE_PACING, { term: 6 })
    const [timing] = timeline.scenes
    assert.ok(timing.end - timing.start >= 6 + RELEASE_PACING.sequenceTail, `duração ${timing.end - timing.start}`)
    assert.equal(timing.end - timing.start, 6 + RELEASE_PACING.sequenceTail)
})

test('sem sequência de quadros, a cena dura entrada + fala + respiro (como antes)', () => {
    const scenes = [scene('a', 'Hello there.', 'Olá aí.')]
    const timeline = buildTimeline(scenes, { en: { a: 2 }, pt: { a: 2 } }, RELEASE_PACING, {})
    assert.equal(timeline.scenes[0].end, RELEASE_PACING.firstEnter + 2 + RELEASE_PACING.breath)
})

test('sequenceHolds: a duração de cada cena com frames é a maior soma de delayMillis entre os idiomas', () => {
    const scenes = [
        { id: 'term', kind: 'screen', frames: 'term' },
        { id: 'hook', kind: 'title' },
    ]
    const sequences = {
        term: {
            en: [{ file: '001.png', delayMillis: 1000 }, { file: '002.png', delayMillis: 2000 }],
            pt: [{ file: '001.png', delayMillis: 4000 }],
        },
    }
    assert.deepEqual(sequenceHolds(scenes, sequences), { term: 4 })
})
