import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildTimeline,
  placeItems,
  splitCaptions,
  timeCaptions,
  buildCues,
  toSrt,
  buildScript,
} from '../timeline.mjs'

const SHORT = { target: 60, tail: 0.6 }
const LONG = { target: 300, tail: 3.0 }

// ---------- buildTimeline ----------

test('gap é limitado a 3,0 s e a duração passa a ser a última fala mais a cauda', () => {
  const t = buildTimeline(
    [
      { id: 'a', durationSec: 5 },
      { id: 'b', durationSec: 5 },
    ],
    SHORT,
  )
  assert.equal(t.gap, 3)
  assert.equal(t.duration, 14.6)
})

test('gap sem teto fica no que cabe no alvo e a duração é o alvo', () => {
  const t = buildTimeline(
    [
      { id: 'a', durationSec: 17.5 },
      { id: 'b', durationSec: 17.5 },
      { id: 'c', durationSec: 17.5 },
    ],
    SHORT,
  )
  assert.equal(t.gap, 2.95)
  assert.equal(t.duration, 60)
  assert.deepEqual(
    t.beats.map((b) => [b.speechStart, b.speechEnd]),
    [
      [1, 18.5],
      [21.45, 38.95],
      [41.9, 59.4],
    ],
  )
})

test('primeiro beat começa em 0 e o último termina em duration', () => {
  const capped = buildTimeline(
    [
      { id: 'a', durationSec: 5 },
      { id: 'b', durationSec: 5 },
    ],
    SHORT,
  )
  assert.equal(capped.beats[0].start, 0)
  assert.equal(capped.beats.at(-1).end, capped.duration)
  assert.equal(capped.beats[0].end, capped.beats[1].start)
  assert.equal(capped.beats[1].start, 8.6)

  const fit = buildTimeline(
    [
      { id: 'a', durationSec: 17.5 },
      { id: 'b', durationSec: 17.5 },
      { id: 'c', durationSec: 17.5 },
    ],
    SHORT,
  )
  assert.equal(fit.beats[0].start, 0)
  assert.equal(fit.beats[1].start, 21.05)
  assert.equal(fit.beats.at(-1).end, fit.duration)
})

test('longo usa a cauda de 3,0 s no cálculo do gap', () => {
  const t = buildTimeline(
    [
      { id: 'a', durationSec: 100 },
      { id: 'b', durationSec: 100 },
    ],
    LONG,
  )
  // 300 - 3 - 1 - 200 = 96 s para um gap: capado em 3,0 s
  assert.equal(t.gap, 3)
  assert.equal(t.duration, 207)
})

test('erro abaixo de 0,8 s informa os segundos que sobram', () => {
  const speech = [
    { id: 'a', durationSec: 20 },
    { id: 'b', durationSec: 20 },
    { id: 'c', durationSec: 20 },
  ]
  assert.throws(() => buildTimeline(speech, SHORT), /abaixo de 0,8 s.*sobram 3,200 s/)
})

// ---------- placeItems ----------

test('itens distribuídos entre speechStart+0,3 e speechEnd-0,6', () => {
  const items = [
    { pt: 'a', en: 'a' },
    { pt: 'b', en: 'b' },
    { pt: 'c', en: 'c' },
  ]
  const out = placeItems(items, { speechStart: 5, speechEnd: 10, kind: 'long' })
  assert.deepEqual(
    out.map((i) => i.at),
    [5.3, 7.35, 9.4],
  )
  assert.equal(out[1].pt, 'b')
})

test('janela colapsa quando a fala é curta: todos os itens no início', () => {
  const items = [
    { pt: 'a', en: 'a' },
    { pt: 'b', en: 'b' },
  ]
  const out = placeItems(items, { speechStart: 5, speechEnd: 5.5, kind: 'long' })
  assert.deepEqual(
    out.map((i) => i.at),
    [5.3, 5.3],
  )
})

test('no curto só entram itens com short; sem nenhum, entram todos até 4', () => {
  const withShort = [
    { pt: 'a', en: 'a', short: true },
    { pt: 'b', en: 'b' },
    { pt: 'c', en: 'c', short: true },
  ]
  const short = placeItems(withShort, { speechStart: 5, speechEnd: 10, kind: 'short' })
  assert.deepEqual(
    short.map((i) => i.pt),
    ['a', 'c'],
  )
  assert.deepEqual(
    short.map((i) => i.at),
    [5.3, 9.4],
  )

  const none = [1, 2, 3, 4, 5, 6].map((n) => ({ pt: `i${n}`, en: `i${n}` }))
  const fallback = placeItems(none, { speechStart: 5, speechEnd: 10, kind: 'short' })
  assert.deepEqual(
    fallback.map((i) => i.pt),
    ['i1', 'i2', 'i3', 'i4'],
  )

  const long = placeItems(withShort, { speechStart: 5, speechEnd: 10, kind: 'long' })
  assert.equal(long.length, 3)
})

