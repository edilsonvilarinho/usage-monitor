import type { CSSProperties } from 'react';

export type AppStepStatus = 'done' | 'failed' | 'notReached' | 'unknown';

export interface AppStep {
  label: string;
  status: AppStepStatus;
  /** What happened at this step; shown by AppStepList only. */
  detail?: string;
}

/**
 * Where a multi-step operation stopped (issue #494: the connection to the team
 * server). Compact trail on one wrapping line; AppStepList is the full form.
 * @startingPoint section="Feedback" subtitle="Where a connection stopped" viewport="700x120"
 */
export interface AppStepTrailProps {
  steps: AppStep[];
  style?: CSSProperties;
}

export function AppStepTrail(props: AppStepTrailProps): JSX.Element;
export function AppStepList(props: AppStepTrailProps): JSX.Element;
