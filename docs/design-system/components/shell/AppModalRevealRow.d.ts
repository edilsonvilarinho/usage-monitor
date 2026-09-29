import type { ReactNode, CSSProperties } from 'react';

/**
 * E9 · plasma filaments: a modal row revealed left to right behind a warm
 * filament, in reading order. The data never moves — it is only clipped.
 * @startingPoint section="Shell" subtitle="Modal row reveal (E9)" viewport="700x160"
 */
export interface AppModalRevealRowProps {
  /** Position in reading order (top, then left). */
  index?: number;
  /** How many marked rows the surface has; sets the cascade step. */
  count?: number;
  children?: ReactNode;
  style?: CSSProperties;
}

export function AppModalRevealRow(props: AppModalRevealRowProps): JSX.Element;
