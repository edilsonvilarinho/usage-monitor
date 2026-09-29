import type { CSSProperties } from 'react';
import type { AppRingArc } from './AppUsageRing';

/** HUD-only decorative scene around fixed, faithful quota arcs. */
export interface AppGargantuaRingProps {
  arcs: AppRingArc[];
  size?: number;
  stroke?: number;
  gap?: number;
  provider?: string;
  /** A colour readable over the always-dark core; preserves the account's choice. */
  providerColor?: string;
  active?: boolean;
  refreshing?: boolean;
  attentionIndex?: number;
  /** Off by default for deterministic captures. */
  continuous?: boolean;
  reduced?: boolean;
  label?: string;
  style?: CSSProperties;
}

export declare function AppGargantuaRing(props: AppGargantuaRingProps): JSX.Element;
