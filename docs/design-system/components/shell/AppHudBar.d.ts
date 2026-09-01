import type { ReactNode, CSSProperties } from 'react';

/**
 * One 24dp line, anchored to the top edge of the screen. The third window
 * chrome (issue #164), one step past cards-only mode: no title, no card, no
 * drag-to-reorder — just the worst risk across every quota, the source that
 * produced it, and the reset countdown. The whole strip is the click target
 * that returns the full window.
 * @startingPoint section="Shell" subtitle="24dp HUD strip — top-anchored" viewport="700x110"
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
