// Contrato video.json (references/video-spec.md): validacao, forma falada e variantes.
// Puro: nenhum I/O. Quem le arquivos injeta fileExists.

const LANGS = ['pt', 'en']
const BEATS_MIN = 3
const BEATS_MAX = 20
const SHORT_MIN = 3
const CLIMAX_MAX = 1
const TITLE_MAX = 34
const ITEMS_MAX = 8
const ITEM_MAX = { plain: 44, mono: 90 }
const IMAGE_ITEMS_MAX = 3
const SPOKEN_MAX = { short: 650, long: 3500 }
const SLUG_RE = /^[a-z0-9-]+$/
const ID_RE = /^[a-z-]+$/
const LINK_RE = /^https:\/\/\S+$/
const IMAGE_EXT = /\.(png|jpe?g)$/i

const isStr = (value) => typeof value === 'string'
const filled = (value) => isStr(value) && value.trim() !== ''
const len = (text) => [...text].length

// Regra padrao da forma falada. Palavra inteira: sem letra, numero, _ ou hifen colado.
const SPOKEN_RULES = [
    { term: 'ci-ok', pt: 'C I OK', en: 'C I OK' },
    { term: 'CI', pt: 'C I', en: 'C I' },
    { term: 'TDD', pt: 'T D D', en: 'T D D' },
    { term: 'PR', pt: 'P R', en: 'P R' },
    { term: 'CLAUDE.md', pt: 'Claude ponto M D', en: 'Claude dot M D' },
    { term: 'usage-monitor-workflow', pt: 'Usage Monitor Workflow', en: 'Usage Monitor Workflow' },
]
const SPOKEN_BY_TERM = new Map(SPOKEN_RULES.map((rule) => [rule.term, rule]))
const escapeRe = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const SPOKEN_RE = new RegExp(
    `(?<![\\p{L}\\p{N}_-])(${[...SPOKEN_RULES]
        .sort((a, b) => b.term.length - a.term.length)
        .map((rule) => escapeRe(rule.term))
        .join('|')})(?![\\p{L}\\p{N}_-])`,
    'gu',
)

// say: objeto { pt, en } (ou string). Explicito vence; sem ele, a regra padrao.
export function toSpoken(text, lang, say) {
    const explicit = typeof say === 'string' ? say : say?.[lang]
    if (explicit) return explicit
    return text.replace(SPOKEN_RE, (term) => SPOKEN_BY_TERM.get(term)?.[lang] ?? term)
}

// Textos falados de cada beat que tem o texto kind ('short' ou 'long') no idioma lang.
export function spokenTexts(spec, kind, lang) {
    if (kind !== 'short' && kind !== 'long') throw new RangeError('spokenTexts: kind precisa ser short ou long')
    const beats = Array.isArray(spec?.beats) ? spec.beats : []
    return beats.flatMap((beat) => {
        const entry = beat?.[kind]
        if (entry == null || !isStr(entry[lang])) return []
        return [toSpoken(entry[lang], lang, entry.say)]
    })
}

// Indice do beat clímax: o marcado com climax: true, ou o penultimo.
export function climaxIndex(spec) {
    const beats = Array.isArray(spec?.beats) ? spec.beats : []
    const index = beats.findIndex((beat) => beat?.climax === true)
    return index >= 0 ? index : beats.length - 2
}

// Nomes de arquivo de saida de uma linha de VARIANTS.
export function outputNames(slug, variant) {
    return variant.files.map((file) => `${slug}-${file}.mp4`)
}

const MODEL = 'elevenlabs/eleven-v4'
const VOICE = { pt: 'matilda', en: 'george' }
const variant = (name, lang, kind, format, files) => ({
    variant: name,
    lang,
    kind,
    format,
    voice: VOICE[lang],
    model: MODEL,
    files,
})

// Tabela "Variantes e plataformas". files: sufixo de cada arquivo, sem slug nem .mp4.
export const VARIANTS = Object.freeze([
    variant('short-pt', 'pt', 'short', '9:16', ['tiktok-pt', 'shorts-pt']),
    variant('short-pt', 'pt', 'short', '16:9', ['linkedin-pt']),
    variant('short-en', 'en', 'short', '16:9', ['x-en']),
    variant('long-pt', 'pt', 'long', '16:9', ['youtube-pt']),
    variant('long-en', 'en', 'long', '16:9', ['youtube-en']),
])

