import type { ReactNode, CSSProperties } from 'react';

/**
 * The HUD: **one 20dp row per account.** Each row carries dot and word for that
 * account's worst quota, the account name, and one dot per quota beside its
 * percentage. At rest only the first account in the user's card order is drawn;
 * hovering draws them all, with the window growing interpolated rather than in
 * one jump.
 *
 * The per-quota dot without a word has an exact precedent: it is the card's own
 * design — a dot per quota, plus one header badge with dot and word summarising
 * the worst. The row's word plays that badge's part, so color never states
 * anything the row has not already said in writing. Dragged to wherever the user wants it; on release
 * it snaps to the nearest work-area edge. A short click anywhere returns the
 * full window.
 *
 * Five content versions, four corrected after using it. A single line with the
 * worst source: the other accounts had no signal they existed. Those others
 * behind a hover tooltip: a popup here is a layer *inside* the window, clipped
 * to its bounds, so it landed on top of its own trigger and flickered. One row
 * per *source*, always visible: an account with both a 5h and a 7d window still
 * showed a single limit. One row per *quota* plus a spend footer, always
 * visible: ten rows on screen to say what fits in one. What stuck joins the two
 * halves that were right.
 *
 * The component doesn't own its width: it fills whatever the host gives it, and
 * the name truncates rather than force it wider. The host measures the window
 * from these same labels (mono type makes the advance calculable), caps it at
 * 420dp, and resizes.
 * @startingPoint section="Shell" subtitle="HUD — one line, list on hover" viewport="700x320"
 */
export interface AppHudBarProps {
  /** Tone of the dot in the collapsed state; each row carries its own. */
  level?: 'ok' | 'warn' | 'crit' | 'off';
  /**
   * One entry per account, in the user's card order. At rest only the **first**
   * is drawn; hovering draws them all.
   */
  sources?: Array<{
    /** Profile or source name, without any quota label. */
    label: ReactNode;
    /** Word for the **worst** quota of that account — the card badge's role. */
    statusLabel: ReactNode;
    level?: 'ok' | 'warn' | 'crit' | 'off';
    /** Every quota of that account, in the API's order. */
    quotas?: Array<{ text: ReactNode; level?: 'ok' | 'warn' | 'crit' | 'off' }>;
  }>;
  /** Word of the single line shown before the first collection lands. */
  fallbackLabel?: ReactNode;
  /** The pointer is over the bar: the list replaces the single line. */
  expanded?: boolean;
  /**
   * Every quota on track and no pointer over it: collapse to the dot alone.
   * The data does not vanish — it stops occupying screen while it says
   * everything is fine, and hover brings the panel back.
   */
  dotOnly?: boolean;
  /** Fires on a short click anywhere — restores the full window. */
  onOpen?: () => void;
  style?: CSSProperties;
}

export function AppHudBar(props: AppHudBarProps): JSX.Element;
