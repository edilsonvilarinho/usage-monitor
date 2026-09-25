import type { CSSProperties, ReactNode } from 'react';

/**
 * A glyph option: emoji or short word, no label, one choice among several
 * (radio). The name goes in `description` (aria-label). The selected option
 * carries a mark besides the highlight, with its slot reserved in every option.
 * Compose: `AppGlyphChip`.
 * @startingPoint section="Forms" subtitle="Account emoji option" viewport="700x100"
 */
export interface AppGlyphChipProps {
  description: string;
  selected?: boolean;
  onClick?: () => void;
  children: ReactNode;
  style?: CSSProperties;
}

export function AppGlyphChip(props: AppGlyphChipProps): JSX.Element;
