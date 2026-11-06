import { test } from 'node:test'
import assert from 'node:assert/strict'
import { speechRequest, segmentName, synthesize, estimateDuration } from '../voice.mjs'

const KEY = 'sk-or-v1-chave-de-teste-0123456789'
const URL_SPEECH = 'https://openrouter.ai/api/v1/audio/speech'

// fetch simulado: cada chamada devolve a próxima resposta da fila (Response ou Error).
function fakeFetch(...queue) {
  const calls = []
  const fetchImpl = async (url, init) => {
    calls.push({ url, init })
    const next = queue.shift()
    if (next instanceof Error) throw next
    return next
  }
  return { fetchImpl, calls }
}
const bytesReply = (status, bytes = [1, 2, 3]) => new Response(new Uint8Array(bytes), { status })
const textReply = (status, text) => new Response(text, { status })

test('speechRequest monta o corpo: modelo, voz por idioma, texto e mp3', () => {
  assert.deepEqual(speechRequest('Olá, mundo.', 'pt'), {
    model: 'elevenlabs/eleven-v4',
    voice: 'matilda',
    input: 'Olá, mundo.',
    response_format: 'mp3',
  })
  assert.deepEqual(speechRequest('Hello.', 'en'), {
    model: 'elevenlabs/eleven-v4',
    voice: 'george',
    input: 'Hello.',
    response_format: 'mp3',
  })
})

test('speechRequest recusa idioma fora de pt e en', () => {
  assert.throws(() => speechRequest('Hola.', 'es'), RangeError)
})

test('segmentName segue <kind>-<lang>-<id>-<hash8>.mp3', () => {
  assert.match(segmentName('short', 'pt', 'abertura', 'Texto falado.'), /^short-pt-abertura-[0-9a-f]{8}\.mp3$/)
})

test('segmentName: texto igual reaproveita o nome; texto alterado gera outro', () => {
  const first = segmentName('long', 'en', 'fim', 'Same text.')
  assert.equal(segmentName('long', 'en', 'fim', 'Same text.'), first)
  assert.notEqual(segmentName('long', 'en', 'fim', 'Same text!'), first)
})

test('synthesize envia o corpo e Authorization, e devolve os bytes do 200', async () => {
  const { fetchImpl, calls } = fakeFetch(bytesReply(200, [7, 8, 9]))
  const bytes = await synthesize({ text: 'Olá.', lang: 'pt', key: KEY, fetchImpl })
  assert.deepEqual([...bytes], [7, 8, 9])
  assert.equal(calls.length, 1)
  assert.equal(calls[0].url, URL_SPEECH)
  assert.equal(calls[0].init.method, 'POST')
  assert.equal(calls[0].init.headers.Authorization, `Bearer ${KEY}`)
  assert.deepEqual(JSON.parse(calls[0].init.body), speechRequest('Olá.', 'pt'))
})

test('synthesize tenta de novo em 429 e devolve o resultado da segunda tentativa', async () => {
  const { fetchImpl, calls } = fakeFetch(textReply(429, 'rate'), bytesReply(200, [4]))
  const bytes = await synthesize({ text: 'Oi.', lang: 'pt', key: KEY, fetchImpl, retryDelayMs: 0 })
  assert.deepEqual([...bytes], [4])
  assert.equal(calls.length, 2)
})

test('synthesize tenta de novo em 5xx e desiste após 3 tentativas', async () => {
  const { fetchImpl, calls } = fakeFetch(textReply(503, 'a'), textReply(502, 'b'), textReply(500, 'c'))
  await assert.rejects(synthesize({ text: 'Oi.', lang: 'pt', key: KEY, fetchImpl, retryDelayMs: 0 }), /500/)
  assert.equal(calls.length, 3)
})

test('synthesize não tenta de novo em 400 e a mensagem traz status e corpo', async () => {
  const { fetchImpl, calls } = fakeFetch(textReply(400, 'modelo inválido'))
  await assert.rejects(
    synthesize({ text: 'Oi.', lang: 'pt', key: KEY, fetchImpl, retryDelayMs: 0 }),
    (err) => {
      assert.match(err.message, /400/)
      assert.match(err.message, /modelo inválido/)
      return true
    },
  )
  assert.equal(calls.length, 1)
})

test('synthesize: a chave não aparece na mensagem de erro, mesmo se o corpo a ecoar', async () => {
  const { fetchImpl } = fakeFetch(textReply(401, `chave recusada: ${KEY}`))
  await assert.rejects(
    synthesize({ text: 'Oi.', lang: 'pt', key: KEY, fetchImpl, retryDelayMs: 0 }),
    (err) => {
      assert.match(err.message, /401/)
      assert.ok(!err.message.includes(KEY), 'a chave vazou na mensagem')
      return true
    },
  )
})

test('estimateDuration: caracteres divididos por 12,2', () => {
  assert.ok(Math.abs(estimateDuration('a'.repeat(122)) - 10) < 1e-9)
  assert.equal(estimateDuration(''), 0)
})
