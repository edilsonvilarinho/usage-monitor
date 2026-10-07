/* Propostas das issues #392 (rodada S) e #393 (rodada T). Dados reproduzidos dos prints das issues, conta sintética. */
(() => {
const $ = (s, r = document) => r.querySelector(s);

/* ── primitivas de mockup (mesmas da galeria #381–#388) ── */
const W = (title, body, opt = {}) => `<div class="w">
  <div class="w-title"><b>${title}</b>${opt.sub ? `<span>· ${opt.sub}</span>` : ''}<span class="x">□×</span></div>
  ${opt.bar ? `<div class="w-bar">${opt.bar}</div>` : ''}
  <div class="${opt.flush ? '' : 'w-in'}">${body}</div>
  ${opt.status ? `<div class="w-status">${opt.status}</div>` : ''}
</div>`;
const seg = (items, on = 0) => `<span class="seg">${items.map((t, i) => `<span class="${i === on ? 'on' : ''}">${t}</span>`).join('')}</span>`;
const btn = (t, k = '') => `<span class="btn ${k}">${t}</span>`;
const tabs = (items, on = 0) => `<div class="tabs">${items.map((t, i) => `<span class="${i === on ? 'on' : ''}">${t}</span>`).join('')}</div>`;
const metric = (l, v, h = '', tip = '') => `<div class="metric"${tip ? ` data-tip="${tip}"` : ''}><div class="l">${l}${tip ? '<span class="help">?</span>' : ''}</div><div class="v">${v}</div>${h ? `<div class="h">${h}</div>` : ''}</div>`;
const metrics = (list, n = list.length) => `<div class="split" style="grid-template-columns:repeat(${n},1fr)">${list.map(m => metric(...m)).join('')}</div>`;
const table = (heads, rows, numCols = [], sel = -1) => `<table class="t"><thead><tr>${heads.map((h, i) => `<th class="${numCols.includes(i) ? 'n' : ''}">${h}</th>`).join('')}</tr></thead><tbody>${rows.map((r, ri) => `<tr class="${ri === sel ? 'sel' : ''}">${r.map((c, i) => `<td class="${numCols.includes(i) ? 'n' : ''}">${c}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
const NEW = s => `<span class="new">${s}</span>`;
const help = tip => `<span class="help" data-tip="${tip}">?</span>`;
const sec = (title, sub, body, c = 'anthropic', right = '') => `<div class="panel mark" style="--c:var(--${c})"><div class="row"><div class="grow"><div class="mono" style="font-size:11px;font-weight:600">${title}</div>${sub ? `<div class="cap">${sub}</div>` : ''}</div>${right}</div>${body}</div>`;
const lg = items => `<div class="lg">${items.map(([t, style, tip]) => `<span${tip ? ` data-tip="${tip}"` : ''}><i style="${style}"></i>${t}</span>`).join('')}</div>`;
const track = (p, c = 'fg', w = '') => `<div class="track" style="${w ? `width:${w};` : ''}"><i style="width:${p}%;--c:var(--${c})"></i></div>`;

/* ── dados dos prints ── */
const X = t => t; // fração do eixo de tempo (24h: 20:57 → 20:54)
// Cota 7d no intervalo de 24h: 93% até o reinício de 01:02, ociosa até 16:06, sobe a 15%.
const L7 = [[0, 93], [.165, 93], [.166, 0], [.80, 0], [.82, 2], [.84, 6], [.86, 10], [.875, 14], [.93, 14], [.935, 15], [1, 15]];
// Cota 5h: janela de 16:06 que esgota às 19:29.
const L5 = [[0, 0], [.80, 0], [.82, 12], [.84, 31], [.86, 52], [.88, 74], [.90, 88], [.94, 100], [.97, 100], [.975, 0], [1, 0]];
const PREV7 = [[0, 40], [.3, 48], [.6, 60], [.8, 70], [1, 88]];
const H5 = Array(24).fill(0); H5[16] = 30; H5[17] = 60; H5[18] = 6; H5[19] = 4;
const H7 = Array(24).fill(0); H7[16] = 31; H7[17] = 53; H7[19] = 16;
const AXIS_OLD = ['20:57', '13:39', '20:54'];
const AXIS_FIX = ['05/10 20:57', '06/10 08:55', '06/10 20:54'];
const ACC = 'Conta: voce@exemplo.com — workspace ▾';

/* gráfico de linha: pontos [fração, %], faixas ativas, reinício, agora */
function lineChart({ w = 520, h = 110, series = [[L7, 'output']], bands = [[.80, .94, 'output']], resetAt = .165, prev = null, axis = AXIS_FIX, tipPts = false, legendInside = false, hideBandLabel = false, zoomOut = null } = {}) {
  const px = f => (f * w).toFixed(1), py = v => (h - 6 - v / 100 * (h - 16)).toFixed(1);
  const path = pts => pts.map(([f, v], i) => `${i ? 'L' : 'M'}${px(f)},${py(v)}`).join('');
  let s = `<svg width="100%" height="${h + 14}" viewBox="0 0 ${w} ${h + 14}" preserveAspectRatio="none">`;
  [0, 50, 100].forEach(v => { s += `<line x1="0" x2="${w}" y1="${py(v)}" y2="${py(v)}" style="stroke:var(--border);stroke-dasharray:${v === 100 ? '4 3' : '0'}"/>`; });
  bands.forEach(([a, b, c]) => {
    s += `<rect x="${px(a)}" y="${py(100)}" width="${((b - a) * w).toFixed(1)}" height="${(h - 6 - py(100)).toFixed(1)}" style="fill:var(--${c});opacity:.14" data-tip="Faixa ativa da janela atual\n16:06 → 19:29 · 3h 23min\nTrecho em que o uso subiu."/>`;
    s += `<rect x="${px(a)}" y="${py(100) - 2}" width="${((b - a) * w).toFixed(1)}" height="2" style="fill:var(--${c})"/>`;
    if (!hideBandLabel) s += `<text x="${px(a) - 3}" y="${py(100) + 9}" text-anchor="end" style="fill:var(--${c});font:8px var(--mono)">ativa</text>`;
  });
  if (prev) s += `<path d="${path(prev)}" style="fill:none;stroke:var(--muted);stroke-dasharray:4 3;stroke-width:1"/>`;
  if (resetAt != null) s += `<line x1="${px(resetAt)}" x2="${px(resetAt)}" y1="${py(100)}" y2="${h - 6}" style="stroke:var(--muted);stroke-dasharray:2 2"/><text x="${px(resetAt) + 3}" y="${py(100) + 9}" style="fill:var(--muted);font:8px var(--mono)" data-tip="Reinício da janela 7d às 01:02 BRT">reinício</text>`;
  series.forEach(([pts, c]) => {
    s += `<path d="${path(pts)}" style="fill:none;stroke:var(--${c});stroke-width:1.6"/>`;
    const [lf, lv] = pts[pts.length - 1];
    s += `<circle cx="${px(lf)}" cy="${py(lv)}" r="3" style="fill:var(--${c})" data-tip="Agora · 06/10 20:54 BRT\n${lv}% usado"/>`;
    if (tipPts) pts.forEach(([f, v]) => { s += `<circle cx="${px(f)}" cy="${py(v)}" r="6" style="fill:transparent" data-tip="${v}% às ${f < .5 ? '05/10' : '06/10'} ${String(Math.floor((20.95 + f * 24) % 24)).padStart(2, '0')}h"/>`; });
  });
  s += `<text x="0" y="${h + 12}" style="fill:var(--muted);font:9px var(--mono)">${axis[0]}</text><text x="${w / 2}" y="${h + 12}" text-anchor="middle" style="fill:var(--muted);font:9px var(--mono)">${axis[1]}</text><text x="${w}" y="${h + 12}" text-anchor="end" style="fill:var(--muted);font:9px var(--mono)">${axis[2]}</text>`;
  return s + '</svg>';
}
const stdLegend = (c = 'output') => lg([
  ['uso da cota', `background:var(--${c});height:2px`, 'Linha: percentual usado da cota em cada leitura.'],
  ['faixa ativa', `background:var(--${c});opacity:.3`, 'Trecho em que o uso subiu dentro da janela.'],
  ['período anterior', 'border-top:1px dashed var(--muted);height:0', 'Mesmo ponto do período anterior, para comparar.'],
  ['reinício', 'border-left:1px dashed var(--muted);width:1px', 'A cota voltou a zero.'],
]);

/* distribuição horária: 24 barras com dica por barra */
function hourBars(vals, c, { h = 48, printed = false, top3 = false, tips = true, axisEvery = 6 } = {}) {
  const w = 480, bw = w / 24, max = Math.max(...vals, 1);
  const ranks = [...vals].map((v, i) => [v, i]).sort((a, b) => b[0] - a[0]).slice(0, 3).map(x => x[1]);
  let s = `<svg width="100%" height="${h + 26}" viewBox="0 0 ${w} ${h + 26}" preserveAspectRatio="none">`;
  vals.forEach((v, i) => {
    const bh = v ? Math.max(2, v / max * h) : 1.5, y = (printed ? 10 : 0) + h - bh;
    const dim = top3 && !ranks.includes(i) && v ? .45 : 1;
    s += `<rect x="${i * bw + 2}" y="${y}" width="${bw - 4}" height="${bh}" rx="1" style="fill:${v ? `var(--${c})` : 'var(--border)'};opacity:${dim}"/>`;
    if (tips) s += `<rect x="${i * bw}" y="0" width="${bw}" height="${h + 12}" style="fill:transparent" data-tip="${String(i).padStart(2, '0')}h–${String(i + 1).padStart(2, '0')}h BRT\n${v}% do consumo do intervalo${v ? (ranks[0] === i ? '\nhora de maior consumo' : '') : '\nsem consumo'}"/>`;
    if (printed && v) s += `<text x="${i * bw + bw / 2}" y="${y - 2}" text-anchor="middle" style="fill:var(--fg);font:8px var(--mono)">${v}%</text>`;
  });
  for (let i = 0; i < 24; i += axisEvery) s += `<text x="${i * bw + bw / 2}" y="${h + 24}" text-anchor="middle" style="fill:var(--muted);font:9px var(--mono)">${i}h</text>`;
  return s + `<text x="${w}" y="${h + 24}" text-anchor="end" style="fill:var(--muted);font:9px var(--mono)">23h</text></svg>`;
}
const WIN5 = ['06/10 16:06 · atual', '16:06 → 19:29 · 3h 23min', '100 %', '3h 23min', '21 %/h'];
const WIN7 = [['06/10 01:02 · atual', '16:06 → 19:29 · 3h 23min', '15 %', '—', '1 %/h'], ['05/10 21:02', '21:02 → 21:02 · 0min', '93 %', '—', '0 %/h']];
const winHeads = ['Início observado', 'Ativa', 'Pico', 'Esgotou em', 'Ritmo'];
const winHeadsHelp = ['Início observado' + help('Primeira leitura da janela feita pelo app.'), 'Ativa' + help('Da última leitura parada até a última subida.'), 'Pico' + help('Maior percentual lido na janela.'), 'Esgotou em' + help('Tempo do início até 100%. — = não esgotou.'), 'Ritmo' + help('Pontos percentuais por hora ativa.')];

/* ══ #392 · Histórico (rodada S) ══ */
const refBar = `${btn(ACC)}${seg(['24h', '7 dias', '30 dias', 'Total'], 0)}${seg(['5h', '7d', 'Ambas'], 1)}${btn('PDF')}`;
const kpis7 = metrics([['Uso atual', '15 / 100 %'], ['Janelas no intervalo', '2'], ['Pico médio por janela', '93 %']], 3);
const chartCard = (inner, extra = '') => sec('Claude', 'Consumo ao longo do intervalo selecionado', inner + extra, 'anthropic');

const issue392 = {
  id: '392', letter: 'S', title: '#392 · Histórico',
  intro: '<b>Problemas do print:</b> (1) dois segmentados lado a lado, e "7 dias" (período) se confunde com "7d" (janela da cota); (2) tabela de janelas e barras horárias empilhadas sem hierarquia; (3) barras de "Consumo por hora do dia" sem dica — não há hover nenhum (<code>HistoryHourlyDistribution</code>); (4) a frase "Janela atual ativa…" é texto solto, sem amostra de cor, e continua falando da janela atual mesmo quando o zoom mostra outro trecho. <b>Defeito confirmado à parte, corrigido em todas as opções:</b> o rótulo do meio do eixo vem do ponto do meio <i>por índice</i> (<code>buildTimeReferenceLabels</code>), mas o eixo é por tempo — no print de 24h ele diz 13:39 onde o tempo do meio é 08:55. Passe o mouse nas barras, na faixa e nos <span class="help">?</span>.',
  ref: { title: 'Hoje (referência)', why: 'Conta, período, janela da cota e PDF numa linha só; "7 dias" ao lado de "7d". Legenda em frase sob o gráfico. Barras horárias sem dica.',
    html: W('Histórico do Anthropic', `${kpis7}${chartCard(lineChart({ axis: AXIS_OLD, hideBandLabel: true, prev: PREV7 }), '<div class="cap">Tracejado: mesmo ponto do período anterior<br>Janela atual ativa 16:06 → 19:29 · 3h 23min. A faixa clara marca o trecho em que o uso subiu; o resto ficou ocioso.</div>')}
      ${sec('Janelas 7d', '2 janelas no intervalo', table(winHeads, WIN7, [2, 3, 4]) + '<div class="eyebrow">Consumo por hora do dia (BRT)</div>' + hourBars(H7, 'output', { tips: false }) + '<div class="cap"><b>Pico às 17h BRT · 53% do consumo</b></div>', 'output')}`, { bar: refBar }) },
  options: [
    { name: 'Abas da janela (o pedido literal)', why: '<b>Aba 1 de cima = janela da cota</b> (5h · 7d · Ambas), o que muda o card inteiro. <b>A barra fica com o resto</b>: conta, período rotulado e PDF. "7 dias" e "7d" deixam de dividir a mesma linha. Legenda com amostras; barras com dica.',
      html: W('Histórico do Anthropic', `${NEW(tabs(['Janela 5h', 'Janela 7d', 'Ambas'], 1))}${kpis7}${chartCard(lineChart({ prev: PREV7 }), NEW(stdLegend()))}
        ${sec('Janelas 7d', '2 janelas no intervalo', table(winHeads, WIN7, [2, 3, 4]) + NEW(hourBars(H7, 'output')) + '<div class="cap"><b>Pico às 17h BRT · 53% do consumo</b></div>', 'output')}`, { bar: `${btn(ACC)}<span class="grp"><span class="eyebrow">Período</span>${seg(['24h', '7 dias', '30 dias', 'Total'], 0)}</span>${btn('PDF')}` }) },
    { name: 'Abas por assunto', why: 'Abas <b>Consumo · Janelas · Horários</b>: cada aba responde uma pergunta. Filtros "Período" e "Janela" rotulados na barra. A tabela e as barras horárias deixam de dividir a mesma rolagem com o gráfico.',
      html: W('Histórico do Anthropic', `${NEW(tabs(['Consumo', 'Janelas', 'Horários'], 2))}
        ${sec('Consumo por hora do dia', 'Soma do intervalo 24h · janela 7d', NEW(hourBars(H7, 'output', { h: 70, axisEvery: 3 })) + '<div class="cap">Pico às 17h BRT · 53% do consumo. Passe o mouse em cada barra.</div>', 'output')}
        ${sec('Consumo por hora do dia', 'Soma do intervalo 24h · janela 5h', hourBars(H5, 'anthropic', { h: 50 }), 'anthropic')}`, { bar: `${btn(ACC)}<span class="grp"><span class="eyebrow">Período</span>${seg(['24h', '7 dias', '30 dias', 'Total'], 0)}</span><span class="grp"><span class="eyebrow">Janela</span>${seg(['5h', '7d', 'Ambas'], 2)}</span>${btn('PDF')}` }) },
    { name: 'Rótulos que desambiguam (sem abas)', why: 'Mudança mínima: os dois grupos ganham rótulo ("Período" / "Janela da cota") e os nomes deixam de colidir — "últimas 24h · 7 dias · 30 dias · tudo" contra "janela de 5h · de 7d · as duas". Duas linhas na barra em vez de uma.',
      html: W('Histórico do Anthropic', `${kpis7}${chartCard(lineChart({ prev: PREV7 }), stdLegend())}`, { bar: `<div class="col" style="width:100%">${btn(ACC)}<div class="row">${NEW(`<span class="grp"><span class="eyebrow">Período</span>${seg(['últimas 24h', '7 dias', '30 dias', 'tudo'], 0)}</span>`)}${NEW(`<span class="grp"><span class="eyebrow">Janela da cota</span>${seg(['de 5h', 'de 7d', 'as duas'], 1)}</span>`)}<span class="grow"></span>${btn('PDF')}</div></div>` }) },
    { name: 'Abas da janela, período dentro do gráfico', why: 'Como S1, mas o período sai da barra e vai para o cabeçalho do gráfico ("mostrando 24h ▾"): o filtro fica junto do que ele filtra. A barra sobra só com conta e PDF.',
      html: W('Histórico do Anthropic', `${tabs(['Janela 5h', 'Janela 7d', 'Ambas'], 1)}${kpis7}${sec('Claude · janela 7d', 'Consumo ao longo do período', lineChart({ prev: PREV7 }) + stdLegend(), 'anthropic', NEW(seg(['24h', '7 dias', '30 dias', 'Total'], 0)))}`, { bar: `${btn(ACC)}<span class="grow"></span>${btn('PDF')}` }) },
    { name: 'Tabela com trilhos e cabeçalho explicado', why: 'Cada linha da tabela de janelas ganha um <b>trilho de pico</b> e o cabeçalho ganha <span class="help">?</span> por coluna. Janela que já chegou usada mostra "antes da 1ª leitura" em vez de "21:02 → 21:02 · 0min".',
      html: W('Histórico do Anthropic', sec('Janelas 7d', '2 janelas no intervalo', NEW(table(['Início observado', winHeadsHelp[1], 'Pico' + help('Maior percentual lido na janela.'), winHeadsHelp[3], winHeadsHelp[4]],
        [['06/10 01:02 · <b>atual</b>', '16:06 → 19:29 · 3h 23min', `<div class="row" style="justify-content:flex-end">${track(15, 'output', '70px')}15 %</div>`, '—', '1 %/h'],
         ['05/10 21:02', '<span class="mut">antes da 1ª leitura</span>', `<div class="row" style="justify-content:flex-end">${track(93, 'output', '70px')}93 %</div>`, '—', '0 %/h']], [2, 3, 4])), 'output'), { bar: `${btn(ACC)}${seg(['24h', '7 dias', '30 dias', 'Total'], 0)}${seg(['5h', '7d', 'Ambas'], 1)}${btn('PDF')}` }) },
    { name: 'Barras com valor escrito', why: 'Sem depender de hover: cada barra com consumo leva o percentual escrito em cima, eixo a cada 3h, e as 3 horas de maior consumo em cor cheia (o resto esmaecido). Funciona também no PDF.',
      html: W('Histórico do Anthropic', `${sec('Consumo por hora do dia (BRT) · janela 5h', '% do consumo do intervalo em cada hora', NEW(hourBars(H5, 'anthropic', { printed: true, top3: true, axisEvery: 3, h: 56 })) + '<div class="cap">Pico às 17h BRT · 60% do consumo. 4 horas com uso no intervalo.</div>', 'anthropic')}
        ${sec('Consumo por hora do dia (BRT) · janela 7d', '', hourBars(H7, 'output', { printed: true, top3: true, axisEvery: 3, h: 56 }), 'output')}`, { bar: refBar }) },
    { name: 'Legenda em amostras que acompanha o zoom', why: 'A frase solta vira <b>legenda com amostra</b> de cada marca (linha, faixa, tracejado, reinício). Com zoom fora da janela atual, a frase diz isso e oferece "Ir para a janela atual" — hoje ela descreve uma faixa que nem aparece.',
      html: W('Histórico do Anthropic', `${chartCard(lineChart({ series: [[[[0, 0], [.1, 60], [.2, 100], [.3, 0], [.5, 40], [.6, 100], [.62, 0], [1, 0]], 'anthropic']], bands: [[.02, .2, 'anthropic'], [.4, .6, 'anthropic']], resetAt: .3, axis: ['02/10', '03/10 18h', '05/10'] }), NEW(stdLegend('anthropic')) + NEW('<div class="row cap"><span>Trecho ampliado 02/10 → 05/10 · 2 janelas ativas visíveis. A janela atual (06/10 16:06 → 19:29) está fora do trecho.</span>' + btn('Ir para a janela atual', 'ghost') + btn('Ver tudo', 'ghost') + '</div>'))}`, { bar: `${btn(ACC)}${seg(['24h', '7 dias', '30 dias', 'Total'], 3)}${seg(['5h', '7d', 'Ambas'], 0)}${btn('PDF')}` }) },
    { name: 'Mapa de calor dia × hora', why: 'As barras horárias viram um mapa <b>7 dias × 24 horas</b>: dá para ver "toda tarde" contra "só ontem". Cor por intensidade da série, número no hover, legenda de escala. Só aparece com período ≥ 7 dias; em 24h ficam as barras.',
      html: W('Histórico do Anthropic', sec('Quando você usa · janela 5h', 'Período 7 dias · % do consumo por célula', NEW(heatmap()) + lg([['0', 'background:var(--border)'], ['baixo', 'background:var(--anthropic);opacity:.35'], ['alto', 'background:var(--anthropic)']]), 'anthropic'), { bar: `${btn(ACC)}${seg(['24h', '7 dias', '30 dias', 'Total'], 1)}${seg(['5h', '7d', 'Ambas'], 0)}${btn('PDF')}` }) },
    { name: 'Lista de janelas e detalhe', why: 'Janelas viram uma lista à esquerda (mais nova primeiro). A escolhida mostra à direita a própria curva, a faixa ativa e as barras horárias <b>só dela</b> — hoje as barras somam o intervalo inteiro e não dizem de qual janela.',
      html: W('Histórico do Anthropic', `<div class="row" style="align-items:stretch;gap:0">${NEW(`<div class="list-sel" style="width:150px;border-right:1px solid var(--border)"><div class="eyebrow" style="padding:4px 8px">Janelas 5h</div><div class="on">06/10 16:06 · atual<br><span class="mut">pico 100 % · 3h 23</span></div><div>05/10 09:40<br><span class="mut">pico 71 %</span></div><div>04/10 14:02<br><span class="mut">pico 100 % · 4h 10</span></div></div>`)}<div class="grow col" style="padding-left:10px">${metrics([['Pico', '100 %'], ['Esgotou em', '3h 23min'], ['Ritmo', '21 %/h']], 3)}${lineChart({ series: [[[[0, 0], [.1, 0], [.3, 31], [.5, 74], [.68, 100], [1, 100]], 'anthropic']], bands: [[.1, .68, 'anthropic']], resetAt: null, axis: ['16:06', '18:36', 'reinício 21:06'], h: 80 })}${hourBars(H5, 'anthropic', { h: 30 })}</div></div>`, { bar: refBar }) },
    { name: 'Dicas e glossário em tudo', why: 'Mesma disposição de hoje, mas <b>todo número e toda marca explicam a si mesmos</b>: <span class="help">?</span> nos KPIs e colunas, hover em barra, faixa, ponto e reinício, e o painel "Glossário" no fim (o mesmo padrão do modal de sessões CLI).',
      html: W('Histórico do Anthropic', `${NEW(`<div class="split" style="grid-template-columns:repeat(3,1fr)">${metric('Uso atual', '15 / 100 %', '', 'Percentual usado agora na janela 7d.')}${metric('Janelas no intervalo', '2', '', 'Quantas janelas 7d tocaram as últimas 24h.')}${metric('Pico médio por janela', '93 %', '', 'Média do maior percentual de cada janela fechada.')}</div>`)}${chartCard(lineChart({ prev: PREV7, tipPts: true }), stdLegend())}
        ${sec('Janelas 7d', '', table(winHeadsHelp, WIN7, [2, 3, 4]) + hourBars(H7, 'output'), 'output')}${NEW('<div class="panel"><div class="mono sm">▸ Glossário — janela, faixa ativa, pico, ritmo, reinício, período anterior</div></div>')}`, { bar: refBar }) },
  ],
};
function heatmap() {
  const days = ['30/09', '01/10', '02/10', '03/10', '04/10', '05/10', '06/10'];
  const rows = days.map((d, di) => `<tr><td class="mono sm mut" style="width:auto;padding-right:6px">${d}</td>${Array.from({ length: 24 }, (_, h) => {
    const v = (h >= 9 && h <= 19 && (di + h) % 3 !== 0) ? ((h * 7 + di * 13) % 9) : (di === 6 && h >= 16 && h <= 19 ? 9 : 0);
    return `<td data-tip="${d} ${h}h BRT\n${v ? v + '% do consumo do período' : 'sem consumo'}" style="background:${v ? `color-mix(in srgb, var(--anthropic) ${20 + v * 9}%, transparent)` : 'var(--raised)'}"></td>`;
  }).join('')}</tr>`).join('');
  return `<table class="hm" style="border-spacing:2px">${rows}<tr><td></td>${Array.from({ length: 24 }, (_, h) => `<td class="mono mut" style="font-size:8px;text-align:center">${h % 6 ? '' : h}</td>`).join('')}</tr></table>`;
}

/* ══ #393 · Sessões CLI (rodada T) ══ */
// Codex: 220 respostas, contexto (entrada da resposta) crescendo, uma compactação.
const CTX_CODEX = Array.from({ length: 220 }, (_, i) => i < 150 ? 36 + i * 1.48 : 60 + (i - 150) * 2.6);
const CTX_CLAUDE = Array.from({ length: 160 }, (_, i) => i < 157 ? 40 + i * 4.6 : (i === 157 ? 20 : 744));
const CACHE_CODEX = Array.from({ length: 220 }, (_, i) => i === 0 ? 0 : i === 150 ? 40 : 94 + ((i * 7) % 5));
const OUT_CODEX = Array.from({ length: 220 }, (_, i) => 300 + ((i * 37) % 900) + (i % 17 === 0 ? 2400 : 0));
const TPS_CODEX = Array.from({ length: 220 }, (_, i) => 22 + ((i * 13) % 18));
function turnChart(vals, c, { h = 80, w = 520, drops = false, unit = 'K', bars = false, label = 'contexto', fmt = v => `${v.toFixed(0)}${unit}` } = {}) {
  const max = Math.max(...vals), n = vals.length, px = i => i / (n - 1) * w, py = v => h - 4 - v / max * (h - 12);
  let s = `<svg width="100%" height="${h + 12}" viewBox="0 0 ${w} ${h + 12}" preserveAspectRatio="none"><rect x="0" y="0" width="${w}" height="${h}" style="fill:var(--raised);opacity:.4"/>`;
  [0, .5, 1].forEach(f => { s += `<line x1="0" x2="${w}" y1="${py(max * f)}" y2="${py(max * f)}" style="stroke:var(--border)"/><text x="2" y="${py(max * f) - 2}" style="fill:var(--muted);font:8px var(--mono)">${fmt(max * f)}</text>`; });
  if (bars) vals.forEach((v, i) => { s += `<rect x="${px(i)}" y="${py(v)}" width="${Math.max(1, w / n - .6)}" height="${h - 4 - py(v)}" style="fill:var(--${c});opacity:.8"/>`; });
  else { const d = vals.map((v, i) => `${i ? 'L' : 'M'}${px(i).toFixed(1)},${py(v).toFixed(1)}`).join(''); s += `<path d="${d}L${w},${h - 4}L0,${h - 4}Z" style="fill:var(--${c});opacity:.12"/><path d="${d}" style="fill:none;stroke:var(--${c});stroke-width:1.4"/>`; }
  if (drops) vals.forEach((v, i) => { if (i && v < vals[i - 1] * .6) s += `<path d="M${px(i) - 4},${h - 2}L${px(i) + 4},${h - 2}L${px(i)},${h + 4}Z" style="fill:var(--crit)" data-tip="Compactação no turno #${i + 1}\n${fmt(vals[i - 1])} → ${fmt(v)}"/>`; });
  for (let i = 0; i < n; i += Math.ceil(n / 40)) s += `<rect x="${px(i) - w / 80}" y="0" width="${w / 40}" height="${h}" style="fill:transparent" data-tip="Turno #${i + 1}\n${label}: ${fmt(vals[i])}"/>`;
  return s + `<text x="0" y="${h + 11}" style="fill:var(--muted);font:8px var(--mono)">#1</text><text x="${w}" y="${h + 11}" text-anchor="end" style="fill:var(--muted);font:8px var(--mono)">#${n}</text></svg>`;
}
const ctxLegend = c => lg([['contexto', `background:var(--${c})`, 'Tokens de entrada da resposta: o prompt inteiro, com cache.'], ['compactação', 'background:var(--crit);clip-path:polygon(0 0,100% 0,50% 100%)', 'O contexto caiu de um turno para o outro (compactação).']]);
const codexMeta = `<div class="panel"><div class="mono" style="font-weight:600">usage-monitor</div><div class="cap">Codex Desktop · vscode</div><div class="row cap"><span>Diretório <b class="mono">C:\\…\\usage-monitor</b></span><span>Branch —</span><span>CLI 0.160.1</span><span>06/10 13:10 → 15:34 BRT</span></div></div>`;
const codexKpis = metrics([['Respostas', '220'], ['Tokens (com cache)', '38,1 M'], ['Cache', '96 %'], ['Entrada', '37,9 M'], ['Saída', '197,7 K', 'Raciocínio 72,4 K']], 5);
const claudeKpis = metrics([['Custo', 'US$ 92,86'], ['Tokens (com cache)', '137,1 M'], ['Taxa de acerto do cache', '99 %'], ['Janela de contexto', '77 %'], ['Tempo ativo', '1h 36']], 5);
const codexTurnRows = [['#1', 'gpt-6.1-sol', '35.983', '0 %', '412', '—'], ['#2', 'gpt-6.1-sol', '40.722', '88 %', '655', '31 tok/s'], ['#3', 'gpt-6.1-sol', '48.978', '83 %', '1.104', '27 tok/s'], ['#4', 'gpt-6.1-sol', '59.856', '82 %', '980', '29 tok/s']];
const advanced = c => `<div class="panel"><div class="mono sm">▸ Avançado <span class="mut">${c}</span></div></div>`;

const issue393 = {
  id: '393', letter: 'T', title: '#393 · Sessões CLI',
  intro: '<b>Claude (1º print):</b> rótulo "Taxa de acerto do…" cortado no bloco, custo com 4 casas, aviso de subagente solto entre blocos, e tudo além do gráfico de contexto escondido em "Avançado". <b>Codex (2º print):</b> zero gráficos; a tabela mostra o id da resposta truncado (não informa nada) e tokens crus "35983" enquanto o resto da tela usa "38,1M". <b>Limites do dado do Codex, que valem para toda opção:</b> há entrada, cache, saída, raciocínio e vazão por resposta — dá para fazer <i>contexto por turno</i> com compactação, <i>cache por turno</i>, <i>saída por turno</i> e <i>vazão</i>. <b>Não há</b> tarifa de modelo GPT na <code>ModelPricingTable</code> (custo e economia ficam fora, nunca como zero) nem tamanho de janela do modelo no rollout (percentual da janela fica fora). Hover nos gráficos mostra o turno.',
  ref: { title: 'Hoje (referência)', why: 'Claude: banner de saúde, metadados, aviso solto, 5 blocos, gráfico de contexto e "Avançado" fechado. Codex: metadados, 5 blocos e tabela "Uso por turno" com id de resposta e tokens crus.',
    html: `<div class="split" style="grid-template-columns:1fr">${W('Sessões CLI — Padrão', `<div class="panel mark" style="--c:var(--crit)"><b class="sm">Sessão saturada</b><span class="cap">77% da janela · $0.3844 por mensagem</span></div><div class="cap">53 turno(s) de subagente somam no custo mas ficam fora dos gráficos de contexto.</div>${metrics([['Custo', '$92.8568'], ['Tokens (com cache)', '137,1M'], ['Taxa de acerto do…', '99%'], ['Janela de contexto', '77%'], ['Tempo ativo', '1h36']], 5)}${sec('Contexto por turno', '', turnChart(CTX_CLAUDE, 'ok', { drops: true, h: 60 }), 'ok')}${advanced('composição dos tokens, distribuição do custo e gráficos por turno')}`, { sub: '34bf3c6d' })}
      ${W('Sessões Codex CLI', `${codexMeta}${codexKpis}${sec('Uso por turno', '220 resposta(s) · 38,1M tokens', table(['#', 'Modelo', 'Resposta', 'Tokens'], [['#1', 'gpt-6.1-sol', 'resp_02d82b9d62bb2…', '35983'], ['#2', 'gpt-6.1-sol', 'resp_02d82b9d62bb2…', '40722'], ['#3', 'gpt-6.1-sol', 'resp_02d82b9d62bb2…', '48978']], [3]), 'codex')}`, { sub: '01a111fa…' })}</div>` },
  options: [
    { name: 'Paridade mínima', why: 'Codex ganha o mesmo <b>"Contexto por turno"</b> do Claude (entrada da resposta, ▼ na compactação) e o mesmo <b>"Avançado"</b> com cache e saída por turno. Tabela troca o id de resposta por Cache e Saída, números formatados. Claude só corrige rótulo e casas.',
      html: W('Sessões Codex CLI', `${codexMeta}${codexKpis}${NEW(sec('Contexto por turno', '', turnChart(CTX_CODEX, 'cread', { drops: true }) + ctxLegend('cread'), 'cread', help('Tokens de entrada de cada resposta: o prompt inteiro que o modelo leu, com cache.')))}${NEW(advanced('cache por turno, saída e raciocínio por turno, vazão'))}${sec('Uso por turno', '220 respostas · 38,1 M tokens', NEW(table(['#', 'Modelo', 'Contexto', 'Cache', 'Saída', 'Vazão'], codexTurnRows, [2, 3, 4, 5])), 'codex')}`, { sub: '01a111fa…' }) },
    { name: 'Abas no detalhe', why: 'O detalhe das duas fontes vira <b>Resumo · Gráficos · Turnos</b>. Resumo: metadados, saúde e blocos. Gráficos: todos, sem "Avançado". Turnos: a tabela. Mesmo esqueleto no Claude e no Codex.',
      html: W('Sessões Codex CLI', `${NEW(tabs(['Resumo', 'Gráficos', 'Turnos'], 1))}${sec('Contexto por turno', '', turnChart(CTX_CODEX, 'cread', { drops: true, h: 60 }) + ctxLegend('cread'), 'cread')}${sec('Cache por turno', 'parte da entrada servida do cache', turnChart(CACHE_CODEX, 'ok', { h: 44, unit: '%', label: 'cache' }), 'ok')}${sec('Saída por turno', '', turnChart(OUT_CODEX, 'output', { h: 44, bars: true, unit: '', label: 'saída', fmt: v => `${(v / 1000).toFixed(1)}K` }), 'output')}`, { sub: '01a111fa…' }) },
    { name: 'Grade de gráficos 2×2', why: 'Quatro gráficos pequenos sempre visíveis, mesma escala de turno no eixo X: <b>contexto · cache · saída · vazão</b>. No Claude o quarto é custo acumulado; no Codex é vazão (não há tarifa).',
      html: W('Sessões Codex CLI', `${codexKpis}${NEW(`<div class="split" style="grid-template-columns:1fr 1fr">${sec('Contexto', '', turnChart(CTX_CODEX, 'cread', { drops: true, h: 50, w: 260 }), 'cread')}${sec('Cache', '', turnChart(CACHE_CODEX, 'ok', { h: 50, w: 260, unit: '%', label: 'cache' }), 'ok')}${sec('Saída', '', turnChart(OUT_CODEX, 'output', { h: 50, w: 260, bars: true, label: 'saída', fmt: v => `${(v / 1000).toFixed(1)}K` }), 'output')}${sec('Vazão', '', turnChart(TPS_CODEX, 'info', { h: 50, w: 260, label: 'vazão', fmt: v => `${v.toFixed(0)} tok/s` }), 'info')}</div>`)}`, { sub: '01a111fa…' }) },
    { name: 'Um gráfico, métrica escolhida', why: 'Um gráfico grande com seletor <b>Contexto · Cache · Saída · Vazão · Custo</b>. No Codex, "Custo" fica desabilitado com a dica "o Codex não tem tarifa no app" — o usuário vê que falta e por quê, em vez de achar que é zero.',
      html: W('Sessões Codex CLI', `${codexKpis}${sec('Por turno', '', NEW(`<div class="row">${seg(['Contexto', 'Cache', 'Saída', 'Vazão'], 0)}<span class="btn off" data-tip="Custo indisponível: o app não tem tarifa para modelos do Codex. Não é zero.">Custo</span></div>`) + turnChart(CTX_CODEX, 'cread', { drops: true, h: 100 }) + ctxLegend('cread'), 'cread')}`, { sub: '01a111fa…' }) },
    { name: 'Cabeçalho compacto e avisos agrupados', why: 'Metadados e saúde numa faixa só; os blocos com rótulo inteiro e <span class="help">?</span>; avisos (subagente, custo incompleto, desatualizado) num único "Avisos (1)" em hint, como no card. Ganha uma tela de altura para os gráficos.',
      html: W('Sessões CLI — Padrão', `${NEW(`<div class="panel row"><span class="pill" style="--c:var(--crit)">Saturada · 77% da janela</span><span class="cap grow">SAMMYSAN · usage-monitor · main · 06/10 16:06 → 19:29</span><span class="btn ghost" data-tip="53 turnos de subagente somam no custo, mas ficam fora dos gráficos de contexto.">Avisos (1)</span></div>`)}${NEW(`<div class="split" style="grid-template-columns:repeat(5,1fr)">${metric('Custo', 'US$ 92,86', 'US$ 0,38 / msg', 'Recalculado dos tokens pela tabela de preços.')}${metric('Tokens', '137,1 M', 'com cache', 'Entrada + saída + cache.')}${metric('Cache', '99 %', 'taxa de acerto', 'Parte da entrada lida do cache.')}${metric('Janela', '77 %', 'de contexto', 'Ocupação da janela do modelo no último turno.')}${metric('Ativo', '1h 36', '', 'Soma dos trechos com turno.')}</div>`)}${sec('Contexto por turno', '', turnChart(CTX_CLAUDE, 'ok', { drops: true, h: 70 }) + ctxLegend('ok'), 'ok')}`, { sub: '34bf3c6d' }) },
    { name: 'Linha do tempo combinada', why: 'Um gráfico só por sessão: <b>área = contexto</b>, <b>barras = saída do turno</b>, ▼ = compactação. O hover mostra o turno inteiro (contexto, cache, saída, raciocínio, vazão). Vale igual para as duas fontes.',
      html: W('Sessões Codex CLI', `${codexKpis}${NEW(sec('Turnos', 'área: contexto · barras: saída', `<div style="position:relative">${turnChart(CTX_CODEX, 'cread', { drops: true, h: 110 })}<div style="position:absolute;inset:0 0 12px 0;opacity:.9">${turnChart(OUT_CODEX.map(v => v / 30), 'output', { h: 110, bars: true, label: 'saída', fmt: v => `${(v * 30 / 1000).toFixed(1)}K` }).replace(/<rect x="0" y="0"[^>]*>/, '').replace(/<line[^>]*>|<text[^>]*>[^<]*<\/text>/g, '')}</div></div>` + lg([['contexto', 'background:var(--cread)'], ['saída do turno', 'background:var(--output)'], ['compactação', 'background:var(--crit)']]), 'codex'))}`, { sub: '01a111fa…' }) },
    { name: 'Tabela de turnos rica', why: 'A tabela vira o centro: cada linha com <b>barra de contexto</b>, cache %, saída e vazão; um sparkline no cabeçalho dá a forma da sessão. Clicar na linha marca o turno no gráfico. Sai o id de resposta.',
      html: W('Sessões Codex CLI', `${codexKpis}${sec('Uso por turno', '220 respostas', NEW(turnChart(CTX_CODEX, 'cread', { drops: true, h: 34 })) + NEW(table(['#', 'Contexto', 'Cache', 'Saída', 'Vazão'], codexTurnRows.map(r => [r[0], `<div class="row">${track(+r[2].replace('.', '') / 3000, 'cread', '110px')}<span class="num">${r[2]}</span></div>`, r[3], r[4], r[5]]), [2, 3, 4], 2)), 'codex')}`, { sub: '01a111fa…' }) },
    { name: 'Duas colunas', why: 'Para a janela larga: à esquerda metadados, saúde e blocos empilhados; à direita os gráficos rolam sozinhos. Abaixo de 760dp volta a uma coluna.',
      html: W('Sessões Codex CLI', `<div class="row" style="align-items:flex-start">${NEW(`<div class="col" style="width:150px">${metric('Respostas', '220')}${metric('Tokens', '38,1 M')}${metric('Cache', '96 %')}${metric('Saída', '197,7 K', 'Raciocínio 72,4 K')}<div class="cap">usage-monitor · CLI 0.160.1<br>13:10 → 15:34 BRT</div></div>`)}<div class="grow col">${sec('Contexto por turno', '', turnChart(CTX_CODEX, 'cread', { drops: true, h: 70 }), 'cread')}${sec('Cache por turno', '', turnChart(CACHE_CODEX, 'ok', { h: 40, unit: '%', label: 'cache' }), 'ok')}${sec('Vazão', '', turnChart(TPS_CODEX, 'info', { h: 40, label: 'vazão', fmt: v => `${v.toFixed(0)} tok/s` }), 'info')}</div></div>`, { sub: '01a111fa…' }) },
    { name: 'Comparada com suas sessões', why: 'Cada bloco diz <b>se está acima ou abaixo da mediana das suas sessões</b> da mesma fonte ("contexto médio 172 K · 2,1× a mediana"). Responde "isso é normal?" — o número só, não responde. Mediana sai do índice que já existe.',
      html: W('Sessões Codex CLI', `${NEW(`<div class="split" style="grid-template-columns:repeat(4,1fr)">${metric('Contexto médio', '172 K', '▲ 2,1× a mediana', 'Mediana das suas sessões Codex nos últimos 30 dias: 82 K.')}${metric('Cache', '96 %', '≈ mediana (94 %)')}${metric('Saída / resposta', '899', '▼ 0,7× a mediana')}${metric('Vazão', '31 tok/s', '≈ mediana')}</div>`)}${sec('Contexto por turno', 'tracejado: mediana das suas sessões', turnChart(CTX_CODEX, 'cread', { drops: true, h: 70 }), 'cread')}`, { sub: '01a111fa…' }) },
    { name: 'Seções recolhíveis padronizadas', why: 'Mesmo esqueleto nas duas fontes, cada seção com o valor-resumo no cabeçalho: <b>Contexto ▸ 258 K pico · Cache ▸ 96 % · Saída ▸ 197,7 K · Custo ▸ (só Claude) · Turnos ▸ 220</b>. Abre só o que interessa; a escolha fica lembrada.',
      html: W('Sessões Codex CLI', `${codexMeta}${NEW(`<div class="col">${sec('▾ Contexto por turno', '', turnChart(CTX_CODEX, 'cread', { drops: true, h: 60 }), 'cread', '<span class="num sm">pico 258 K · 1 compactação</span>')}<div class="panel row"><span class="mono sm grow">▸ Cache por turno</span><span class="num sm">96 %</span></div><div class="panel row"><span class="mono sm grow">▸ Saída e raciocínio</span><span class="num sm">197,7 K · 72,4 K</span></div><div class="panel row"><span class="mono sm grow">▸ Vazão</span><span class="num sm">31 tok/s</span></div><div class="panel row"><span class="mono sm grow">▸ Turnos</span><span class="num sm">220</span></div></div>`)}`, { sub: '01a111fa…' }) },
  ],
};

const ISSUES = [issue392, issue393];

/* ── montagem ── */
const card = (o, cls, label) => `<article class="g-card ${cls}"><h3><span class="tag">${label}</span>${o.title || o.name}</h3><p class="why">${o.why}</p><div class="g-stage">${o.html}</div></article>`;
function render(id) {
  const is = ISSUES.find(i => i.id === id);
  $('#g-body').innerHTML = `<p class="g-intro">${is.intro}</p><div class="g-grid">${card(is.ref, 'ref', 'ref')}${is.options.map((o, i) => card(o, '', `${is.letter}${i + 1}`)).join('')}</div>`;
  document.querySelectorAll('.g-tab').forEach(t => t.setAttribute('aria-selected', String(t.dataset.id === id)));
  try { localStorage.setItem('g392-tab', id); } catch (e) {}
}
function tips() {
  const box = document.createElement('div'); box.className = 'tipbox'; document.body.appendChild(box);
  document.addEventListener('mousemove', e => {
    const el = e.target.closest && e.target.closest('[data-tip]');
    if (!el) { box.style.display = 'none'; return; }
    box.textContent = el.getAttribute('data-tip'); box.style.display = 'block';
    const x = Math.min(e.clientX + 12, innerWidth - box.offsetWidth - 8), y = Math.min(e.clientY + 14, innerHeight - box.offsetHeight - 8);
    box.style.left = x + 'px'; box.style.top = y + 'px';
  });
}
document.addEventListener('DOMContentLoaded', () => {
  $('#g-tabs').innerHTML = ISSUES.map(i => `<button class="g-tab" data-id="${i.id}">${i.title}</button>`).join('');
  $('#g-tabs').addEventListener('click', e => { const b = e.target.closest('.g-tab'); if (b) render(b.dataset.id); });
  $('#g-theme').addEventListener('change', e => { document.documentElement.dataset.appTheme = e.target.checked ? 'light' : 'dark'; });
  tips();
  let start = '392'; try { start = localStorage.getItem('g392-tab') || start; } catch (e) {}
  if (location.hash) start = location.hash.slice(1);
  render(ISSUES.some(i => i.id === start) ? start : '392');
});
window.GALLERY_ISSUES = ISSUES;
})();
