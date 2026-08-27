import type { ReactNode, CSSProperties } from 'react';

/**
 * Boxed single number: burn rate, totals, cache savings, minimized-card quota badge.
 * @startingPoint section="Core" subtitle="Boxed metric — label, tabular value, hint" viewport="700x130"
 */
export interface AppMetricProps {
  /** Mono 10, uppercase, tracked. Names the unit as well as the thing. */
  label: ReactNode;
  /** Always tabular-nums so a row of metrics aligns. */
  value: ReactNode;
  hint?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  align?: 'start' | 'center';
  style?: CSSProperties;
}

export function AppMetric(props: AppMetricProps): JSX.Element;
