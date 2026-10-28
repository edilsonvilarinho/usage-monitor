import { test } from 'node:test'
import assert from 'node:assert/strict'
import { PACING, buildTimeline, captionBlocks, captionCues, spokenLength, toSrt } from '../timeline.mjs'

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
        /status \(en\): 27 caracteres em 1\.00 s \(27\.0\/s\)/,
    )
})

test('roteiro longo demais falha com a duracao medida', () => {
    const many = Array.from({ length: 20 }, (_, index) => scene(`s${index}`, 'one two', 'um dois'))
    const durations = Object.fromEntries(many.map((item) => [item.id, 3]))
    assert.throws(() => buildTimeline(many, { en: durations, pt: durations }), /passa de 90 s/)
})

test('o ritmo conta os caracteres do texto falado, nao palavras', () => {
    assert.equal(spokenLength({ text: 'usage-monitor status --watch', say: 'usage monitor status watch' }), 26)
    assert.equal(spokenLength({ text: 'Versão 42.' }), 10)
    assert.equal(PACING.maxCharsPerSecond, 15)
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

test('legenda divide a fala em blocos equilibrados, sem palavra orfa', () => {
    const blocks = captionBlocks('Chegou o Usage Monitor 42. Agora também no seu terminal.')
    assert.equal(blocks.length, 2)
    const sizes = blocks.map((block) => block.join(' ').length)
    assert.ok(Math.abs(sizes[0] - sizes[1]) <= 12, `blocos ${sizes.join(' × ')}`)
    for (const block of blocks) for (const line of block) assert.ok(line.length <= PACING.captionChars, line)
})

test('nenhuma linha de legenda fica com uma palavra so', () => {
    for (const text of [
        'Interactive mode puts six tabs on one screen, even over SSH.',
        'Um comando mostra todas as cotas e o ritmo de cada uma.',
        'Team server down? Now you see the step where it stopped.',
    ]) {
        const lines = captionBlocks(text).flat()
        assert.equal(lines.join(' '), text)
        for (const line of lines) assert.ok(line.split(' ').length > 1, `"${line}" em "${text}"`)
    }
})
