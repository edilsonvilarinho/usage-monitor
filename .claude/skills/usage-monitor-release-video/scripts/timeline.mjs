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
    // Normas de legenda para adulto aceitam até 17 caracteres/s; 15 fica abaixo, sem pressa.
    maxCharsPerSecond: 15,
    maxTotal: 90,
    captionChars: 26,
    captionLines: 2,
    captionLead: 0.15,
    captionTail: 0.3,
}

export const LANGUAGES = ['en', 'pt']

// Caracteres, não palavras: o português tem muitas palavras curtas e passaria por rápido demais
// numa contagem de palavras; caractere por segundo é também a medida das normas de legenda.
export function spokenLength(line) {
    return (line.say ?? line.text).length
}

export function buildTimeline(scenes, durations) {
    const problems = []
    let cursor = 0
    const timed = scenes.map((scene, index) => {
        for (const language of LANGUAGES) {
            const seconds = durations[language][scene.id]
            const characters = spokenLength(scene.lines[language])
            if (characters / seconds > PACING.maxCharsPerSecond) {
                const rate = (characters / seconds).toFixed(1)
                problems.push(`${scene.id} (${language}): ${characters} caracteres em ${seconds.toFixed(2)} s (${rate}/s)`)
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
        throw new Error(`Fala acima de ${PACING.maxCharsPerSecond} caracteres/s — corte texto:\n${problems.join('\n')}`)
    }
    const total = cursor + PACING.lastExit
    if (total > PACING.maxTotal) {
        throw new Error(`Vídeo de ${total.toFixed(1)} s passa de ${PACING.maxTotal} s — corte cenas ou texto`)
    }
    return { total, scenes: timed }
}

function wrapLines(words) {
    const lines = []
    let current = ''
    for (const word of words) {
        const candidate = current ? `${current} ${word}` : word
        if (candidate.length > PACING.captionChars && current) {
            lines.push(current)
            current = word
        } else {
            current = candidate
        }
    }
    if (current) lines.push(current)
    return lines
}

// Quebra em exatamente `count` linhas de no máximo captionChars, com a menor irregularidade
// (soma dos quadrados da sobra), um desconto para terminar a linha em pontuação e uma multa
// pesada para linha de uma palavra só. Nula se não cabe.
function balancedLines(words, count) {
    const size = (from, to) => words.slice(from, to).join(' ').length
    const lineCost = (from, to) => (PACING.captionChars - size(from, to)) ** 2 + (to - from === 1 ? 10_000 : 0)
    const memo = new Map()
    const best = (from, left) => {
        const key = `${from}:${left}`
        if (memo.has(key)) return memo.get(key)
        let result = null
        if (left === 1) {
            const length = size(from, words.length)
            if (length <= PACING.captionChars) result = { cost: lineCost(from, words.length), breaks: [] }
        } else {
            for (let to = from + 1; to <= words.length - (left - 1); to += 1) {
                const length = size(from, to)
                if (length > PACING.captionChars) break
                const rest = best(to, left - 1)
                if (!rest) continue
                const punctuation = /[,.:;?!]$/.test(words[to - 1]) ? 60 : 0
                const cost = lineCost(from, to) - punctuation + rest.cost
                if (!result || cost < result.cost) result = { cost, breaks: [to, ...rest.breaks] }
            }
        }
        memo.set(key, result)
        return result
    }
    const plan = best(0, count)
    if (!plan) return null
    const edges = [0, ...plan.breaks, words.length]
    return edges.slice(1).map((edge, index) => words.slice(edges[index], edge).join(' '))
}

// Linhas equilibradas em blocos de 2: encher cada linha até o limite deixava palavra sozinha
// na tela (v42.0.0, abertura PT: "terminal." por 1,2 s). Com mais de uma linha, o total é par,
// para nenhum bloco sair com uma linha curta depois de um cheio.
export function captionBlocks(text) {
    const words = text.split(/\s+/).filter(Boolean)
    const greedy = wrapLines(words)
    const count = greedy.length > 1 && greedy.length % 2 === 1 ? greedy.length + 1 : greedy.length
    const lines = (count <= words.length && balancedLines(words, count)) || balancedLines(words, greedy.length) || greedy
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
