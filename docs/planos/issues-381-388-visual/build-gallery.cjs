// Monta a galeria única das issues #382, #384, #386, #387, #388 num HTML autocontido.
// Uso: node docs/planos/issues-381-388-visual/build-gallery.cjs [saída]
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../../..');
const ds = path.join(root, 'docs/design-system/tokens');
const output = path.resolve(process.argv[2] || path.join(root, 'build/gargantua-preview/issues-381-388-index.html'));
const read = file => fs.readFileSync(file, 'utf8');
const fonts = read(path.join(ds, 'fonts.css'));
const tokens = ['colors', 'typography', 'spacing', 'shape', 'motion', 'base'].map(name => read(path.join(ds, `${name}.css`))).join('\n');
// O anel é o mesmo desenho da skill usage-monitor-visual-options (AppGargantuaRing).
const ring = read(path.join(root, '.claude/skills/usage-monitor-visual-options/assets/gargantua-ring.js'));
const styles = read(path.join(__dirname, 'gallery.css'));
const script = read(path.join(__dirname, 'gallery.js'));
const html = `<!doctype html>
<html lang="pt-BR" data-app-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Galeria issues 381-388</title>
<style>
${fonts}
${tokens}
${styles}
</style>
</head>
<body>
<header class="g-head">
  <div class="g-title">
    <h1>Usage Monitor · galeria das issues #382 #384 #386 #387 #388</h1>
    <p>10 direções por issue · dados sintéticos · contorno tracejado dourado = o que é novo</p>
    <label class="g-tools"><input type="checkbox" id="g-theme"> tema claro</label>
  </div>
  <nav class="g-tabs" id="g-tabs" role="tablist"></nav>
</header>
<main class="g-body" id="g-body"></main>
<script>
${ring}
${script}
</script>
</body>
</html>
`;
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, html, 'utf8');
console.log(`galeria: ${output} (${(html.length / 1024).toFixed(1)} KB)`);
