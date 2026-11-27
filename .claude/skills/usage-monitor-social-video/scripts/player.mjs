// Prévia jogável (A03c): funções puras. A página é assets/player.html; make.mjs preview grava qa/player-<variante>.html.
import { dirname, relative, sep } from 'node:path'

export const PLAYER_MARKER = '/*__PLAYER_JSON__*/null'

// Caracteres que não podem aparecer crus no JSON dentro do <script>: < > & e os separadores de linha Unicode.
const UNSAFE = new RegExp(`[<>&${String.fromCharCode(0x2028, 0x2029)}]`, 'g')

// Índice do beat que contém o instante t (start <= t < end). Antes do início, o primeiro; depois do fim, o último.
// A página (assets/player.html) tem a mesma regra em JS.
export function beatIndexAt(beats, t) {
  if (!beats.length) return -1
  if (!(t >= beats[0].start)) return 0
  const i = beats.findIndex((beat) => t < beat.end)
  return i === -1 ? beats.length - 1 : i
}

// Lista que a página recebe: título, texto falado inteiro e início de cada beat, na ordem do roteiro.
export function playerBeats(script, spoken) {
  if (spoken.length !== script.beats.length) {
    throw new Error(`playerBeats: ${spoken.length} textos para ${script.beats.length} beats`)
  }
  return script.beats.map((beat, i) => ({
    index: i,
    id: beat.id,
    kicker: beat.kicker,
    title: beat.title,
    text: spoken[i],
    start: beat.start,
    end: beat.end,
  }))
}

// Texto escrito (long, ou short) de cada beat do tipo, no idioma: é o que a lista mostra, como na legenda; não a forma falada.
export function writtenTexts(spec, kind, lang) {
  return spec.beats
    .filter((beat) => kind === "long" || beat.short)
    .map((beat) => beat[kind][lang])
}

// Injeta o modelo como JSON no marcador; os caracteres perigosos saem como escape, então o dado não fecha o script.
export function buildPlayerHtml(template, model) {
  if (!template.includes(PLAYER_MARKER)) throw new Error('modelo da prévia sem o marcador __PLAYER_JSON__')
  const json = JSON.stringify(model).replace(UNSAFE, (c) => `\\u${c.charCodeAt(0).toString(16).padStart(4, '0')}`)
  return template.replace(PLAYER_MARKER, () => json)
}

// src do iframe: caminho do index.html relativo à pasta da prévia; ?data= relativo ao index.html; ?format=.
export function playerSrc({ fromDir, indexFile, dataFile, format }) {
  const page = relative(fromDir, indexFile).split(sep).join('/')
  const data = relative(dirname(indexFile), dataFile).split(sep).join('/')
  return `${page}?${new URLSearchParams({ data, format }).toString()}`
}
