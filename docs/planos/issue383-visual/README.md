# Direções visuais do histórico — issue #383

As dez propostas usam as primitivas publicadas do design system, compiladas sem alterar seus arquivos. O fragmento da conversa é gerado dos mesmos fontes da galeria `index.html`.

## Escopo

- Dados sintéticos de cotas e atividade local; estados sem dados e temas claro/escuro.
- Dez organizações, com abas, expansão e seleção local de séries/modelos.
- Controles da galeria ficam fora da interface proposta. Intervalo e conta ilustram seleção/contexto sobre uma fixture fixa: não consultam o banco nem recalculam métricas.
- O botão PDF abre uma prévia de conteúdo. Não cria ou grava PDF.
- A implementação Compose foi inspecionada também em escala global de 125%. O aplicativo instalado permanece pendente de QA manual.

Direção escolhida na conversa: **01 — Resumo, gráfico e detalhes**. A implementação Compose e sua exportação real de PDF estão descritas em `../modal-historico-383-adequacao.md`; os botões desta galeria continuam sendo amostras locais.

## Fontes

- `HistoryDirections.jsx`: conteúdo, fixtures e interações das propostas.
- `history-directions.template.html`: carrossel com as dez direções.
- `history-directions.css`: composição e adaptação ao espaço; tokens do produto vêm do design system.
- `build-gallery.cjs`: compila treze módulos publicados para o fragmento e para a revisão standalone.
- `inspect-gallery.cjs`: checa erros, transbordamento, estados e interações em Chromium/Edge headless.

## Reprodução

O compilador utiliza o mesmo Babel standalone 7.26.4 do UI kit. Baixar para `build/issue383/babel.cjs`, sem instalar dependência do aplicativo. Passar os caminhos de Node, Python e pacotes retornados pelo runtime do Codex.

```powershell
New-Item -ItemType Directory -Force build/issue383
Invoke-WebRequest 'https://unpkg.com/@babel/standalone@7.26.4/babel.min.js' -OutFile build/issue383/babel.cjs
Invoke-WebRequest 'https://unpkg.com/react@18.3.1/umd/react.production.min.js' -OutFile build/issue383/react.js
Invoke-WebRequest 'https://unpkg.com/react-dom@18.3.1/umd/react-dom.production.min.js' -OutFile build/issue383/react-dom.js
Invoke-WebRequest 'https://unpkg.com/react-dom@18.3.1/umd/react-dom-server-legacy.browser.production.min.js' -OutFile build/issue383/react-dom-server.js
node docs/planos/issue383-visual/build-gallery.cjs build/issue383/babel.cjs CAMINHO_ABSOLUTO_DO_FRAGMENTO.html
python CAMINHO_DA_SKILL_VISUALIZE/scripts/render.py CAMINHO_ABSOLUTO_DO_FRAGMENTO.html build/issue383/gallery-preview.html --force
node docs/planos/issue383-visual/inspect-gallery.cjs CAMINHO_DOS_PACOTES_NODE build/issue383/gallery-preview.html build/issue383/gallery-inspection
node docs/planos/issue383-visual/verify-startup.cjs CAMINHO_DOS_PACOTES_NODE after build/issue383/gallery-preview.html build/issue383/startup
.\gradlew.bat generateScreenshots '-PscreenshotScenario=history-baseline' '-PscreenshotOutputDir=build/issue383/baseline' --no-daemon --console=plain
```

O carrossel da conversa é fornecido pelo host. `index.html` oferece um seletor externo para revisar as mesmas propostas no navegador. Nenhum controle envia aprovação ou inicia implementação automaticamente.

## Inicialização do preview

React e ReactDOM são incorporados no HTML gerado. As dez propostas também recebem markup estático produzido com ReactDOMServer: a primeira tela aparece antes da execução de scripts e continua legível sem JavaScript. Essa correção foi verificada bloqueando toda a rede externa e abrindo `index.html` com JavaScript desabilitado. O relato de tela branca não forneceu log do host; a correção remove a dependência de scripts externos e a tela vazia durante a inicialização.

`verify-startup.cjs` verifica dez telas, cota semanal, expansão, seleção do oitavo modelo e prévia de PDF com a rede bloqueada. Execute com os mesmos caminhos de pacotes e wrapper usados em `inspect-gallery.cjs`.
