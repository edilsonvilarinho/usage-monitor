// Cena "explica" (#686, A07b): porte do protótipo aprovado final/index.html. Só no corte longo, 16:9
// (index.html escolhe pelo WF.scene). Tudo é função de t, em segundos: renderAt(t) define o quadro inteiro.
// Sem transition, sem animação própria, sem Date, performance, timers ou Math.random. Texto de dado entra por textContent.
WF.register({
  id: 'explica',
  name: { pt: 'Explica', en: 'Explain' },
  concept: {
    pt: 'Quatro tópicos com imagem, esquema ou código; o buraco negro vai para o canto no primeiro.',
    en: 'Four topics with image, diagram or code; the black hole moves to the corner in the first.',
  },
  mount(stage, { format, images = [] }) {
    const { clamp, easeOut, span, beats, link } = WF
    const lang = WF.lang
    const tx = (v) => WF.text(v, lang)
    const W = 1920
    const H = 1080
    const MONO = '"IBM Plex Mono", monospace'
    const SANS = '"IBM Plex Sans", sans-serif'
    const NS = 'http://www.w3.org/2000/svg'
    const TAU = Math.PI * 2
    const COL = {
      gold: '#E8AE63', lens: '#F4CF97', hot: '#FFF0CB', fg: '#F2EDED', mu: '#B8B2B2', dim: '#4A4444',
      line: '#3D3838', s1: '#131010', s2: '#1B1818', s3: '#211E1E', ok: '#4CAF50', crit: '#E86A6A',
      info: '#4C8DFF', warn: '#FFA726',
    }
    const TONE = { info: COL.gold, crit: COL.crit, ok: COL.ok } // cor do sublinhado e do balão
    const CHIP = { info: COL.info, crit: COL.crit, ok: COL.ok } // cor do ponto do chip
    const PANEL = 'position:absolute;box-sizing:border-box;background:rgba(19,16,16,.94);border:1px solid #3D3838;border-radius:10px;overflow:hidden;'
    const LINK = String(link || '').replace(/^https?:\/\//, '')
    // Buraco negro: grande na abertura; desliza para o canto entre 0,6 e 1,8 s depois do speechStart do beat 0.
    const BIG = { x: 1180, y: 610, core: 330 }
    const SMALL = { x: 1760, y: 112, core: 54 }
    const EMBER = [180, 80, 30]
    const HOT = [255, 240, 203]
    const HS = beats[0].speechStart
    const HERO_END = HS + 1.8
    const enterOf = (i) => (i === 0 ? HERO_END : beats[i].start + 0.3)

    const mk = (css, text, tag = 'div') => {
      const e = document.createElement(tag)
      e.style.cssText = css
      if (text !== undefined && text !== null) e.textContent = String(text)
      return e
    }
    const sv = (tag, attrs) => {
      const e = document.createElementNS(NS, tag)
      for (const k of Object.keys(attrs)) e.setAttribute(k, String(attrs[k]))
      return e
    }
    const mctx = document.createElement('canvas').getContext('2d')

    // Céu do protótipo: gradientes no fundo e estrelas fixas (semente 7), todas no quadro inicial.
    stage.style.background = 'radial-gradient(ellipse 60% 70% at 8% 0%, rgba(30,61,143,.42), transparent 70%), '
      + 'radial-gradient(ellipse 55% 65% at 92% 100%, rgba(90,45,140,.50), transparent 70%), #080B12'
    let seed = 7
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647
    for (let i = 0; i < 70; i++) {
      const x = Math.round(rnd() * W)
      const y = Math.round(rnd() * H)
      const o = (0.15 + rnd() * 0.4).toFixed(2)
      stage.append(mk(`position:absolute;width:2px;height:2px;border-radius:50%;background:${COL.hot};opacity:${o};left:${x}px;top:${y}px`))
    }

    // Canvas do buraco negro, por baixo dos painéis.
    const bh = document.createElement('canvas')
    bh.width = W
    bh.height = H
    bh.style.cssText = `position:absolute;left:0;top:0;width:${W}px;height:${H}px;pointer-events:none`
    stage.append(bh)
    const hx = bh.getContext('2d')

    // Curva cubic-bezier(x1,y1,x2,y2) por bisseção no eixo x.
    const bezier = (x1, y1, x2, y2) => {
      const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx
      const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by
      const sx = (u) => ((ax * u + bx) * u + cx) * u
      const sy = (u) => ((ay * u + by) * u + cy) * u
      return (x) => {
        let lo = 0, hi = 1
        for (let i = 0; i < 30; i++) {
          const mid = (lo + hi) / 2
          if (sx(mid) < x) lo = mid; else hi = mid
        }
        return sy((lo + hi) / 2)
      }
    }
    const EASE_SLIDE = bezier(0.2, 0, 0, 1)
    const EASE_IN = bezier(0.2, 0, 0.2, 1)

    const holeAt = (t) => {
      const e = EASE_SLIDE(clamp((t - HS - 0.6) / 1.2))
      const grow = EASE_IN(clamp(t / 0.6))
      let core = BIG.core + (SMALL.core - BIG.core) * e
      core *= 0.92 + 0.08 * grow
      return {
        cx: BIG.x + (SMALL.x - BIG.x) * e,
        cy: BIG.y + (SMALL.y - BIG.y) * e,
        core,
        alpha: clamp(t / 0.4),
      }
    }
    // Brilho: uma volta em 9 s, com dois ou três pontos quentes; o lado da frente é mais claro.
    const bright = (a, ms) => {
      const phase = (ms / 9000) * TAU
      const w1 = Math.pow(0.5 + 0.5 * Math.cos(a - phase), 2.2)
      const w2 = 0.5 + 0.5 * Math.cos(2 * a - phase * 1.3 + 0.6)
      const warm = -Math.cos(a) * 0.12
      return clamp(0.1 + 0.7 * w1 + 0.25 * Math.pow(w2, 2) + warm, 0, 1)
    }
    const bandPass = (c, front, ms, cx, cy, room, k, weight) => {
      const rx = room * 0.95 * k, ry = rx * 0.29
      const a0 = front ? 0 : Math.PI, a1 = front ? Math.PI : TAU
      const w = room * 0.022 * (front ? 1 : 0.85)
      const S = 72
      const gBody = c.createLinearGradient(rx, 0, -rx, 0)
      const gFil = c.createLinearGradient(rx, 0, -rx, 0)
      for (let i = 0; i <= S; i++) {
        const a = a0 + (i / S) * (a1 - a0)
        const off = (1 - Math.cos(a)) / 2
        const b = bright(a, ms)
        const kk = Math.pow(b, 1.2)
        const r = Math.round(EMBER[0] + (HOT[0] - EMBER[0]) * kk)
        const g = Math.round(EMBER[1] + (HOT[1] - EMBER[1]) * kk)
        const bl = Math.round(EMBER[2] + (HOT[2] - EMBER[2]) * kk)
        const alpha = (front ? 1 : 0.6) * (0.2 + 0.8 * b) * weight
        gBody.addColorStop(off, `rgba(${r},${g},${bl},${alpha.toFixed(3)})`)
        gFil.addColorStop(off, `rgba(255,240,203,${(alpha * 0.5 * b).toFixed(3)})`)
      }
      c.save()
      c.translate(cx, cy + room * 0.08)
      c.rotate(-12 * Math.PI / 180)
      c.lineCap = 'round'
      c.beginPath()
      c.ellipse(0, 0, rx, ry, 0, a0, a1)
      c.strokeStyle = gBody
      c.lineWidth = w
      c.stroke()
      c.strokeStyle = gFil
      c.lineWidth = w * 0.4
      c.stroke()
      c.restore()
    }
    const ringPass = (c, part, ms, cx, cy, room) => {
      const BANDS = [0.84, 0.9, 0.96, 1.02, 1.08]
      for (let j = 0; j < BANDS.length; j++) {
        const k = BANDS[j]
        const speed = Math.pow(1 / k, 1.5)
        const weight = 1 - Math.abs(j - 2) / 3
        bandPass(c, part === 'front', ms * speed + j * 1700, cx, cy, room, k, weight)
      }
    }
    const drawHole = (t) => {
      const { cx, cy, core, alpha } = holeAt(t)
      const room = core / 0.62
      const ms = t * 1000
      hx.clearRect(0, 0, W, H)
      hx.globalAlpha = alpha
      // Halo suave.
      const hg = hx.createRadialGradient(cx, cy, core * 0.95, cx, cy, core * 1.9)
      hg.addColorStop(0, 'rgba(232,174,99,0.22)')
      hg.addColorStop(1, 'rgba(232,174,99,0)')
      hx.fillStyle = hg
      hx.beginPath()
      hx.arc(cx, cy, core * 1.9, 0, TAU)
      hx.fill()
      // Metade de trás do disco.
      ringPass(hx, 'back', ms, cx, cy, room)
      // Arco da lente, por cima do núcleo.
      hx.save()
      const lr = core * 1.09
      const lb = bright(1.5 * Math.PI, ms)
      const lg = hx.createLinearGradient(cx - lr, 0, cx + lr, 0)
      const la = (0.3 + 0.3 * lb).toFixed(3)
      lg.addColorStop(0, 'rgba(255,240,203,0)')
      lg.addColorStop(0.25, `rgba(255,240,203,${(la * 0.45).toFixed(3)})`)
      lg.addColorStop(0.5, `rgba(255,240,203,${la})`)
      lg.addColorStop(0.75, `rgba(255,240,203,${(la * 0.45).toFixed(3)})`)
      lg.addColorStop(1, 'rgba(255,240,203,0)')
      hx.strokeStyle = lg
      hx.lineWidth = Math.max(1.2, room * 0.012)
      hx.lineCap = 'butt'
      hx.beginPath()
      hx.arc(cx, cy, lr, Math.PI, 2 * Math.PI)
      hx.stroke()
      hx.restore()
      // Núcleo quase preto.
      hx.beginPath()
      hx.arc(cx, cy, core, 0, TAU)
      hx.fillStyle = '#030508'
      hx.fill()
      // Anel de fótons que respira, ciclo de 6 s.
      hx.save()
      hx.shadowColor = 'rgba(232,174,99,0.9)'
      hx.shadowBlur = 6 + 6 * (0.5 + 0.5 * Math.sin((TAU * ms) / 6000))
      hx.strokeStyle = COL.lens
      hx.lineWidth = Math.max(1.5, room * 0.022)
      hx.beginPath()
      hx.arc(cx, cy, core, 0, TAU)
      hx.stroke()
      hx.restore()
      // Metade da frente, por cima.
      ringPass(hx, 'front', ms, cx, cy, room)
      hx.globalAlpha = 1
    }

    // Sintaxe: mesmo critério do protótipo (palavras-chave, textos, chamadas, números, comentários).
    const KW = new Set('const let var function for of return if else try catch throw fun val class inline in is null true false new'.split(' '))
    const TOKEN = /\/\/.*$|\/\*[\s\S]*?\*\/|'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|\/(?:\\.|[^\/\\\n])+\/[a-z]*|\b\d+\b|[A-Za-z_]\w*|\s+|[\s\S]/g
    const SYN = { k: COL.gold, s: COL.lens, f: COL.info, d: COL.warn, cm: COL.dim }
    const tokens = (line) => {
      const out = []
      TOKEN.lastIndex = 0
      let m
      while ((m = TOKEN.exec(line))) {
        const s = m[0]
        const rest = line.slice(m.index + s.length)
        let cls = ''
        if (s.startsWith('//') || s.startsWith('/*')) cls = 'cm'
        else if (s[0] === "'" || s[0] === '"') cls = 's'
        else if (s[0] === '/' && s.length > 1) cls = 's'
        else if (/^\d+$/.test(s)) cls = 'd'
        else if (/^[A-Za-z_]/.test(s)) cls = KW.has(s) ? 'k' : (/^\s*\(/.test(rest) ? 'f' : '')
        out.push([s, cls])
      }
      return out
    }
    // Maior fonte (até 26 px) em que nenhuma linha passa da coluna de balões e tudo cabe na altura; piso de 24 px.
    const TEXT_X = 120 + 24 + 56 + 14
    const TEXT_MAX = 1585
    const CODE_X = 144
    const fitCode = (lines, availH) => {
      for (let fs = 26; fs > 24; fs -= 0.25) {
        mctx.font = `${fs}px ${MONO}`
        let right = 0
        for (const s of lines) right = Math.max(right, CODE_X + (TEXT_X - CODE_X + mctx.measureText(s).width) * 1.04)
        if (right <= TEXT_MAX && lines.length * fs * 1.5 <= availH) return fs
      }
      return 24
    }

    // Entrada em fade e deslize; a posição final fica fixa depois de entrar.
    const chip = (it, parent, fx) => {
      const c = mk(`display:inline-flex;align-items:center;gap:12px;height:50px;padding:0 20px;box-sizing:border-box;border:1px solid ${COL.line};border-radius:8px;background:${COL.s2};font:500 24px/1 ${MONO};color:${COL.fg};white-space:nowrap;opacity:0`)
      c.append(mk(`width:12px;height:12px;border-radius:50%;background:${CHIP[it.status] || COL.info}`), mk('', tx(it.text), 'span'))
      parent.append(c)
      fx.push((t) => {
        const p = easeOut(span(t, it.at, 0.38))
        c.style.opacity = String(p)
        c.style.transform = `translateY(${((1 - p) * 12).toFixed(2)}px)`
      })
    }

    const buildShot = (B, i, content, fx) => {
      const shot = mk(`${PANEL}left:120px;top:200px;width:1040px;height:680px;background:${COL.s2}`)
      const wrap = mk('position:absolute;left:0;top:14px;width:1040px;height:652px')
      const pic = images[i]
      if (pic) {
        pic.alt = 'Captura da janela de detalhe do monitor de uso'
        pic.style.cssText = 'display:block;width:1040px;height:652px'
        wrap.append(pic)
      }
      shot.append(wrap)
      const key = mk(`${PANEL}left:1200px;top:200px;width:600px;height:680px;background:${COL.s1};padding:0 44px;display:flex;flex-direction:column;justify-content:center;gap:36px`)
      content.append(shot, key)
      for (const m of B.markers || []) {
        const dot = mk(`position:absolute;box-sizing:border-box;width:40px;height:40px;margin:-20px 0 0 -20px;border-radius:50%;background:${COL.gold};color:#131010;font:600 22px/40px ${MONO};text-align:center;left:${m.x}%;top:${m.y}%;opacity:0`, String(m.n))
        wrap.append(dot)
        const row = mk('display:flex;align-items:center;gap:20px;opacity:0')
        row.append(
          mk(`flex:none;width:44px;height:44px;border-radius:50%;background:${COL.gold};color:#131010;font:600 22px/44px ${MONO};text-align:center`, String(m.n)),
          mk(`font:400 30px/1.3 ${SANS};color:${COL.fg}`, tx(m.label)),
        )
        key.append(row)
        fx.push((t) => {
          const p = easeOut(span(t, m.at, 0.38))
          dot.style.opacity = String(p)
          row.style.opacity = String(p)
          row.style.transform = `translateY(${((1 - p) * 12).toFixed(2)}px)`
        })
      }
      return key
    }

    // Esquema do conceito: fluxo (2 a 4 nós, volta por baixo) ou comparação (2 nós).
    const buildDiagram = (D, i, content, fx, E) => {
      const nodes = D.nodes || []
      const n = nodes.length
      const box = mk('position:absolute;left:120px;top:200px;width:1680px;height:270px')
      const svg = sv('svg', { width: 1680, height: 270, viewBox: '0 0 1680 270' })
      svg.style.cssText = 'position:absolute;left:0;top:0;overflow:visible'
      const defs = sv('defs', {})
      for (const [id, color] of [[`ahm${i}`, COL.mu], [`aha${i}`, COL.gold]]) {
        const m = sv('marker', { id, viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto' })
        m.append(sv('path', { d: 'M0,0 L10,5 L0,10 z', fill: color }))
        defs.append(m)
      }
      svg.append(defs)
      box.append(svg)
      const flow = D.type !== 'compare'
      const w = flow ? (n <= 3 ? 400 : 330) : 780
      const h = flow ? 120 : 150
      const fs = flow ? 36 : 32
      const xs = nodes.map((_, k) => (flow ? (k * (1680 - w)) / Math.max(1, n - 1) : k * 900))
      nodes.forEach((nd, k) => {
        const border = nd.tone === 'crit' ? COL.crit : nd.tone === 'ok' ? COL.ok : COL.line
        const e = mk(`position:absolute;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center;padding:0 28px;background:${COL.s2};border:2px solid ${border};border-radius:10px;font:500 ${fs}px/1.3 ${MONO};color:${COL.fg};left:${xs[k]}px;top:14px;width:${w}px;height:${h}px;opacity:0`, tx(nd.label))
        box.append(e)
        fx.push((t) => { e.style.opacity = String(easeOut(span(t, E + 0.15 * k, 0.38))) })
      })
      const arrow = (d, id, color, at) => {
        const p = sv('path', { d, pathLength: 1, fill: 'none', stroke: color, 'stroke-width': 3, 'stroke-dasharray': 1, 'marker-end': `url(#${id})` })
        p.style.strokeDashoffset = '1'
        p.style.opacity = '0'
        svg.append(p)
        fx.push((t) => {
          const q = easeOut(span(t, at, 0.45))
          p.style.strokeDashoffset = String(1 - q)
          p.style.opacity = q > 0 ? '1' : '0'
        })
      }
      if (flow) {
        for (let k = 0; k < n - 1; k++) {
          arrow(`M${xs[k] + w + 4} 74 L${xs[k + 1] - 4} 74`, `ahm${i}`, COL.mu, E + 0.15 * k + 0.1)
        }
        if (D.back) {
          const xl = xs[n - 1] + w / 2
          const x0 = xs[0] + w / 2
          arrow(`M${xl} 136 C${xl} 236, ${x0} 236, ${x0} 140`, `aha${i}`, COL.gold, E + 0.15 * n + 0.1)
          const lb = mk(`position:absolute;left:${(x0 + xl) / 2 - 260}px;top:222px;width:520px;text-align:center;font:400 26px/1.3 ${SANS};color:${COL.mu};opacity:0`, tx(D.back.label))
          box.append(lb)
          fx.push((t) => { lb.style.opacity = String(easeOut(span(t, E + 0.15 * n + 0.3, 0.38))) })
        }
      }
      content.append(box)
    }

    // Código: painel largo, linhas sem quebra, destaque sublinhado com zoom de 1,04 e balão à direita.
    const buildCode = (B, content, fx, E) => {
      const C = B.code
      const hasD = Boolean(B.diagram)
      const top = hasD ? 492 : 270
      const ph = hasD ? 408 : 610
      const panel = mk(`${PANEL}left:120px;top:${top}px;width:1680px;height:${ph}px;padding:16px 24px;background:${COL.s1}`)
      const head = mk(`height:38px;margin-bottom:12px;border-bottom:1px solid ${COL.line};font:400 22px/38px ${MONO};color:${COL.mu};white-space:nowrap;overflow:hidden`, `${C.file} · linhas ${C.from} a ${C.to}`)
      const fs = fitCode(C.lines, ph - 32 - 50)
      const lh = fs * 1.5
      const body = mk(`white-space:pre;font:400 ${fs}px/${lh}px ${MONO};color:${COL.fg}`)
      panel.append(head, body)
      content.append(panel)
      const rows = C.lines.map((src, k) => {
        const row = mk('position:relative;display:flex;white-space:pre;transform-origin:0 50%;opacity:0')
        const txt = mk('white-space:pre', '', 'span')
        for (const [s, cls] of tokens(src)) {
          if (cls) txt.append(mk(`color:${SYN[cls]}${cls === 'cm' ? ';font-style:italic' : ''}`, s, 'span'))
          else txt.append(document.createTextNode(s))
        }
        row.append(mk(`flex:none;width:56px;margin-right:14px;text-align:right;color:${COL.dim}`, String(C.from + k)), txt)
        body.append(row)
        return { row, txt, ln: C.from + k, k }
      })
      const HL = C.highlight || []
      const activeAt = (t) => {
        let act = null
        for (const h of HL) if (h.at <= t) act = h
        return act
      }
      for (const { row, txt, ln, k } of rows) {
        fx.push((t) => {
          const act = activeAt(t)
          const on = Boolean(act) && act.lines.includes(ln)
          const enter = easeOut(span(t, E + 0.06 * k, 0.3))
          row.style.opacity = String(enter * (act && !on ? 0.35 : 1))
          row.style.transform = on ? 'scale(1.04)' : 'none'
          txt.style.textDecoration = on ? 'underline' : 'none'
          if (on) {
            txt.style.textDecorationColor = TONE[act.tone] || COL.gold
            txt.style.textDecorationThickness = '3px'
            txt.style.textUnderlineOffset = '8px'
          }
        })
      }
      for (const h of HL) {
        if (!h.note) continue
        const color = TONE[h.tone] || COL.gold
        const cy = top + 66 + (h.lines[0] - C.from) * lh + lh / 2
        const ball = mk(`position:absolute;box-sizing:border-box;left:1590px;top:${cy.toFixed(1)}px;width:210px;padding:10px 14px 10px 16px;background:${COL.s3};border:1px solid ${COL.line};border-left:4px solid ${color};border-radius:8px;font:500 22px/1.3 ${SANS};color:${COL.fg};transform:translateY(-50%);opacity:0`, tx(h.note))
        ball.append(mk(`position:absolute;left:-9px;top:50%;margin-top:-8px;width:0;height:0;border-style:solid;border-width:8px 9px 8px 0;border-color:transparent ${color} transparent transparent`))
        content.append(ball)
        fx.push((t) => {
          const on = activeAt(t) === h
          ball.style.opacity = on ? String(easeOut(span(t, h.at, 0.38))) : '0'
        })
      }
    }

    // Cartões centralizados: todos a 35% desde a entrada; cada um acende no seu item.at.
    const buildCards = (items, content, fx) => {
      const list = mk('position:absolute;left:510px;top:285px;width:900px;display:flex;flex-direction:column;gap:30px')
      content.append(list)
      for (const it of items) {
        const ok = it.status === 'ok'
        const crit = it.status === 'crit'
        const color = ok ? COL.ok : crit ? COL.crit : COL.gold
        const card = mk(`display:flex;align-items:center;gap:24px;height:150px;padding:0 40px;box-sizing:border-box;background:${COL.s2};border:1px solid ${COL.line};border-left:8px solid ${color};border-radius:10px;font:500 44px/1.2 ${SANS};color:${COL.fg};opacity:0.35`)
        card.append(mk(`font:600 40px/1 ${MONO};color:${color}`, ok ? '✓' : crit ? '✗' : '·', 'span'), mk('', tx(it.text), 'span'))
        list.append(card)
        fx.push((t) => { card.style.opacity = String(0.35 + 0.65 * easeOut(span(t, it.at, 0.38))) })
      }
    }

    const isClosing = (B, i) => i === beats.length - 1 && !B.code && !B.image && !B.diagram
      && !(B.items || []).length && !(B.markers || []).length

    // Cada beat vira um bloco próprio; renderAt mostra só o bloco do beat atual.
    const beatEls = []
    const contents = []
    const fxs = []
    const enters = []
    beats.forEach((B, i) => {
      const fx = []
      const E = enterOf(i)
      const root = mk(`position:absolute;left:0;top:0;width:${W}px;height:${H}px;display:none`)
      const head = mk('position:absolute;left:120px;top:52px;width:1680px;height:120px')
      head.append(
        mk(`margin:0;font:500 24px/1 ${MONO};letter-spacing:.08em;color:${COL.gold}`, tx(B.kicker), 'p'),
        mk(`margin:16px 0 0;font:600 60px/72px ${MONO};color:${COL.fg};white-space:nowrap`, tx(B.title), 'h1'),
      )
      const content = mk(`position:absolute;left:0;top:0;width:${W}px;height:${H}px`)
      const items = B.items || []
      if (B.image) {
        const key = buildShot(B, i, content, fx)
        for (const it of items) chip(it, key, fx)
      } else if (B.code || B.diagram) {
        if (B.diagram) buildDiagram(B.diagram, i, content, fx, E)
        if (B.code) buildCode(B, content, fx, E)
        if (items.length) {
          const row = mk(`position:absolute;left:120px;top:${B.diagram ? 430 : 200}px;width:1680px;display:flex;justify-content:flex-end;gap:16px`)
          content.append(row)
          for (const it of items) chip(it, row, fx)
        }
      } else if (items.length) {
        buildCards(items, content, fx)
      }
      if (isClosing(B, i)) {
        const lock = mk('position:absolute;left:0;top:380px;width:1920px;text-align:center')
        lock.append(
          mk(`font:600 80px/1.1 ${MONO};color:${COL.fg}`, 'Usage Monitor'),
          mk(`margin-top:28px;font:500 30px/1.3 ${MONO};color:${COL.gold}`, LINK),
        )
        content.append(lock)
      }
      root.append(head, content)
      stage.append(root)
      beatEls.push(root)
      contents.push(content)
      fxs.push(fx)
      enters.push(E)
    })

    return {
      // t em segundos; função pura de t. Mesmo t, mesmo quadro.
      renderAt(time) {
        const t = clamp(Number(time) || 0, 0, WF.duration)
        const k = WF.beatAt(t).index
        beatEls.forEach((e, i) => { e.style.display = i === k ? 'block' : 'none' })
        const B = beats[k]
        // Conteúdo: entra em fade e deslize de 12 px em 380 ms; sai em fade nos 300 ms antes do fim do beat.
        const inP = easeOut(span(t, enters[k], 0.38))
        const outP = 1 - span(t, B.end - 0.3, 0.3)
        contents[k].style.opacity = String(inP * outP)
        contents[k].style.transform = `translateY(${((1 - inP) * 12).toFixed(2)}px)`
        for (const f of fxs[k]) f(t)
        drawHole(t)
      },
    }
  },
})
