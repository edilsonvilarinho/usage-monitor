// Linha do tempo do vídeo de fluxo (#540, B03). Função pura: sem I/O, sem ffprobe.
// Entrada: falas com duração já medida (segundos) e o roteiro de video.json.
// Saída: beats, itens, cues, legendas e o objeto window.WF_SCRIPT.
// Contas internas em milissegundos inteiros; a saída é em segundos com 3 casas.
// Porta de prototipos/workflow-video-v3/final/build.mjs, sem leitura de arquivos.

const FIRST_MS = 1000 // primeira fala em 1,0 s
const GAP_MIN_MS = 800 // gap mínimo entre falas
const GAP_MAX_MS = 3000 // gap máximo entre falas
const BEAT_LEAD_MS = 400 // beat começa 0,4 s antes da fala
const ITEM_HEAD_MS = 300 // primeiro item: speechStart + 0,3 s
const ITEM_TAIL_MS = 600 // último item: speechEnd - 0,6 s
const SHORT_ITEM_CAP = 4 // no curto, sem nenhum item marcado: até 4
const WHOOSH_LEAD_MS = 300 // whoosh 0,3 s antes do beat
const IMPACT_AFTER_LAST_MS = 600 // impact em start do último beat + 0,6 s
const MAX_CHARS = 72

// Alvo e cauda por tipo de vídeo (video.json, seção "Linha do tempo"). Interno: a lista de variantes é spec.mjs VARIANTS.
const TIMING = {
  short: { target: 60, tail: 0.6 },
  long: { target: 300, tail: 3.0 },
}

const toMs = (s) => Math.round(s * 1000)
const sec = (ms) => Math.round(ms) / 1000
const fmtComma = (v) => v.toFixed(3).replace('.', ',')

function srtTime(s) {
  const ms = toMs(s)
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const sc = Math.floor((ms % 60000) / 1000)
  const r = ms % 1000
  return [h, m, sc].map((v) => String(v).padStart(2, '0')).join(':') + ',' + String(r).padStart(3, '0')
}

// Gap igual entre falas: o maior entre 0,8 e 3,0 s que caiba no alvo.
// Com gap no teto (3,0 s), a duração é a última fala mais a cauda; sem teto, é o alvo.
export function buildTimeline(speech, { target, tail }) {
  if (!speech.length) throw new Error('linha do tempo sem falas')
  const targetMs = toMs(target)
  const tailMs = toMs(tail)
  const n = speech.length
  const durs = speech.map((s) => toMs(s.durationSec))
  const gaps = n - 1
  const speechMs = durs.reduce((a, d) => a + d, 0)
  const availMs = targetMs - tailMs - FIRST_MS - speechMs

  let gapMs = GAP_MAX_MS
  let capped = true
  if (gaps > 0) {
    const fitMs = Math.floor(availMs / gaps)
    if (fitMs < GAP_MIN_MS) {
      const over = (GAP_MIN_MS * gaps - availMs) / 1000
      throw new Error(
        `gap ficaria abaixo de 0,8 s; sobram ${fmtComma(over)} s de fala para caber em ${target} s`,
      )
    }
    capped = fitMs >= GAP_MAX_MS
    gapMs = Math.min(fitMs, GAP_MAX_MS)
  }

  const timed = []
  let cursor = FIRST_MS
  for (let i = 0; i < n; i++) {
    const speechStart = cursor
    const speechEnd = speechStart + durs[i]
    timed.push({ id: speech[i].id, speechStart, speechEnd })
    cursor = speechEnd + gapMs
  }

  const durationMs = capped ? timed[n - 1].speechEnd + tailMs : targetMs
  if (durationMs > targetMs) throw new Error(`duração ${sec(durationMs)} s acima do alvo ${target} s`)

  const beats = timed.map((b, i) => {
    const start = i === 0 ? 0 : b.speechStart - BEAT_LEAD_MS
    const end = i < n - 1 ? timed[i + 1].speechStart - BEAT_LEAD_MS : durationMs
    return {
      id: b.id,
      index: i,
      start: sec(start),
      end: sec(end),
      speechStart: sec(b.speechStart),
      speechEnd: sec(b.speechEnd),
    }
  })
  return { gap: sec(gapMs), duration: sec(durationMs), beats }
}

// Itens distribuídos por igual entre speechStart+0,3 e speechEnd-0,6.
// No curto só entram itens com short; sem nenhum, entram todos, até 4.
export function placeItems(items, { speechStart, speechEnd, kind }) {
  let list = items
  if (kind === 'short') {
    const marked = items.filter((it) => it.short)
    list = marked.length ? marked : items.slice(0, SHORT_ITEM_CAP)
  }
  const a = toMs(speechStart) + ITEM_HEAD_MS
  const b = Math.max(a, toMs(speechEnd) - ITEM_TAIL_MS)
  const n = list.length
  return list.map((it, j) => ({
    ...it,
    at: sec(n === 1 ? a : a + ((b - a) * j) / (n - 1)),
  }))
}

