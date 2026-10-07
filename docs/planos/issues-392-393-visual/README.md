# Galeria das issues #392 e #393

Rodadas S (#392, histórico) e T (#393, sessões CLI) da skill `usage-monitor-visual-options`:
10 direções por issue e o card "Hoje (referência)" tracejado. Telas estáticas (mudança de layout,
não de animação), com dica no hover onde a proposta é justamente a dica.

- `gallery.js`: dados dos prints das issues (conta sintética) e os mockups de cada opção.
- `gallery.css`: composição; tokens vêm de `docs/design-system/tokens/`.
- `build-gallery.cjs`: gera o HTML autocontido.

```bash
node docs/planos/issues-392-393-visual/build-gallery.cjs
```

Saída: `build/gargantua-preview/issues-392-393-index.html` (ignorado pelo git). Contorno tracejado
dourado marca o que é novo em relação ao app. Nenhum controle executa ação de produção.
