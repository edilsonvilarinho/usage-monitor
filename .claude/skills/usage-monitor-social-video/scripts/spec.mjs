// Contrato video.json (references/video-spec.md): validacao, forma falada e variantes.
// Puro: nenhum I/O. Quem le arquivos injeta fileExists.

import { CODE_CARD, codeColumns, codeExcerpt } from './code-card.mjs'

const LANGS = ['pt', 'en']
const BEATS_MIN = 3
const SHORT_MIN = 3
const CLIMAX_MAX = 1
const TITLE_MAX = 34
const ITEMS_MAX = 8
const ITEM_MAX = { plain: 44, mono: 90 }
const IMAGE_ITEMS_MAX = 3
const SPOKEN_MAX_SHORT = 650
const SPOKEN_MAX_LONG_AT_300 = 3500 // teto do longo em 300 s; em 600 s, 3500 × 600 / 300
const SLUG_RE = /^[a-z0-9-]+$/
const ID_RE = /^[a-z-]+$/
const LINK_RE = /^https:\/\/\S+$/
const IMAGE_EXT = /\.(png|jpe?g)$/i

// Duração do corte longo (campo longSeconds, opcional). Padrão 300 s; 600 s é o outro valor permitido.
export const LONG_SECONDS = Object.freeze([300, 600])
const LONG_DEFAULT = 300
const BEATS_MAX_BY_LONG = Object.freeze({ 300: 20, 600: 30 })

// Teto de caracteres falados por idioma. Curto fixo; longo escala com longSeconds.
export function spokenMax(kind, longSeconds = LONG_DEFAULT) {
    return kind === 'short' ? SPOKEN_MAX_SHORT : (SPOKEN_MAX_LONG_AT_300 * longSeconds) / LONG_DEFAULT
}

// longSeconds efetivo do roteiro (padrão 300). Valor fora de 300/600 lança RangeError.
export function longSecondsOf(spec) {
    const value = spec?.longSeconds === undefined ? LONG_DEFAULT : spec.longSeconds
    if (!LONG_SECONDS.includes(value)) throw new RangeError(`longSeconds: use 300 ou 600 (veio ${JSON.stringify(value)})`)
    return value
}

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
    variant('long-pt', 'pt', 'long', '16:9', ['linkedin-pt', 'youtube-pt']),
    variant('long-en', 'en', 'long', '16:9', ['x-en', 'youtube-en']),
])

