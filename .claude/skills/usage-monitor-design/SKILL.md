---
name: usage-monitor-design
description: Apply the Usage Monitor design system when writing or reviewing any visible surface of this repository — Compose screens, dialogs, the tray, the PDF report, and throwaway mocks. Contains the token layer, the published primitive contracts, the precedence rules against the approved prototype, and the UI kit. Use when Claude needs to draw, change, or audit a screen, or to build a visual prototype for this project.
---

# Usage Monitor — design system

A fonte de verdade visual deste repositório é [`docs/design-system/`](../../../docs/design-system/).
Ela é normativa: divergência entre o Compose e o design system é defeito do Compose. A tabela
completa de precedência está em [CLAUDE.md](../../../CLAUDE.md), seção *Sistema visual → Design
system — precedência*.

## Antes de desenhar qualquer coisa

1. Ler [`docs/design-system/readme.md`](../../../docs/design-system/readme.md) inteiro. Ele carrega
   os fundamentos de conteúdo (voz, casing, "todo estado carrega uma palavra", "controle
   desabilitado sempre diz por quê") e as fundações visuais.
2. Ler os tokens em [`docs/design-system/tokens/`](../../../docs/design-system/tokens/). Eles já têm
   equivalente exato em Kotlin — `AppSpacing`, `AppShapes`, `AppElevation`, `AppMotion`,
   `AppThemePreset.OBSIDIANA_DARK`/`PORCELANA_LIGHT` e `AppAccents` em
   `src/commonMain/kotlin/com/usagemonitor/presentation/ui/theme/`. **Não introduza um valor novo:**
   se falta um degrau, a decisão vai para o design system antes de ir para o código.
3. Ler o contrato da primitiva que você vai usar em
   [`docs/design-system/components/`](../../../docs/design-system/components/). Cada `*.prompt.md`
   diz o que a primitiva resolve e o que é proibido dentro dela.
4. Abrir o mockup da tela no protótipo aprovado,
   [`docs/planos/prototipo-visual-opencode.html`](../../../docs/planos/prototipo-visual-opencode.html),
   e o kit correspondente em
   [`docs/design-system/ui_kits/desktop-app/`](../../../docs/design-system/ui_kits/desktop-app/).

## Regras que não se negociam

- **Nenhuma tela reimplementa uma primitiva.** Antes de escrever `Surface`, `Card`,
  `Modifier.border`, `.background` com cor de superfície ou `RoundedCornerShape`, procure em
  `presentation/ui/components/AppStructure.kt`, `AppControls.kt` e `AppStates.kt`.
- **Primitiva construída e não adotada não conserta nada.** O commit que cria e o que consome são o
  mesmo.
- **Cor de acento vem de `AppAccents.current` e `AppTone`**, nunca de `darkAppAccents` /
  `lightAppAccents` diretamente — `val` de topo de arquivo não lê o tema em vigor.
- **Cor nunca informa sozinha.** Todo estado carrega ponto **e** palavra (`AppStatusIndicator`).
- **Nenhuma animação infinita.** Ela trava o `waitForIdle` dos testes de componente. Carregamento é
  esqueleto estático, sem shimmer e sem spinner.
- **Sem emoji.** Os ícones são marcas Unicode em IBM Plex Mono, e a lista está no `readme.md`.
- Escala tipográfica fechada em 10/12/14/16/20/28; raio com teto de 10dp; elevação 8 só para janela,
  diálogo, menu e overlay.

## Ao terminar

Registrar a mudança **no mesmo commit**, nos dois documentos: a seção da tela no protótipo e, quando
a tela tem kit, o `.jsx` em `ui_kits/desktop-app/`. Primitiva nova ou contrato alterado vira
`components/<grupo>/<Nome>.prompt.md` mais a entrada no índice do `readme.md`.

Verificar com `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` e, para comparação visual,
`gradlew.bat generateScreenshots`.

## Para mocks e protótipos descartáveis

Copie `docs/design-system/styles.css`, `tokens/` e `assets/` para junto do HTML e monte a página com
as primitivas de `components/`. `_ds_local.js` monta os componentes sem o bundle compilado.
