# Usage Monitor — Design System

Visual system for **Usage Monitor**, a Kotlin Multiplatform + Compose Desktop app that
tracks consumption, balance and quotas of AI tools and APIs in one panel.

The product monitors remote and local integrations (Anthropic, Codex, MiniMax, DeepSeek,
OpenCode Zen Free, OpenCode Go, Kilo Free), keeps history in SQLite, auto-refreshes every 10 minutes,
supports card reorder/minimize, light/dark theme, PT/EN, auto-start on Windows/Linux/macOS,
and update checks through GitHub Releases. Optional self-hosted team server aggregates the
same Anthropic account across machines.

## Sources this system was built from

| Source | What was read |
| --- | --- |
| `uploads/prototipo-visual-opencode.html` (attached by the owner) | The approved visual prototype: full token board, 17 shared primitives, 24 screens, PDF layout, light theme. **This file is the ground truth for every value here.** |
| https://github.com/edilsonvilarinho/usage-monitor | Repository README (product behaviour, integrations, persisted preferences, architecture, update lifecycle, installer constraints). Read over the public web — the GitHub tools were not connected in this session, so **no Kotlin source or Compose theme file was read directly**. |

Not read: `src/**` Kotlin/Compose sources, `img/*.png` product screenshots, `UI_REDESIGN_PLAN.md`,
`server/`. See CAVEATS at the bottom.

---

## CONTENT FUNDAMENTALS

