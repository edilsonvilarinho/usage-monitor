import type { ReactNode, CSSProperties } from 'react';

/**
 * One 34dp strip holding every parameter of the view below it — source, account,
 * range, filter, sort, export. Three controls that pick parameters of the same
 * content live in the same strip; totals go in the scrollable area.
 * @startingPoint section="Shell" subtitle="34dp parameter toolbar" viewport="700x110"
 */
export interface AppToolbarProps {
  children?: ReactNode;
  style?: CSSProperties;
}

export function AppToolbar(props: AppToolbarProps): JSX.Element;
