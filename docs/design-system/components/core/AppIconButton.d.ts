import type { ReactNode, CSSProperties } from 'react';

/**
 * Square 26dp icon button. Card actions, window chrome, row affordances.
 * @startingPoint section="Core" subtitle="26dp square icon button, ghost and bordered" viewport="700x120"
 */
export interface AppIconButtonProps {
  /** The glyph. Unicode mark or inline SVG — never an emoji. */
  glyph: ReactNode;
  /**
   * Accessible name AND tooltip, in one string. Card navigation buttons carry the
   * full explanation here (e.g. "1 sessão ativa agora pede atenção: …") — a text
   * button would have nowhere to put it, which is why these are icon buttons.
   */
  label: string;
  variant?: 'default' | 'ghost';
  size?: number;
  disabled?: boolean;
  onClick?: () => void;
  style?: CSSProperties;
}

export function AppIconButton(props: AppIconButtonProps): JSX.Element;
