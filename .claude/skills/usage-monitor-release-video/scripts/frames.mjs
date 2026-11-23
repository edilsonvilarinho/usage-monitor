// Cena `screen` com sequência de quadros (#633): um demo gravado vira NNN.png + frames.json (file, delayMillis).
// As funções de tempo são puras; o video.html tem a mesma frameIndexAt (testada em scripts/test/frames.test.mjs).
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

export const LANGUAGE_CODES = ['en', 'pt']

// Quadro mostrado `seconds` depois do início da cena. Cada quadro dura o seu delayMillis; depois do último
// segura o último, ou recomeça do primeiro se `loop`. Antes do início mostra o primeiro.
export function frameIndexAt(frames, seconds, loop = false) {
    const total = frames.reduce((sum, frame) => sum + frame.delayMillis, 0)
    let ms = Math.max(0, seconds * 1000)
    if (loop && total > 0) ms %= total
    let accumulated = 0
    for (let index = 0; index < frames.length; index += 1) {
        accumulated += frames[index].delayMillis
        if (ms < accumulated) return index
    }
    return frames.length - 1
}

// "build/video-media/{lang}/demo" → "build/video-media/pt/demo"
export function framesPathFor(pattern, language) {
    return pattern.replaceAll('{lang}', language)
}

// Copia os quadros de cada fonte `frames` para stage/<chave>/<lang>/ e devolve { [chave]: { [lang]: [{file, delayMillis}] } }.
export function copyFrameSequences({ root, stage, sources, languages = LANGUAGE_CODES }) {
    const sequences = {}
    for (const [key, source] of Object.entries(sources ?? {})) {
        if (!source.frames) continue
        sequences[key] = {}
        for (const language of languages) {
            const from = join(root, framesPathFor(source.frames, language))
            const frames = JSON.parse(readFileSync(join(from, 'frames.json'), 'utf8'))
            const to = join(stage, key, language)
            mkdirSync(to, { recursive: true })
            for (const frame of frames) {
                if (!existsSync(join(from, frame.file))) throw new Error(`quadro ausente: ${join(from, frame.file)}`)
                copyFileSync(join(from, frame.file), join(to, frame.file))
            }
            writeFileSync(join(to, 'frames.json'), JSON.stringify(frames, null, 2))
            sequences[key][language] = frames
        }
    }
    return sequences
}

// Roteiro do idioma: cena `screen` com "frames" recebe `sequence` = [{ src, delayMillis }], com src relativo ao palco.
export function withSequences(scenes, sequences, language) {
    return scenes.map((scene) => {
        if (scene.kind !== 'screen' || !scene.frames) return scene
        const frames = sequences[scene.frames]?.[language]
        if (!frames) throw new Error(`cena ${scene.id}: fonte de quadros "${scene.frames}" não copiada`)
        const sequence = frames.map((frame) => ({ src: `${scene.frames}/${language}/${frame.file}`, delayMillis: frame.delayMillis }))
        return { ...scene, sequence }
    })
}