// Quebra em frases no fim de sentença; frase longa quebra em vírgula, ponto e vírgula ou dois-pontos;
// cláusula longa quebra em espaço. Nenhuma legenda passa de 72 caracteres.
function wrapWords(s) {
  const out = []
  let cur = ''
  for (const w of s.split(/\s+/)) {
    const cand = cur ? `${cur} ${w}` : w
    if (cand.length <= MAX_CHARS) cur = cand
    else {
      out.push(cur)
      cur = w
    }
  }
  if (cur) out.push(cur)
  return out
}

export function splitCaptions(text) {
  const normalized = text.replace(/\s+/g, ' ').trim()
  const out = []
  for (const sentence of normalized.split(/(?<=[.!?])\s+/).filter(Boolean)) {
    if (sentence.length <= MAX_CHARS) {
      out.push(sentence)
      continue
    }
    let cur = ''
    for (const clause of sentence.split(/(?<=[,;:])\s+/)) {
      if (clause.length > MAX_CHARS) {
        if (cur) out.push(cur)
        cur = ''
        out.push(...wrapWords(clause))
        continue
      }
      const cand = cur ? `${cur} ${clause}` : clause
      if (cand.length <= MAX_CHARS) cur = cand
      else {
        out.push(cur)
        cur = clause
      }
    }
    if (cur) out.push(cur)
  }
  for (const p of out) {
    if (p.length > MAX_CHARS) throw new Error(`legenda com ${p.length} caracteres: ${p}`)
  }
  return out
}

// Legendas de uma fala: tempo proporcional aos caracteres, contíguas, cobrindo speechStart..speechEnd.
export function timeCaptions(text, speechStart, speechEnd) {
  const parts = splitCaptions(text)
  const s = toMs(speechStart)
  const dur = toMs(speechEnd) - s
  const total = parts.reduce((a, p) => a + p.length, 0)
  let cum = 0
  return parts.map((p) => {
    const from = cum
    cum += p.length
    return {
      start: sec(s + Math.round((dur * from) / total)),
      end: sec(s + Math.round((dur * cum) / total)),
      text: p,
    }
  })
}

// Cues ordenadas: rise em 0; whoosh antes de cada beat exceto o primeiro;
// um som por item (tick, red com crit, green com ok); impact em 0,6 s do último beat.
export function buildCues(beats) {
  if (!beats.length) return []
  const raw = [{ tMs: 0, sound: 'rise', note: beats[0].id }]
  beats.forEach((b, i) => {
    for (const it of b.items) {
      const sound = it.status === 'crit' ? 'red' : it.status === 'ok' ? 'green' : 'tick'
      raw.push({ tMs: toMs(it.at), sound, note: `${b.id}: ${it.text}` })
    }
    if (i > 0) raw.push({ tMs: toMs(b.start) - WHOOSH_LEAD_MS, sound: 'whoosh', note: `transição ${b.id}` })
  })
  const last = beats[beats.length - 1]
  raw.push({ tMs: toMs(last.start) + IMPACT_AFTER_LAST_MS, sound: 'impact', note: 'merge' })
  return raw
    .sort((x, y) => x.tMs - y.tMs)
    .map((c) => ({ t: sec(c.tMs), sound: c.sound, note: c.note }))
}

// SRT em UTF-8: índice, tempo HH:MM:SS,mmm --> HH:MM:SS,mmm, texto, linha em branco entre blocos.
export function toSrt(captions) {
  return captions
    .map((c, i) => `${i + 1}\n${srtTime(c.start)} --> ${srtTime(c.end)}\n${c.text}\n`)
    .join('\n')
}

// Monta window.WF_SCRIPT. kind: 'short' | 'long'. lang: 'pt' | 'en'.
// durations: { [beatId]: segundos da fala medida }. No curto entram só beats com short.
// Legendas usam o texto escrito (short/long), nunca a forma falada (say).
export function buildScript(spec, kind, lang, durations) {
  const variant = TIMING[kind]
  if (!variant) throw new Error(`kind desconhecido: ${kind}`)
  const isShort = kind === 'short'
  const source = spec.beats.filter((b) => !isShort || b.short)

  const speech = source.map((b) => {
    const d = durations[b.id]
    if (!Number.isFinite(d)) throw new Error(`duração ausente para o beat ${b.id}`)
    return { id: b.id, durationSec: d }
  })
  const timeline = buildTimeline(speech, variant)

  const beats = timeline.beats.map((tb, i) => {
    const src = source[i]
    const placed = placeItems(src.items || [], { speechStart: tb.speechStart, speechEnd: tb.speechEnd, kind })
    return {
      id: tb.id,
      index: i,
      start: tb.start,
      end: tb.end,
      speechStart: tb.speechStart,
      speechEnd: tb.speechEnd,
      kicker: src.kicker[lang],
      title: src.title[lang],
      items: placed.map((it) => ({
        text: it[lang],
        mono: Boolean(it.mono),
        status: it.status ?? null,
        at: it.at,
      })),
    }
  })

  const captions = timeline.beats.flatMap((tb, i) => {
    const src = source[i]
    const written = (isShort ? src.short : src.long)[lang]
    return timeCaptions(written, tb.speechStart, tb.speechEnd)
  })

  return {
    variant: `${kind}-${lang}`,
    lang,
    duration: timeline.duration,
    beats,
    cues: buildCues(beats),
    captions,
    link: spec.link,
  }
}
