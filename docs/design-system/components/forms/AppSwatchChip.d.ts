import type { CSSProperties } from 'react';

/**
 * A colour option: round swatch + label, one choice among several (radio).
 * `swatch` null is the "Default" option (the vendor accent), drawn as an empty
 * ring. The selected option carries a mark besides the highlight.
 * Compose: `AppSwatchChip`.
 * @startingPoint section="Forms" subtitle="Account colour option" viewport="700x100"
 */
export interface AppSwatchChipProps {
  label: string;
  swatch: string | null;
  selected?: boolean;
  onClick?: () => void;
  style?: CSSProperties;
}

export function AppSwatchChip(props: AppSwatchChipProps): JSX.Element;
