import type { ReactNode, CSSProperties } from 'react';

/**
 * A whole surface failed to load. Partial failure of ONE integration uses
 * AppBanner instead — the other cards keep showing their numbers.
 * @startingPoint section="Feedback" subtitle="Error state with retry" viewport="700x200"
 */
export interface AppErrorStateProps {
  message: ReactNode;
  detail?: ReactNode;
  /** Between the message and the detail: the team windows put an AppStepTrail here (#494). */
  children?: ReactNode;
  /** A default <AppButton>: "Tentar novamente". */
  action?: ReactNode;
  style?: CSSProperties;
}

export function AppErrorState(props: AppErrorStateProps): JSX.Element;
