import type { CSSProperties } from 'react';

/**
 * 4dp quota bar. Sits under the label/value line of a quota row.
 * @startingPoint section="Data" subtitle="4dp quota track at each level" viewport="700x140"
 */
export interface AppProgressTrackProps {
  percent: number;
  /** Drawn from the same threshold that produced the row's status word. */
  level?: 'ok' | 'warn' | 'crit' | 'info' | 'neutral';
  color?: string;
  /** Accessible name — the quota's name ("Sessão 5h"). */
  label?: string;
  style?: CSSProperties;
}

export function AppProgressTrack(props: AppProgressTrackProps): JSX.Element;