// Devolve a lista de erros; cada um nomeia o beat e o campo. Nao para no primeiro.
export function validateSpec(spec, { fileExists } = {}) {
    if (typeof fileExists !== 'function') throw new TypeError('validateSpec precisa de { fileExists }')
    const errors = []
    const err = (message) => errors.push(message)
    const beatsValid = Array.isArray(spec?.beats)
    const beats = beatsValid ? spec.beats : []
    const facts = Array.isArray(spec?.facts) ? spec.facts : []
    const factNumbers = new Set(facts.flatMap((fact) => String(fact).match(/\d+/g) ?? []))

    const secret = (text, where) => {
        if (isStr(text) && text.toLowerCase().includes('sk-or-')) err(`${where}: contém "sk-or-"`)
    }
    const numbers = (text, where) => {
        if (!isStr(text)) return
        for (const number of text.match(/\d+/g) ?? []) {
            if (number.length >= 2 && !factNumbers.has(number)) err(`${where}: número ${number} não aparece em facts`)
        }
    }

    // Regra 1
    if (!isStr(spec?.slug) || !SLUG_RE.test(spec.slug)) err('slug: use minúsculas, dígitos e hífen')
    if (!isStr(spec?.link) || !LINK_RE.test(spec.link)) err('link: precisa ser uma URL https://')

    // Regra 2
    if (!beatsValid) err('beats: precisa ser uma lista')
    else if (beats.length < BEATS_MIN) err(`beats: ${beats.length} beats; o mínimo é ${BEATS_MIN}`)
    else if (beats.length > BEATS_MAX) err(`beats: ${beats.length} beats; o máximo é ${BEATS_MAX}`)

    // Regra 4 (contagem) e regra 8
    if (beatsValid) {
        const shortCount = beats.filter((beat) => beat?.short != null).length
        if (shortCount < SHORT_MIN) err(`beats: só ${shortCount} beats têm short; o mínimo é ${SHORT_MIN}`)
    }
    const climaxCount = beats.filter((beat) => beat?.climax === true).length
    if (climaxCount > CLIMAX_MAX) err(`beats: ${climaxCount} beats com climax: true; no máximo ${CLIMAX_MAX}`)

    // Regra 10 (facts)
    facts.forEach((fact, index) => secret(fact, `facts[${index}]`))

    const ids = beats.map((beat) => beat?.id)
    beats.forEach((raw, index) => {
        const beat = raw ?? {}
        const last = index === beats.length - 1
        const where = isStr(beat.id) ? `beat "${beat.id}"` : `beat #${index + 1}`

        // Regra 2: id
        if (!isStr(beat.id) || !ID_RE.test(beat.id)) err(`${where} id: use minúsculas e hífen`)
        else if (ids.indexOf(beat.id) !== index) err(`${where} id: repetido`)

        // Regra 3: kicker e title; title ate 34
        for (const lang of LANGS) {
            const kicker = beat.kicker?.[lang]
            if (!filled(kicker)) err(`${where} kicker.${lang}: ausente`)
            else secret(kicker, `${where} kicker.${lang}`)

            const title = beat.title?.[lang]
            const path = `${where} title.${lang}`
            if (!filled(title)) err(`${path}: ausente`)
            else {
                if (len(title) > TITLE_MAX) err(`${path}: ${len(title)} caracteres; o máximo é ${TITLE_MAX}`)
                secret(title, path)
                numbers(title, path)
            }
        }

        // Regras 4 e 5: long obrigatorio, short opcional, tetos da forma falada
        for (const kind of ['long', 'short']) {
            const entry = beat[kind]
            if (entry == null) {
                if (kind === 'long') err(`${where} long: ausente`)
                else if (last) err(`${where} short: o último beat precisa de short`)
                continue
            }
            for (const lang of LANGS) {
                const text = entry[lang]
                const path = `${where} ${kind}.${lang}`
                if (!filled(text)) {
                    err(`${path}: ausente`)
                    continue
                }
                secret(text, path)
                numbers(text, path)
                const spoken = len(toSpoken(text, lang, entry.say))
                const max = SPOKEN_MAX[kind]
                if (spoken > max) err(`${path}: ${spoken} caracteres falados; cortar ${spoken - max} (máximo ${max})`)
            }
        }

        // Regra 6: items
        if (beat.items != null) {
            if (!Array.isArray(beat.items)) err(`${where} items: precisa ser uma lista`)
            else {
                if (beat.items.length > ITEMS_MAX) err(`${where} items: ${beat.items.length} itens; o máximo é ${ITEMS_MAX}`)
                beat.items.forEach((item, j) => {
                    const max = item?.mono === true ? ITEM_MAX.mono : ITEM_MAX.plain
                    for (const lang of LANGS) {
                        const text = item?.[lang]
                        const path = `${where} items[${j}].${lang}`
                        if (!filled(text)) err(`${path}: ausente`)
                        else {
                            if (len(text) > max) err(`${path}: ${len(text)} caracteres; o máximo é ${max}`)
                            secret(text, path)
                            numbers(text, path)
                        }
                    }
                    if (item?.status != null && !['ok', 'crit'].includes(item.status)) {
                        err(`${where} items[${j}].status: use ok ou crit`)
                    }
                })
            }
        }

        // Regra 2: o fechamento nao tem items nem image
        if (last && beat.items != null) err(`${where} items: o fechamento não tem itens`)
        if (last && beat.image != null) err(`${where} image: o fechamento não tem imagem`)

        // Regra 7: image
        if (beat.image != null) {
            const count = Array.isArray(beat.items) ? beat.items.length : 0
            if (count > IMAGE_ITEMS_MAX) err(`${where} items: ${count} itens com imagem; o máximo é ${IMAGE_ITEMS_MAX}`)
            if (!isStr(beat.image) || !IMAGE_EXT.test(beat.image)) {
                err(`${where} image: extensão precisa ser .png, .jpg ou .jpeg`)
            } else if (!fileExists(beat.image)) {
                err(`${where} image: arquivo não existe (${beat.image})`)
            }
        }
    })

    return errors
}
