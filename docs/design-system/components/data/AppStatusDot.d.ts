import type { CSSProperties } from 'react';

/**
 * The dot of `AppStatusIndicator`, on its own.
 *
 * Exists for the HUD pill, which collapses to the dot while every source is on
 * track: data that says "everything is fine" does not need to occupy screen
 * until it stops being true. Extracted from `AppStatusIndicator`, which now
 * consumes it — two anatomies for the same dot would drift apart.
 *
 * This does not loosen "color never informs alone". It is the one place in the
 * system where the dot appears without its word, and there the word is one
 * mouse movement away: the pill comes back whole on hover. Do not use it in a
 * list, a cell or a header — there the indicator with its word is still right.
 * @startingPoint section="Data" subtitle="Status dot — the collapsed HUD state" viewport="700x110"
 */
export interface AppStatusDotProps {
  level?: 'ok' | 'warn' | 'crit' | 'info' | 'off';
  /** The word the dot is standing in for, as a native tooltip. */
  title?: string;
  style?: CSSProperties;
}

export function AppStatusDot(props: AppStatusDotProps): JSX.Element;
