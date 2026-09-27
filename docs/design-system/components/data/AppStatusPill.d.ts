import type { ReactNode, CSSProperties } from 'react';

/**
 * Dot + word on a tone-tinted background (8%), radius 4. The HUD's per-account
 * state (issue #322). The word is not optional: color reinforces, it never informs alone.
 * @startingPoint section="Data" subtitle="Status pill — dot plus word on a tinted chip" viewport="700x110"
 */
export interface AppStatusPillProps {
  level?: 'ok' | 'warn' | 'crit' | 'info' | 'off';
  /** The word. "Normal", "Atenção", "Crítico", "Sem projeção". */
  children: ReactNode;
  title?: string;
  style?: CSSProperties;
}

export function AppStatusPill(props: AppStatusPillProps): JSX.Element;
