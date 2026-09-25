import type { CSSProperties } from 'react';

export type AppRingLevel = 'ok' | 'warn' | 'crit' | 'info' | 'off';

export interface AppRingArc {
  /** 0..1 — the ring never turns more than once. */
  fraction: number;
  level: AppRingLevel;
  /** `false` draws the track dashed: no verdict was computed for this quota. */
  forecast?: boolean;
}

/**
 * Usage ring: one arc per quota, concentric, `arcs[0]` outermost. The HUD puts
 * the longest window outside (issue #278). At most three. Never informs alone — the percentage and
 * the status word sit next to it, and `label` carries both for assistive tech.
 * Compose: `AppUsageRing`.
 */
export interface AppUsageRingProps {
  arcs: AppRingArc[];
  size?: number;
  stroke?: number;
  gap?: number;
  /** CLI session with a turn in the last 5 min: thin info arc inside (spins in Compose, only with continuous motion). */
  active?: boolean;
  /** The arc that pulses at Attention or worse — the quota in focus (Compose only). */
  attentionIndex?: number;
  label?: string;
  style?: CSSProperties;
}

export declare function AppUsageRing(props: AppUsageRingProps): JSX.Element;
