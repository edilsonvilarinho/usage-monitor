// Falas por momento (#633, A14): lines[lang] aceita uma lista de beats { at, text, say? }.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { PACING, RELEASE_PACING, beatsOf, buildTimeline, captionCues, voiceTracks } from '../timeline.mjs'
import { sceneProblems } from '../scene-check.mjs'
import { speakScenes } from '../speak.mjs'
import { previewAudioArgs } from '../preview.mjs'

const beats = (language) => [
    { at: 0, text: `${language} um um um.` },
    { at: 8.2, text: `${language} dois dois.`, say: `${language} dois falado.` },
    { at: 19.5, text: `${language} tres tres.` },
]
const minimize = { id: 'minimize', lines: { en: beats('en'), pt: beats('pt') } }
const single = { id: 'title', lines: { en: { text: 'Usage Monitor.' }, pt: { text: 'Usage Monitor.' } } }
const secs = (en, pt = en) => ({ en, pt })

test('beatsOf: objeto unico vira um beat em 0 com a chave do id; lista ganha chave id-n e mantem o say', () => {
    assert.deepEqual(beatsOf(single, 'en'), [{ at: 0, text: 'Usage Monitor.', say: undefined, key: 'title' }])
    const list = beatsOf(minimize, 'pt')
    assert.deepEqual(list.map((beat) => [beat.key, beat.at]), [['minimize-1', 0], ['minimize-2', 8.2], ['minimize-3', 19.5]])
    assert.equal(list[1].say, 'pt dois falado.')
    assert.deepEqual(beatsOf({ id: 'one', lines: { en: [{ at: 0, text: 'x' }] } }, 'en').map((beat) => beat.key), ['one'])
})

test('timeline: cada beat toca em max(voiceAt + at, fim do anterior + 0,35 s) e a cena dura ate o fim do ultimo + respiro', () => {
    const durations = {
        en: { 'minimize-1': 5, 'minimize-2': 4, 'minimize-3': 9 },
        pt: { 'minimize-1': 6, 'minimize-2': 4, 'minimize-3': 9 },
    }
    const timeline = buildTimeline([minimize], durations, RELEASE_PACING)
    const [scene] = timeline.scenes
    assert.equal(PACING.beatGap, 0.35)
    assert.deepEqual(scene.beats.map((beat) => beat.key), ['minimize-1', 'minimize-2', 'minimize-3'])
    assert.equal(scene.beats[0].start, scene.voiceAt)
    assert.equal(scene.beats[1].start, scene.voiceAt + 8.2)
    assert.equal(scene.beats[2].start, scene.voiceAt + 19.5)
    assert.equal(scene.end, scene.beats[2].start + 9 + RELEASE_PACING.breath)
})

test('timeline: fala longa empurra o beat seguinte para depois do fim dela + 0,35 s, igual nos dois idiomas', () => {
    const durations = secs({ 'minimize-1': 8.7, 'minimize-2': 4, 'minimize-3': 5 }, { 'minimize-1': 9, 'minimize-2': 4, 'minimize-3': 5 })
    const [scene] = buildTimeline([minimize], durations, RELEASE_PACING).scenes
    assert.ok(Math.abs(scene.beats[1].start - (scene.voiceAt + 9 + 0.35)) < 1e-9)
})

test('timeline: a cena dura no minimo a sequencia de quadros + sequenceTail mesmo com as falas curtas', () => {
    const durations = secs({ 'minimize-1': 2, 'minimize-2': 2, 'minimize-3': 2 })
    const [scene] = buildTimeline([minimize], durations, RELEASE_PACING, { minimize: 30.6 }).scenes
    assert.equal(scene.end, scene.start + 30.6 + RELEASE_PACING.sequenceTail)
})

test('timeline: beat que ultrapassa o at do proximo em mais de 1,5 s e recusado, com a mensagem', () => {
    const durations = secs({ 'minimize-1': 10, 'minimize-2': 4, 'minimize-3': 5 })
    assert.throws(() => buildTimeline([minimize], durations, RELEASE_PACING), /minimize.*fala 1.*1,8 s.*fala 2.*1,5 s/)
})

