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
    minTotal: 0,
    maxTotal: 90,
    captionChars: 26,
    captionLines: 2,
    captionLead: 0.15,
    captionTail: 0.3,
}

// Tour em vídeo 16:9 (#505): 5 a 10 minutos. A entrada de cada capítulo cabe a lente (1,1 s) e o cartão com o
// número e o nome (1,3 s); a abertura, a subida e o anel acendendo. O pedido do dono foi "não pode ficar muito
// rápido": 14 caracteres/s e 1,4 s de respiro. Legenda de 16:9: duas linhas de 42 caracteres.
export const TOUR_PACING = {
    ...PACING,
    firstEnter: 3.2,
    enter: 2.6,
    breath: 1.4,
    lastExit: 3.0,
    maxCharsPerSecond: 14,
    minTotal: 300,
    maxTotal: 600,
    captionChars: 42,
}

// Onde, dentro do capítulo, o foco de uma captura acende: no primeiro terço do tempo dela.
const FOCUS_AT = 0.35
// Os comandos são digitados a 30 caracteres/s, com 0,2 s até a saída e a pausa da cena entre eles.
const TYPING_RATE = 30
const OUTPUT_DELAY = 0.2

export const LANGUAGES = ['en', 'pt']

// Caracteres, não palavras: o português tem muitas palavras curtas e passaria por rápido demais
// numa contagem de palavras; caractere por segundo é também a medida das normas de legenda.
export function spokenLength(line) {
    return (line.say ?? line.text).length
}

// Capturas de um capítulo: a primeira entra com a fala, as outras dividem o resto do capítulo em partes iguais.
function shotsOf(scene, voiceAt, end) {
    if (!scene.media) return undefined
    const span = (end - voiceAt) / scene.media.length
    return scene.media.map((shot, index) => {
        const at = voiceAt + index * span
        return { at, focusAt: shot.focus ? at + span * FOCUS_AT : null }
    })
}

export function buildTimeline(scenes, durations, pacing = PACING) {
    const problems = []
    let cursor = 0
    const timed = scenes.map((scene, index) => {
        for (const language of LANGUAGES) {
            const seconds = durations[language][scene.id]
            const characters = spokenLength(scene.lines[language])
            if (characters / seconds > pacing.maxCharsPerSecond) {
                const rate = (characters / seconds).toFixed(1)
                problems.push(`${scene.id} (${language}): ${characters} caracteres em ${seconds.toFixed(2)} s (${rate}/s)`)
            }
        }
        const longest = Math.max(...LANGUAGES.map((language) => durations[language][scene.id]))
        const start = cursor
        const voiceAt = start + (index === 0 ? pacing.firstEnter : pacing.enter)
        const end = voiceAt + longest + pacing.breath
        cursor = end
        const shots = shotsOf(scene, voiceAt, end)
        return shots ? { id: scene.id, start, voiceAt, end, shots } : { id: scene.id, start, voiceAt, end }
    })
    if (problems.length > 0) {
        throw new Error(`Fala acima de ${pacing.maxCharsPerSecond} caracteres/s — corte texto:\n${problems.join('\n')}`)
    }
    const total = cursor + pacing.lastExit
    if (total > pacing.maxTotal) {
        throw new Error(`Vídeo de ${total.toFixed(1)} s passa de ${pacing.maxTotal} s — corte cenas ou texto`)
    }
    if (total < pacing.minTotal) {
        throw new Error(`Vídeo de ${total.toFixed(1)} s fica abaixo de ${pacing.minTotal} s — falta conteúdo`)
    }
    return { total, scenes: timed }
}

// Efeitos sonoros, cada um no instante de um acontecimento da tela: a subida antes do anel acender, a lente na
// entrada de cada capítulo, o deslize de cada captura nova, o sino de cada foco e as teclas de cada comando digitado.
export function soundEvents(scenes, timeline, language) {
    const events = [{ name: 'riser', at: 0 }]
    scenes.forEach((scene, index) => {
        const timing = timeline.scenes[index]
        if (index > 0) events.push({ name: 'lens', at: timing.start })
        timing.shots?.forEach((shot, position) => {
            if (position > 0) events.push({ name: 'whoosh', at: shot.at })
            if (shot.focusAt !== null) events.push({ name: 'chime', at: shot.focusAt })
        })
        let cursor = timing.voiceAt
        for (const command of scene.commands ?? []) {
            const text = typeof command.cmd === 'object' ? command.cmd[language] : command.cmd
            const duration = text.length / TYPING_RATE
            events.push({ name: 'keys', at: cursor, duration })
            cursor += duration + OUTPUT_DELAY + (scene.pause ?? 0.5)
        }
    })
    return events
}

function wrapLines(words, pacing) {
    const lines = []
    let current = ''
    for (const word of words) {
        const candidate = current ? `${current} ${word}` : word
        if (candidate.length > pacing.captionChars && current) {
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
function balancedLines(words, count, pacing) {
    const size = (from, to) => words.slice(from, to).join(' ').length
    const lineCost = (from, to) => (pacing.captionChars - size(from, to)) ** 2 + (to - from === 1 ? 10_000 : 0)
    const memo = new Map()
    const best = (from, left) => {
        const key = `${from}:${left}`
        if (memo.has(key)) return memo.get(key)
        let result = null
        if (left === 1) {
            const length = size(from, words.length)
            if (length <= pacing.captionChars) result = { cost: lineCost(from, words.length), breaks: [] }
        } else {
            for (let to = from + 1; to <= words.length - (left - 1); to += 1) {
                const length = size(from, to)
                if (length > pacing.captionChars) break
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
export function captionBlocks(text, pacing = PACING) {
    const words = text.split(/\s+/).filter(Boolean)
    const greedy = wrapLines(words, pacing)
    const count = greedy.length > 1 && greedy.length % 2 === 1 ? greedy.length + 1 : greedy.length
    const lines = (count <= words.length && balancedLines(words, count, pacing))
        || balancedLines(words, greedy.length, pacing) || greedy
    const blocks = []
    for (let index = 0; index < lines.length; index += pacing.captionLines) {
        blocks.push(lines.slice(index, index + pacing.captionLines))
    }
    return blocks
}

// Cada bloco fica na tela o tempo proporcional aos seus caracteres dentro da fala.
export function captionCues(scenes, timeline, durations, language, pacing = PACING) {
    const cues = []
    scenes.forEach((scene, index) => {
        const voiceAt = timeline.scenes[index].voiceAt
        const spoken = durations[language][scene.id]
        const blocks = captionBlocks(scene.lines[language].text, pacing)
        const sizes = blocks.map((block) => block.join(' ').length)
        const totalChars = sizes.reduce((sum, size) => sum + size, 0)
        let before = 0
        blocks.forEach((block, position) => {
            const from = voiceAt + (spoken * before) / totalChars
            before += sizes[position]
            const last = position === blocks.length - 1
            cues.push({
                start: position === 0 ? from - pacing.captionLead : from,
                end: last ? voiceAt + spoken + pacing.captionTail : voiceAt + (spoken * before) / totalChars,
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

