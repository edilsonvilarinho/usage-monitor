import type { ReactNode, CSSProperties } from 'react';

/**
 * STATIC skeleton — no shimmer, no spinner. An endless animation hangs
 * waitForIdle in the Compose component tests, and this app's data arrives on a
 * 600s cycle: a pulsing rectangle is noise, not feedback.
 * @startingPoint section="Feedback" subtitle="Static skeleton loading state" viewport="700x180"
 */
export interface AppLoadingStateProps {
  lines?: number;
  message?: ReactNode;
  style?: CSSProperties;
}

export function AppLoadingState(props: AppLoadingStateProps): JSX.Element;
