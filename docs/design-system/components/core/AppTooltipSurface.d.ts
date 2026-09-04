import type { ReactNode, CSSProperties } from 'react';

/**
 * The bubble anatomy shared by every floating hint in the app: `--raised`
 * background, radius 6 (`--r2`), a 1dp border and `--shadow-2` — the
 * "tooltip and dropdown menu" elevation step.
 *
 * It exists because that anatomy was written out by hand in four places —
 * the plain-text tooltip, the usage card's metric tooltip, the turn chart's
 * hover bubble and the history chart's hover bubble — and the drift between
 * them is what let two tooltips over the same chart float at different
 * heights. This is the single owner now.
 *
 * **Not `AppMenu`'s surface.** The menu opens on `--surface`; this opens on
 * `--raised`, one rung up the ladder — a menu is a list of actions sitting
 * beside the content it acts on, a tooltip is a footnote floating over the
 * content it explains, and the extra rung is what keeps the two reading as
 * different things when they appear side by side.
 *
 * **Content only — no padding, no max-width.** Every real bubble sizes
 * itself differently (a one-line label, a five-row metric block, a chart
 * annotation), so this component only owns the anatomy, never the layout of
 * what goes inside it.
 * @startingPoint section="Core" subtitle="Tooltip bubble — the shared anatomy" viewport="360x160"
 */
export interface AppTooltipSurfaceProps {
  children?: ReactNode;
  style?: CSSProperties;
}

export function AppTooltipSurface(props: AppTooltipSurfaceProps): JSX.Element;
