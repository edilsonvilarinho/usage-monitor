import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { DEFAULT_VOICE } from '../openrouter.mjs'
import { speakScenes, spokenText, speechSignature, voicePlan, wavSeconds } from '../speak.mjs'

// WAV PCM 16 bits mono com a duracao pedida.
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

const project = {
    scenes: [
        { id: 'title', lines: { en: { text: 'Usage Monitor 42.' }, pt: { text: 'Usage Monitor 42.', say: 'Usage Monitor, quarenta e dois.' } } },
        { id: 'status', lines: { en: { text: 'Every quota in one command.' }, pt: { text: 'Todas as cotas num comando só.' } } },
    ],
}

test('assinatura muda com modelo, voz ou texto e fica igual com os mesmos dados', () => {
    const base = { model: 'm', voice: 'v', text: 't' }
    assert.equal(speechSignature(base), speechSignature({ ...base }))
    for (const change of [{ model: 'x' }, { voice: 'x' }, { text: 'x' }]) {
        assert.notEqual(speechSignature(base), speechSignature({ ...base, ...change }))
    }
})

test('plano de voz: padrao do openrouter sem bloco voice; sobrescrito pelo roteiro', () => {
    assert.deepEqual(voicePlan({ scenes: [] }), { model: DEFAULT_VOICE.model, voice: { en: 'george', pt: 'george' } })
    assert.deepEqual(voicePlan({ voice: { model: 'x/y', voice: { en: 'a', pt: 'b' } } }), {
        model: 'x/y',
        voice: { en: 'a', pt: 'b' },
    })
    assert.deepEqual(voicePlan({ voice: { voice: 'ana' } }), { model: DEFAULT_VOICE.model, voice: { en: 'ana', pt: 'ana' } })
})

test('texto falado: say vence text', () => {
    assert.equal(spokenText(project.scenes[0], 'pt'), 'Usage Monitor, quarenta e dois.')
    assert.equal(spokenText(project.scenes[0], 'en'), 'Usage Monitor 42.')
})

test('duracao do wav vem do cabecalho; arquivo que nao e wav falha', () => {
    assert.equal(wavSeconds(makeWav(2.5)), 2.5)
    assert.throws(() => wavSeconds(Buffer.from('nope, nao e wav')), /WAV/)
})

test('speakScenes: uma chamada por cena e idioma, durations.json no formato do synthesize.py', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'speak-test-'))
    const calls = []
    const synthesize = async (args) => {
        calls.push(args)
        return { bytes: Buffer.from(args.text), format: 'mp3' }
    }
    const convert = ({ bytes, target }) => writeFileSync(target, makeWav(bytes.length / 10))
    const durations = await speakScenes({ project, outDir: dir, synthesize, convert })
    assert.equal(calls.length, 4)
    const sayCall = calls.find((call) => call.text === 'Usage Monitor, quarenta e dois.')
    assert.equal(sayCall.model, 'elevenlabs/eleven-v4')
    assert.equal(sayCall.voice, 'george')
    assert.deepEqual(durations, {
        en: { title: 1.7, status: 2.7 },
        pt: { title: 3.1, status: 3.1 },
    })
    assert.deepEqual(JSON.parse(readFileSync(join(dir, 'durations.json'), 'utf8')), durations)
    assert.ok(existsSync(join(dir, 'pt', 'title.wav')))
    assert.ok(existsSync(join(dir, 'en', 'status.sig')))
})

test('cache: segunda execucao nao chama a API; so a cena com texto novo e sintetizada de novo', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'speak-test-'))
    const synthesize = async ({ text }) => ({ bytes: Buffer.from(text), format: 'mp3' })
    const convert = ({ bytes, target }) => writeFileSync(target, makeWav(bytes.length / 10))
    await speakScenes({ project, outDir: dir, synthesize, convert })
    let count = 0
    const counting = async (args) => {
        count += 1
        return synthesize(args)
    }
    await speakScenes({ project, outDir: dir, synthesize: counting, convert })
    assert.equal(count, 0)
    const edited = structuredClone(project)
    edited.scenes[0].lines.pt.say = 'Usage Monitor, quarenta e dois, agora.'
    const after = await speakScenes({ project: edited, outDir: dir, synthesize: counting, convert })
    assert.equal(count, 1)
    assert.ok(after.pt.title > 3.1)
})
