import type { CSSProperties } from 'react';

/**
 * Lateral navigation for the Settings dialog: one section composed at a time,
 * never a single column of stacked cards. With every section mounted the nav
 * would be decoration.
 * @startingPoint section="Shell" subtitle="Settings lateral navigation" viewport="700x230"
 */
export interface AppSettingsNavProps {
  items: Array<{ id: string; label: string } | string>;
  value: string;
  onChange?: (id: string) => void;
  style?: CSSProperties;
}

export function AppSettingsNav(props: AppSettingsNavProps): JSX.Element;
