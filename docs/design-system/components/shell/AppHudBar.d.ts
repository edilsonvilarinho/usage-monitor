import type { ReactNode, CSSProperties } from 'react';

/**
 * A 24dp-tall pill, as wide as its own content and never wider than 320dp,
 * dragged to wherever the user wants it. The third window chrome (issue #164),
 * one step past cards-only mode: no title, no card, no drag-to-reorder — just
 * the worst risk across every quota, the source that produced it, and the reset
 * countdown. A short click anywhere on the pill returns the full window; a drag
 * moves it, and on release it snaps to the nearest work-area edge.
 *
 * Two versions were found wrong live before this one. Full width: always-on-top
 * plus edge-to-edge covered whatever another window had in its own top 24dp. A
 * fixed 320dp corner pill: it still measured 320dp to show the word "Normal",
 * in the very corner where IDEs and browsers put controls. Desktop windows have
 * no partial click-through, so the only mitigation is to occupy less area and
 * let the user pick the corner. The component doesn't own its width: it fills
 * whatever the host gives it, and `sourceLabel`/`resetLabel` truncate rather
 * than force it wider.
 * @startingPoint section="Shell" subtitle="HUD pill — draggable, content-sized" viewport="700x260"
 */
export interface AppHudBarProps {
  level?: 'ok' | 'warn' | 'crit';
  /** The word next to the dot — "Normal", "Atenção", "Crítico". */
  label?: ReactNode;
  /** The winning source, e.g. "Anthropic · Padrão". Omitted while loading. */
  sourceLabel?: ReactNode;
  /** The reset countdown, e.g. "reset em 42min". Omitted while loading. */
  resetLabel?: ReactNode;
  /**
   * Every source is on track: collapse to the dot alone (`AppStatusDot`).
   * The data does not vanish — it stops occupying screen while it says
   * everything is fine, and hover brings the whole pill back.
   */
  dotOnly?: boolean;
  /**
   * Hover state. The host grows the *window* rather than opening a popup: a
   * popup on this platform is a layer inside the window, clipped to its bounds,
   * and in a 24dp-tall window it landed on top of its own trigger and flickered.
   */
  expanded?: boolean;
  /** Every monitored source, worst first. Empty means no panel is drawn. */
  sources?: Array<{ label: ReactNode; statusLabel: ReactNode; level?: 'ok' | 'warn' | 'crit' }>;
  /** Fires on a short click anywhere on the pill — restores the full window. */
  onOpen?: () => void;
  style?: CSSProperties;
}

export function AppHudBar(props: AppHudBarProps): JSX.Element;
