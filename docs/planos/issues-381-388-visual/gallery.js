/* Propostas das issues #382, #384, #386, #387, #388 (rodadas N–R). Dados sintéticos, sem ações de produção. */
(() => {
const $ = (s, r = document) => r.querySelector(s);

/* ── primitivas de mockup ── */
const W = (title, body, opt = {}) => `<div class="w">
  <div class="w-title"><b>${title}</b>${opt.sub ? `<span>· ${opt.sub}</span>` : ''}<span class="x">□×</span></div>
  ${opt.bar ? `<div class="w-bar">${opt.bar}</div>` : ''}
  <div class="${opt.flush ? '' : 'w-in'}">${body}</div>
  ${opt.status ? `<div class="w-status">${opt.status}</div>` : ''}
</div>`;
const seg = (items, on = 0) => `<span class="seg">${items.map((t, i) => `<span class="${i === on ? 'on' : ''}">${t}</span>`).join('')}</span>`;
const btn = (t, k = '') => `<span class="btn ${k}">${t}</span>`;
const chip = (t, on) => `<span class="chip ${on ? 'on' : ''}">${t}</span>`;
const pill = (t, c) => `<span class="pill" style="--c:var(--${c})">${t}</span>`;
const metric = (l, v, h = '', cls = '') => `<div class="metric ${cls}"><div class="l">${l}</div><div class="v">${v}</div>${h ? `<div class="h">${h}</div>` : ''}</div>`;
const metrics = (list, n = list.length) => `<div class="split" style="grid-template-columns:repeat(${n},1fr)">${list.map(m => metric(...m)).join('')}</div>`;
const track = (p, c = 'fg', w = '') => `<div class="track" style="${w ? `width:${w};` : ''}"><i style="width:${p}%;--c:var(--${c})"></i></div>`;
const table = (heads, rows, numCols = [], sel = -1) => `<table class="t"><thead><tr>${heads.map((h, i) => `<th class="${numCols.includes(i) ? 'n' : ''}">${h}</th>`).join('')}</tr></thead><tbody>${rows.map((r, ri) => `<tr class="${ri === sel ? 'sel' : ''}">${r.map((c, i) => `<td class="${numCols.includes(i) ? 'n' : ''}">${c}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
const legend = items => `<div class="legend">${items.map(([t, c]) => `<span style="--c:var(--${c})">${t}</span>`).join('')}</div>`;
const NEW = s => `<span class="new">${s}</span>`;
const spark = (pts, c, w = 120, h = 28, fill = false) => {
  const max = Math.max(...pts, 1), step = w / (pts.length - 1);
  const d = pts.map((v, i) => `${i ? 'L' : 'M'}${(i * step).toFixed(1)},${(h - 2 - (v / max) * (h - 4)).toFixed(1)}`).join('');
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${fill ? `<path d="${d}L${w},${h}L0,${h}Z" style="fill:var(--${c});opacity:.15"/>` : ''}<path d="${d}" style="fill:none;stroke:var(--${c});stroke-width:1.5"/></svg>`;
};
const bars = (vals, c, w = 220, h = 46, labels) => {
  const max = Math.max(...vals, 1), bw = w / vals.length;
  return `<svg width="100%" height="${h + (labels ? 12 : 0)}" viewBox="0 0 ${w} ${h + (labels ? 12 : 0)}" preserveAspectRatio="none">${vals.map((v, i) => `<rect x="${i * bw + 1}" y="${h - (v / max) * h}" width="${bw - 2}" height="${(v / max) * h}" rx="1" style="fill:var(--${Array.isArray(c) ? c[i] : c})"/>`).join('')}${labels ? labels.map((l, i) => `<text x="${i * bw + bw / 2}" y="${h + 10}" text-anchor="middle" style="fill:var(--muted);font:8px var(--mono)">${l}</text>`).join('') : ''}</svg>`;
};

/* ── dados sintéticos coerentes entre as telas ── */
const PROJ = ['usage-monitor', 'api-gateway', 'checkout-web', 'coletor-android'];
const CLAUDE_SESS = [
  ['7c4a1f92', 'usage-monitor', 'claude-opus-5-5', '48', '4,40 M', '96%', 'US$ 5,4792', '2h 14min', '94 tok/s'],
  ['b81e35c0', 'api-gateway', 'claude-sonnet-5', '26', '1,92 M', '95%', 'US$ 3,6100', '1h 5min', '118 tok/s'],
  ['e03d77aa', 'checkout-web', 'claude-opus-5', '11', '612 k', '91%', 'US$ 0,9921', '24min', '87 tok/s'],
];
const CODEX_SESS = [
  ['01a0fee1', 'usage-monitor', 'gpt-5.6-luna', '73', '3,10 M', '88%', '—', '1h 52min', '33 tok/s'],
  ['01a08196', 'coletor-android', 'gpt-6.1-sol', '19', '820 k', '79%', '—', '31min', '29 tok/s'],
];
const MODELS = [
  { m: 'claude-opus-5-5', src: 'Anthropic', c: 'anthropic', tok: '6,32 M', out: '182 k', cost: 'US$ 9,10', perM: 'US$ 1,44', tps: 94, cache: 96, quota: 68, turns: 112 },
  { m: 'claude-sonnet-5', src: 'Anthropic', c: 'anthropic', tok: '2,54 M', out: '96 k', cost: 'US$ 2,31', perM: 'US$ 0,91', tps: 118, cache: 95, quota: 68, turns: 61 },
  { m: 'gpt-5.6-luna', src: 'Codex', c: 'codex', tok: '3,10 M', out: '141 k', cost: 'sem tarifa', perM: '—', tps: 33, cache: 88, quota: 41, turns: 73 },
  { m: 'gpt-6.1-sol', src: 'Codex', c: 'codex', tok: '820 k', out: '38 k', cost: 'sem tarifa', perM: '—', tps: 29, cache: 79, quota: 41, turns: 19 },
  { m: 'MiniMax-M2', src: 'MiniMax', c: 'minimax', tok: '—', out: '—', cost: '—', perM: '—', tps: null, cache: null, quota: 22, turns: 340, unit: '340 req' },
];
const HOURS = [0,0,0,0,0,0,0,1,4,12,18,22,9,6,14,20,17,8,3,1,0,0,0,0];

/* trilho de uma janela: início → reinício, faixa ativa e agora */
const windowRail = ({ w = 520, label = '5h', start = '08:05', end = '13:05', a0 = .02, a1 = .72, now = .78, peak = .66, peakTxt = '68%', c = 'anthropic', compact = false } = {}) => {
  const h = compact ? 30 : 44, y = compact ? 12 : 16;
  return `<svg width="100%" height="${h}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">
  <rect x="0" y="${y}" width="${w}" height="8" rx="4" style="fill:var(--raised);stroke:var(--border)"/>
  <rect x="${a0 * w}" y="${y}" width="${(a1 - a0) * w}" height="8" rx="4" style="fill:var(--${c});opacity:.85"/>
  <line x1="${now * w}" x2="${now * w}" y1="${y - 6}" y2="${y + 14}" style="stroke:var(--fg);stroke-width:1.2"/>
  <circle cx="${peak * w}" cy="${y + 4}" r="3" style="fill:var(--fg)"/>
  ${compact ? '' : `<text x="0" y="${y + 24}" style="fill:var(--muted);font:9px var(--mono)">${start}</text>
  <text x="${w}" y="${y + 24}" text-anchor="end" style="fill:var(--muted);font:9px var(--mono)">reinício ${end}</text>
  <text x="${now * w + 4}" y="${y - 4}" style="fill:var(--fg);font:9px var(--mono)">agora</text>
  <text x="${peak * w}" y="${y - 4}" text-anchor="end" style="fill:var(--muted);font:9px var(--mono)">pico ${peakTxt}</text>`}
  <text x="4" y="${y - 3}" style="fill:var(--muted);font:9px var(--mono)">${compact ? '' : label}</text>
  </svg>`;
};

/* ── #382 — Histórico: faixa ativa da sessão (rodada N) ── */
const histBar = `${seg(['24h', '7 dias', '30 dias', 'Total'], 1)} ${seg(['5h', '7d', 'Ambas'], 2)} <span class="grow"></span>${btn('PDF')}`;
const histWin = (body, sub = 'Anthropic · Padrão') => W('Histórico', body, { sub, bar: histBar, status: '<span>Última coleta: Ter 06/10 11h00 BRT</span>' });
const WIN_ROWS = [
  ['06/10 08:05 · atual', '08:12 → 11:40', '3h 28min', '68%', '—', '20 %/h'],
  ['06/10 03:02', '03:02 → 06:14', '3h 12min', '100%', '3h 12min', '31 %/h'],
  ['05/10 21:58', '22:20 → 23:05', '45min', '24%', '—', '32 %/h'],
  ['05/10 14:10', '14:10 → 18:55', '4h 45min', '74%', '—', '16 %/h'],
];
const issue382 = {
  id: '382', letter: 'N', title: '#382 · Janelas 5h / 7d / 30d',
  intro: 'Histórico guarda hoje só o <b>primeiro e o último poll</b> da janela. A proposta grava a <b>faixa ativa</b>: primeiro e último instante em que o uso subiu (delta de cota, #385), cruzado com os turnos do CLI no Anthropic e no Codex. A janela nem sempre esgota — a faixa mostra quanto dela foi usada. OpenCode Go (e Codex mensal) ganham a janela de 30 dias no mesmo agrupamento. <b>Sem valor novo em <code>HistoryQuotaView</code></b>: 30d entra por parâmetro/linha própria.',
  ref: { title: 'Hoje (referência)', why: 'Tabela "Janelas 5h" com início observado (primeiro poll), pico, esgotou e ritmo. Go aparece em três cards soltos (Go, Go semanal, Go mensal) sem seletor.',
    html: histWin(`<div class="eyebrow">Janelas 5h · 5 janelas no intervalo</div>${table(['Início observado', 'Pico', 'Esgotou em', 'Ritmo'], WIN_ROWS.map(r => [r[0], r[3], r[4], r[5]]), [1, 2, 3])}`) },
  options: [
    { name: 'Faixa na linha do tempo', why: 'Cada janela vira um trilho de início ao reinício; a faixa colorida é a atividade real, o ponto é o pico, o traço é agora. Uma linha por janela (5h, 7d, 30d).',
      html: histWin(`<div class="eyebrow">Janela 5h atual</div>${windowRail({})}<div class="row sm mut"><span>Ativa 08:12 → 11:40 · <b class="num" style="color:var(--fg)">3h 28min</b> de 5h</span><span class="grow"></span><span>ociosa 1h 32min</span></div>
      <div class="eyebrow">Janela 7d atual</div>${windowRail({ label: '7d', start: 'Sáb 21h', end: 'Sáb 21h', a0: .05, a1: .52, now: .55, peak: .5, peakTxt: '41%' })}`) },
    { name: 'Colunas na tabela de janelas', why: 'Menor mudança: a tabela atual ganha <b>Ativa (de → até)</b> e <b>Duração ativa</b>. "Início observado" passa a ser a primeira atividade.',
      html: histWin(`<div class="eyebrow">Janelas 5h · 5 janelas no intervalo</div>${table(['Início', NEW('Ativa'), NEW('Duração ativa'), 'Pico', 'Esgotou', 'Ritmo'], WIN_ROWS, [3, 4, 5], 0)}`) },
    { name: 'Cartão por janela', why: 'Uma janela por cartão: rótulo, faixa ativa em texto grande, mini trilho e veredito (esgotou / reiniciou com folga).',
      html: histWin(`<div class="split" style="grid-template-columns:1fr 1fr">${WIN_ROWS.slice(0, 4).map((r, i) => `<div class="panel mark" style="--c:var(--${i === 1 ? 'crit' : 'anthropic'})"><div class="row"><span class="eyebrow">${r[0]}</span></div><div class="num" style="font-size:14px">${r[1]}</div>${windowRail({ compact: true, a0: [.02, 0, .07, 0][i], a1: [.72, .64, .22, .95][i], now: i ? 1 : .78 })}<div class="row sm"><span class="mut">ativa ${r[2]}</span><span class="grow"></span>${i === 1 ? pill('Esgotou em 3h 12min', 'crit') : pill(`Pico ${r[3]}`, 'ok')}</div></div>`).join('')}</div>`) },
    { name: 'Gantt das janelas', why: 'As últimas 8 janelas empilhadas no mesmo eixo de horas. Mostra de relance quando o uso concentra e quanto de cada janela ficou ocioso.',
      html: histWin(`<div class="eyebrow">Janelas 5h · eixo de 0 a 5h</div>${['06/10 08:05', '06/10 03:02', '05/10 21:58', '05/10 14:10', '05/10 09:02', '04/10 20:40'].map((l, i) => `<div class="row"><span class="mono sm mut" style="width:86px">${l}</span><div class="grow">${windowRail({ compact: true, a0: [.02, 0, .07, 0, .2, .1][i], a1: [.72, .64, .22, .95, .5, .9][i], now: i ? 2 : .78, peak: [.66, .64, .2, .9, .45, .85][i], c: i === 1 ? 'crit' : 'anthropic' })}</div><span class="num sm" style="width:44px;text-align:right">${['68%', '100%', '24%', '74%', '39%', '81%'][i]}</span></div>`).join('')}${legend([['ativa', 'anthropic'], ['esgotou', 'crit'], ['ociosa', 'raised']])}`) },
    { name: 'Relógio da janela', why: 'A janela de 5h como mostrador: o arco é o tempo ativo, a abertura é o tempo ocioso, o ponteiro é agora. 7d vira mostrador de dias da semana.',
      html: histWin(`<div class="row" style="justify-content:space-around">${[['5h', .69, '3h 28min de 5h', 'anthropic'], ['7d', .47, '3d 7h de 7d', 'anthropic'], ['30d', .3, 'Go · 9 de 30 dias', 'oc']].map(([l, f, t, c]) => { const r = 34, C = 2 * Math.PI * r; return `<div class="col" style="align-items:center"><svg width="96" height="96" viewBox="0 0 96 96"><circle cx="48" cy="48" r="${r}" style="fill:none;stroke:var(--raised);stroke-width:8"/><circle cx="48" cy="48" r="${r}" transform="rotate(-90 48 48)" style="fill:none;stroke:var(--${c});stroke-width:8;stroke-dasharray:${C * f} ${C}"/><text x="48" y="46" text-anchor="middle" style="fill:var(--fg);font:600 14px var(--mono)">${l}</text><text x="48" y="60" text-anchor="middle" style="fill:var(--muted);font:9px var(--mono)">${Math.round(f * 100)}% ativa</text></svg><span class="sm mut">${t}</span></div>`; }).join('')}</div>`) },
    { name: 'Degraus de consumo', why: 'O gráfico de % ganha uma chave sobre o trecho em que a curva subiu (faixa ativa) e sombreia o resto como ocioso. Mantém o gráfico aprovado na #383.',
      html: histWin(`<svg width="100%" height="110" viewBox="0 0 520 110" preserveAspectRatio="none"><rect x="10" y="10" width="${.7 * 500}" height="90" style="fill:var(--anthropic);opacity:.08"/><path d="M10,96 L40,92 L80,80 L120,70 L150,52 L190,46 L230,40 L270,34 L300,30 L360,30 L510,30" style="fill:none;stroke:var(--anthropic);stroke-width:2"/><path d="M10,6 L10,2 L360,2 L360,6" style="fill:none;stroke:var(--fg)"/><text x="185" y="12" text-anchor="middle" style="fill:var(--fg);font:9px var(--mono)">ativa 08:12 → 11:40 · 3h 28min</text><text x="440" y="24" text-anchor="middle" style="fill:var(--muted);font:9px var(--mono)">ociosa 1h 32min</text></svg>${legend([['Claude 5h', 'anthropic'], ['faixa ativa', 'raised']])}`) },
    { name: 'Resumo em frase', why: 'Antes do gráfico, uma frase por cota com os números da janela atual. Bom para quem só quer saber "quanto da janela usei e quando".',
      html: histWin(`<div class="panel mark" style="--c:var(--anthropic)"><div><b>5h</b> · Sessão ativa das <b class="num">08:12</b> às <b class="num">11:40</b> (3h 28min de 5h). Pico de 68% às 11:31. Reinicia às 13:05 com 32% de folga.</div></div><div class="panel mark" style="--c:var(--anthropic)"><div><b>7d</b> · Uso em 4 dos 7 dias, das <b class="num">Sáb 21h</b> até agora. 41% consumido; no ritmo atual reinicia antes do limite.</div></div><div class="panel mark" style="--c:var(--oc)"><div><b>Go 30d</b> · Ativo em 9 de 30 dias. 30% consumido.</div></div>`) },
    { name: 'Calendário de atividade', why: 'Para 7d e 30d: grade dia × hora com as horas em que o uso subiu; as janelas de 5h aparecem como contornos por cima.',
      html: histWin(`<div class="eyebrow">7 dias · horas com consumo (BRT)</div><svg width="100%" height="100" viewBox="0 0 480 100">${['Qua', 'Qui', 'Sex', 'Sáb', 'Dom', 'Seg', 'Ter'].map((d, r) => `<text x="0" y="${r * 13 + 10}" style="fill:var(--muted);font:8px var(--mono)">${d}</text>` + Array.from({ length: 24 }, (_, h) => { const v = (HOURS[h] * (1 + ((r * 7 + h) % 3))) % 25; return `<rect x="${26 + h * 18.5}" y="${r * 13}" width="16" height="11" rx="2" style="fill:var(--anthropic);opacity:${v ? .15 + v / 30 : .04}"/>`; }).join('')).join('')}<rect x="${26 + 8 * 18.5 - 1}" y="${6 * 13 - 1}" width="${5 * 18.5}" height="13" rx="2" style="fill:none;stroke:var(--fg)"/></svg><div class="sm mut">Contorno: janela 5h atual (08:05 → 13:05).</div>`) },
    { name: 'Três trilhos alinhados ao agora', why: 'Todas as janelas da fonte empilhadas e alinhadas pelo instante atual: Go mostra 5h, 7d e 30d juntos; Anthropic mostra 5h e 7d.',
      html: histWin(`${[['Go 5h', .1, .6, .66, '58%'], ['Go 7d', .2, .7, .62, '44%'], ['Go 30d', .05, .4, .38, '30%']].map(([l, a0, a1, p, t]) => `<div class="row"><span class="mono sm" style="width:52px">${l}</span><div class="grow">${windowRail({ compact: true, a0, a1, now: .7, peak: p, c: 'oc' })}</div><span class="num sm" style="width:36px;text-align:right">${t}</span></div>`).join('')}<div class="sm mut">Traço vertical = agora. Faixa = uso detectado por variação da cota (OpenCode Go não tem CLI local).</div>`, 'OpenCode Go') },
    { name: 'Marcos da janela', why: 'Lista vertical de eventos da janela atual: abriu, primeira atividade, pico, última atividade, reinício previsto. Copia o vocabulário do log.',
      html: histWin(`${[['08:05', 'Janela aberta', 'muted'], ['08:12', 'Primeira atividade · 2%', 'anthropic'], ['10:02', 'Ritmo máximo · 31 %/h', 'warn'], ['11:31', 'Pico · 68%', 'anthropic'], ['11:40', 'Última atividade', 'anthropic'], ['13:05', 'Reinício previsto · 32% de folga', 'ok']].map(([t, l, c]) => `<div class="row"><span class="num sm mut" style="width:40px">${t}</span>${pill(l, c)}</div>`).join('')}<div class="sm mut">Ativa 3h 28min · ociosa até agora 1h 20min.</div>`) },
  ],
};

/* ── #384 — Modais de sessões CLI (rodada O) ── */
const cliBar = (prov = 'Anthropic') => `${seg(['Sessões', 'Resumo'])} <span class="live">AO VIVO</span> ${seg(['5h', '7 dias', '30 dias', 'Total'])} <span class="grow"></span>${btn('CSV')}${btn('JSON')}${btn('PDF', prov === 'Codex' ? 'pri' : '')}`;
const cliWin = (body, prov = 'Anthropic', sub) => W(`Sessões CLI — ${prov}`, body, { sub: sub || (prov === 'Anthropic' ? 'Padrão' : 'Codex'), bar: cliBar(prov), status: '<span>Lido 11:42:05</span><span>3 sessões · índice em dia</span>' });
const sessRows = (list, tps = true) => list.map(s => [`<span class="mono">${s[0]}</span>`, s[1], s[3], s[4], s[5], s[6], s[7], ...(tps ? [NEW(s[8])] : [])]);
const sessHeads = (tps = true) => ['Sessão', 'Projeto', 'Turnos', 'Tokens', 'Cache', 'Custo', 'Ativo', ...(tps ? ['Vazão'] : [])];
const issue384 = {
  id: '384', letter: 'O', title: '#384 · Modais CLI Anthropic e Codex',
  intro: 'Os dois modais passam a ter o <b>mesmo padrão</b>: lista + resumo, selo ao vivo sem botão manual (o laço de 5 s já existe nos dois — A02 mediu índice Codex em ~12 ms), <b>vazão em tok/s</b> (#381: Claude mediana 95 tok/s, Codex 32 tok/s, medida ponta a ponta) e <b>PDF no idioma configurado</b> (o rodapé hoje cai em PT). Destaque tracejado = o que é novo.',
  ref: { title: 'Hoje (referência)', why: 'Anthropic: lista em colunas estáveis, Sessões/Resumo, selo AO VIVO, CSV/JSON/PDF. Codex: botão manual "Atualizar", "Atualizando…" a cada tique, sem Resumo e sem PDF.',
    html: `<div class="col">${W('Sessões CLI — Codex', `${table(['Sessão', 'Projeto', 'Turnos', 'Tokens', 'Ativo'], CODEX_SESS.map(s => [s[0], s[1], s[3], s[4], s[7]]), [2, 3, 4])}`, { bar: `<span class="live">AO VIVO</span> ${seg(['5h', '24h', '7 dias'])} <span class="grow"></span><span class="sm mut">Atualizando…</span>${btn('Atualizar')}${btn('CSV')}${btn('JSON')}` })}</div>` },
  options: [
    { name: 'Evolução mínima', why: 'Mantém a lista aprovada (#81) e só iguala os dois: coluna Vazão, PDF no Codex, Resumo no Codex, sem botão manual.',
      html: cliWin(`${metrics([['Sessões', '3'], ['Tokens', '6,53 M'], ['Custo estimado', 'US$ 9,5012'], [NEW('Vazão mediana'), '95 tok/s', 'ponta a ponta']])}${table(sessHeads(), sessRows(CLAUDE_SESS), [2, 3, 4, 5, 6, 7])}`) },
    { name: 'Mestre e detalhe', why: 'Lista estreita à esquerda, detalhe da sessão à direita (turnos, modelos, vazão por turno). Sem abrir janela nova para o detalhe.',
      html: cliWin(`<div class="split" style="grid-template-columns:200px 1fr">${`<div class="col">${CLAUDE_SESS.map((s, i) => `<div class="panel ${i ? '' : 'mark'}" style="--c:var(--anthropic)"><div class="row"><span class="mono">${s[0]}</span><span class="grow"></span><span class="num sm">${s[6]}</span></div><div class="sm mut">${s[1]} · ${s[7]}</div></div>`).join('')}</div>`}<div class="col"><div class="eyebrow">7c4a1f92 · usage-monitor · main</div>${metrics([['Turnos', '48'], ['Vazão', '94 tok/s'], ['Cache', '96%']])}<div class="eyebrow">Vazão por turno</div>${spark([80, 95, 101, 88, 94, 120, 76, 92, 99, 110, 85, 94], 'output', 300, 40, true)}${table(['Hora', 'Modelo', 'Saída', 'tok/s'], [['11:40', 'opus-5-5', '2,1 k', '98'], ['11:38', 'opus-5-5', '840', '91'], ['11:31', 'opus-5-5', '3,4 k', '104']], [2, 3])}</div></div>`) },
    { name: 'Cartões de sessão', why: 'Cada sessão é um cartão com 4 números e a barra de cache. Escaneável em janela estreita; a tabela fica para o Resumo.',
      html: cliWin(`<div class="split" style="grid-template-columns:1fr 1fr">${CLAUDE_SESS.concat([CLAUDE_SESS[0]]).slice(0, 4).map((s, i) => `<div class="panel mark" style="--c:var(--anthropic)"><div class="row"><span class="mono">${s[0]}</span><span class="sm mut">${s[1]}</span><span class="grow"></span>${i === 0 ? pill('Trabalhando agora', 'ok') : ''}</div><div class="split" style="grid-template-columns:repeat(4,1fr)">${[['Tokens', s[4]], ['Custo', s[6].replace('US$ ', '$')], ['Ativo', s[7]], ['Vazão', s[8]]].map(([l, v]) => `<div><div class="eyebrow">${l}</div><div class="num">${v}</div></div>`).join('')}</div>${track(parseInt(s[5]), 'cread')}<div class="sm mut">cache ${s[5]}</div></div>`).join('')}</div>`) },
    { name: 'Uma janela, duas fontes', why: 'Anthropic e Codex na mesma janela, com abas por fonte e a mesma tabela. O menu da HUD abre direto na aba da conta clicada.',
      html: W('Sessões CLI', `${table(sessHeads(), sessRows(CODEX_SESS), [2, 3, 4, 5, 6, 7])}<div class="sm mut">Custo "—": Codex sem tarifa na tabela de preços; não vira custo zero.</div>`, { bar: `<span class="seg"><span>Anthropic · Padrão</span><span class="on">Codex</span></span> <span class="live">AO VIVO</span>${seg(['5h', '7 dias', '30 dias', 'Total'])}<span class="grow"></span>${btn('CSV')}${btn('JSON')}${btn('PDF')}` }) },
    { name: 'Trilhas no tempo', why: 'Cada sessão é uma trilha no eixo das últimas 5h; cada risco é um turno, a altura é a saída. Mostra paralelismo entre sessões.',
      html: cliWin(`<svg width="100%" height="120" viewBox="0 0 520 120">${CLAUDE_SESS.map((s, r) => `<text x="0" y="${r * 36 + 22}" style="fill:var(--fg);font:10px var(--mono)">${s[0]}</text><line x1="70" x2="520" y1="${r * 36 + 26}" y2="${r * 36 + 26}" style="stroke:var(--border)"/>` + Array.from({ length: [28, 16, 8][r] }, (_, k) => { const x = 70 + ([20, 140, 300][r] + k * [11, 9, 12][r]) % 450; const hh = 4 + ((k * 7 + r * 3) % 14); return `<rect x="${x}" y="${r * 36 + 26 - hh}" width="3" height="${hh}" style="fill:var(--anthropic)"/>`; }).join('')).join('')}<text x="70" y="118" style="fill:var(--muted);font:9px var(--mono)">06:45</text><text x="520" y="118" text-anchor="end" style="fill:var(--muted);font:9px var(--mono)">agora 11:45</text></svg>${legend([['turno (altura = tokens de saída)', 'anthropic']])}`) },
    { name: 'Agora em destaque', why: 'Faixa fixa no topo com a sessão ativa: vazão do último turno, contexto, sem resposta há N min. Abaixo, o histórico em tabela.',
      html: cliWin(`<div class="panel mark" style="--c:var(--ok)"><div class="row">${pill('Trabalhando agora', 'ok')}<span class="mono">7c4a1f92</span><span class="sm mut">usage-monitor · claude-opus-5-5</span><span class="grow"></span><span class="num" style="font-size:16px">98 tok/s</span></div><div class="row sm mut"><span>último turno 11:40:12 · 2,1 k saída</span><span class="grow"></span><span>contexto 72%</span></div>${track(72, 'warn')}</div>${table(sessHeads(), sessRows(CLAUDE_SESS.slice(1)), [2, 3, 4, 5, 6, 7])}`) },
    { name: 'Agrupado por projeto', why: 'Projetos como grupos recolhíveis com totais; sessões dentro. Responde "quanto gastei em cada repositório" sem ir ao Resumo.',
      html: cliWin(`${[['usage-monitor', '2 sessões', '7,50 M', 'US$ 5,48', '64 tok/s', true], ['api-gateway', '1 sessão', '1,92 M', 'US$ 3,61', '118 tok/s', false], ['checkout-web', '1 sessão', '612 k', 'US$ 0,99', '87 tok/s', false]].map(([p, n, t, c, v, open]) => `<div class="panel"><div class="row"><span class="mono">${open ? '▾' : '▸'} ${p}</span><span class="sm mut">${n}</span><span class="grow"></span><span class="num sm">${t}</span><span class="num sm" style="width:70px;text-align:right">${c}</span><span class="num sm" style="width:64px;text-align:right">${v}</span></div>${open ? table(['Sessão', 'Fonte', 'Turnos', 'Tokens', 'Vazão'], [['7c4a1f92', 'Anthropic', '48', '4,40 M', '94 tok/s'], ['01a0fee1', 'Codex', '73', '3,10 M', '33 tok/s']], [2, 3, 4]) : ''}</div>`).join('')}`) },
    { name: 'Relatório contínuo', why: 'A tela é o próprio PDF: título, período, resumo em frases, tabelas. Exportar não surpreende porque é o que está na tela.',
      html: cliWin(`<div class="col" style="max-width:440px;margin:0 auto"><div class="eyebrow">Relatório · últimas 5h · Ter 06/10</div><div style="font-size:14px;font-weight:600">Sessões CLI — Anthropic · Padrão</div><div class="sm">3 sessões, 6,53 M tokens (96% cache), custo estimado US$ 9,5012 a preço de tabela. Vazão mediana 95 tok/s, ponta a ponta.</div>${table(['Sessão', 'Projeto', 'Tokens', 'Custo', 'Vazão'], CLAUDE_SESS.map(s => [s[0], s[1], s[4], s[6], s[8]]), [2, 3, 4])}<div class="sm mut">Não trafega conteúdo de prompt nem de resposta. Só metadados de uso.</div></div>`) },
    { name: 'Filtros laterais', why: 'Coluna de filtros (projeto, modelo, branch, origem) à esquerda; a tabela reage. Útil com dezenas de sessões no Total.',
      html: cliWin(`<div class="split" style="grid-template-columns:130px 1fr"><div class="col">${[['Projeto', PROJ.slice(0, 3)], ['Modelo', ['opus-5-5', 'sonnet-5', 'opus-5']], ['Origem', ['CLI', 'IDE', 'Desktop']]].map(([l, xs]) => `<div class="eyebrow">${l}</div>${xs.map((x, i) => `<div class="row sm"><span class="sw ${i === 0 ? 'on' : ''}" style="transform:scale(.7)"></span>${x}</div>`).join('')}`).join('')}</div><div>${table(['Sessão', 'Turnos', 'Tokens', 'Custo', 'Vazão'], CLAUDE_SESS.slice(0, 2).map(s => [s[0], s[3], s[4], s[6], s[8]]), [1, 2, 3, 4])}<div class="sm mut">2 de 3 sessões · filtro: usage-monitor, opus-5-5</div></div></div>`) },
    { name: 'Painel de métricas', why: 'Mosaico de 4 números, gráfico de tokens por hora (entrada/saída/cache) e só as 3 sessões mais recentes; "ver todas" leva à lista.',
      html: cliWin(`${metrics([['Sessões', '3'], ['Tokens', '6,53 M'], ['Custo', 'US$ 9,50'], ['Vazão', '95 tok/s']])}<div class="eyebrow">Tokens por hora</div>${bars([2, 8, 14, 22, 18, 9], ['input', 'output', 'cread', 'cread', 'cread', 'output'], 520, 50, ['07h', '08h', '09h', '10h', '11h', '12h'])}${legend([['entrada', 'input'], ['saída', 'output'], ['cache', 'cread']])}${table(['Sessão', 'Projeto', 'Tokens', 'Vazão'], CLAUDE_SESS.map(s => [s[0], s[1], s[4], s[8]]), [2, 3])}`) },
  ],
};

/* ── #386 — Análise comparativa entre modelos/APIs (rodada P) ── */
const cmpBar = `${seg(['24h', '7 dias', '30 dias'], 1)} ${chip('Anthropic', 1)}${chip('Codex', 1)}${chip('MiniMax', 1)}${chip('DeepSeek')} <span class="grow"></span>${btn('CSV')}${btn('PDF')}`;
const cmpWin = (body) => W('Comparar modelos e APIs', body, { bar: cmpBar, status: '<span>7 dias · Ter 29/09 → Ter 06/10 BRT</span><span>custo a preço de tabela, não é fatura</span>' });
const issue386 = {
  id: '386', letter: 'P', title: '#386 · Análise comparativa',
  intro: 'Tela <b>nova</b> (janela própria, entrada pelo balão da engrenagem). Compara modelos e fontes no mesmo período: tokens, saída, custo recalculado pela <code>ModelPricingTable</code> (modelo sem tarifa mostra "sem tarifa", nunca zero), custo por 1 M, cache, <b>vazão tok/s</b> (#381) e pico de cota. Fontes sem tokens (MiniMax em requisições) aparecem só nas métricas que medem.',
  ref: { title: 'Hoje (referência)', why: 'Não existe comparação entre fontes. Só o Resumo de cada CLI, com breakdown por modelo dentro da própria fonte.',
    html: W('Sessões CLI — Anthropic', `${table(['Modelo', 'Turnos', 'Tokens', 'Custo'], MODELS.slice(0, 2).map(m => [m.m, m.turns, m.tok, m.cost]), [1, 2, 3])}`, { sub: 'Resumo › por modelo', bar: seg(['Sessões', 'Resumo'], 1) }) },
  options: [
    { name: 'Tabela com barras', why: 'Uma linha por modelo, colunas fixas, barra inline na coluna ordenada. Clique no cabeçalho reordena. Mais denso, mais preciso.',
      html: cmpWin(table(['Modelo', 'Fonte', 'Tokens', 'Custo', 'US$/1M', 'Cache', 'Vazão ▾'], MODELS.map(m => [`<span class="mark" style="--c:var(--${m.c})">${m.m}</span>`, m.src, m.unit || m.tok, m.cost, m.perM, m.cache == null ? '—' : m.cache + '%', m.tps == null ? '<span class="mut">não medido</span>' : `<span class="row" style="justify-content:flex-end">${track(m.tps / 1.2, m.c, '60px')}${m.tps}</span>`]), [2, 3, 4, 5, 6])) },
    { name: 'Versus em cartões', why: 'Escolhe 2 a 4 modelos; cada um vira uma coluna-cartão com os mesmos números na mesma altura, para ler na horizontal.',
      html: cmpWin(`<div class="split" style="grid-template-columns:repeat(3,1fr)">${MODELS.slice(0, 3).map(m => `<div class="panel mark" style="--c:var(--${m.c})"><div class="mono">${m.m}</div><div class="sm mut">${m.src}</div>${[['Tokens', m.tok], ['Saída', m.out], ['Custo', m.cost], ['US$/1M', m.perM], ['Cache', m.cache + '%'], ['Vazão', m.tps + ' tok/s'], ['Pico de cota', m.quota + '%']].map(([l, v]) => `<div class="row sm"><span class="mut">${l}</span><span class="grow"></span><span class="num">${v}</span></div>`).join('')}</div>`).join('')}</div>`) },
    { name: 'Custo × velocidade', why: 'Dispersão: eixo X = custo por 1 M tokens, Y = tok/s, tamanho = volume. Responde "qual entrega mais por dólar". Sem tarifa fica numa faixa à parte.',
      html: cmpWin(`<svg width="100%" height="170" viewBox="0 0 520 170"><line x1="40" y1="150" x2="510" y2="150" style="stroke:var(--border)"/><line x1="40" y1="10" x2="40" y2="150" style="stroke:var(--border)"/><text x="275" y="166" text-anchor="middle" style="fill:var(--muted);font:9px var(--mono)">US$ por 1 M tokens →</text><text x="12" y="80" transform="rotate(-90 12 80)" text-anchor="middle" style="fill:var(--muted);font:9px var(--mono)">tok/s →</text>${[[1.44, 94, 26, 'claude-opus-5-5', 'anthropic'], [.91, 118, 16, 'claude-sonnet-5', 'anthropic']].map(([x, y, r, l, c]) => `<circle cx="${40 + x * 280}" cy="${150 - y}" r="${r / 2}" style="fill:var(--${c});opacity:.7"/><text x="${40 + x * 280 + r / 2 + 4}" y="${150 - y + 3}" style="fill:var(--fg);font:9px var(--mono)">${l}</text>`).join('')}<rect x="455" y="10" width="55" height="140" style="fill:var(--raised)"/><text x="482" y="24" text-anchor="middle" style="fill:var(--muted);font:8px var(--mono)">sem tarifa</text>${[[33, 'luna'], [29, 'sol']].map(([y, l]) => `<circle cx="470" cy="${150 - y}" r="7" style="fill:var(--codex);opacity:.7"/><text x="480" y="${150 - y + 3}" style="fill:var(--fg);font:8px var(--mono)">${l}</text>`).join('')}</svg>`) },
    { name: 'Ranking por métrica', why: 'Escolhe a pergunta (mais rápido, mais barato, mais usado, mais cache) e vê o pódio em barras horizontais com o valor escrito.',
      html: cmpWin(`<div class="row">${seg(['Mais rápido', 'Mais barato', 'Mais usado', 'Mais cache'])}</div>${MODELS.filter(m => m.tps).sort((a, b) => b.tps - a.tps).map((m, i) => `<div class="row"><span class="num sm mut" style="width:16px">${i + 1}</span><span class="mono sm" style="width:120px">${m.m}</span><div class="grow">${track(m.tps / 1.2, m.c)}</div><span class="num sm" style="width:64px;text-align:right">${m.tps} tok/s</span></div>`).join('')}<div class="sm mut">MiniMax fora: a fonte não informa tokens.</div>`) },
    { name: 'Perfis paralelos', why: 'Pequenos múltiplos: uma faixa por métrica, todos os modelos na mesma escala dentro da faixa. Compara forma, não um número.',
      html: cmpWin(`${[['Tokens', [63, 25, 31, 8]], ['Custo', [91, 23, 0, 0]], ['Cache', [96, 95, 88, 79]], ['Vazão', [78, 98, 28, 24]]].map(([l, vs]) => `<div class="row"><span class="eyebrow" style="width:56px">${l}</span><div class="grow">${bars(vs, ['anthropic', 'anthropic', 'codex', 'codex'], 300, 22)}</div></div>`).join('')}${legend(MODELS.slice(0, 4).map(m => [m.m, m.c]))}<div class="sm mut">Custo 0 nas barras Codex = sem tarifa (rótulo no hover), não grátis.</div>`) },
    { name: 'Linhas no tempo', why: 'Uso diário por fonte sobreposto (tokens ou % da cota), com seletor de métrica. Compara tendência ao longo da semana.',
      html: cmpWin(`<div class="row">${seg(['Tokens', '% da cota 7d', 'Custo'])}</div><svg width="100%" height="110" viewBox="0 0 520 110" preserveAspectRatio="none">${[['anthropic', [20, 35, 50, 30, 70, 90, 60]], ['codex', [10, 12, 30, 45, 40, 22, 35]], ['minimax', [5, 8, 6, 12, 10, 4, 9]]].map(([c, p]) => `<path d="${p.map((v, i) => `${i ? 'L' : 'M'}${i * 86},${105 - v}`).join('')}" style="fill:none;stroke:var(--${c});stroke-width:2"/>`).join('')}</svg>${legend([['Anthropic', 'anthropic'], ['Codex', 'codex'], ['MiniMax', 'minimax']])}`) },
    { name: 'Mapa de calor', why: 'Matriz modelo × métrica; a cor é o valor normalizado por coluna e o número fica sempre escrito na célula.',
      html: cmpWin(`<table class="t"><thead><tr><th>Modelo</th>${['Tokens', 'Custo', 'Cache', 'Vazão', 'Cota'].map(h => `<th class="n">${h}</th>`).join('')}</tr></thead><tbody>${MODELS.slice(0, 4).map(m => `<tr><td>${m.m}</td>${[[m.tok, 1], [m.cost, .7], [m.cache + '%', m.cache / 100], [m.tps + '', m.tps / 120], [m.quota + '%', m.quota / 100]].map(([v, k]) => `<td class="n" style="background:color-mix(in srgb,var(--${m.c}) ${Math.round(k * 45)}%,transparent)">${v}</td>`).join('')}</tr>`).join('')}</tbody></table>`) },
    { name: 'Duelo A × B', why: 'Dois seletores e uma coluna de diferença (Δ e ×). Para a pergunta direta "Opus ou Sonnet nesta semana?".',
      html: cmpWin(`<div class="row"><span class="input">claude-opus-5-5 ▾</span><span class="mut">×</span><span class="input">claude-sonnet-5 ▾</span></div>${table(['Métrica', 'opus-5-5', 'sonnet-5', 'Diferença'], [['Tokens', '6,32 M', '2,54 M', '2,5×'], ['Custo', 'US$ 9,10', 'US$ 2,31', '+US$ 6,79'], ['US$/1M', '1,44', '0,91', '1,6×'], ['Cache', '96%', '95%', '+1 pp'], ['Vazão', '94 tok/s', '118 tok/s', '−20%']], [1, 2, 3])}`) },
    { name: 'Eficiência de custo', why: 'Foco em dinheiro: custo por 1 M, economia de cache, custo por turno e "sem tarifa" separado no rodapé.',
      html: cmpWin(`${metrics([['Custo total', 'US$ 11,41', '2 modelos com tarifa'], ['Economia de cache', 'US$ 38,20', 'vs. sem cache'], ['Custo por turno', 'US$ 0,07']])}${table(['Modelo', 'US$/1M', 'Economia', 'US$/turno'], [['claude-opus-5-5', '1,44', 'US$ 29,10', '0,081'], ['claude-sonnet-5', '0,91', 'US$ 9,10', '0,038']], [1, 2, 3])}<div class="sm mut">Sem tarifa: gpt-5.6-luna, gpt-6.1-sol (92 turnos) — não entram no total.</div>`) },
    { name: 'Leitura automática', why: 'Texto gerado das mesmas contas (sem IA): 3 a 5 frases com as diferenças relevantes, cada uma com link para a métrica.',
      html: cmpWin(`<div class="col">${['<b>claude-sonnet-5</b> foi o mais rápido: 118 tok/s, 25% acima do opus-5-5.', '<b>claude-opus-5-5</b> concentrou 80% do custo (US$ 9,10) com 49% dos tokens.', 'Codex usou 3,92 M tokens sem tarifa conhecida — custo não estimado.', 'MiniMax: 340 requisições, 22% da cota de 5h no pico.'].map(t => `<div class="panel mark" style="--c:var(--gargantua-gold)">${t}</div>`).join('')}<div class="sm mut">Frases montadas por regra fixa sobre os números da tabela; nenhum dado sai do app.</div></div>`) },
  ],
};

/* ── #387 — Bot Telegram nas Configurações (rodada Q) ── */
const NAV = ['Geral', 'Alertas', 'APIs', 'Contas', 'Time', 'Rede'];
const cfgWin = (on, body) => W('Configurações', `<div class="row" style="align-items:stretch;flex-wrap:nowrap"><div class="nav">${NAV.map(n => `<div class="${n === on ? 'on' : ''}">${n}</div>`).join('')}</div><div class="w-in grow">${body}</div></div>`, { flush: true });
const botHead = (state = 'Conectado', c = 'ok') => `<div class="row"><b>Bot do Telegram</b>${pill(state, c)}<span class="grow"></span><span class="sw ${c === 'ok' ? 'on' : ''}"></span></div>`;
const tokenField = `<div class="field"><label>Token do bot</label><div class="input">•••••••••••••••••••• 4f2a</div></div>`;
const pairField = `<div class="field"><label>Pareamento</label><div class="row"><span class="input mono">/start UM-7K4Q</span>${btn('Copiar')}<span class="sm mut">expira em 9 min</span></div></div>`;
const chats = `<div class="field"><label>Conversas autorizadas</label><div class="row sm"><span class="mono">@edilson · 51•••902</span><span class="grow"></span>${btn('Remover', 'ghost')}</div></div>`;
const privacy = `<div class="note">Só metadados de uso. Nunca conteúdo de prompt ou resposta. O token fica em <code>~/.usage-monitor/telegram.json</code>, fora do registro.</div>`;
const discordOff = `<div class="row sm mut">${btn('Discord', 'off')}<span>Discord exige conexão permanente (Gateway); fica para uma segunda fase.</span></div>`;
const issue387 = {
  id: '387', letter: 'Q', title: '#387 · Bot Telegram',
  intro: '<b>Viável</b> (A03): Bot API do Telegram por long polling (<code>getUpdates</code>), só HTTPS de saída — sem porta aberta. Mão dupla: alertas saem pelo mesmo fluxo da bandeja; comandos <code>/status</code>, <code>/alertas</code>, <code>/silencio</code>, <code>/limiar</code> alteram as mesmas preferências de alerta. Só conversas pareadas por código são aceitas. Restrição: <b>sem aba nova</b> (regra de enum) — a seção mora em Alertas ou Rede, conforme a opção.',
  ref: { title: 'Hoje (referência)', why: 'Alertas só na bandeja do sistema. Não há canal externo.',
    html: cfgWin('Alertas', `<div class="row"><b>Alertas de uso</b><span class="grow"></span><span class="sw on"></span></div><div class="field"><label>Limiar</label><div class="input">80%</div></div><div class="field"><label>Silêncio</label><div class="input">22:00 → 07:00</div></div><div class="note">Notificação na bandeja do sistema.</div>`) },
  options: [
    { name: 'Canal dentro de Alertas', why: 'Abaixo dos alertas atuais, um bloco "Enviar também para": Telegram. Tudo que já configura alerta passa a valer para o bot.',
      html: cfgWin('Alertas', `<div class="row"><b>Alertas de uso</b><span class="grow"></span><span class="sw on"></span></div><div class="row sm mut"><span>Limiar 80% · silêncio 22:00 → 07:00</span></div><div class="panel">${botHead()}${tokenField}${chats}<div class="row">${btn('Enviar teste')}${btn('Parear outra conversa', 'ghost')}</div></div>${privacy}`) },
    { name: 'Integrações em Rede', why: 'Rede ganha a subseção "Integrações externas" (Telegram ativo, Discord desabilitado com motivo). Junto do proxy, que o bot usa.',
      html: cfgWin('Rede', `<div class="eyebrow">Proxy</div><div class="input">Sem proxy</div><div class="eyebrow">Integrações externas</div><div class="panel">${botHead()}${chats}</div>${discordOff}`) },
    { name: 'Assistente em 3 passos', why: 'Primeira configuração guiada: 1 colar token (com link para o BotFather), 2 parear pela conversa, 3 escolher eventos. Depois vira resumo.',
      html: cfgWin('Alertas', `<div class="row">${['1 · Token', '2 · Parear', '3 · Eventos'].map((s, i) => `<span class="chip ${i <= 1 ? 'on' : ''}">${s}</span>`).join('')}</div><div class="panel"><b>Passo 2 de 3 — parear a conversa</b><div class="sm">No Telegram, abra o seu bot e envie:</div>${pairField}<div class="row sm mut">${pill('Aguardando mensagem…', 'info')}</div></div><div class="row"><span class="grow"></span>${btn('Voltar', 'ghost')}${btn('Continuar', 'off')}</div>`) },
    { name: 'Status e registro', why: 'Cartão de estado (conectado, última mensagem, erros 429) e um registro das últimas 10 mensagens trocadas, só com o tipo do evento.',
      html: cfgWin('Alertas', `<div class="panel">${botHead()}<div class="row sm mut"><span>Última consulta 11:42:10</span><span>· 0 falhas</span></div></div><div class="eyebrow">Últimas mensagens</div>${table(['Hora', 'Sentido', 'Evento'], [['11:31', '↑ enviada', 'Cota 5h Anthropic 80%'], ['10:02', '↓ recebida', '/status'], ['10:02', '↑ enviada', 'Resumo de 4 contas'], ['09:15', '↓ recebida', '/silencio 12:00-13:00']], [])}`) },
    { name: 'Matriz evento × canal', why: 'Cada tipo de alerta numa linha, colunas Bandeja e Telegram com chaves. Permite mandar só o crítico para o celular.',
      html: cfgWin('Alertas', `${botHead()}${table(['Evento', 'Bandeja', 'Telegram'], [['Cota passou do limiar', '<span class="sw on"></span>', '<span class="sw on"></span>'], ['Sessão saturada', '<span class="sw on"></span>', '<span class="sw"></span>'], ['Sessão sem resposta', '<span class="sw on"></span>', '<span class="sw"></span>'], ['Pico de gasto', '<span class="sw on"></span>', '<span class="sw on"></span>'], ['Resumo diário 18:00', '—', '<span class="sw on"></span>']], [])}`) },
    { name: 'Pareamento por QR', why: 'QR code que abre <code>t.me/&lt;bot&gt;?start=código</code> no celular; parear vira apontar a câmera. O código em texto continua ao lado.',
      html: cfgWin('Rede', `${botHead('Sem conversa pareada', 'warn')}<div class="row" style="align-items:flex-start"><svg width="92" height="92" viewBox="0 0 23 23">${Array.from({ length: 23 * 23 }, (_, i) => { const x = i % 23, y = (i / 23) | 0; const f = (x < 7 && y < 7) || (x > 15 && y < 7) || (x < 7 && y > 15); const on = f ? (x % 6 === 0 || y % 6 === 0 || (x > 1 && x < 5 && y > 1 && y < 5) || (x > 17 && x < 21 && y > 1 && y < 5) || (x > 1 && x < 5 && y > 17 && y < 21) || x === 16 || y === 16 || x === 22 || y === 22) : ((x * 7 + y * 13 + x * y) % 3 === 0); return on ? `<rect x="${x}" y="${y}" width="1" height="1" style="fill:var(--fg)"/>` : ''; }).join('')}</svg><div class="col grow">${pairField}<div class="note">Aponte a câmera do celular ou envie o código ao bot.</div></div></div>`) },
    { name: 'Comandos com permissão', why: 'Lista dos comandos que o bot aceita, com chave por comando: dá para deixar o bot só de leitura (só /status).',
      html: cfgWin('Alertas', `${botHead()}<div class="eyebrow">Comandos aceitos</div>${[['/status', 'Resumo das contas e cotas', 1, 'leitura'], ['/alertas on|off', 'Liga ou desliga os alertas', 1, 'altera'], ['/silencio 22-07', 'Define o horário de silêncio', 0, 'altera'], ['/limiar 80', 'Muda o limiar de alerta', 0, 'altera']].map(([c, d, on, k]) => `<div class="row"><span class="sw ${on ? 'on' : ''}"></span><span class="mono" style="width:120px">${c}</span><span class="sm">${d}</span><span class="grow"></span><span class="chip">${k}</span></div>`).join('')}`) },
    { name: 'Prévia da mensagem', why: 'Mostra exatamente como o alerta chega no Telegram (balão), com o texto no idioma do app. Botão "Enviar esta prévia".',
      html: cfgWin('Alertas', `${botHead()}<div class="panel" style="background:var(--bg)"><div class="bubble"><b>Usage Monitor</b><br>Anthropic · Padrão — Claude 5h em 80%.<br>Reinicia Ter 13h05 BRT. No ritmo atual esgota às 12h40.</div></div><div class="row">${seg(['Cota', 'Saturada', 'Gasto', 'Resumo'])}<span class="grow"></span>${btn('Enviar esta prévia')}</div>`) },
    { name: 'Linha compacta por canal', why: 'Uma linha por canal (Bandeja, Telegram, Discord) com estado e "Configurar", que abre um diálogo. Seção curta e sem rolagem.',
      html: cfgWin('Alertas', `<div class="row"><b>Alertas de uso</b><span class="grow"></span><span class="sw on"></span></div><div class="eyebrow">Canais</div>${[['Bandeja do sistema', 'Ativo', 'ok', 'Configurar'], ['Telegram', 'Conectado · 1 conversa', 'ok', 'Configurar'], ['Discord', 'Segunda fase', 'muted', '']].map(([n, s, c, b]) => `<div class="panel"><div class="row"><b>${n}</b>${pill(s, c)}<span class="grow"></span>${b ? btn(b) : btn('Indisponível', 'off')}</div></div>`).join('')}`) },
    { name: 'Configuração e conversa', why: 'Duas colunas: à esquerda os campos, à direita uma conversa simulada que mostra a mão dupla (comando → resposta) com os dados reais da última leitura.',
      html: cfgWin('Alertas', `<div class="split" style="grid-template-columns:1fr 1fr"><div class="col">${botHead()}${tokenField}${chats}</div><div class="panel" style="background:var(--bg)"><div class="bubble me mono">/status</div><div class="bubble">Anthropic 5h 68% · 7d 41%<br>Codex 5h 22%<br>DeepSeek saldo US$ 4,12</div><div class="bubble me mono">/silencio 12-13</div><div class="bubble">Silêncio 12:00 → 13:00 aplicado.</div></div></div>`) },
  ],
};

/* ── #388 — HUD pela web local (rodada R) ── */
const ACCOUNTS = [
  { n: 'Anthropic · Padrão', c: 'anthropic', mark: 'claude', q: [['5h', 68, 'warn', '13h05'], ['7d', 41, 'ok', 'Sáb 21h']], active: true },
  { n: 'Codex', c: 'codex', mark: 'openai', q: [['5h', 22, 'ok', '14h30'], ['7d', 41, 'ok', 'Seg 09h']], active: false },
  { n: 'OpenCode Go', c: 'oc', mark: 'none', q: [['5h', 58, 'ok', '12h10'], ['7d', 44, 'ok', 'Qui'], ['30d', 30, 'ok', '01/11']], active: false },
  { n: 'DeepSeek', c: 'deepseek', mark: 'none', q: [['saldo', 0, 'ok', 'US$ 4,12']], active: false },
];
const ringCanvas = (a, size = 64) => `<canvas class="ring" width="${size + 56}" height="${size + 56}" data-ring='${JSON.stringify({ arcs: a.q.filter(q => q[0] !== 'saldo').map(q => ({ f: q[1] / 100, t: q[2] })), mark: a.mark, size })}' style="width:${(size + 56) * .75}px;height:${(size + 56) * .75}px"></canvas>`;
const webUrl = 'http://192.168.0.14:47110/?t=••••';
const phone = body => `<div class="phone"><div class="scr"><div class="browser"><div class="url">${webUrl}</div></div>${body}</div></div>`;
const desk = body => `<div class="browser grow"><div class="url">${webUrl}</div><div class="w-in">${body}</div></div>`;
const accLine = a => `<div class="row sm"><span class="mark" style="--c:var(--${a.c})">${a.n}</span><span class="grow"></span>${a.active ? pill('Trabalhando agora', 'ok') : ''}</div>`;
const qLine = q => q[0] === 'saldo' ? `<div class="row sm"><span class="mut">saldo</span><span class="grow"></span><span class="num">${q[3]}</span></div>` : `<div class="row sm"><span class="mono" style="width:28px">${q[0]}</span><div class="grow">${track(q[1], q[2])}</div><span class="num" style="width:30px;text-align:right">${q[1]}%</span><span class="mut" style="width:52px;text-align:right">${q[3]}</span></div>`;
const issue388 = {
  id: '388', letter: 'R', title: '#388 · HUD pela web local',
  intro: 'Servidor local opcional (desligado por padrão) servindo a HUD e o detalhe de cada API. Escolha do usuário: <b>acesso pela rede local</b> (celular) → bind em todas as interfaces, <b>token obrigatório</b>, Windows deve perguntar no firewall ao habilitar. Só lê: nenhuma ação de escrita pela web. O anel é o Gargantua real (mesmo desenho do app).',
  shared: { title: 'Seção nas Configurações (comum às 10)', why: 'Sem aba nova: subseção "Acesso pela rede local" em Rede. URL com token, QR para o celular, porta e "gerar novo token" (invalida links antigos).',
    html: cfgWin('Rede', `<div class="eyebrow">Proxy</div><div class="input">Sem proxy</div><div class="eyebrow">Acesso pela rede local</div><div class="panel"><div class="row"><b>Ver a HUD no navegador</b>${pill('Ativo', 'ok')}<span class="grow"></span><span class="sw on"></span></div><div class="field"><label>Endereço</label><div class="row"><span class="input grow">${webUrl}</span>${btn('Copiar')}${btn('QR')}</div></div><div class="row"><div class="field"><label>Porta</label><div class="input">47110</div></div><span class="grow"></span>${btn('Gerar novo token', 'ghost')}</div><div class="note">Qualquer aparelho desta rede com o link vê os dados de uso (nunca prompt ou resposta). O Windows pode pedir permissão no firewall ao ativar.</div></div>`) },
  ref: { title: 'Hoje (referência)', why: 'Só a HUD (notch) na borda da tela do computador; nada acessível de outro aparelho.',
    html: `<div class="row" style="justify-content:center;background:var(--hud-body,#1B1818);border:1px solid var(--border);border-radius:0 0 14px 14px;padding:4px 14px;width:max-content;margin:0 auto">${ACCOUNTS.slice(0, 3).map(a => ringCanvas(a, 40)).join('')}<span class="mono sm mut">⚙</span></div>` },
  options: [
    { name: 'Réplica da HUD', why: 'A mesma faixa de anéis do notch no topo; tocar num anel abre o balão da conta logo abaixo, com as mesmas linhas do app.',
      html: `<div class="row" style="align-items:flex-start">${phone(`<div class="row" style="justify-content:center;padding:6px">${ACCOUNTS.slice(0, 3).map(a => ringCanvas(a, 36)).join('')}</div><div class="w-in"><div class="panel">${accLine(ACCOUNTS[0])}${ACCOUNTS[0].q.map(qLine).join('')}<div class="sm mut">Atualizado 11:42 · cada 60 s</div></div></div>`)}<div class="note grow">Mesmo vocabulário da HUD; o balão aqui é bloco fixo (sem hover no toque).</div></div>` },
    { name: 'Lista de contas', why: 'Mobile-first: um cartão por conta com as barras de cota e reinício; o pior risco vai para o topo do cartão em palavra.',
      html: `<div class="row" style="align-items:flex-start">${phone(`<div class="w-in">${ACCOUNTS.map(a => `<div class="panel">${accLine(a)}${a.q.map(qLine).join('')}</div>`).join('')}</div>`)}${desk(`<div class="split" style="grid-template-columns:1fr 1fr">${ACCOUNTS.map(a => `<div class="panel">${accLine(a)}${a.q.map(qLine).join('')}</div>`).join('')}</div>`)}</div>` },
    { name: 'Grade de anéis', why: 'Painel desktop: anéis grandes (1,5×) em grade, legenda das janelas abaixo de cada um. Bom para um segundo monitor.',
      html: desk(`<div class="split" style="grid-template-columns:repeat(4,1fr)">${ACCOUNTS.map(a => `<div class="col" style="align-items:center">${a.q[0][0] === 'saldo' ? `<div class="num" style="font-size:18px;padding:24px 0">${a.q[0][3]}</div>` : ringCanvas(a, 64)}<div class="sm mark" style="--c:var(--${a.c})">${a.n}</div>${a.q.filter(q => q[0] !== 'saldo').map(q => `<div class="sm mono mut">${q[0]} ${q[1]}% · ${q[3]}</div>`).join('')}</div>`).join('')}</div>`) },
    { name: 'Tabela compacta', why: 'Uma linha por cota (conta, janela, %, reinício, previsão). Máxima densidade; funciona até em navegador de TV.',
      html: desk(table(['Conta', 'Janela', 'Uso', 'Reinício', 'Previsão'], [['Anthropic · Padrão', '5h', '68%', '13h05', 'Esgota 12h40'], ['Anthropic · Padrão', '7d', '41%', 'Sáb 21h', 'Folga'], ['Codex', '5h', '22%', '14h30', 'Folga'], ['OpenCode Go', '30d', '30%', '01/11', 'Folga'], ['DeepSeek', 'saldo', 'US$ 4,12', '—', '9 dias de autonomia']], [2])) },
    { name: 'Uma conta por tela', why: 'No celular, deslizar entre contas; cada tela mostra anel grande, cotas, sessão ativa e o histórico curto de 24h.',
      html: `<div class="row" style="align-items:flex-start">${phone(`<div class="w-in" style="align-items:center"><div class="row sm mut">● ○ ○ ○</div>${ringCanvas(ACCOUNTS[0], 64)}${accLine(ACCOUNTS[0])}${ACCOUNTS[0].q.map(qLine).join('')}<div class="eyebrow">Últimas 24h</div>${spark([5, 10, 30, 50, 20, 40, 68], 'anthropic', 180, 34, true)}</div>`)}<div class="note grow">Deslize para o lado para a próxima conta. Ordem = ordem dos cards no app.</div></div>` },
    { name: 'Abas por API', why: 'Topo com abas por fonte; cada aba mostra o detalhe completo daquela API (cotas, sessões ativas, modelos observados).',
      html: desk(`<div class="row">${ACCOUNTS.map((a, i) => `<span class="chip ${i ? '' : 'on'}">${a.n}</span>`).join('')}</div>${metrics([['5h', '68%', 'reinicia 13h05'], ['7d', '41%', 'Sáb 21h'], ['Sessões ativas', '1', 'usage-monitor'], ['Vazão', '94 tok/s']])}${table(['Sessão', 'Projeto', 'Tokens', 'Ativo'], CLAUDE_SESS.map(s => [s[0], s[1], s[4], s[7]]), [2, 3])}`) },
    { name: 'Pior risco primeiro', why: 'Triagem: blocos "Crítico", "Atenção", "Em dia", em ordem fixa de risco. O que precisa de ação aparece sem rolar.',
      html: `<div class="row" style="align-items:flex-start">${phone(`<div class="w-in">${[['Atenção', 'warn', [ACCOUNTS[0]]], ['Em dia', 'ok', ACCOUNTS.slice(1)]].map(([t, c, xs]) => `<div class="eyebrow">${pill(t, c)}</div>${xs.map(a => `<div class="panel">${accLine(a)}${a.q.slice(0, 1).map(qLine).join('')}</div>`).join('')}`).join('')}</div>`)}<div class="note grow">Agrupamento por <code>worstQuotaRisk</code>, mesma ordem do enum do app.</div></div>` },
    { name: 'Linha do tempo de reinícios', why: 'Um eixo das próximas 7 dias com cada reinício de janela marcado e o uso atual em cada marco. Responde "quando volta".',
      html: desk(`<svg width="100%" height="110" viewBox="0 0 520 110"><line x1="10" y1="60" x2="510" y2="60" style="stroke:var(--border)"/>${[[20, 'Go 5h · 12h10', 'oc'], [44, 'Claude 5h · 13h05', 'anthropic'], [70, 'Codex 5h · 14h30', 'codex'], [300, 'Go 7d · Qui', 'oc'], [400, 'Claude 7d · Sáb 21h', 'anthropic'], [470, 'Codex 7d · Seg', 'codex']].map(([x, t, c], i) => `<circle cx="${x}" cy="60" r="4" style="fill:var(--${c})"/><text x="${x}" y="${i % 2 ? 84 : 42}" text-anchor="${x > 400 ? 'end' : 'start'}" style="fill:var(--fg);font:9px var(--mono)">${t}</text>`).join('')}<text x="10" y="104" style="fill:var(--muted);font:9px var(--mono)">agora</text><text x="510" y="104" text-anchor="end" style="fill:var(--muted);font:9px var(--mono)">+7 dias</text></svg>`) },
    { name: 'Barras leves', why: 'Sem canvas nem anel: só texto e barras CSS. Carrega instantâneo, funciona em navegador antigo e em leitor de tela.',
      html: `<div class="row" style="align-items:flex-start">${phone(`<div class="w-in">${ACCOUNTS.map(a => `<div class="col"><div class="sm"><b>${a.n}</b></div>${a.q.map(qLine).join('')}</div>`).join('<hr style="border:0;border-top:1px solid var(--border);width:100%">')}</div>`)}<div class="note grow">Página sem JavaScript obrigatório: recarrega sozinha a cada 60 s por meta refresh.</div></div>` },
    { name: 'Modo parede', why: 'Tela cheia escura para TV ou tablet fixo: anéis grandes, percentuais em display, relógio BRT, sem controles.',
      html: `<div style="background:var(--gargantua-night);border-radius:var(--r2);padding:14px"><div class="row"><span class="num" style="font-size:22px">11:42</span><span class="mono sm mut">BRT</span><span class="grow"></span><span class="mono sm mut">Usage Monitor</span></div><div class="row" style="justify-content:space-around">${ACCOUNTS.slice(0, 3).map(a => `<div class="col" style="align-items:center">${ringCanvas(a, 64)}<div class="num" style="font-size:20px">${a.q[0][1]}%</div><div class="mono sm mut">${a.n} · ${a.q[0][0]}</div></div>`).join('')}</div></div>` },
  ],
};

const ISSUES = [issue382, issue384, issue386, issue387, issue388];

/* ── montagem ── */
const card = (o, cls, label) => `<article class="g-card ${cls}"><h3><span class="tag">${label}</span>${o.title || o.name}</h3><p class="why">${o.why}</p><div class="g-stage">${o.html}</div></article>`;
function render(id) {
  const is = ISSUES.find(i => i.id === id);
  const body = $('#g-body');
  body.innerHTML = `<p class="g-intro">${is.intro}</p><div class="g-grid">${card(is.ref, 'ref', 'ref')}${is.shared ? card(is.shared, 'shared', 'comum') : ''}${is.options.map((o, i) => card(o, '', `${is.letter}${i + 1}`)).join('')}</div>`;
  document.querySelectorAll('.g-tab').forEach(t => t.setAttribute('aria-selected', String(t.dataset.id === id)));
  drawRings();
  try { localStorage.setItem('g381-tab', id); } catch (e) {}
}
function drawRings() {
  if (typeof drawRing !== 'function') return;
  document.querySelectorAll('canvas.ring').forEach(cv => {
    const d = JSON.parse(cv.dataset.ring), ctx = cv.getContext('2d');
    const k = d.size / 64; ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.clearRect(0, 0, cv.width, cv.height);
    ctx.translate(28, 28); ctx.scale(k, k);
    drawRing(ctx, { arcs: d.arcs.length ? d.arcs : [{ f: 0, t: 'ok' }], mark: d.mark }, .3, { markAlpha: d.mark === 'none' ? 0 : 1 });
  });
}
document.addEventListener('DOMContentLoaded', () => {
  $('#g-tabs').innerHTML = ISSUES.map(i => `<button class="g-tab" data-id="${i.id}">${i.title}</button>`).join('');
  $('#g-tabs').addEventListener('click', e => { const b = e.target.closest('.g-tab'); if (b) render(b.dataset.id); });
  $('#g-theme').addEventListener('change', e => { document.documentElement.dataset.appTheme = e.target.checked ? 'light' : 'dark'; drawRings(); });
  let start = '382'; try { start = localStorage.getItem('g381-tab') || start; } catch (e) {}
  if (location.hash) start = location.hash.slice(1);
  render(ISSUES.some(i => i.id === start) ? start : '382');
});
window.GALLERY_ISSUES = ISSUES;
})();
