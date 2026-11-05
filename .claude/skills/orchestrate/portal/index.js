'use strict';
// Ponto de entrada de `node --test skills/orchestrate/portal/`.
// O Node 24 não varre diretórios: ele resolve a pasta como módulo e executa este arquivo,
// que registra os testes dos módulos .mjs.
for (const nome of ['collector', 'server', 'cli']) {
  import(`./${nome}.test.mjs`);
}
