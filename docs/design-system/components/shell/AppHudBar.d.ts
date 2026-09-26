import type { CSSProperties } from 'react';
import type { AppRingLevel } from '../data/AppUsageRing';

export interface AppHudQuota {
  /** `5h`, `7d`, `Saldo` — last word of the quota label, for the ring description. */
  short: string;
  /** The card's quota title in the balloon: `Sessão 5h`, `Semanal`. */
  title?: string;
  /** The card's truncated percentage. */
  percent: string;
  fraction: number;
  level: AppRingLevel;
  /** Short reset time, drawn in the balloon as "Reinicia …"; absent prints nothing. */
  reset?: string;
  /** "68% usado · 32% restante"; absent for balances and observed activity. */
  usedLeft?: string;
  forecast?: boolean;
}

export interface AppHudAccount {
  label: string;
  /** Word of the account's worst quota — always shown, even at rest. */
  statusLabel: string;
  level: AppRingLevel;
  quotas: AppHudQuota[];
  /** Index of the quota whose percent the notch prints: worst risk, then highest percent. */
  focus?: number;
  /** CLI session with a turn in the last 5 minutes. */
  active?: boolean;
  /** Plan and origin of the reading: "Plus · via Codex". */
  detail?: string;
}

/**
 * The HUD notch (Compose: `HudNotch` in its own transparent window,
 * `HudWindowHost`). Docked to a screen edge — flat and flush there, rounded on
 * the inner side, concave shoulders joining the two. The notch never grows: per
 * account, a usage ring, the focus percentage and the status word. Hovered, the
 * move hand and the gear appear past its ends, and a balloon for **one**
 * account — the ring under the pointer — or for the gear opens beside it.
 */
export interface AppHudBarProps {
  accounts?: AppHudAccount[];
  edge?: 'top' | 'bottom' | 'left' | 'right';
  /** Which balloon is open: an account index, `'gear'`, or none (resting). */
  balloon?: number | 'gear';
  fallbackLabel?: string;
  /** `02:05` — next automatic collection, once, at the end of the strip. */
  countdown?: string;
  /** Pending update sentence. On the notch it is only the gear's dot and its label (#291) — no click. */
  update?: string;
  /** The same notice split for the gear balloon's banner: one-line title… */
  updateHeadline?: string;
  /** …and a detail of up to two lines. */
  updateDetail?: string;
  /** The update strip's action label, a button in the gear balloon only (none while downloading). */
  updateAction?: string;
  /** Glyphs of the card's buttons shown in an account balloon (refresh is always last). */
  actions?: string[];
  style?: CSSProperties;
}

export declare function AppHudBar(props: AppHudBarProps): JSX.Element;
