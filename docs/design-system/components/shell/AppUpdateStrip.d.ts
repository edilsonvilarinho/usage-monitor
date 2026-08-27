import type { ReactNode, CSSProperties } from 'react';

/**
 * One 28dp line at the top of the dashboard carrying the update lifecycle.
 * Four states, one line — the update is silent by construction, so the strip is
 * the only place it is visible before the release-notes dialog.
 * @startingPoint section="Shell" subtitle="28dp update strip — four states" viewport="700x200"
 */
export interface AppUpdateStripProps {
  state?: 'available' | 'downloading' | 'ready' | 'failed';
  message: ReactNode;
  /** 0-100. Only meaningful while downloading (~120 MB per version, no delta). */
  progress?: number;
  action?: ReactNode;
  style?: CSSProperties;
}

export function AppUpdateStrip(props: AppUpdateStripProps): JSX.Element;
