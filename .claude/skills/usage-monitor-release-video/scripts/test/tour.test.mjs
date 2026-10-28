import { test } from 'node:test'
import assert from 'node:assert/strict'
import { PACING, TOUR_PACING, buildTimeline, captionBlocks, soundEvents } from '../timeline.mjs'

// Tour em vídeo (#505): 16:9, 5 a 10 minutos, cartão de capítulo entre as cenas, trilha e efeitos sonoros.
const scene = (id, extra = {}) => ({ id, lines: { en: { text: 'x'.repeat(10) }, pt: { text: 'x'.repeat(10) } }, ...extra })
const durations = (ids, seconds) => {
    const table = Object.fromEntries(ids.map((id) => [id, seconds]))
    return { en: table, pt: table }
}
const chapters = (count) => Array.from({ length: count }, (_, index) => scene(`c${index}`))

test('o tour tem 5 a 10 minutos, cartão de capítulo na entrada e fala mais lenta que o vídeo de release', () => {
    assert.equal(TOUR_PACING.minTotal, 300)
    assert.equal(TOUR_PACING.maxTotal, 600)
    assert.equal(TOUR_PACING.maxCharsPerSecond, 14)
    assert.ok(TOUR_PACING.enter > PACING.enter, 'a lente e o cartão do capítulo cabem na entrada')
    assert.equal(PACING.maxTotal, 90, 'o vídeo de release continua com 90 s')
})

test('cada capítulo do tour fala depois da lente e do cartão', () => {
    const list = chapters(20)
    const timeline = buildTimeline(list, durations(list.map((item) => item.id), 12), TOUR_PACING)
    const [first, second] = timeline.scenes
    assert.equal(first.voiceAt, TOUR_PACING.firstEnter)
    assert.equal(second.voiceAt, first.end + TOUR_PACING.enter)
    assert.equal(first.end, TOUR_PACING.firstEnter + 12 + TOUR_PACING.breath)
})

test('tour curto demais falha dizendo que falta conteúdo', () => {
    const list = chapters(3)
    assert.throws(() => buildTimeline(list, durations(list.map((item) => item.id), 10), TOUR_PACING), /abaixo de 300 s/)
})

test('tour longo demais falha com o teto de 10 minutos', () => {
    const list = chapters(40)
    assert.throws(() => buildTimeline(list, durations(list.map((item) => item.id), 14), TOUR_PACING), /passa de 600 s/)
})

test('o tour recusa fala acima de 14 caracteres por segundo', () => {
    const list = [...chapters(20), scene('fast', { lines: { en: { text: 'y'.repeat(60) }, pt: { text: 'y'.repeat(30) } } })]
    const table = durations(list.map((item) => item.id), 15)
    table.en.fast = 4
    table.pt.fast = 4
    assert.throws(() => buildTimeline(list, table, TOUR_PACING), /fast \(en\): 60 caracteres em 4\.00 s \(15\.0\/s\)/)
})

test('capturas de um capítulo dividem o tempo depois da entrada, e o foco acende no primeiro terço', () => {
    const list = [scene('hud', { media: [{ image: 'a' }, { image: 'b', focus: [0, 0, 1, 1] }, { image: 'c' }] }), ...chapters(25)]
    const timeline = buildTimeline(list, durations(list.map((item) => item.id), 12), TOUR_PACING)
    const timing = timeline.scenes[0]
    const span = (timing.end - timing.voiceAt) / 3
    assert.deepEqual(timing.shots.map((shot) => shot.at), [timing.voiceAt, timing.voiceAt + span, timing.voiceAt + 2 * span])
    assert.equal(timing.shots[0].focusAt, null)
    assert.equal(timing.shots[1].focusAt, timing.shots[1].at + span * 0.35)
    assert.equal(timeline.scenes[1].shots, undefined)
})

test('efeitos sonoros: subida na abertura, lente em cada capítulo, deslize em cada captura nova, sino no foco', () => {
    const list = [
        scene('title'),
        scene('hud', { media: [{ image: 'a' }, { image: 'b', focus: [0, 0, 1, 1] }] }),
        ...chapters(24),
    ]
    const timeline = buildTimeline(list, durations(list.map((item) => item.id), 12), TOUR_PACING)
    const events = soundEvents(list, timeline, 'en')
    const hud = timeline.scenes[1]
    assert.deepEqual(events[0], { name: 'riser', at: 0 })
    assert.deepEqual(events.filter((event) => event.name === 'lens').map((event) => event.at),
        timeline.scenes.slice(1).map((timing) => timing.start))
    assert.deepEqual(events.filter((event) => event.name === 'whoosh').map((event) => event.at), [hud.shots[1].at])
    assert.deepEqual(events.filter((event) => event.name === 'chime').map((event) => event.at), [hud.shots[1].focusAt])
})

test('teclas soam enquanto cada comando é digitado, a 30 caracteres por segundo a partir da fala', () => {
    const list = [
        scene('terminal', { commands: [{ cmd: 'usage-monitor status' }, { cmd: { en: 'abc', pt: 'abcdef' } }], pause: 0.5 }),
        ...chapters(25),
    ]
    const timeline = buildTimeline(list, durations(list.map((item) => item.id), 12), TOUR_PACING)
    const voiceAt = timeline.scenes[0].voiceAt
    const keys = soundEvents(list, timeline, 'pt').filter((event) => event.name === 'keys')
    assert.deepEqual(keys, [
        { name: 'keys', at: voiceAt, duration: 20 / 30 },
        { name: 'keys', at: voiceAt + 20 / 30 + 0.2 + 0.5, duration: 6 / 30 },
    ])
})

test('a legenda do tour usa linhas de 42 caracteres', () => {
    const text = 'One ring per account, one arc per quota: weekly outside, five hours inside.'
    const blocks = captionBlocks(text, TOUR_PACING)
    assert.equal(blocks.length, 1)
    assert.ok(blocks[0].every((line) => line.length <= 42))
    assert.ok(captionBlocks(text).flat().length > 2, 'o release continua com 26 caracteres por linha')
})
