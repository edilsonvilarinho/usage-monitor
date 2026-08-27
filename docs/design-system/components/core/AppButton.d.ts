import type { ReactNode, CSSProperties } from 'react';

/**
 * Text button. Rectangle, radius 6, height 28dp. The only four variants the app has.
 * @startingPoint section="Core" subtitle="Text button — primary, default, ghost, danger" viewport="700x160"
 */
export interface AppButtonProps {
  /** primary = one per surface, the committing action. ghost = toolbars and status bars. danger = destructive. */
  variant?: 'primary' | 'default' | 'ghost' | 'danger';
  disabled?: boolean;
  /** Stretch to the container width — settings rows and dialog footers. */
  fullWidth?: boolean;
  /** Small glyph before the label. Never an emoji. */
  leading?: ReactNode;
  children?: ReactNode;
  onClick?: () => void;
  /** Native tooltip. On disabled buttons this MUST carry the reason. */
  title?: string;
  style?: CSSProperties;
}

export function AppButton(props: AppButtonProps): JSX.Element;
