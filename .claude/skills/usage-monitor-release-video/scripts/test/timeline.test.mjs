import { test } from 'node:test'
import assert from 'node:assert/strict'
import { PACING, buildTimeline, captionBlocks, captionCues, toSrt, wordCount } from '../timeline.mjs'

const scene = (id, en, pt) => ({ id, lines: { en: { text: en }, pt: { text: pt } } })
const scenes = [
    scene('title', 'Usage Monitor 42.', 'Usage Monitor 42.'),
    scene('status', 'Every quota in one command.', 'Todas as cotas num comando só.'),
]

test('cada cena dura a entrada, a fala mais longa entre os idiomas e o respiro', () => {
    const timeline = buildTimeline(scenes, { en: { title: 2, status: 3 }, pt: { title: 2.5, status: 2.8 } })
    const [first, second] = timeline.scenes
    assert.equal(first.start, 0)
    assert.equal(first.voiceAt, PACING.firstEnter)
    assert.equal(first.end, PACING.firstEnter + 2.5 + PACING.breath)
    assert.equal(second.start, first.end)
    assert.equal(second.voiceAt, first.end + PACING.enter)
    assert.equal(timeline.total, second.end + PACING.lastExit)
})

test('fala rapida demais falha em vez de acelerar o video', () => {
    assert.throws(
        () => buildTimeline(scenes, { en: { title: 2, status: 1 }, pt: { title: 2, status: 3 } }),
        /status \(en\): 5 palavras em 1\.00 s/,
    )
})

test('roteiro longo demais falha com a duracao medida', () => {
    const many = Array.from({ length: 20 }, (_, index) => scene(`s${index}`, 'one two', 'um dois'))
    const durations = Object.fromEntries(many.map((item) => [item.id, 3]))
    assert.throws(() => buildTimeline(many, { en: durations, pt: durations }), /passa de 90 s/)
})

test('palavra conta o texto falado quando ele difere da legenda', () => {
    assert.equal(wordCount({ text: 'usage-monitor status --watch', say: 'usage monitor status watch' }), 4)
    assert.equal(wordCount({ text: 'Versão 42.' }), 2)
})

test('legenda quebra em linhas de no maximo 26 caracteres e blocos de 2 linhas', () => {
    const blocks = captionBlocks('No Windows, o instalador agora coloca o comando no PATH do usuário.')
    for (const block of blocks) {
        assert.ok(block.length <= 2)
        for (const line of block) assert.ok(line.length <= PACING.captionChars, line)
    }
    assert.equal(blocks.flat().join(' '), 'No Windows, o instalador agora coloca o comando no PATH do usuário.')
})

test('a legenda entra antes da fala, sai depois e nunca invade a proxima', () => {
    const timeline = buildTimeline(scenes, { en: { title: 2, status: 3 }, pt: { title: 2, status: 3 } })
    const cues = captionCues(scenes, timeline, { en: { title: 2, status: 3 } }, 'en')
    assert.equal(cues[0].start, timeline.scenes[0].voiceAt - PACING.captionLead)
    assert.equal(cues[0].end, timeline.scenes[0].voiceAt + 2 + PACING.captionTail)
    for (let index = 1; index < cues.length; index += 1) assert.ok(cues[index].start >= cues[index - 1].end)
})

test('SRT numera os blocos e escreve o tempo com virgula', () => {
    assert.equal(
        toSrt([{ start: 1.25, end: 3.4, lines: ['Usage Monitor 42.'] }, { start: 61.05, end: 62, lines: ['a', 'b'] }]),
        '1\n00:00:01,250 --> 00:00:03,400\nUsage Monitor 42.\n\n2\n00:01:01,050 --> 00:01:02,000\na\nb\n',
    )
})
