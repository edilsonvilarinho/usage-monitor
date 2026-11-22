// Cliente do OpenRouter para a voz e a imagem do vídeo de release (#633). Sem dependências: fetch nativo.
// A chave vem só de OPENROUTER_API_KEY; nunca é gravada nem impressa.
export const DEFAULT_VOICE = { model: 'elevenlabs/eleven-v4', voice: 'george' }
export const DEFAULT_IMAGE_MODEL = 'google/gemini-3.1-flash-image'
const BASE = 'https://openrouter.ai/api/v1'

// Gemini TTS só aceita pcm (24 kHz, s16le); os demais modelos de fala devolvem mp3.
export function speechFormat(model) {
    return model.startsWith('google/') && model.endsWith('-tts') ? 'pcm' : 'mp3'
}

function keyOf(apiKey) {
    if (!apiKey) throw new Error('OPENROUTER_API_KEY não definida: exporte a chave do OpenRouter nessa variável de ambiente')
    return apiKey
}

async function post(fetchImpl, path, key, body) {
    const response = await fetchImpl(`${BASE}${path}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
    if (!response.ok) {
        const text = (await response.text().catch(() => '')).split(key).join('[chave]')
        throw new Error(`OpenRouter ${path} respondeu HTTP ${response.status}: ${text.slice(0, 500)}`)
    }
    return response
}

export async function synthesizeSpeech({
    text,
    model = DEFAULT_VOICE.model,
    voice = DEFAULT_VOICE.voice,
    fetch: fetchImpl = globalThis.fetch,
    apiKey = process.env.OPENROUTER_API_KEY,
}) {
    const key = keyOf(apiKey)
    const format = speechFormat(model)
    const response = await post(fetchImpl, '/audio/speech', key, { model, input: text, voice, response_format: format })
    return { bytes: Buffer.from(await response.arrayBuffer()), format }
}

export async function generateImage({
    prompt,
    model = DEFAULT_IMAGE_MODEL,
    fetch: fetchImpl = globalThis.fetch,
    apiKey = process.env.OPENROUTER_API_KEY,
}) {
    const key = keyOf(apiKey)
    const response = await post(fetchImpl, '/chat/completions', key, {
        model,
        messages: [{ role: 'user', content: prompt }],
        modalities: ['image', 'text'],
    })
    const json = await response.json()
    const url = json?.choices?.[0]?.message?.images?.[0]?.image_url?.url ?? ''
    const match = /^data:image\/[\w+.-]+;base64,(.+)$/s.exec(url)
    if (!match) throw new Error(`OpenRouter não devolveu imagem para ${model}`)
    return { bytes: Buffer.from(match[1], 'base64'), cost: json.usage?.cost ?? null }
}
