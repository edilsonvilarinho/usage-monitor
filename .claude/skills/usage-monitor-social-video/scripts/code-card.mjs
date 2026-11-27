// Cartão de código real do vídeo social (#686, A03b). Puro: nenhum I/O.
// O PNG é gerado pelo navegador em code-render.mjs (renderCodeCard), chamado por make.mjs stageImages.
// Visual: references/video-spec.md, "code". Texto vindo do arquivo entra por textContent, nunca innerHTML.

export const CODE_CARD = Object.freeze({
  width: 1500,
  font: 26,
  fontFloor: 18,
  maxLines: 16,
  maxColumns: 110,
  bg: '#141414',
  border: '#3D3838',
  radius: 8,
  padding: 28,
  text: '#E6E1E1',
  number: '#7A7373',
  head: '#FAB283',
  headFont: 20,
})

const TAB_COLUMNS = 4

// Colunas de um texto: tab conta 4, cada outro caractere conta 1.
export function codeColumns(text) {
  return [...text].reduce((sum, ch) => sum + (ch === '\t' ? TAB_COLUMNS : 1), 0)
}

// Linhas do arquivo (sem a quebra final), de from a to, inclusive; from e to de 1 em diante.
export function codeExcerpt(text, from, to) {
  const lines = text.replace(/\r?\n$/, '').split(/\r?\n/)
  return lines.slice(from - 1, to)
}

// Tira a indentação comum a todas as linhas não vazias do trecho. Linha em branco vira ''.
export function dedentLines(lines) {
  const indents = lines.filter((line) => line.trim() !== '').map((line) => line.match(/^[ \t]*/)[0].length)
  const common = indents.length ? Math.min(...indents) : 0
  return lines.map((line) => (line.trim() === '' ? '' : line.slice(common)))
}

// Dados do cartão: cabeçalho file:from-to, números de linha e linhas já sem indentação comum.
export function codeCardData({ file, from, to, lines }) {
  return {
    head: `${file}:${from}-${to}`,
    numbers: lines.map((_, i) => String(from + i)),
    lines: dedentLines(lines),
  }
}

// HTML do cartão. fonts: { regular, medium } com URLs file:// das TTF do projeto.
// Os dados entram num <script type="application/json"> e vão para o DOM com textContent.
// A fonte começa em 26 px e desce até 18 px enquanto a linha mais longa não couber.
export function codeCardHtml(fonts, data = { head: '', numbers: [], lines: [] }) {
  const c = CODE_CARD
  const payload = JSON.stringify(data).replace(/</g, '\\u003c')
  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<style>
@font-face { font-family: "Plex Mono"; font-weight: 400; src: url("${fonts.regular}") format("truetype"); }
@font-face { font-family: "Plex Mono"; font-weight: 500; src: url("${fonts.medium}") format("truetype"); }
html, body { margin: 0; background: transparent; }
#card { box-sizing: border-box; width: ${c.width}px; background: ${c.bg}; border: 1px solid ${c.border}; border-radius: ${c.radius}px; padding: ${c.padding}px; font-family: "Plex Mono", monospace; }
#head { font-weight: 500; font-size: ${c.headFont}px; color: ${c.head}; margin-bottom: 16px; white-space: pre; }
#body { display: flex; align-items: flex-start; }
#numbers { flex: 0 0 auto; color: ${c.number}; font-weight: 400; font-size: ${c.font}px; line-height: 1.45; text-align: right; padding-right: 24px; white-space: pre; }
#code { flex: 1 1 auto; min-width: 0; overflow: hidden; color: ${c.text}; font-weight: 400; font-size: ${c.font}px; line-height: 1.45; white-space: pre; }
</style>
</head>
<body>
<div id="card"><div id="head"></div><div id="body"><div id="numbers"></div><div id="code"></div></div></div>
<script type="application/json" id="data">${payload}</script>
<script>
(function () {
  var data = JSON.parse(document.getElementById('data').textContent);
  var size = ${c.font};
  var floor = ${c.fontFloor};
  document.getElementById('head').textContent = data.head;
  document.getElementById('numbers').textContent = data.numbers.join('\\n');
  document.getElementById('code').textContent = data.lines.join('\\n');
  function apply() {
    document.getElementById('numbers').style.fontSize = size + 'px';
    document.getElementById('code').style.fontSize = size + 'px';
  }
  function fits() {
    var el = document.getElementById('code');
    return el.scrollWidth <= el.clientWidth;
  }
  function fit() {
    apply();
    while (!fits() && size > floor) { size -= 1; apply(); }
    window.__codeFont = size;
    window.__codeReady = true;
  }
  document.fonts.ready.then(fit);
})();
</script>
</body>
</html>
`
}
