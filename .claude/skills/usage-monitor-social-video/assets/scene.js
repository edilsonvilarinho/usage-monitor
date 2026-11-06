// Cena do vídeo de fluxo: horizonte de eventos (visual congelado do protótipo final).
// Beats, itens, cues, legenda e link vêm de WF (dados da variante). Nada fixo por id: o primeiro beat é a abertura,
// o último é o fechamento, e os do meio cicam as poses da tabela. Imagem opcional por beat (beat.image, já carregada pela página).
// renderAt(t) é função pura de t. Sem Date, performance, animação por frame, timers ou Math.random;
// aleatório só por WF.rng no mount.
WF.register({
  id: 'final',
  name: { pt: 'Horizonte de eventos', en: 'Event horizon' },
  concept: {
    pt: 'Horizonte preto em céu noturno; cada etapa é um corpo em órbita com o seu dado preso a ele.',
    en: 'Black horizon in a night sky; each stage is a body in orbit with its data bound to it.',
  },
  mount(stage, { format, images = [] }) {
    const { clamp, smooth, easeOut, easeInOut, span, rng, beatAt, beats, cues, duration, link } = WF
    const vertical = format === '9:16'
    const W = vertical ? 1080 : 1920
    const H = vertical ? 1920 : 1080
    const RS = 300 // raio do horizonte na escala 1 da cena
    const BAND = (vertical ? 0.74 : 0.84) * H // topo da faixa de legenda: a cena não passa daqui
    const MARG = vertical ? 86 : 110 // 8% lateral em 9:16
    const TOP = vertical ? 96 : 120
    const DISC = -0.21
    const MONO = '"IBM Plex Mono", monospace'
    const SANS = '"IBM Plex Sans", sans-serif'
    const TITLE = 80
    const KICK = vertical ? 34 : 30
    const LBL = vertical ? 36 : 30
    const LH = Math.round(LBL * 1.3)
    const LINK = String(link || '').replace(/^https?:\/\//, '') // lockup mostra o link de WF.link sem o protocolo
    const COL = {
      gold: '#E8AE63', hot: '#FFF0CB', dust: '#8C643F', ember: '#B4501E', lens: '#F4CF97',
      fg: '#F2EDED', muted: '#B8B2B2', ok: '#4CAF50', crit: '#E86A6A', night: '#080B12', core: '#030508',
      blue: '#1E3D8F', blueDeep: '#16306E', violet: '#5A2D8C', violetDeep: '#3E2070', frame: '#3D3838',
    }
    const rgba = (h, a) => {
      const n = parseInt(h.slice(1), 16)
      return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${clamp(a, 0, 1).toFixed(3)})`
    }

    const canvas = document.createElement('canvas')
    canvas.width = W
    canvas.height = H
    canvas.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%'
    stage.append(canvas)
    const ctx = canvas.getContext('2d')

    const measure = (str, size, weight, family = MONO) => {
      ctx.font = `${weight} ${size}px ${family}`
      return ctx.measureText(str).width
    }
    const wrap = (str, maxW, size, weight, family) => {
      const out = []
      let line = ''
      for (const w of str.split(' ')) {
        const test = line ? `${line} ${w}` : w
        if (line && measure(test, size, weight, family) > maxW) {
          out.push(line)
          line = w
        } else line = test
      }
      if (line) out.push(line)
      return out
    }
    const makeSprite = (size, stops) => {
      const c = document.createElement('canvas')
      c.width = size
      c.height = size
      const g = c.getContext('2d')
      const grd = g.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
      for (const [o, col, a] of stops) grd.addColorStop(o, rgba(col, a))
      g.fillStyle = grd
      g.fillRect(0, 0, size, size)
      return c
    }
    const glowSprite = makeSprite(512, [[0, COL.gold, 0.2], [0.35, COL.ember, 0.06], [1, COL.ember, 0]])
    const headSprite = makeSprite(64, [[0, COL.hot, 1], [0.2, COL.hot, 0.9], [0.45, COL.gold, 0.32], [1, COL.gold, 0]])
    const starSprites = [
      makeSprite(32, [[0, COL.fg, 1], [0.35, COL.fg, 0.45], [1, COL.fg, 0]]),
      makeSprite(32, [[0, COL.hot, 1], [0.35, COL.hot, 0.45], [1, COL.hot, 0]]),
    ]

    // Céu pré-desenhado: night #080B12, nebulosa azul e violeta, estrelas densas.
    const bg = document.createElement('canvas')
    bg.width = W
    bg.height = H
    const bgc = bg.getContext('2d')
    bgc.fillStyle = COL.night
    bgc.fillRect(0, 0, W, H)
    const addBlob = (x, y, r, col, a) => {
      const g = bgc.createRadialGradient(x, y, 0, x, y, r)
      g.addColorStop(0, rgba(col, a))
      g.addColorStop(1, rgba(col, 0))
      bgc.fillStyle = g
      bgc.fillRect(0, 0, W, H)
    }
    const big = Math.max(W, H)
    addBlob(W * 0.2, H * 0.3, big * 0.7, COL.blue, 0.42)
    addBlob(W * 0.85, H * 0.75, big * 0.6, COL.blueDeep, 0.34)
    addBlob(W * 0.7, H * 0.25, big * 0.6, COL.violet, 0.32)
    addBlob(W * 0.1, H * 0.88, big * 0.5, COL.violetDeep, 0.28)
    const rand = rng(540)
    for (let i = 0; i < 60; i++) {
      const hue = rand()
      const col = hue < 0.5 ? COL.blue : hue < 0.8 ? COL.violet : COL.lens
      addBlob(rand() * W, rand() * H, 120 + rand() * 260, col, 0.03 + rand() * 0.04)
    }
    for (let i = 0; i < 900; i++) {
      bgc.fillStyle = rgba(rand() < 0.7 ? COL.fg : '#BFD4FF', 0.12 + rand() * 0.4)
      bgc.fillRect(rand() * W, rand() * H, 1 + rand() * 2, 1 + rand() * 2)
    }
    // Estrelas em três camadas de paralaxe (espaço de tela).
    const STARS = []
    for (const [n, v, rmin, rmax, a, s] of [[160, 3, 0.6, 1.3, 0.32, 0], [90, 9, 1.0, 2.0, 0.45, 0], [36, 20, 1.6, 2.6, 0.5, 1]]) {
      for (let i = 0; i < n; i++) STARS.push({ x0: rand() * W, y0: rand() * H, size: (rmin + rand() * (rmax - rmin)) * 10, v, a, s })
    }
    // Disco de partículas: órbita kepleriana (as internas são mais rápidas).
    const PARTS = []
    for (let i = 0; i < 3000; i++) {
      const r = RS * (1.08 + 1.5 * Math.pow(rand(), 1.3))
      PARTS.push({ r, ph: rand() * Math.PI * 2, om: 0.5 / Math.pow(r / RS, 1.5), sz: 2 + rand() * 3.5, hot: rand() < 0.35 })
    }

    // Câmera: tabela de 15 poses. Índice 0 = abertura, 14 = fechamento; 1 a 13 cicladas nos beats do meio.
    // Horizonte à direita e título à esquerda, e alterna a cada beat. A pose vem sempre do índice do beat.
    const POSE16 = [
      { z: 1.10, cx: 1340, cy: 540, k: 0.28, rot: -0.05 },
      { z: 1.22, cx: 640, cy: 540, k: 0.32, rot: 0.10 },
      { z: 1.32, cx: 1360, cy: 520, k: 0.26, rot: -0.12 },
      { z: 1.40, cx: 640, cy: 560, k: 0.34, rot: 0.08 },
      { z: 1.26, cx: 1340, cy: 540, k: 0.30, rot: 0.15 },
      { z: 1.36, cx: 640, cy: 520, k: 0.28, rot: -0.10 },
      { z: 1.46, cx: 1340, cy: 540, k: 0.36, rot: 0.06 },
      { z: 1.24, cx: 640, cy: 560, k: 0.30, rot: -0.14 },
      { z: 1.30, cx: 1340, cy: 520, k: 0.26, rot: 0.18 },
      { z: 1.20, cx: 640, cy: 540, k: 0.30, rot: 0.0 },
      { z: 1.34, cx: 1340, cy: 560, k: 0.32, rot: -0.08 },
      { z: 1.42, cx: 640, cy: 520, k: 0.28, rot: 0.12 },
      { z: 1.28, cx: 1340, cy: 540, k: 0.34, rot: -0.16 },
      { z: 1.36, cx: 640, cy: 540, k: 0.30, rot: 0.09 },
      { z: 1.10, cx: 1340, cy: 540, k: 0.28, rot: 0.0 },
    ]
    // 9:16: horizonte fixo, com centro a 63,5% da altura e raio ~8,5% de H (faixa 55% a 72%).
    const POSE9_V = { z: 0.86, cx: 540, cy: 1180, k: 0.3, rot: 0 }
    // Primeiro índice = abertura; último = fechamento; os do meio percorrem a tabela de 1 a 13 em ciclo.
    const pickPose = (table, idx, n) => (idx === 0
      ? table[0]
      : idx === n - 1 ? table[table.length - 1] : table[1 + ((idx - 1) % (table.length - 2))])
    // Beat com imagem em 16:9: câmera recua (z 0,72) e o centro vai ao lado oposto ao título; o horizonte sai parcialmente do quadro.
    const POSE_IMG = { z: 0.72, cx: W - 80, cy: 540, k: 0.28, rot: -0.05 }
    const hasImg = (i) => !!(images[i] && images[i].naturalWidth)
    const POSE_OF = beats.map((_, i) => (vertical ? POSE9_V : hasImg(i) ? POSE_IMG : pickPose(POSE16, i, beats.length)))
    const poseOf = (idx) => POSE_OF[idx]

    // Transição de 1,2 s entre poses (easing) + deriva contínua + travelling dentro do beat.
    const camAt = (t) => {
      const { index, p } = beatAt(t)
      const cur = poseOf(index)
      const pre = index > 0 ? poseOf(index - 1) : cur
      const tr = easeInOut(span(t, beats[index].start, 1.2))
      const L = (key) => pre[key] + (cur[key] - pre[key]) * tr
      const within = easeInOut(p)
      return {
        z: L('z') * (1 + 0.06 * within) + 0.015 * Math.sin(0.11 * t),
        cx: L('cx') + 20 * Math.sin(0.13 * t + 1.1) + 30 * (within - 0.5) * (cur.cx > W / 2 ? -1 : 1),
        cy: L('cy') + 12 * Math.sin(0.17 * t),
        k: L('k'),
        rot: L('rot') + 0.03 * Math.sin(0.19 * t),
      }
    }
    // Posição de tela de um ponto em órbita: Rb em unidades de tela (RS = 300); th em radianos.
    const place = (cam, Rb, th) => {
      const x = Rb * Math.cos(th)
      const y = Rb * cam.k * Math.sin(th)
      const rot = DISC + cam.rot
      const c = Math.cos(rot)
      const s = Math.sin(rot)
      return { x: cam.cx + cam.z * (x * c - y * s), y: cam.cy + cam.z * (x * s + y * c), depth: Math.sin(th) }
    }
    const recent = (sound, t, dur) => {
      const out = []
      for (const c of cues) if (c.sound === sound && t >= c.t && t - c.t < dur) out.push(t - c.t)
      return out
    }

    // Capítulos: só no longo (duração > 2 min). Primeiro e último beat (abertura e fechamento) não têm número.
    const longForm = duration > 120
    const CHAPTERS = beats.length - 2
    const kickerOf = (beat, idx) => {
      const k = beat.kicker.toUpperCase()
      if (!longForm || idx === 0 || idx === beats.length - 1) return k
      return `${String(idx).padStart(2, '0')}/${CHAPTERS} · ${k}`
    }

    // Título de cada beat: lado oposto ao horizonte (16:9) ou alternado esquerda/direita (9:16). Caixa é obstáculo.
    const TB = beats.map((beat, idx) => {
      const pose = poseOf(idx)
      const R = RS * pose.z * 1.3
      let align
      let w
      if (vertical) {
        align = idx % 2 ? 'right' : 'left'
        w = W - 2 * MARG
      } else if (pose.cx > W / 2) {
        align = 'left'
        w = Math.max(480, pose.cx - R - 40 - MARG)
      } else {
        align = 'right'
        w = Math.max(480, W - MARG - pose.cx - R - 40)
      }
      const anchor = align === 'left' ? MARG : W - MARG
      const kicker = kickerOf(beat, idx)
      const lines = wrap(beat.title, w, TITLE, 600, MONO)
      const tw = Math.max(measure(kicker, KICK, 500), ...lines.map((ln) => measure(ln, TITLE, 600)))
      const lastBase = TOP + KICK + 28 + TITLE * 1.12 * lines.length
      const x0 = align === 'left' ? anchor : anchor - tw
      return {
        kicker, lines, align, anchor, col: w,
        box: { x: x0 - 22, y: TOP - 16, w: tw + 44, h: lastBase + 22 - (TOP - 16) },
      }
    })

    // Imagem opcional do beat (beat.image, carregada pela página): painel com moldura, raio 8, 1 px #3D3838.
    // 16:9: imagem herói com 58% de W, no lado do título, abaixo dele; a altura é limitada pela faixa e a imagem é cortada (cover).
    // 9:16: na faixa da coluna, com os itens abaixo.
    const IMGS = beats.map((_, i) => images[i] || null)
    const FRAME = beats.map((_, idx) => {
      const img = IMGS[idx]
      if (!img || !img.naturalWidth) return null
      const aw = img.naturalWidth / img.naturalHeight
      const tb = TB[idx].box
      if (vertical) {
        const top = Math.max(0.16 * H, tb.y + tb.h + 14)
        const maxH = (0.46 * H - top) * 0.6
        const w = Math.max(0, Math.min(W - 2 * MARG, maxH * aw))
        return { x: (W - w) / 2, y: top, w, h: w / aw }
      }
      const pose = poseOf(idx)
      const Rd = RS * pose.z * 1.1 * 1.07 + 16 // folga do anel do horizonte, com o zoom da câmera
      const left = pose.cx > W / 2 // horizonte à direita: imagem à esquerda (lado do título)
      const avail = left ? pose.cx - Rd - 12 - MARG : W - MARG - (pose.cx + Rd + 12)
      const top = tb.y + tb.h + 24
      const maxH = BAND - 20 - top
      const w = Math.max(0, Math.min(0.58 * W, avail))
      const h = Math.max(0, Math.min(w / aw, maxH))
      return { x: left ? MARG : W - MARG - w, y: top, w, h }
    })
    // Obstáculos de cada beat: a caixa do título e, se houver, a moldura da imagem.
    const OBST = beats.map((_, i) => (FRAME[i] ? [TB[i].box, FRAME[i]] : [TB[i].box]))

    // Itens: um corpo em órbita por item; surge em `at`; no máximo 6 visíveis (o 7º apaga o mais antigo em 0,6 s).
    const BODIES = beats.map((beat) => {
      const items = beat.items || []
      return items.map((it, k) => {
        const mono = !!it.mono
        const fam = mono ? MONO : SANS
        const lines = wrap(it.text, vertical ? W * 0.84 - 28 : 520, LBL, 500, fam)
        const tw = Math.max(...lines.map((ln) => measure(ln, LBL, 500, fam)))
        const col = it.status === 'crit' ? COL.crit : it.status === 'ok' ? COL.ok : mono ? COL.hot : COL.fg
        const next = items[k + 6]
        return {
          at: it.at,
          retire: next ? next.at : Infinity,
          lines, fam, tw, col,
          R: 1.3 + 0.13 * (k % 3) + 0.04 * Math.floor(k / 3),
          ph: 0.35 + k * 1.9,
          sp: (k % 2 ? -1 : 1) * (0.14 + 0.02 * (k % 3)),
        }
      })
    })

    // Fundo mínimo de conforto: legenda cobre a faixa inferior; a cena não desenha nada nela.
    const drawStars = (t) => {
      for (const s of STARS) {
        const x = (((s.x0 - t * s.v * 0.3) % W) + W) % W
        const y = (((s.y0 - t * s.v) % H) + H) % H
        ctx.globalAlpha = s.a
        ctx.drawImage(starSprites[s.s], x - s.size / 2, y - s.size / 2, s.size, s.size)
      }
      ctx.globalAlpha = 1
    }
    const drawWaves = (t) => {
      ctx.lineWidth = 3
      for (const age of recent('whoosh', t, 1.0)) {
        ctx.strokeStyle = rgba(COL.lens, 0.3 * (1 - age))
        ctx.beginPath()
        ctx.arc(0, 0, RS * (1.05 + 1.9 * easeOut(age)), 0, Math.PI * 2)
        ctx.stroke()
      }
      ctx.lineWidth = 2
      for (const age of recent('rise', t, 1.4)) {
        const k = age / 1.4
        ctx.strokeStyle = rgba(COL.hot, 0.5 * (1 - k))
        ctx.beginPath()
        ctx.arc(0, 0, RS * (0.4 + 2.4 * easeOut(k)), 0, Math.PI * 2)
        ctx.stroke()
      }
    }
    // Disco de acreção: metade de trás (atrás do horizonte) ou da frente (cruza o horizonte).
    // Doppler: lado que se aproxima quente; lado que se afasta em brasa.
    const drawBand = (t, cam, rot, front) => {
      const born = smooth(span(t, 0, 1.2))
      if (born <= 0.002) return
      const RB = 1.55 * RS
      const ry = RB * cam.k
      const n = 48
      const a0 = front ? 0 : Math.PI
      ctx.lineCap = 'round'
      for (let i = 0; i < n; i++) {
        const a1 = a0 + (i / n) * Math.PI
        const a2 = a1 + Math.PI / n + 0.02
        const mid = a1 + Math.PI / (2 * n)
        const c = Math.cos(mid)
        const col = c > 0.25 ? COL.hot : c < -0.25 ? COL.ember : COL.gold
        const shimmer = 0.7 + 0.3 * Math.sin(3 * mid + 0.9 * t)
        const alpha = born * shimmer * (front ? 0.85 : 0.4)
        ctx.strokeStyle = rgba(col, alpha)
        ctx.lineWidth = front ? 16 : 10
        ctx.beginPath()
        ctx.ellipse(0, 0, RB, ry, rot, a1, a2)
        ctx.stroke()
      }
    }
    const drawDisc = (t, cr, sr, cam, front) => {
      ctx.globalAlpha = front ? 0.95 : 0.4
      for (const p of PARTS) {
        const th = p.ph + p.om * t
        const s = Math.sin(th)
        if ((s >= 0) !== front) continue
        const c = Math.cos(th)
        const x = p.r * c
        const y = p.r * cam.k * s
        const px = x * cr - y * sr
        const py = x * sr + y * cr
        ctx.fillStyle = c >= 0 ? (p.hot ? COL.hot : COL.gold) : (p.hot ? COL.ember : COL.dust)
        ctx.fillRect(px - p.sz / 2, py - p.sz / 2, p.sz, p.sz)
      }
      ctx.globalAlpha = 1
    }
    // Horizonte: disco preto, anel de luz no limite.
    const drawHorizon = (t) => {
      const born = smooth(span(t, 0, 1.2))
      if (born <= 0.002) return
      ctx.globalAlpha = born
      ctx.fillStyle = COL.core
      ctx.beginPath()
      ctx.arc(0, 0, RS, 0, Math.PI * 2)
      ctx.fill()
      ctx.lineWidth = 14
      ctx.strokeStyle = rgba(COL.hot, 0.12)
      ctx.beginPath()
      ctx.arc(0, 0, RS * 1.07, 0, Math.PI * 2)
      ctx.stroke()
      ctx.lineWidth = 3
      ctx.strokeStyle = rgba(COL.hot, 0.85)
      ctx.beginPath()
      ctx.arc(0, 0, RS * 1.01, 0, Math.PI * 2)
      ctx.stroke()
      ctx.globalAlpha = 1
    }
    // Lente (mais forte): arco superior largo e brilhante sobre o horizonte; arco fino por baixo.
    const drawLensArcs = (t, rot) => {
      const born = smooth(span(t, 0.2, 1.4))
      if (born <= 0.002) return
      ctx.lineCap = 'round'
      const Rt = 1.3 * RS
      const ryT = Rt * 0.62
      ctx.lineWidth = 34
      ctx.strokeStyle = rgba(COL.gold, 0.28 * born)
      ctx.beginPath()
      ctx.ellipse(0, 0, Rt, ryT, rot, Math.PI, Math.PI * 2)
      ctx.stroke()
      ctx.lineWidth = 14
      ctx.strokeStyle = rgba(COL.hot, 0.8 * born)
      ctx.beginPath()
      ctx.ellipse(0, 0, Rt, ryT, rot, Math.PI, Math.PI * 2)
      ctx.stroke()
    }
    const cueOf = (note) => {
      const c = cues.find((cue) => cue.note === note)
      return c ? c.t : null
    }
    const drawMerge = (t) => {
      const at = cueOf('merge')
      if (at == null) return
      const age = t - at
      if (age < 0 || age > 1.8) return
      const k = age / 1.8
      ctx.lineWidth = 6
      ctx.strokeStyle = rgba(COL.lens, 0.6 * (1 - k))
      ctx.beginPath()
      ctx.arc(0, 0, RS * (1.2 + 2.6 * easeOut(k)), 0, Math.PI * 2)
      ctx.stroke()
      const s2 = RS * 6
      if (age < 1.2) {
        ctx.globalAlpha = 0.6 * (1 - age / 1.2)
        ctx.drawImage(glowSprite, -s2 / 2, -s2 / 2, s2, s2)
        ctx.globalAlpha = 1
      }
    }

    // Empurra caixas para fora do horizonte, dos títulos e umas das outras; prende ao quadro e acima da legenda.
    const separate = (a, b, ka) => {
      const ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x)
      const oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y)
      if (ox <= 0 || oy <= 0) return
      if (ox < oy) {
        const s = a.x + a.w / 2 < b.x + b.w / 2 ? -1 : 1
        a.x += s * (ox + 8) * ka
        b.x -= s * (ox + 8) * (1 - ka)
      } else {
        const s = a.y + a.h / 2 < b.y + b.h / 2 ? -1 : 1
        a.y += s * (oy + 8) * ka
        b.y -= s * (oy + 8) * (1 - ka)
      }
    }
    const solve = (boxes, cam, obst) => {
      const Rd = RS * cam.z * 1.08 + 12
      for (let it = 0; it < 40; it++) {
        for (const bx of boxes) {
          const nx = clamp(cam.cx, bx.x, bx.x + bx.w)
          const ny = clamp(cam.cy, bx.y, bx.y + bx.h)
          let dx = nx - cam.cx
          let dy = ny - cam.cy
          let d = Math.hypot(dx, dy)
          if (d < Rd) {
            if (d < 1e-3) { dx = 1; dy = 0; d = 1 }
            const push = Rd - d + 4
            bx.x += (dx / d) * push
            bx.y += (dy / d) * push
          }
          for (const o of obst) separate(bx, o, 1)
        }
        for (let i = 0; i < boxes.length; i++) {
          for (let j = i + 1; j < boxes.length; j++) separate(boxes[i], boxes[j], 0.5)
        }
        for (const bx of boxes) {
          bx.x = clamp(bx.x, MARG, W - MARG - bx.w)
          bx.y = clamp(bx.y, TOP, BAND - bx.h)
        }
      }
    }

    const drawLabel = ({ b, a, head, box }) => {
      const nx = clamp(head.x, box.x, box.x + box.w)
      const ny = clamp(head.y, box.y, box.y + box.h)
      ctx.globalAlpha = a
      ctx.strokeStyle = rgba(b.col, 0.55)
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.moveTo(head.x, head.y)
      ctx.lineTo(nx, ny)
      ctx.stroke()
      ctx.beginPath()
      ctx.roundRect(box.x, box.y, box.w, box.h, 8)
      ctx.fillStyle = rgba(COL.night, 0.9)
      ctx.fill()
      ctx.lineWidth = 1.5
      ctx.strokeStyle = rgba(b.col, 0.5)
      ctx.stroke()
      ctx.font = `500 ${LBL}px ${b.fam}`
      ctx.fillStyle = b.col
      ctx.textAlign = 'left'
      ctx.textBaseline = 'top'
      b.lines.forEach((ln, i) => ctx.fillText(ln, box.x + 14, box.y + 10 + i * LH))
      ctx.textBaseline = 'alphabetic'
      ctx.globalAlpha = 1
    }

    // Desenha só fora do círculo (cx, cy, r), no sistema de coordenadas atual.
    const clipOutside = (cx, cy, r, fn) => {
      ctx.save()
      ctx.beginPath()
      ctx.rect(-1e5, -1e5, 2e5, 2e5)
      ctx.moveTo(cx + r, cy)
      ctx.arc(cx, cy, r, 0, Math.PI * 2)
      ctx.clip('evenodd')
      fn()
      ctx.restore()
    }
    // Corpos do beat ativo: trilha, cabeça, pulso no tick (cue) e dado preso ao corpo.
    const drawBodies = (t, idx, cam) => {
      const beat = beats[idx]
      const endFade = 1 - smooth(span(t, beat.end - 0.6, 0.6))
      const items = []
      for (const b of BODIES[idx]) {
        const a = smooth(span(t, b.at, 0.6)) * (1 - smooth(span(t, b.retire, 0.6))) * endFade
        if (a <= 0.002) continue
        const th = b.ph + b.sp * t
        const Rb = b.R * RS
        ctx.lineCap = 'round'
        // Rasto só fora do disco preto: nenhuma linha fina atravessa o horizonte.
        clipOutside(cam.cx, cam.cy, RS * cam.z * 1.01, () => {
          for (let j = 0; j < 10; j++) {
            const A = place(cam, Rb, th - 0.07 * j)
            const B = place(cam, Rb, th - 0.07 * (j + 1))
            ctx.strokeStyle = rgba(COL.gold, 0.5 * a * (1 - j / 10))
            ctx.lineWidth = 2 + 4 * (1 - j / 10)
            ctx.beginPath()
            ctx.moveTo(A.x, A.y)
            ctx.lineTo(B.x, B.y)
            ctx.stroke()
          }
        })
        const head = place(cam, Rb, th)
        ctx.globalAlpha = a
        ctx.drawImage(headSprite, head.x - 20, head.y - 20, 40, 40)
        ctx.globalAlpha = 1
        const age = t - b.at
        if (age >= 0 && age < 0.9) {
          const k = age / 0.9
          ctx.lineWidth = 2.5
          ctx.strokeStyle = rgba(b.col, 0.9 * (1 - k))
          ctx.beginPath()
          ctx.arc(head.x, head.y, 8 + 34 * easeOut(k), 0, Math.PI * 2)
          ctx.stroke()
        }
        const h = b.lines.length * LH + 20
        const w = b.tw + 28
        // Lado: o do corpo se houver espaço fora do horizonte; senão, o lado com mais espaço. Caixa encosta no anel.
        const Rd = RS * cam.z * 1.1 + 16
        const room = { '-1': cam.cx - Rd - MARG, '1': W - MARG - (cam.cx + Rd) }
        const pref = head.x < cam.cx ? -1 : 1
        const side = room[pref] >= w + 12 || room[-pref] < room[pref] ? pref : -pref
        const x = side < 0 ? cam.cx - Rd - 12 - w : cam.cx + Rd + 12
        items.push({ b, a, head, box: { x, y: head.y - h / 2, w, h } })
      }
      if (!items.length) return
      solve(items.map((i) => i.box), cam, OBST[idx])
      clipOutside(cam.cx, cam.cy, RS * cam.z * 1.01, () => {
        for (const it of items) drawLabel(it)
      })
    }

    // 9:16: sem etiquetas em órbita. Coluna única entre o título e o horizonte (16% a 54% da altura), margem de 8%.
    // Com imagem, a coluna começa abaixo dela. Mais novo perto do horizonte; cada item desliza do horizonte em 0,6 s.
    // Até 6 visíveis (o 7º aposenta o mais antigo).
    const lerp = (a, b, k) => a + (b - a) * k
    const drawColumn = (t, idx) => {
      const beat = beats[idx]
      const endFade = 1 - smooth(span(t, beat.end - 0.6, 0.6))
      const bottom = 0.46 * H
      const fb = FRAME[idx]
      const top = fb ? fb.y + fb.h + 14 : Math.max(0.16 * H, TB[idx].box.y + TB[idx].box.h + 14)
      const vis = []
      for (const b of BODIES[idx]) {
        if (t < b.at) continue
        const a = smooth(span(t, b.at, 0.6)) * (1 - smooth(span(t, b.retire, 0.6))) * endFade
        if (a <= 0.002) continue
        vis.push({ b, a, p: easeOut(span(t, b.at, 0.6)), h: b.lines.length * LH + 18 })
      }
      // Se a pilha não cabe entre o título (ou a imagem) e o horizonte, o mais antigo sai.
      while (vis.length > 1 && vis.reduce((s, v) => s + v.h + 10, -10) > bottom - top) vis.shift()
      // Pilha de baixo para cima; cada item novo empurra os antigos conforme desliza (sem salto).
      let acc = 0
      for (let i = vis.length - 1; i >= 0; i--) {
        const { b, a, p, h } = vis[i]
        const slot = bottom - acc - h
        acc += (h + 10) * p
        const yy = lerp(0.47 * H, slot, p)
        ctx.globalAlpha = a
        ctx.beginPath()
        ctx.roundRect(MARG, yy, 0.84 * W, h, 8)
        ctx.fillStyle = rgba(COL.night, 0.9)
        ctx.fill()
        ctx.lineWidth = 1.5
        ctx.strokeStyle = rgba(b.col, 0.5)
        ctx.stroke()
        ctx.font = `500 ${LBL}px ${b.fam}`
        ctx.fillStyle = b.col
        ctx.textAlign = 'left'
        ctx.textBaseline = 'top'
        b.lines.forEach((ln, k) => ctx.fillText(ln, MARG + 14, yy + 9 + k * LH))
        ctx.textBaseline = 'alphabetic'
        ctx.globalAlpha = 1
      }
    }
    // Painel da imagem do beat: entra e sai em 0,6 s; zoom lento de 1,00 a 1,04 ao longo do beat.
    const drawFrame = (t, idx) => {
      const img = IMGS[idx]
      const fb = FRAME[idx]
      if (!img || !fb) return
      const beat = beats[idx]
      const a = smooth(span(t, beat.start, 0.6)) * (1 - smooth(span(t, beat.end - 0.6, 0.6)))
      if (a <= 0.002) return
      const p = clamp((t - beat.start) / (beat.end - beat.start))
      const s = 1 + 0.04 * p
      ctx.save()
      ctx.globalAlpha = a
      ctx.beginPath()
      ctx.roundRect(fb.x, fb.y, fb.w, fb.h, 8)
      ctx.fillStyle = COL.night
      ctx.fill()
      ctx.clip()
      ctx.translate(fb.x + fb.w / 2, fb.y + fb.h / 2)
      ctx.scale(s, s)
      // Recorte cover: preenche o painel sem distorcer (no 9:16 o aspecto já bate e não corta).
      const ia = img.naturalWidth / img.naturalHeight
      const fa = fb.w / fb.h
      let sx = 0
      let sy = 0
      let sw = img.naturalWidth
      let sh = img.naturalHeight
      if (ia > fa) { sw = img.naturalHeight * fa; sx = (img.naturalWidth - sw) / 2 } else { sh = img.naturalWidth / fa; sy = (img.naturalHeight - sh) / 2 }
      ctx.drawImage(img, sx, sy, sw, sh, -fb.w / 2, -fb.h / 2, fb.w, fb.h)
      ctx.restore()
      ctx.save()
      ctx.globalAlpha = a
      ctx.beginPath()
      ctx.roundRect(fb.x + 0.5, fb.y + 0.5, fb.w - 1, fb.h - 1, 8)
      ctx.lineWidth = 1
      ctx.strokeStyle = COL.frame
      ctx.stroke()
      ctx.restore()
    }
    const drawTitle = (idx, t) => {
      const beat = beats[idx]
      const tb = TB[idx]
      const a = smooth(span(t, beat.start, 0.8)) * (1 - smooth(span(t, beat.end - 0.6, 0.6)))
      if (a <= 0.002) return
      ctx.globalAlpha = a
      ctx.fillStyle = rgba(COL.night, 0.7)
      ctx.beginPath()
      ctx.roundRect(tb.box.x, tb.box.y, tb.box.w, tb.box.h, 10)
      ctx.fill()
      ctx.textAlign = tb.align
      ctx.textBaseline = 'alphabetic'
      ctx.font = `500 ${KICK}px ${MONO}`
      ctx.fillStyle = COL.gold
      ctx.fillText(tb.kicker, tb.anchor, TOP + KICK)
      ctx.font = `600 ${TITLE}px ${MONO}`
      ctx.fillStyle = COL.fg
      tb.lines.forEach((ln, i) => ctx.fillText(ln, tb.anchor, TOP + KICK + 28 + TITLE * 1.12 * (i + 1)))
      ctx.globalAlpha = 1
      ctx.textAlign = 'left'
    }
    // Lockup do fechamento (último beat): "Usage Monitor" e o link de WF.link, do lado livre do horizonte;
    // o link encolhe só se não couber.
    const lastIdx = beats.length - 1
    const drawLockup = (t) => {
      const last = beats[lastIdx]
      const a = smooth(span(t, last.start + 0.8, 0.8))
      if (a <= 0.002) return
      const tb = TB[lastIdx]
      const x = tb.anchor
      const y = vertical ? 0.42 * H : H * 0.5 // 9:16: acima da legenda (74%) e fora do horizonte
      const maxW = vertical ? W - 2 * MARG : tb.col - 4
      let fs = vertical ? 34 : 30
      while (fs > 28 && measure(LINK, fs, 500) > maxW) fs -= 1
      ctx.globalAlpha = a
      ctx.textAlign = tb.align
      ctx.textBaseline = 'alphabetic'
      ctx.font = `600 ${TITLE}px ${MONO}`
      ctx.fillStyle = COL.fg
      ctx.fillText('Usage Monitor', x, y)
      ctx.font = `500 ${fs}px ${MONO}`
      ctx.fillStyle = COL.muted
      ctx.fillText(LINK, x, y + 70)
      ctx.globalAlpha = 1
      ctx.textAlign = 'left'
    }

    return {
      renderAt(time) {
        const t = clamp(time, 0, duration)
        const cam = camAt(t)
        const rot = DISC + cam.rot
        const cr = Math.cos(rot)
        const sr = Math.sin(rot)
        const { index: active } = beatAt(t)
        ctx.setTransform(1, 0, 0, 1, 0, 0)
        ctx.globalAlpha = 1
        ctx.drawImage(bg, 0, 0)
        drawStars(t)

        ctx.setTransform(cam.z, 0, 0, cam.z, cam.cx, cam.cy)
        ctx.globalAlpha = 0.85
        ctx.drawImage(glowSprite, -RS * 2.8, -RS * 2.8, RS * 5.6, RS * 5.6)
        ctx.globalAlpha = 1
        drawWaves(t)
        drawBand(t, cam, rot, false)
        drawDisc(t, cr, sr, cam, false)
        drawHorizon(t)
        clipOutside(0, 0, RS * 1.01, () => drawLensArcs(t, rot))
        drawBand(t, cam, rot, true)
        drawDisc(t, cr, sr, cam, true)
        drawMerge(t)

        ctx.setTransform(1, 0, 0, 1, 0, 0)
        ctx.globalAlpha = 1
        drawFrame(t, active)
        if (vertical) drawColumn(t, active)
        else drawBodies(t, active, cam)
        drawTitle(active, t)
        drawLockup(t)
        ctx.globalAlpha = 1
      },
      destroy() {
        canvas.remove()
      },
    }
  },
})
