import type { ReactNode, CSSProperties } from 'react';

/**
 * Persistent inline notice: configuration problem, partial-collection failure,
 * available release. Not a toast — it stays until the condition clears.
 * @startingPoint section="Feedback" subtitle="Banner — info, warn, crit, with action" viewport="700x210"
 */
export interface AppBannerProps {
  level?: 'info' | 'warn' | 'crit' | 'neutral';
  /** Mono 12 semibold. Names the source and the fact. */
  title: ReactNode;
  /** Sans 12. One or two sentences of consequence — what the user now sees or does not see. */
  children?: ReactNode;
  /** A ghost <AppButton>: "Tentar de novo", "Ver release". */
  action?: ReactNode;
  style?: CSSProperties;
}

export function AppBanner(props: AppBannerProps): JSX.Element;
