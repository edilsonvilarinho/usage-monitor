// Auxiliares do render final (#540): data/wf.js adaptado a variantes com beats de duração variável.
// Lê window.WF_SCRIPT carregado de data/<variante>.js. Nada fixo por índice: beats, cues e legenda vêm da variante.
(function () {
  const script = window.WF_SCRIPT
  if (!script) throw new Error('WF_SCRIPT ausente: carregue data/<variante>.js antes de wf.js')
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v))
  const lerp = (a, b, p) => a + (b - a) * p
  const smooth = (p) => { const x = clamp(p); return x * x * (3 - 2 * x) }
  const easeOut = (p) => 1 - Math.pow(1 - clamp(p), 3)
  const easeInOut = (p) => { const x = clamp(p); return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2 }
  // Progresso 0..1 de uma janela [start, start+length]: base de toda animação; nunca use relógio próprio.
  const span = (t, start, length) => clamp((t - start) / length)

  function rng(seed) {
    let s = seed >>> 0
    return () => {
      s = (s + 0x6d2b79f5) >>> 0
      let x = Math.imul(s ^ (s >>> 15), 1 | s)
      x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x
      return ((x ^ (x >>> 14)) >>> 0) / 4294967296
    }
  }

  // Busca binária: último beat com start <= t. Funciona com qualquer número de beats.
  function beatAt(t) {
    const list = script.beats
    const x = clamp(t, 0, script.duration)
    let lo = 0
    let hi = list.length - 1
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1
      if (list[mid].start <= x) lo = mid
      else hi = mid - 1
    }
    const beat = list[lo]
    const local = x - beat.start
    return { beat, index: lo, local, p: clamp(local / (beat.end - beat.start)) }
  }

  // Quanto o cue mais recente de um som já avançou, em segundos; Infinity antes do primeiro.
  function sinceCue(t, sound) {
    let last = -Infinity
    for (const c of script.cues) if (c.sound === sound && c.t <= t) last = c.t
    return t - last
  }

  // Legenda do instante t (texto escrito); null entre legendas.
  function captionAt(t) {
    for (const c of script.captions) if (t >= c.start && t < c.end) return c
    return null
  }

  const beatById = (id) => script.beats.find((b) => b.id === id) || null
  const text = (value, lang) => (typeof value === 'string' ? value : value[lang])

  window.WF = {
    ...script, clamp, lerp, smooth, easeOut, easeInOut, span, rng, beatAt, sinceCue, captionAt, beatById, text,
    models: [],
    register(model) { window.WF.models.push(model) },
  }
})()
