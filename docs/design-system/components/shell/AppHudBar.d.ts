import type { ReactNode, CSSProperties } from 'react';

/**
 * The HUD panel: one 20dp line per monitored source — state word, name, percent
 * and reset — plus an optional footer with what the machine actually burned in
 * the current 5h window. Dragged to wherever the user wants it; on release it
 * snaps to the nearest work-area edge. A short click anywhere returns the full
 * window.
 *
 * Three content versions were found wrong live, one at a time. A single line
 * with only the worst source: with several accounts, the others had no signal
 * they existed. The others behind a hover tooltip: the data sat behind a
 * gesture, and the popup flickered — a popup here is a layer *inside* the
 * window, clipped to its bounds, and in a 24dp-tall window it landed on top of
 * its own trigger. The list with no consumption: quota is the provider's
 * ceiling, and what the machine spent appeared nowhere.
 *
 * The component doesn't own its width: it fills whatever the host gives it, and
 * the source name truncates rather than force it wider. The host measures the
 * window from these same labels (mono type makes the advance calculable), caps
 * it at 420dp, and resizes.
 * @startingPoint section="Shell" subtitle="HUD panel — one line per source" viewport="700x320"
 */
export interface AppHudBarProps {
  /** Tone of the dot in the collapsed state; each row carries its own. */
  level?: 'ok' | 'warn' | 'crit' | 'off';
  /** Every monitored source, worst first. Empty renders the loading line. */
  sources?: Array<{
    label: ReactNode;
    statusLabel: ReactNode;
    level?: 'ok' | 'warn' | 'crit';
    /** Consumption of the quota that decided the state, e.g. "92%". */
    percentLabel: ReactNode;
    /** Short reset, e.g. "Ter 22h59". Absent hides the column — never a dash. */
    resetLabel?: ReactNode;
  }>;
  /** Word of the single line shown before the first collection lands. */
  fallbackLabel?: ReactNode;
  /** Session summary line; absent draws neither divider nor row. */
  footerLabel?: ReactNode;
  /**
   * Every source on track and no pointer over it: collapse to the dot alone.
   * The data does not vanish — it stops occupying screen while it says
   * everything is fine, and hover brings the whole panel back.
   */
  dotOnly?: boolean;
  /** Fires on a short click anywhere on the panel — restores the full window. */
  onOpen?: () => void;
  style?: CSSProperties;
}

export function AppHudBar(props: AppHudBarProps): JSX.Element;
