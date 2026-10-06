# Galeria das issues #382, #384, #386, #387, #388

Rodadas N (#382), O (#384), P (#386), Q (#387) e R (#388) da skill `usage-monitor-visual-options`:
10 direções por issue, card "Hoje (referência)" tracejado e, na #388, a seção de Configurações comum às 10.
Telas estáticas (mudança de layout, não de animação); o anel da #388 é o `gargantua-ring.js` da skill.

- `gallery.js`: dados sintéticos e os mockups de cada opção.
- `gallery.css`: composição; tokens vêm de `docs/design-system/tokens/`.
- `build-gallery.cjs`: gera o HTML autocontido.

```bash
node docs/planos/issues-381-388-visual/build-gallery.cjs
```

Saída: `build/gargantua-preview/issues-381-388-index.html` (ignorado pelo git). Contorno tracejado dourado
marca o que é novo em relação ao app. Nenhum controle executa ação de produção.