// Devolve a lista de erros; cada um nomeia o beat e o campo. Nao para no primeiro.
// readText(caminho relativo à raiz do repo) devolve o texto do arquivo, ou null se não existe (regra do code).
export function validateSpec(spec, { fileExists, readText = () => null } = {}) {
    if (typeof fileExists !== 'function') throw new TypeError('validateSpec precisa de { fileExists }')
    const errors = []
    const err = (message) => errors.push(message)
    // Valor inválido já sai como erro; os tetos usam o padrão para não repetir a falha.
    const longRaw = spec?.longSeconds
    const longValid = longRaw === undefined || LONG_SECONDS.includes(longRaw)
    if (!longValid) err("longSeconds: use 300 ou 600")
    const longSeconds = longValid && longRaw !== undefined ? longRaw : LONG_DEFAULT
    const beatsMax = BEATS_MAX_BY_LONG[longSeconds]
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
    else if (beats.length > beatsMax) err(`beats: ${beats.length} beats; o máximo é ${beatsMax}`)

    // Regra 4 (contagem) e regra 8
    if (beatsValid) {
        const shortCount = beats.filter((beat) => beat?.short != null).length
        if (shortCount < SHORT_MIN) err(`beats: só ${shortCount} beats têm short; o mínimo é ${SHORT_MIN}`)
    }
    const climaxCount = beats.filter((beat) => beat?.climax === true).length
    if (climaxCount > CLIMAX_MAX) err(`beats: ${climaxCount} beats com climax: true; no máximo ${CLIMAX_MAX}`)

    // Regra 10 (facts)
    facts.forEach((fact, index) => secret(fact, `facts[${index}]`))

    // Regra 11: trecho de código (file, from, to, caption opcional). Só roda para beat que não é o fechamento.
    const codeRules = (code, where) => {
        const path = `${where} code`
        if (code === null || typeof code !== 'object' || Array.isArray(code)) {
            err(`${path}: precisa ser um objeto { file, from, to }`)
            return
        }
        if (!filled(code.file)) {
            err(`${where} code.file: ausente`)
            return
        }
        const text = readText(code.file)
        if (!isStr(text)) err(`${where} code.file: arquivo não existe (${code.file})`)
        else {
            const total = text.replace(/\r?\n$/, '').split(/\r?\n/).length
            const { from, to } = code
            if (!(Number.isInteger(from) && Number.isInteger(to) && from >= 1 && from <= to && to <= total)) {
                err(`${path}: from e to precisam ser inteiros, com 1 ≤ from ≤ to ≤ ${total}`)
            } else {
                const count = to - from + 1
                if (count > CODE_CARD.maxLines) err(`${path}: ${count} linhas; o máximo é ${CODE_CARD.maxLines}`)
                const widest = Math.max(...codeExcerpt(text, from, to).map(codeColumns))
                if (widest > CODE_CARD.maxColumns) {
                    err(`${path}: linha mais longa com ${widest} colunas; o máximo é ${CODE_CARD.maxColumns}`)
                }
            }
        }
        if (code.caption != null) {
            if (typeof code.caption !== 'object') err(`${path}.caption: precisa ser { pt, en }`)
            else {
                for (const lang of LANGS) {
                    const caption = code.caption[lang]
                    const at = `${path}.caption.${lang}`
                    if (!filled(caption)) err(`${at}: ausente`)
                    else {
                        if (len(caption) > ITEM_MAX.plain) err(`${at}: ${len(caption)} caracteres; o máximo é ${ITEM_MAX.plain}`)
                        secret(caption, at)
                        numbers(caption, at)
                    }
                }
            }
        }
    }

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
                const max = spokenMax(kind, longSeconds)
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

        // Regra 11 (A03b): code, trecho real de arquivo; exclusivo com image; sem no fechamento
        if (beat.code != null) {
            if (last) err(`${where} code: o fechamento não tem código`)
            else {
                if (beat.image != null) err(`${where} code: não pode vir junto com image`)
                codeRules(beat.code, where)
            }
        }

        // Regra 7: image ou code, ate 3 itens
        if (beat.image != null || beat.code != null) {
            const count = Array.isArray(beat.items) ? beat.items.length : 0
            const kind = beat.code != null ? 'código' : 'imagem'
            if (count > IMAGE_ITEMS_MAX) err(`${where} items: ${count} itens com ${kind}; o máximo é ${IMAGE_ITEMS_MAX}`)
        }
        if (beat.image != null) {
            if (!isStr(beat.image) || !IMAGE_EXT.test(beat.image)) {
                err(`${where} image: extensão precisa ser .png, .jpg ou .jpeg`)
            } else if (!fileExists(beat.image)) {
                err(`${where} image: arquivo não existe (${beat.image})`)
            }
        }
    })

    // Regra 5 no total: soma por tipo e idioma, so beats que tem o texto
    for (const kind of ['short', 'long']) {
        for (const lang of LANGS) {
            const total = beats.reduce((sum, raw) => {
                const entry = raw?.[kind]
                if (entry == null || !filled(entry[lang])) return sum
                return sum + len(toSpoken(entry[lang], lang, entry.say))
            }, 0)
            const max = spokenMax(kind, longSeconds)
            if (total > max) err(`${kind}.${lang}: ${total} caracteres falados; cortar ${total - max} (máximo ${max})`)
        }
    }

    return errors
}
