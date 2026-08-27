import type { ReactNode, CSSProperties } from 'react';

/**
 * 30dp status bar. On the window: version, refresh countdown, global refresh,
 * settings. On a card: the navigation icon buttons.
 * @startingPoint section="Shell" subtitle="30dp status bar" viewport="700x110"
 */
export interface AppStatusBarProps {
  left?: ReactNode;
  right?: ReactNode;
  position?: 'bottom' | 'top';
  /** Full manual control — overrides left/right. */
  children?: ReactNode;
  style?: CSSProperties;
}

export function AppStatusBar(props: AppStatusBarProps): JSX.Element;
