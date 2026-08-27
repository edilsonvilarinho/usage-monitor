import type { ReactNode, CSSProperties } from 'react';

/**
 * Tabular data: history metrics, per-axis breakdowns, tool rankings, presence.
 * Mono 12, tabular-nums, numeric columns right-aligned, no zebra striping on screen.
 * @startingPoint section="Data" subtitle="Data table with numeric columns" viewport="700x260"
 */
export interface AppDataTableColumn {
  key: string;
  label: ReactNode;
  /** Right-align and treat as a number. Money and token counts are always numeric. */
  numeric?: boolean;
}

export interface AppDataTableProps {
  columns: AppDataTableColumn[];
  rows: Array<Record<string, ReactNode> & { id?: string | number }>;
  style?: CSSProperties;
}

export function AppDataTable(props: AppDataTableProps): JSX.Element;
