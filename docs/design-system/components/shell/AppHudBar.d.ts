import type { CSSProperties } from 'react';
import type { AppRingLevel } from '../data/AppUsageRing';

export interface AppHudQuota {
  /** `5h`, `7d`, `Saldo` — last word of the quota label. */
  short: string;
  /** The card's truncated percentage. */
  percent: string;
  fraction: number;
  level: AppRingLevel;
  /** Short reset time, only drawn when expanded; absent prints nothing. */
  reset?: string;
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
}

/**
 * The HUD notch (Compose: `HudNotch` in its own transparent window,
 * `HudWindowHost`). Docked to a screen edge — flat and flush there, rounded on
 * the inner side, concave shoulders joining the two. At rest: per account, a
 * usage ring, the focus percentage and the status word. Hovered: it unfolds on
 * the `EXPRESSIVE` spring into one block per account with a row per quota.
 */
export interface AppHudBarProps {
  accounts?: AppHudAccount[];
  edge?: 'top' | 'bottom' | 'left' | 'right';
  expanded?: boolean;
  fallbackLabel?: string;
  /** `02:05` — next automatic collection, once, at the end of the strip. */
  countdown?: string;
  /** Pending update sentence; the icon has no click of its own. */
  update?: string;
  style?: CSSProperties;
}

export declare function AppHudBar(props: AppHudBarProps): JSX.Element;
