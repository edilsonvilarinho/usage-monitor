// Compila as primitivas publicadas, sem alterar o design system.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const root = path.resolve(__dirname, '../../..');
const ds = path.join(root, 'docs/design-system');
const babelPath = process.argv[2];
const output = process.argv[3];
if (!babelPath || !output) throw new Error('Uso: node build-gallery.cjs caminho-babel.cjs caminho-fragmento.html');
const babel = require(path.resolve(babelPath));
const files = [
  'core/AppButton', 'core/AppPanel', 'core/AppMetric', 'core/AppSourceMark',
  'forms/AppTabs', 'forms/AppSegmentedControl', 'data/AppStatusIndicator',
  'data/AppDataRow', 'data/AppDataTable', 'feedback/AppEmptyState',
  'shell/AppWindowFrame', 'shell/AppToolbar', 'shell/AppStatusBar'
];
let code = 'const DS = {};\n';
for (const file of files) {
  let source = fs.readFileSync(path.join(ds, 'components', `${file}.jsx`), 'utf8');
  const exports = [...source.matchAll(/export function (\w+)/g)].map(match => match[1]);
  source = source.replace(/^import[^;]+;\s*/gm, '').replace(/export function /g, 'function ');
  if (file === 'shell/AppWindowFrame') {
    const mark = fs.readFileSync(path.join(ds, 'assets/app-icon-tray.png')).toString('base64');
    source = source.replace('../../assets/app-icon-tray.png', `data:image/png;base64,${mark}`);
  }
  code += `Object.assign(DS, (() => {\n${source}\nreturn {${exports.join(',')}};\n})());\n`;
}
code += `const {${files.flatMap(file => [...fs.readFileSync(path.join(ds, 'components', `${file}.jsx`), 'utf8').matchAll(/export function (\w+)/g)].map(match => match[1])).join(',')}} = DS;\n`;
code += fs.readFileSync(path.join(__dirname,'HistoryDirections.jsx'),'utf8');
const compiled = babel.transform(code,{presets:['react'],comments:false,compact:false}).code;
const bundleDirectory = path.dirname(path.resolve(babelPath));
const react = fs.readFileSync(path.join(bundleDirectory,'react.js'),'utf8');
const reactDom = fs.readFileSync(path.join(bundleDirectory,'react-dom.js'),'utf8');
const reactServer = fs.readFileSync(path.join(bundleDirectory,'react-dom-server.js'),'utf8');
// A primeira tela existe como HTML mesmo sem execução de JavaScript ou rede.
const initialScreens = Array.from({length:10},()=>({html:''}));
const initialRoot = {querySelectorAll:()=>initialScreens,dispatchEvent:()=>{},querySelector:()=>null};
const context = {console,TextEncoder,TextDecoder,setTimeout,clearTimeout,
  document:{getElementById:()=>initialRoot},addEventListener:()=>{}};
context.window = context;
context.self = context;
vm.createContext(context);
vm.runInContext(react,context);
vm.runInContext(reactServer,context);
context.ReactDOM = {createRoot:element=>({render:screen=>{element.html=context.ReactDOMServer.renderToStaticMarkup(screen);}})};
vm.runInContext(compiled,context);
const tokens = ['colors','typography','spacing','shape','motion'].map(name =>
  fs.readFileSync(path.join(ds,'tokens',`${name}.css`),'utf8').replace(/:root/g,'#history-directions-383 .hm-product')
).join('\n');
const fonts = fs.readFileSync(path.join(ds,'tokens/fonts.css'),'utf8');
const styles = fs.readFileSync(path.join(__dirname,'history-directions.css'),'utf8');
let screenIndex = 0;
const template = fs.readFileSync(path.join(__dirname,'history-directions.template.html'),'utf8')
  .replace(/<div data-mock-root><\/div>/g,()=>`<div data-mock-root>${initialScreens[screenIndex++].html}</div>`);
const fragment = `<style>\n${fonts}\n${tokens}\n${styles}\n</style>\n${template}\n` +
  `<script>\n${react}\n${reactDom}\n(()=>{\n${compiled}\n})();\n</script>\n`;
fs.mkdirSync(path.dirname(path.resolve(output)),{recursive:true});
fs.writeFileSync(path.resolve(output),fragment,'utf8');
// A revisão no repositório usa controles externos à UI proposta; a conversa usa o carrossel do host.
const review = `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Issue 383 · direções do histórico</title>
<style>body{margin:0;padding:24px;background:#211e1e;color:#f2eded;font:14px system-ui} .review-controls{display:flex;gap:16px;flex-wrap:wrap;margin:0 auto 16px;max-width:1030px} .review-controls label{display:flex;gap:8px;align-items:center} .review-controls select{font:inherit;padding:6px} #history-directions-383{max-width:1030px;margin:auto} #history-directions-383 [data-variant]{min-height:0!important}</style></head><body>
<div class="review-controls" aria-label="Controles da galeria">
<label>Proposta <select id="review-direction"></select></label>
<label>Tema <select id="review-theme"><option>Escuro</option><option>Claro</option></select></label>
<label>Dados <select id="review-data"><option>Cotas</option><option>Atividade local</option><option>Sem dados</option></select></label>
<label>Largura <select id="review-width"><option>Normal</option><option>Estreita</option></select></label>
</div>${fragment}<script>
const direction=document.getElementById('review-direction');
issue383Gallery.names.forEach((name,index)=>{const option=document.createElement('option');option.value=index;option.textContent=String(index+1).padStart(2,'0')+' · '+name;direction.appendChild(option);});
direction.onchange=()=>document.querySelectorAll('[data-variant]').forEach((section,index)=>section.hidden=index!==Number(direction.value));
for(const [id,key] of [['review-theme','tema'],['review-data','dados'],['review-width','largura']]){document.getElementById(id).onchange=event=>{issue383Gallery.settings[key]=event.target.value;issue383Gallery.applySettings();};}
</script></body></html>`;
fs.writeFileSync(path.join(__dirname,'index.html'),review,'utf8');
console.log(`Fragmento gerado: ${Buffer.byteLength(fragment)} bytes; ${files.length} módulos publicados.`);
