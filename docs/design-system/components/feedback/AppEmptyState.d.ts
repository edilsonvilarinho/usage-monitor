import type { ReactNode, CSSProperties } from 'react';

/**
 * Nothing to show, and that is fine.
 * @startingPoint section="Feedback" subtitle="Empty state" viewport="700x180"
 */
export interface AppEmptyStateProps {
  glyph?: ReactNode;
  /** Says what is absent and in which slice. Never "Nenhum dado". */
  message: ReactNode;
  action?: ReactNode;
  style?: CSSProperties;
}

export function AppEmptyState(props: AppEmptyStateProps): JSX.Element;
