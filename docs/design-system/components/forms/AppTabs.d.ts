import type { CSSProperties } from 'react';

/**
 * Underlined tabs for switching the CONTENT of a window (Sessões / Resumo / Tendência).
 * Underline, never a pill.
 * @startingPoint section="Forms" subtitle="Underlined content tabs" viewport="700x100"
 */
export interface AppTabItem { id: string; label: string; }

export interface AppTabsProps {
  items: Array<AppTabItem | string>;
  value: string;
  onChange?: (id: string) => void;
  style?: CSSProperties;
}

export function AppTabs(props: AppTabsProps): JSX.Element;