test('timeline: ritmo checado por beat, com o numero do beat na mensagem', () => {
    const durations = secs({ 'minimize-1': 5, 'minimize-2': 0.5, 'minimize-3': 5 })
    assert.throws(() => buildTimeline([minimize], durations, RELEASE_PACING), /minimize#2 \(en\)/)
})

test('validador: beats fora de ordem ou sem texto sao recusados', () => {
    const bad = { id: 'm', kind: 'screen', lines: { en: [{ at: 5, text: 'a' }, { at: 2, text: 'b' }], pt: [{ at: 0, text: 'a' }, { at: 3 }] } }
    const problems = sceneProblems(bad)
    assert.equal(problems.length, 2)
    assert.match(problems[0], /m.*en.*fora de ordem/)
    assert.match(problems[1], /m.*pt.*texto/)
    assert.deepEqual(sceneProblems({ id: 'ok', lines: { en: beats('en'), pt: { text: 'x' } } }), [])
})

test('legendas: cada beat fica no seu instante, com a duracao da propria fala', () => {
    const durations = secs({ 'minimize-1': 3, 'minimize-2': 2, 'minimize-3': 4 })
    const timeline = buildTimeline([minimize], durations, RELEASE_PACING)
    const cues = captionCues([minimize], timeline, durations, 'en', RELEASE_PACING)
    const [first, second, third] = timeline.scenes[0].beats
    assert.equal(cues.length, 3)
    assert.equal(cues[0].start, first.start - RELEASE_PACING.captionLead)
    assert.equal(cues[1].start, second.start - RELEASE_PACING.captionLead)
    assert.equal(cues[1].end, second.start + 2 + RELEASE_PACING.captionTail)
    assert.equal(cues[2].end, third.start + 4 + RELEASE_PACING.captionTail)
    assert.deepEqual(cues[1].lines.join(' '), 'en dois dois.')
})

test('voiceTracks: uma entrada por beat; timeline antiga sem beats cai na fala unica em voiceAt', () => {
    const table = { title: 2, 'minimize-1': 5, 'minimize-2': 4, 'minimize-3': 5 }
    const timeline = buildTimeline([single, minimize], secs(table), RELEASE_PACING)
    const tracks = voiceTracks([single, minimize], timeline)
    assert.deepEqual(tracks.map((track) => track.key), ['title', 'minimize-1', 'minimize-2', 'minimize-3'])
    assert.equal(tracks[2].at, timeline.scenes[1].beats[1].start)
    assert.deepEqual(voiceTracks([{ id: 'a' }], { scenes: [{ voiceAt: 1.4 }] }), [{ key: 'a', at: 1.4 }])
})

test('trilha do preview: cada beat no seu instante', () => {
    const args = previewAudioArgs({
        scenes: [{ id: 'm' }],
        timeline: { total: 40, scenes: [{ voiceAt: 1, beats: [{ key: 'm-1', start: 1 }, { key: 'm-2', start: 9.2 }] }] },
        voiceDir: '/voz/en',
        output: '/out/en.mp3',
    })
    const filter = args[args.indexOf('-filter_complex') + 1]
    assert.ok(filter.includes('adelay=1000:all=1') && filter.includes('adelay=9200:all=1'))
    assert.ok(filter.includes('amix=inputs=2'))
    assert.ok(args.includes(join('/voz/en', 'm-1.wav')) && args.includes(join('/voz/en', 'm-2.wav')))
})

function makeWav(seconds, rate = 24000) {
    const data = Buffer.alloc(Math.round(seconds * rate) * 2)
    const header = Buffer.alloc(44)
    header.write('RIFF', 0)
    header.writeUInt32LE(36 + data.length, 4)
    header.write('WAVE', 8)
    header.write('fmt ', 12)
    header.writeUInt32LE(16, 16)
    header.writeUInt16LE(1, 20)
    header.writeUInt16LE(1, 22)
    header.writeUInt32LE(rate, 24)
    header.writeUInt32LE(rate * 2, 28)
    header.writeUInt16LE(2, 32)
    header.writeUInt16LE(16, 34)
    header.write('data', 36)
    header.writeUInt32LE(data.length, 40)
    return Buffer.concat([header, data])
}

test('sintese: um wav por beat (<cena>-<n>.wav), say vence text, durations por beat e cache por assinatura', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'beats-test-'))
    const calls = []
    const synthesize = async ({ text }) => {
        calls.push(text)
        return { bytes: Buffer.from(text), format: 'mp3' }
    }
    const convert = ({ bytes, target }) => writeFileSync(target, makeWav(bytes.length / 10))
    const project = { scenes: [single, minimize] }
    const durations = await speakScenes({ project, outDir: dir, synthesize, convert })
    assert.equal(calls.length, 2 * (1 + 3))
    assert.ok(calls.includes('en dois falado.'))
    assert.deepEqual(Object.keys(durations.en), ['title', 'minimize-1', 'minimize-2', 'minimize-3'])
    assert.ok(existsSync(join(dir, 'pt', 'minimize-2.wav')) && existsSync(join(dir, 'pt', 'minimize-2.sig')))
    assert.deepEqual(JSON.parse(readFileSync(join(dir, 'durations.json'), 'utf8')), durations)
    await speakScenes({ project, outDir: dir, synthesize, convert })
    assert.equal(calls.length, 8, 'segunda execucao nao chama a API')
    const edited = structuredClone(project)
    edited.scenes[1].lines.en[2].text = 'en tres alterado.'
    await speakScenes({ project: edited, outDir: dir, synthesize, convert })
    assert.equal(calls.length, 9, 'so o beat editado e sintetizado de novo')
})