// ---------- splitCaptions ----------

test('legenda com no máximo 72 caracteres e texto escrito preservado', () => {
  const text =
    'Um pedido vira issue, a issue vira um plano com testes, os agentes implementam em worktrees isolados e o PR só entra no main depois que o CI passa sem falhas. Depois disso o merge automático fecha o ciclo.'
  const parts = splitCaptions(text)
  for (const p of parts) assert.ok(p.length <= 72, `legenda com ${p.length}: ${p}`)
  assert.equal(parts.join(' '), text)
})

test('splitCaptions não corta CLAUDE.md', () => {
  const parts = splitCaptions('Atualize o CLAUDE.md com a regra nova antes de abrir o PR, sem exceção.')
  assert.ok(parts.some((p) => p.includes('CLAUDE.md')), JSON.stringify(parts))
})

// ---------- timeCaptions ----------

test('legendas de uma fala são contíguas e cobrem speechStart..speechEnd', () => {
  const caps = timeCaptions(
    'Primeira frase curta. Segunda frase bem mais longa que a primeira, com vírgula e mais palavras para estourar o limite de setenta e dois caracteres.',
    1,
    9,
  )
  assert.ok(caps.length >= 3, `esperava >= 3 legendas, veio ${caps.length}`)
  assert.equal(caps[0].start, 1)
  assert.equal(caps.at(-1).end, 9)
  for (let i = 1; i < caps.length; i++) assert.equal(caps[i].start, caps[i - 1].end)
  for (const c of caps) assert.ok(c.text.length <= 72, c.text)
})

// ---------- buildCues ----------

test('cues: rise em 0, whoosh antes de cada beat exceto o primeiro, itens por status, impact no último beat, ordenados', () => {
  const beats = [
    { id: 'a', index: 0, start: 0, end: 13.6, items: [{ text: 'x', at: 1.3 }] },
    {
      id: 'b',
      index: 1,
      start: 13.6,
      end: 26.6,
      items: [
        { text: 'FALHA', at: 14.3, status: 'crit' },
        { text: 'ok', at: 16, status: 'ok' },
      ],
    },
    { id: 'c', index: 2, start: 26.6, end: 37.6, items: [] },
  ]
  const cues = buildCues(beats)
  assert.deepEqual(
    cues.map((c) => [c.t, c.sound]),
    [
      [0, 'rise'],
      [1.3, 'tick'],
      [13.3, 'whoosh'],
      [14.3, 'red'],
      [16, 'green'],
      [26.3, 'whoosh'],
      [27.2, 'impact'],
    ],
  )
  assert.ok(cues.every((c, i) => i === 0 || c.t >= cues[i - 1].t))
})

// ---------- toSrt ----------

test('toSrt: índice, tempo HH:MM:SS,mmm, texto e linha em branco entre blocos', () => {
  const srt = toSrt([
    { start: 1, end: 2.5, text: 'Olá, mundo' },
    { start: 2.5, end: 3616.04, text: 'Dois' },
  ])
  assert.equal(srt, '1\n00:00:01,000 --> 00:00:02,500\nOlá, mundo\n\n2\n00:00:02,500 --> 01:00:16,040\nDois\n')
})

// ---------- buildScript ----------

const SPEC = {
  slug: 'fluxo-agentico',
  link: 'https://github.com/edilsonvilarinho/usage-monitor',
  beats: [
    {
      id: 'abertura',
      kicker: { pt: 'Fluxo', en: 'Workflow' },
      title: { pt: 'Do pedido ao merge', en: 'From request to merge' },
      items: [
        { pt: '235 PRs', en: '235 PRs', short: true },
        { pt: 'gh pr merge --auto', en: 'gh pr merge --auto', mono: true, short: true },
        { pt: 'FALHA', en: 'FAIL', status: 'crit', short: true },
      ],
      short: { pt: 'Texto de 60 s.', en: '60 s text.' },
      long: { pt: 'Texto de 5 minutos.', en: '5 minute text.' },
    },
    {
      id: 'meio',
      kicker: { pt: 'Meio', en: 'Middle' },
      title: { pt: 'Meio', en: 'Middle' },
      items: [{ pt: 'OK', en: 'OK', status: 'ok' }],
      long: { pt: 'Texto do meio, com CLAUDE.md.', en: 'Middle text, with CLAUDE.md.', say: { pt: 'Claude ponto M D', en: 'Claude dot M D' } },
    },
    {
      id: 'passo',
      kicker: { pt: 'Passo', en: 'Step' },
      title: { pt: 'Passo', en: 'Step' },
      items: [],
      short: { pt: 'Passo.', en: 'Step.' },
      long: { pt: 'Passo longo.', en: 'Long step.' },
    },
    {
      id: 'fecho',
      kicker: { pt: 'Fim', en: 'End' },
      title: { pt: 'Fim', en: 'End' },
      items: [],
      short: { pt: 'Fecho.', en: 'Close.' },
      long: { pt: 'Fecho longo.', en: 'Long close.' },
    },
  ],
}

