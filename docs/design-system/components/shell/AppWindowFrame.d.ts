import type { ReactNode, CSSProperties } from 'react';

/**
 * A desktop window: radius 10 (the ceiling), elevation 8, 34dp title bar with
 * the monogram, and a body at one of the two densities.
 * @startingPoint section="Shell" subtitle="Desktop window frame with title bar" viewport="700x300"
 */
export interface AppWindowFrameProps {
  title?: ReactNode;
  /**
   * false = cards-only mode: no title bar, no footer, the app is the size of what
   * it reports. The frame returns as an overlay strip while the pointer is in the
   * top 34px — plus the tray item and Ctrl+Shift+M. Three ways back, not one.
   */
  chrome?: boolean;
  showMinimize?: boolean;
  showMaximize?: boolean;
  width?: number | string;
  /** true = --s3 padding / --s2 gap: the dashboard window, kept narrow beside an editor. */
  dense?: boolean;
  children?: ReactNode;
  /** An <AppStatusBar>. */
  footer?: ReactNode;
  style?: CSSProperties;
}

export function AppWindowFrame(props: AppWindowFrameProps): JSX.Element;
