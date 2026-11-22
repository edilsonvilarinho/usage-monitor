// Síntese das falas pela API do OpenRouter (#633): um WAV por cena e idioma, cacheado por assinatura
// (modelo, voz, texto), e durations.json no mesmo formato que o synthesize.py (Piper) produzia.
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { DEFAULT_VOICE, synthesizeSpeech } from './openrouter.mjs'

export const LANGUAGES = ['en', 'pt']

export function speechSignature({ model, voice, text }) {
    return createHash('sha256').update(JSON.stringify([model, voice, text])).digest('hex')
}

// Bloco "voice" do scenes.json: model e voice (string para os dois idiomas, ou {en, pt}); ausente, o padrão.
export function voicePlan(project) {
    const block = project.voice ?? {}
    const model = block.model ?? DEFAULT_VOICE.model
    const voice = block.voice ?? DEFAULT_VOICE.voice
    const perLanguage = typeof voice === 'string' ? { en: voice, pt: voice } : voice
    return {
        model,
        voice: { en: perLanguage.en ?? DEFAULT_VOICE.voice, pt: perLanguage.pt ?? DEFAULT_VOICE.voice },
    }
}

export function spokenText(scene, language) {
    const line = scene.lines[language]
    return line.say ?? line.text
}

// Duração em segundos a partir do cabeçalho RIFF/WAVE, com três casas como o synthesize.py.
export function wavSeconds(buffer) {
    if (buffer.toString('ascii', 0, 4) !== 'RIFF' || buffer.toString('ascii', 8, 12) !== 'WAVE') {
        throw new Error('não é WAV')
    }
    let offset = 12
    let byteRate = 0
    let dataSize = 0
    while (offset + 8 <= buffer.length) {
        const id = buffer.toString('ascii', offset, offset + 4)
        const size = buffer.readUInt32LE(offset + 4)
        if (id === 'fmt ') byteRate = buffer.readUInt32LE(offset + 16)
        if (id === 'data') {
            dataSize = Math.min(size, buffer.length - offset - 8)
            break
        }
        offset += 8 + size + (size % 2)
    }
    if (!byteRate || !dataSize) throw new Error('WAV sem cabeçalho fmt/data')
    return Math.round((dataSize / byteRate) * 1000) / 1000
}

// MP3 da API (ou PCM s16le 24 kHz do Gemini) para WAV 24 kHz mono, com o ffmpeg.
export function convertToWav({ bytes, format, target }) {
    const input = `${target}.${format}`
    writeFileSync(input, bytes)
    const raw = format === 'pcm' ? ['-f', 's16le', '-ar', '24000', '-ac', '1'] : []
    try {
        execFileSync('ffmpeg', ['-v', 'error', '-y', ...raw, '-i', input, '-ar', '24000', '-ac', '1', target])
    } finally {
        rmSync(input, { force: true })
    }
}

// Sintetiza só o que mudou: a assinatura de cada WAV fica em <id>.sig ao lado dele.
export async function speakScenes({ project, outDir, synthesize = synthesizeSpeech, convert = convertToWav, apiKey }) {
    const plan = voicePlan(project)
    const durations = {}
    for (const language of LANGUAGES) {
        const target = join(outDir, language)
        mkdirSync(target, { recursive: true })
        durations[language] = {}
        for (const scene of project.scenes) {
            const text = spokenText(scene, language)
            const voice = plan.voice[language]
            const signature = speechSignature({ model: plan.model, voice, text })
            const wav = join(target, `${scene.id}.wav`)
            const sig = join(target, `${scene.id}.sig`)
            if (!(existsSync(wav) && existsSync(sig) && readFileSync(sig, 'utf8') === signature)) {
                const { bytes, format } = await synthesize({ text, model: plan.model, voice, apiKey })
                convert({ bytes, format, target: wav })
                writeFileSync(sig, signature)
            }
            durations[language][scene.id] = wavSeconds(readFileSync(wav))
        }
    }
    writeFileSync(join(outDir, 'durations.json'), JSON.stringify(durations, null, 2))
    return durations
}