**Language.** Interface copy is **Portuguese (pt-BR)**; code identifiers are English, comments
Portuguese (the repo's own rule). The app also ships EN, so no copy may depend on Portuguese
word length. Numbers follow pt-BR: comma decimal, thin space thousands, `US$ 3,1841`.

**Voice.** Third person, impersonal, no "you". The app states facts about the machine, never
about the user: "Limite de requisições atingido", not "Você atingiu o limite".

**Casing.** Sentence case for titles, tabs, buttons and prose. UPPERCASE only in mono-10
eyebrows and column headers, with `.07em` tracking. Never small caps, never uppercase above 10px.

**Every state carries a word.** `Normal` · `Atenção` · `Crítico` · `Desconectado` ·
`Saudável` · `Saturada` · `Trabalhando agora` · `Conectado`. A colored dot alone is never
a status.

**A disabled control always says why.** Not "Indisponível" but the actual reason:
"macOS: o DMG não tem Developer ID…", "Instalação .deb: aqueles arquivos pertencem ao
gerenciador de pacotes…". A grey control without an explanation is worse than no control.

**Explain the measure, not just the number.** "Janela 5h — ancorada no reset da conta"
(not the last five wall-clock hours). "Custo estimado a preço de tabela, não é fatura."
"Pausas maiores que 5 min descartadas." "Saldo não expira." The app reports money and quotas;
an unqualified number invites a wrong decision.

**Never claim an aggregate the app does not compute.** The prototype drew "4 fontes" in green
in the status bar and it was cut: with one source in error the green would lie. Source failure
belongs to a banner.

**No emoji, anywhere.** No exclamation marks. No marketing adjectives. Tone examples:

- Empty: "Nenhum turno nesta janela."
- Partial failure: "Anthropic — Padrão · Limite de requisições atingido. Aguarde antes de tentar de novo."
- Data absent by API shape: "Créditos de uso indisponíveis · A resposta veio sem limite mensal. A linha de créditos não é exibida nesta coleta."
- Privacy: "Não trafega conteúdo de prompt nem de resposta. Só metadados de uso."

---

## VISUAL FOUNDATIONS

**Dark-first.** `:root` carries the full dark palette — the approved reference. Light comes
from `prefers-color-scheme` **and** the explicit `[data-app-theme="light"]` scope, never from
the media query alone, because the app owns a theme switch that must win.

**Surfaces.** Four steps inside ~14% luminance: `--bg` `--surface` `--raised` `--border`.
Depth comes from the **1dp border and from spacing** — not from shadow, not from gradient.
No accent glow on top of surfaces, no tinted cards, no colored card backgrounds.

**Color area.** The six integration accents are fixed (AA 4.5:1 on both surfaces, hue preserved
across themes, ≥20° apart). What changed from the old UI is the **area**: from whole-card fill
to a 2px marker, a chart line, and value text. `--oc` is the color of the monitored OpenCode
integration — never the app's brand color.

**Six accents, seven sources.** The accent is the identity of the *vendor*, not of one card:
OpenCode Zen Free (read from the local SQLite) and OpenCode Go (the paid subscription, read over
HTTP) are two sources that both wear `--oc`. Adding a seventh hue would have to clear AA on both
surfaces and stay ≥20° from the other six — a real cost, paid to distinguish two plans of the same
product. What separates the two cards is the title, and "colour never informs alone" already
guarantees that is enough.

**Type.** Two families. **IBM Plex Mono** for titles, labels, numbers, tables and window chrome
(tabular alignment); **IBM Plex Sans** for explanatory prose only. Weights 400/500/600. Six sizes:
10 · 12 · 14 · 16 · 20 · 28. Every number is `tabular-nums`; money and token counts right-align.

**Space.** 4px grid: 4 · 8 · 12 · 16 · 24 · 32. Two densities — `--s3` padding / `--s2` gap in
the dashboard window (kept narrow beside an editor), `--s4` in every other window (they open wide).
Fixed chrome heights are contracts with Compose: title bar 34, toolbar 34, status bar 30,
update strip 28, control 28.

**Shape.** Radius 4 chip · 6 control · 8 panel · 10 window. **10dp is the ceiling** — the old
10–28dp range is what made every surface read as one big card.

**Elevation.** 0 for data surfaces, 2 rarely, **8 reserved for window, dialog, menu and overlay**.
Hierarchy is read by layer and divider, not by shadow depth.

**Backgrounds.** Flat. No imagery, no illustration, no pattern, no texture, no gradient anywhere
in the product. The only graphics are data: line charts, bar series, a per-hour activity heatmap,
stacked composition bars — all drawn in accent colors on `--raised`.

**Borders and dividers.** 1px `--border` everywhere. A row owns its **bottom** divider (which is
why a list needs no gap and the nested guide comes out continuous). The nested-group stroke is
2dp, drawn per item with `drawBehind` — not `Modifier.border`, which rounds thickness up and
paints after the content.

**Transparency and blur.** Not used in the product. The main window has a user-set opacity
(50–100%) applied to the whole window by the OS, not per element.

**Hover.** Background steps to `--raised`, text from `--muted` to `--fg`. Never a color shift,
never a scale, never a shadow. Close button is the one exception: it fills `--crit` with white.

**Press / focus.** Press has no separate treatment beyond hover. Focus is a 2px `--info` outline
with 1px offset (inset on fields).

**Motion.** 120ms hover/focus · 180ms selection · 240ms expand/collapse, ease
`cubic-bezier(.2,0,.2,1)`. **No infinite animation anywhere** — it hangs `waitForIdle` in the
Compose component tests, and this app's data arrives on a 600s cycle. Loading is a **static
skeleton**, never a shimmer, never a spinner.

**Cards.** There are no "cards" in the decorative sense. There is one data surface: `--surface`
fill, 1px border, radius 8, no shadow, optional 2px source marker in its header.

**Layout rules.** Dashboard is a 2-column grid of panels with `--s3` gap; other windows stack
full-width panels. Every window that slices by time pins its parameters in one 34dp toolbar and
scrolls the content below. Minimum sizes: dashboard 380dp wide (cards-only mode), the seven-column
presence table needs 1030 × 620dp.

**PDF report.** Canonical **dark** layout independent of the interface theme: IBM Plex embedded,
compact header, discreet surface alternation in tables, restricted semantic color. The only place
zebra striping is allowed.

---

## ICONOGRAPHY

**There is no icon library and no icon font.** The app embeds neither an icon font nor an SVG
sprite. Glyphs are **Unicode marks rendered in IBM Plex Mono**, which is already loaded:

`↻` refresh · `–` minimize · `+` expand · `×` close · `□` maximize · `⏱` history ·
`▣` CLI sessions · `◫` team usage · `◉` connected now · `▾ ▸` collapse/expand · `·` empty.

Every glyph button carries its meaning in `aria-label` / `contentDescription` — that is where the
semaphore explanation lives ("1 sessão ativa agora pede atenção: …"), and it is what the Compose
component tests observe. A text button would have nowhere to put it.

**Emoji are never used.** Unicode marks are used as icons, deliberately and only from the list above.

**Brand mark.** Own geometric monogram, built by a deterministic script: three stems joined by a
bowl, reads as **U** and **M** overlapped at large sizes and keeps a distinct silhouette at 16px,
where the tray icon lives. In `assets/`: `mark.svg`, `mark-on-light.svg`, `lockup.svg`, and the
tray states `mark-tray-warn.svg` / `mark-tray-crit.svg`. `ON_TRACK` lights nothing — a permanent
green dot is decoration. This mark comes from the owner's own approved prototype; nothing was
invented here.

---

## Index

| Path | What it is |
| --- | --- |
| `styles.css` | Global entry point. `@import` lines only. |
| `tokens/` | `fonts` `colors` `typography` `spacing` `shape` `motion` `base` |
| `assets/` | Monogram, light variant, lockup, tray badge states |
| `components/core/` | AppButton · AppIconButton · AppPanel (+Header/Body) · AppSourceMark (+Dot) · AppMetric |
| `components/forms/` | AppTextField · AppSwitch · AppTabs · AppSegmentedControl |
| `components/data/` | AppProgressTrack · AppStatusIndicator · AppDataRow (+AppKey/AppValue) · AppDataTable · AppColumnHeader · AppGroupBand |
| `components/feedback/` | AppBanner · AppEmptyState · AppLoadingState · AppErrorState |
| `components/shell/` | AppWindowFrame · AppStatusBar · AppToolbar · AppUpdateStrip · AppSettingsNav |
| `guidelines/` | 21 foundation specimen cards (Colors, Type, Spacing, Patterns, Brand) |
| `ui_kits/desktop-app/` | Click-through recreation: Dashboard, cards-only mode, History, CLI Sessions, Session detail, Team usage, Presence, Settings |
| `_ds_local.js` | Mount helper so cards and kits render before/without the compiled bundle |
| `SKILL.md` | Agent Skills entry point |

### Intentional additions

The prototype names 17 shared primitives but does not enumerate them all. Beyond the ones it
draws explicitly, this system adds four **chrome** components that the prototype's screens use
without naming: `AppWindowFrame`, `AppStatusBar`, `AppToolbar`, `AppSettingsNav` — plus
`AppUpdateStrip` (the prototype's "faixa de atualização — os quatro estados") and
`AppColumnHeader` (its `.colhead` strip). Nothing else was invented.

### The conformance pass — 2026-08-27

Adopting this system across the Compose app (issue #117) produced six primitives, each one born of a
repetition that was measured, not guessed. Three are components; three are Compose `Modifier`
extensions or token objects that have no counterpart here, and are listed for the record.

| Name | What it is | Why it exists |
| --- | --- | --- |
| `AppGroupBand` | Component, `components/data/` | The quiet account sub-band. Team usage and presence both drew it by hand — it is the third step of the surface ladder the prototype draws but does not name. **Not** the panel header: that one speaks loud (`--t12` on `--fg`), this one speaks low (`--t10` on `--muted`), and swapping them inverts the hierarchy. |
| `AppSettingsNav` | Component, `components/shell/` | Already published here; the Compose side had it as two private functions inside the dialog, under another name. The names now match. |
| `AppTooltipSurface` | Component | The bubble anatomy — `--raised`, radius 6, 1px border, the short 2dp overlay — was written out in four places, which is what let two tooltips over the same chart float at different heights. |
| `Modifier.appNestedGroupItem` | Compose-only | Surface + 2dp guide + indent, per list item. It cannot be the panel: that one clips and rounds, and applied per row it would box every child and cut the guide, which stays continuous only because the list has no gap. |
| `Modifier.appSurfaceBlock` | Compose-only | Clip + fill + 1px border with no layout. It is what removed the product's last Material `Card`. Colour is a parameter because hover in this system **is** a neutral-surface swap. |
| `AppChrome` | Compose-only token object | The five fixed heights `tokens/spacing.css` publishes. They lived as three private constants across two files plus a literal in the desktop frame — four owners for one value. |

**What the pass did not convert, and why.** The chrome button is not `AppIconButton`: it fills the
title bar's height, and the icon button is 26dp with a border. The two account bands that carry
seven aligned columns are not panel headers: a header is title, subtitle and actions, with nowhere
to put columns. Stacked composition bars, colour swatches and theme previews keep painting directly —
charts and previews are the one place this system allows it.

---

## CAVEATS

- **No Kotlin/Compose source was read.** Values here come from the approved HTML prototype and
  the repo README. Compose theme files may name things differently; if a token name in
  `AppTheme` disagrees, the Kotlin side wins and this system should be corrected.
- **No product screenshots or the tour GIF were imported** (`img/*.png`, `img/tour.gif`) —
  binaries could not be fetched in this session. Drop them into `assets/` (or connect the
  GitHub repository) and the UI kit can be cross-checked pixel by pixel.
- **Fonts come from Google Fonts**, not from the app's embedded TTFs. If the app ships specific
  IBM Plex files (subset, hinted, or a different version), add them and replace
  `tokens/fonts.css` with local `@font-face` rules.
- The prototype's section 15 lists open questions it does not decide (Compose Desktop 1.7.1 font
  loading signature, the macOS `.icns` validation). Those remain open here too.
- Team-trend and active-time features depend on server 0.6.0+/0.7.0+; the kit shows the
  supported case and the degraded banner, not every server version combination.
