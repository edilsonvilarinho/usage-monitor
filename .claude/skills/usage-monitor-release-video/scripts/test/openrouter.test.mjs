import { test } from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_IMAGE_MODEL, DEFAULT_VOICE, generateImage, speechFormat, synthesizeSpeech } from '../openrouter.mjs'

const KEY = 'sk-or-test-123'

function fakeFetch(status, body, contentType = 'application/json') {
    const calls = []
    const fn = async (url, init) => {
        calls.push({ url, init })
        return new Response(body, { status, headers: { 'Content-Type': contentType } })
    }
    return { fn, calls }
}

const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x01])

test('os padroes: voz da escolha do dono e modelo de imagem', () => {
    assert.deepEqual(DEFAULT_VOICE, { model: 'elevenlabs/eleven-v4', voice: 'george' })
    assert.equal(DEFAULT_IMAGE_MODEL, 'google/gemini-3.1-flash-image')
})

test('formato: pcm so para google/*-tts, mp3 nos demais', () => {
    assert.equal(speechFormat('google/gemini-3.1-flash-tts'), 'pcm')
    assert.equal(speechFormat('microsoft/mai-voice-2.1'), 'mp3')
    assert.equal(speechFormat('elevenlabs/eleven-v4'), 'mp3')
    assert.equal(speechFormat('google/gemini-3.1-flash-image'), 'mp3')
})

test('fala: POST em /audio/speech com Bearer e corpo {model,input,voice,response_format}', async () => {
    const audio = Buffer.from('ID3-fake-mp3')
    const { fn, calls } = fakeFetch(200, audio, 'audio/mpeg')
    const result = await synthesizeSpeech({ text: 'Usage Monitor 42.', fetch: fn, apiKey: KEY })
    assert.equal(calls.length, 1)
    assert.equal(calls[0].url, 'https://openrouter.ai/api/v1/audio/speech')
    assert.equal(calls[0].init.method, 'POST')
    assert.equal(calls[0].init.headers.Authorization, `Bearer ${KEY}`)
    assert.deepEqual(JSON.parse(calls[0].init.body), {
        model: 'elevenlabs/eleven-v4',
        input: 'Usage Monitor 42.',
        voice: 'george',
        response_format: 'mp3',
    })
    assert.equal(result.format, 'mp3')
    assert.deepEqual(Buffer.from(result.bytes), audio)
})

test('fala: google/*-tts pede pcm e devolve pcm', async () => {
    const { fn, calls } = fakeFetch(200, Buffer.from([1, 2, 3, 4]), 'audio/pcm')
    const result = await synthesizeSpeech({
        text: 'oi', model: 'google/gemini-3.1-flash-tts', voice: 'Kore', fetch: fn, apiKey: KEY,
    })
    assert.equal(JSON.parse(calls[0].init.body).response_format, 'pcm')
    assert.equal(result.format, 'pcm')
})

test('fala: HTTP de erro lanca com o status e o corpo, sem a chave', async () => {
    const { fn } = fakeFetch(400, `{"error":"Gemini TTS only supports response_format=\\"pcm\\""}`)
    await assert.rejects(
        () => synthesizeSpeech({ text: 'oi', model: 'google/gemini-3.1-flash-tts', fetch: fn, apiKey: KEY }),
        (error) => {
            assert.match(error.message, /HTTP 400/)
            assert.match(error.message, /Gemini TTS only supports/)
            assert.doesNotMatch(error.message, new RegExp(KEY))
            return true
        },
    )
})

test('chave ausente: erro dizendo a variavel, sem chamar a rede', async () => {
    const saved = process.env.OPENROUTER_API_KEY
    delete process.env.OPENROUTER_API_KEY
    try {
        const { fn, calls } = fakeFetch(200, Buffer.from('x'))
        await assert.rejects(() => synthesizeSpeech({ text: 'oi', fetch: fn }), /OPENROUTER_API_KEY não definida/)
        await assert.rejects(() => generateImage({ prompt: 'capa', fetch: fn }), /OPENROUTER_API_KEY não definida/)
        assert.equal(calls.length, 0)
    } finally {
        if (saved !== undefined) process.env.OPENROUTER_API_KEY = saved
    }
})

test('imagem: POST em /chat/completions com modalities e le a data URL e o custo', async () => {
    const body = JSON.stringify({
        choices: [{ message: { images: [{ image_url: { url: `data:image/png;base64,${PNG.toString('base64')}` } }] } }],
        usage: { cost: 0.067 },
    })
    const { fn, calls } = fakeFetch(200, body)
    const result = await generateImage({ prompt: 'capa sem tela', fetch: fn, apiKey: KEY })
    assert.equal(calls[0].url, 'https://openrouter.ai/api/v1/chat/completions')
    assert.equal(calls[0].init.headers.Authorization, `Bearer ${KEY}`)
    const sent = JSON.parse(calls[0].init.body)
    assert.equal(sent.model, 'google/gemini-3.1-flash-image')
    assert.deepEqual(sent.modalities, ['image', 'text'])
    assert.equal(sent.messages[0].content, 'capa sem tela')
    assert.deepEqual(Buffer.from(result.bytes), PNG)
    assert.equal(result.cost, 0.067)
})

test('imagem: resposta sem imagem lanca erro', async () => {
    const { fn } = fakeFetch(200, JSON.stringify({ choices: [{ message: { content: 'nao' } }] }))
    await assert.rejects(() => generateImage({ prompt: 'capa', fetch: fn, apiKey: KEY }), /não devolveu imagem/)
})

test('imagem: HTTP de erro lanca com o status', async () => {
    const { fn } = fakeFetch(402, '{"error":"insufficient credits"}')
    await assert.rejects(() => generateImage({ prompt: 'capa', fetch: fn, apiKey: KEY }), /HTTP 402/)
})
