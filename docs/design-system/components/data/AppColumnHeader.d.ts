import type { ReactNode, CSSProperties } from 'react';

/**
 * The caption strip above a list of AppDataRow. The caption belongs to the COLUMN,
 * not the cell: repeated in every row it doubles the text on screen.
 * @startingPoint section="Data" subtitle="Column caption strip for row lists" viewport="700x120"
 */
export interface AppColumnHeaderItem {
  label: ReactNode;
  flex?: number;
  width?: number;
  align?: 'left' | 'right' | 'center';
}

export interface AppColumnHeaderProps {
  items: Array<AppColumnHeaderItem | string>;
  /** Left offset in px so captions line up past the source marker. Default 14. */
  offset?: number;
  style?: CSSProperties;
}

export function AppColumnHeader(props: AppColumnHeaderProps): JSX.Element;

/**
 * Legenda ordenável (#537). `sortDescending` null = coluna inativa; true = descendente; false = ascendente.
 * `aria-description` carrega a direção, porque a seta sozinha não informa.
 */
export interface AppSortableColumnHeaderLabelProps {
  label: ReactNode;
  /** null = inativa; true = maior primeiro; false = menor primeiro. */
  sortDescending?: boolean | null;
  onClick?: () => void;
  style?: CSSProperties;
}

export function AppSortableColumnHeaderLabel(props: AppSortableColumnHeaderLabelProps): JSX.Element;
