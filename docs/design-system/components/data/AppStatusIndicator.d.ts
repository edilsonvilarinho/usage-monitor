import type { ReactNode, CSSProperties } from 'react';

/**
 * Dot + word. The word is not optional: color reinforces, it never informs alone.
 * @startingPoint section="Data" subtitle="Status — dot plus word, five levels" viewport="700x110"
 */
export interface AppStatusIndicatorProps {
  level?: 'ok' | 'warn' | 'crit' | 'info' | 'off';
  /** The word. "Normal", "Atenção", "Crítico", "Desconectado", "Saturada". */
  children: ReactNode;
  /**
   * The explanation: which quota produced the worst state and the projection
   * behind the label. On a card header this tooltip is the feature.
   */
  title?: string;
  style?: CSSProperties;
}

export function AppStatusIndicator(props: AppStatusIndicatorProps): JSX.Element;