const SHORT_DUR = { abertura: 10, passo: 10, fecho: 10 }
const LONG_DUR = { abertura: 10, meio: 10, passo: 10, fecho: 10 }

test('buildScript curto: só beats com short, variante short-pt, duração e beats', () => {
  const s = buildScript(SPEC, 'short', 'pt', SHORT_DUR)
  assert.equal(s.variant, 'short-pt')
  assert.equal(s.lang, 'pt')
  assert.equal(s.link, SPEC.link)
  assert.deepEqual(
    s.beats.map((b) => b.id),
    ['abertura', 'passo', 'fecho'],
  )
  assert.equal(s.duration, 37.6)
  assert.equal(s.beats[0].start, 0)
  assert.equal(s.beats.at(-1).end, s.duration)
  assert.equal(s.beats[0].kicker, 'Fluxo')
  assert.equal(s.beats[0].title, 'Do pedido ao merge')
  assert.deepEqual(
    s.beats[0].items.map((i) => i.text),
    ['235 PRs', 'gh pr merge --auto', 'FALHA'],
  )
  assert.equal(s.beats[0].items[2].status, 'crit')
  assert.equal(s.beats[0].items[0].at, 1.3)
  assert.equal(s.beats[0].items[2].at, 10.4)
})

test('buildScript curto em inglês: variante short-en com textos EN', () => {
  const s = buildScript(SPEC, 'short', 'en', SHORT_DUR)
  assert.equal(s.variant, 'short-en')
  assert.equal(s.lang, 'en')
  assert.equal(s.beats[0].kicker, 'Workflow')
  assert.equal(s.beats[0].title, 'From request to merge')
  assert.equal(s.beats[0].items[2].text, 'FAIL')
})

test('buildScript longo: todos os beats, itens sem short entram, variante long-pt', () => {
  const s = buildScript(SPEC, 'long', 'pt', LONG_DUR)
  assert.equal(s.variant, 'long-pt')
  assert.deepEqual(
    s.beats.map((b) => b.id),
    ['abertura', 'meio', 'passo', 'fecho'],
  )
  assert.deepEqual(
    s.beats[1].items.map((i) => i.text),
    ['OK'],
  )
  assert.equal(s.beats[1].items[0].status, 'ok')
  assert.equal(s.duration, 53)
})

test('legendas usam o texto escrito, não a forma falada; beat sem short fica fora do curto', () => {
  const long = buildScript(SPEC, 'long', 'pt', LONG_DUR)
  assert.ok(long.captions.some((c) => c.text.includes('CLAUDE.md')))
  assert.ok(!long.captions.some((c) => c.text.includes('ponto')))

  const short = buildScript(SPEC, 'short', 'pt', SHORT_DUR)
  assert.ok(!short.captions.some((c) => c.text.includes('CLAUDE.md')))
  for (const c of short.captions) assert.ok(c.text.length <= 72, c.text)
  assert.ok(short.captions.every((c, i) => i === 0 || c.start >= short.captions[i - 1].end))
})

test('buildScript: cues ordenadas, rise primeiro e um whoosh por beat exceto o primeiro', () => {
  const s = buildScript(SPEC, 'short', 'pt', SHORT_DUR)
  assert.deepEqual(s.cues[0], { t: 0, sound: 'rise', note: 'abertura' })
  assert.ok(s.cues.every((c, i) => i === 0 || c.t >= s.cues[i - 1].t))
  assert.equal(s.cues.filter((c) => c.sound === 'whoosh').length, s.beats.length - 1)
  assert.equal(s.cues.at(-1).sound, 'impact')
})

test('buildScript sem duração de um beat lança erro citando o beat', () => {
  assert.throws(() => buildScript(SPEC, 'short', 'pt', { abertura: 10, passo: 10 }), /fecho/)
})
