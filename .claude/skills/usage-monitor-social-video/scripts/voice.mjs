// Voz do video social (#542): OpenRouter, elevenlabs/eleven-v4 (matilda em PT, george em EN).
// fetchImpl e retryDelayMs entram por parametro (testes). A chave nunca entra em mensagem de erro.
import { createHash } from 'node:crypto'

export const SPEECH_URL = 'https://openrouter.ai/api/v1/audio/speech'
const MODEL = 'elevenlabs/eleven-v4'
const VOICE = { pt: 'matilda', en: 'george' }
const MAX_ATTEMPTS = 3
const CHARS_PER_SECOND = 12.2

// Corpo do POST /audio/speech.
export function speechRequest(text, lang) {
  const voice = VOICE[lang]
  if (!voice) throw new RangeError(`idioma sem voz: ${lang}`)
  return { model: MODEL, voice, input: text, response_format: 'mp3' }
}

// <kind>-<lang>-<id>-<hash8>.mp3; o hash e do texto falado.
export function segmentName(kind, lang, id, text) {
  const hash = createHash('sha256').update(text, 'utf8').digest('hex').slice(0, 8)
  return `${kind}-${lang}-${id}-${hash}.mp3`
}

// Devolve os bytes do MP3. Nova tentativa so em 429 ou 5xx (ate 3 tentativas).
export async function synthesize({ text, lang, key, fetchImpl = fetch, retryDelayMs = 2000 }) {
  if (!key) throw new Error('chave da OpenRouter ausente')
  const body = JSON.stringify(speechRequest(text, lang))
  for (let attempt = 1; ; attempt++) {
    const res = await fetchImpl(SPEECH_URL, {
      method: 'POST',
      headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
      body,
    })
    if (res.status === 200) return Buffer.from(await res.arrayBuffer())
    const detail = (await res.text()).split(key).join('[chave oculta]')
    const retryable = res.status === 429 || res.status >= 500
    if (retryable && attempt < MAX_ATTEMPTS) {
      await new Promise((resolveWait) => setTimeout(resolveWait, retryDelayMs * attempt))
      continue
    }
    throw new Error(`OpenRouter respondeu ${res.status}: ${detail}`)
  }
}

// Duracao estimada da fala: caracteres (pontos de codigo) / 12,2.
export function estimateDuration(text) {
  return [...text].length / CHARS_PER_SECOND
}
