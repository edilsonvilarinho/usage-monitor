import type { CSSProperties } from 'react';

/**
 * Time-window picker: 5h / 7 dias / 30 dias / Total. A parameter of the content,
 * not a change of content — that is what separates it from AppTabs.
 * @startingPoint section="Forms" subtitle="Time-window segmented control" viewport="700x100"
 */
export interface AppSegmentedControlProps {
  items: Array<{ id: string; label: string } | string>;
  value: string;
  onChange?: (id: string) => void;
  style?: CSSProperties;
}

export function AppSegmentedControl(props: AppSegmentedControlProps): JSX.Element;
