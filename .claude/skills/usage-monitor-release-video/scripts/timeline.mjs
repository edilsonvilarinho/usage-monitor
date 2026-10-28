// Linha do tempo do vídeo a partir da duração medida de cada fala, e as legendas que saem dela.
// Os dois idiomas dividem a mesma linha do tempo: cada cena dura a fala mais longa entre EN e PT.
// O ritmo é regra, não sugestão: fala rápida demais ou vídeo longo demais falham, e o conserto
// é cortar texto do roteiro — nunca acelerar a voz.

export const PACING = {
    // A abertura espera o anel acender; as outras cenas, a entrada do quadro.
    firstEnter: 1.4,
    enter: 0.6,
    // Silêncio depois de cada fala: o pedido do dono foi "não ficar muito rápido".
    breath: 1.2,
    lastExit: 1.8,
    maxWordsPerSecond: 2.6,
    maxTotal: 90,
    captionChars: 26,
    captionLines: 2,
    captionLead: 0.15,
    captionTail: 0.3,
}

export const LANGUAGES = ['en', 'pt']

export function wordCount(line) {
    return (line.say ?? line.text).split(/\s+/).filter((word) => /[\p{L}\p{N}]/u.test(word)).length
}

export function buildTimeline(scenes, durations) {
    const problems = []
    let cursor = 0
    const timed = scenes.map((scene, index) => {
        for (const language of LANGUAGES) {
            const seconds = durations[language][scene.id]
            const words = wordCount(scene.lines[language])
            if (words / seconds > PACING.maxWordsPerSecond) {
                problems.push(`${scene.id} (${language}): ${words} palavras em ${seconds.toFixed(2)} s`)
            }
        }
        const longest = Math.max(...LANGUAGES.map((language) => durations[language][scene.id]))
        const start = cursor
        const voiceAt = start + (index === 0 ? PACING.firstEnter : PACING.enter)
        const end = voiceAt + longest + PACING.breath
        cursor = end
        return { id: scene.id, start, voiceAt, end }
    })
    if (problems.length > 0) {
        throw new Error(`Fala acima de ${PACING.maxWordsPerSecond} palavras/s — corte texto:\n${problems.join('\n')}`)
    }
    const total = cursor + PACING.lastExit
    if (total > PACING.maxTotal) {
        throw new Error(`Vídeo de ${total.toFixed(1)} s passa de ${PACING.maxTotal} s — corte cenas ou texto`)
    }
    return { total, scenes: timed }
}

export function captionBlocks(text) {
    const lines = []
    let current = ''
    for (const word of text.split(/\s+/).filter(Boolean)) {
        const candidate = current ? `${current} ${word}` : word
        if (candidate.length > PACING.captionChars && current) {
            lines.push(current)
            current = word
        } else {
            current = candidate
        }
    }
    if (current) lines.push(current)
    const blocks = []
    for (let index = 0; index < lines.length; index += PACING.captionLines) {
        blocks.push(lines.slice(index, index + PACING.captionLines))
    }
    return blocks
}

// Cada bloco fica na tela o tempo proporcional aos seus caracteres dentro da fala.
export function captionCues(scenes, timeline, durations, language) {
    const cues = []
    scenes.forEach((scene, index) => {
        const voiceAt = timeline.scenes[index].voiceAt
        const spoken = durations[language][scene.id]
        const blocks = captionBlocks(scene.lines[language].text)
        const sizes = blocks.map((block) => block.join(' ').length)
        const totalChars = sizes.reduce((sum, size) => sum + size, 0)
        let before = 0
        blocks.forEach((block, position) => {
            const from = voiceAt + (spoken * before) / totalChars
            before += sizes[position]
            const last = position === blocks.length - 1
            cues.push({
                start: position === 0 ? from - PACING.captionLead : from,
                end: last ? voiceAt + spoken + PACING.captionTail : voiceAt + (spoken * before) / totalChars,
                lines: block,
            })
        })
    })
    for (let index = 0; index < cues.length - 1; index += 1) {
        cues[index].end = Math.min(cues[index].end, cues[index + 1].start)
    }
    return cues
}

function srtTime(seconds) {
    const millis = Math.round(seconds * 1000)
    const pad = (value, size) => String(value).padStart(size, '0')
    const hours = Math.floor(millis / 3_600_000)
    const minutes = Math.floor(millis / 60_000) % 60
    const secs = Math.floor(millis / 1000) % 60
    return `${pad(hours, 2)}:${pad(minutes, 2)}:${pad(secs, 2)},${pad(millis % 1000, 3)}`
}

export function toSrt(cues) {
    return cues
        .map((cue, index) => `${index + 1}\n${srtTime(cue.start)} --> ${srtTime(cue.end)}\n${cue.lines.join('\n')}\n`)
        .join('\n')
}
