import type { ReactNode, CSSProperties } from 'react';

/**
 * A 320×24dp pill, docked to the top-right corner of the screen. The third
 * window chrome (issue #164), one step past cards-only mode: no title, no
 * card, no drag-to-reorder — just the worst risk across every quota, the
 * source that produced it, and the reset countdown. The whole pill is the
 * click target that returns the full window.
 *
 * Full width was the first version; always-on-top plus edge-to-edge covered
 * whatever another window had in its own top 24dp. Fixed-width pill in one
 * corner is the fix — desktop windows have no partial click-through. The
 * component doesn't own its width: it fills whatever the host gives it, and
 * `sourceLabel`/`resetLabel` truncate rather than force it wider.
 * @startingPoint section="Shell" subtitle="320×24dp HUD pill — corner-docked" viewport="700x110"
 */
export interface AppHudBarProps {
  level?: 'ok' | 'warn' | 'crit';
  /** The word next to the dot — "Normal", "Atenção", "Crítico". */
  label: ReactNode;
  /** The winning source, e.g. "Anthropic · Padrão". Omitted while loading. */
  sourceLabel?: ReactNode;
  /** The reset countdown, e.g. "reset em 42min". Omitted while loading. */
  resetLabel?: ReactNode;
  /** Fires on a click anywhere on the strip — restores the full window. */
  onOpen?: () => void;
  style?: CSSProperties;
}

export function AppHudBar(props: AppHudBarProps): JSX.Element;
